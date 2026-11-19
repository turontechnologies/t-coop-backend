package com.turontechnologies.tcoop.membership;

import com.turontechnologies.tcoop.auth.EmailService;
import com.turontechnologies.tcoop.coopgateway.CoopGatewayResolver;
import com.turontechnologies.tcoop.cooperative.CoopDefaultPasswordController;
import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.cooperative.MemberGuarantorRules;
import com.turontechnologies.tcoop.cooperative.MemberIdGenerator;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.notification.NotificationService;
import com.turontechnologies.tcoop.savings.SavingsRecord;
import com.turontechnologies.tcoop.savings.SavingsRecordRepository;
import com.turontechnologies.tcoop.savings.SavingsType;
import com.turontechnologies.tcoop.savings.SavingsTypeRepository;
import com.turontechnologies.tcoop.subscription.PaymentGatewayService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The steps shared by the public join pages and the admin's review screen. */
@Service
public class MembershipApplicationService {

  private static final Logger log = LoggerFactory.getLogger(MembershipApplicationService.class);

  /** A plain error the controller turns into {"error": message} with this status. */
  public static class ApplicationProblem extends RuntimeException {
    private final int status;

    public ApplicationProblem(int status, String message) {
      super(message);
      this.status = status;
    }

    public int status() {
      return status;
    }
  }

  private final RegistrationSettingsRepository settingsRepository;
  private final MembershipApplicationRepository applicationRepository;
  private final ApplicationGuarantorRepository guarantorRepository;
  private final MemberRepository memberRepository;
  private final SavingsTypeRepository savingsTypeRepository;
  private final SavingsRecordRepository savingsRecordRepository;
  private final MemberGuarantorRules guarantorRules;
  private final MemberIdGenerator memberIdGenerator;
  private final CoopGatewayResolver gatewayResolver;
  private final PaymentGatewayService paymentGatewayService;
  private final NotificationService notificationService;
  private final EmailService emailService;
  private final PasswordEncoder passwordEncoder;

  public MembershipApplicationService(
      RegistrationSettingsRepository settingsRepository,
      MembershipApplicationRepository applicationRepository,
      ApplicationGuarantorRepository guarantorRepository,
      MemberRepository memberRepository,
      SavingsTypeRepository savingsTypeRepository,
      SavingsRecordRepository savingsRecordRepository,
      MemberGuarantorRules guarantorRules,
      MemberIdGenerator memberIdGenerator,
      CoopGatewayResolver gatewayResolver,
      PaymentGatewayService paymentGatewayService,
      NotificationService notificationService,
      EmailService emailService,
      PasswordEncoder passwordEncoder) {
    this.settingsRepository = settingsRepository;
    this.applicationRepository = applicationRepository;
    this.guarantorRepository = guarantorRepository;
    this.memberRepository = memberRepository;
    this.savingsTypeRepository = savingsTypeRepository;
    this.savingsRecordRepository = savingsRecordRepository;
    this.guarantorRules = guarantorRules;
    this.memberIdGenerator = memberIdGenerator;
    this.gatewayResolver = gatewayResolver;
    this.paymentGatewayService = paymentGatewayService;
    this.notificationService = notificationService;
    this.emailService = emailService;
    this.passwordEncoder = passwordEncoder;
  }

  /** The co-op's settings, or closed defaults when the admin hasn't set them up. */
  public RegistrationSettings settingsFor(String cooperativeId) {
    return settingsRepository.findById(cooperativeId).orElseGet(() -> new RegistrationSettings(cooperativeId));
  }

  public boolean canPayOnline(String cooperativeId) {
    return gatewayResolver.checkoutFor(cooperativeId).isPresent();
  }

  public String savingsTypeName(RegistrationSettings settings) {
    SavingsType type = savingsTypeFor(settings);
    return type != null ? type.getName() : null;
  }

  public List<ApplicationGuarantor> guarantorsOf(MembershipApplication app) {
    return guarantorRepository.findAllByApplicationId(app.getId());
  }

  public ApplicationViews.ApplicantView view(MembershipApplication app, Cooperative coop) {
    return ApplicationViews.ApplicantView.of(app, coop, settingsFor(coop.getId()), guarantorsOf(app));
  }

  public ApplicationViews.CoopApplyInfo applyInfo(Cooperative coop) {
    RegistrationSettings settings = settingsFor(coop.getId());
    return new ApplicationViews.CoopApplyInfo(
        coop.getId(),
        coop.getName(),
        coop.getLogoUrl(),
        coop.getAddress(),
        isAcceptingApplications(coop, settings),
        settings.getRules(),
        settings.requirementList(),
        settings.getFormFee(),
        settings.getFirstSavings(),
        settings.totalFee(),
        coop.getCurrency(),
        savingsTypeName(settings),
        settings.isRequireIdDocument(),
        settings.getMinAge(),
        ApplicationViews.GuarantorRules.of(coop),
        canPayOnline(coop.getId()));
  }

  /** Open, the co-op is working, and if there's a fee it can be paid online. */
  public boolean isAcceptingApplications(Cooperative coop, RegistrationSettings settings) {
    return settings.isOpen()
        && "Active".equals(coop.getStatus())
        && coop.canOperate()
        && (settings.totalFee().signum() == 0 || canPayOnline(coop.getId()));
  }

  /** Replaces the applicant's guarantors with these (already checked against the co-op's rules). */
  @Transactional
  public void saveGuarantors(MembershipApplication app, List<MemberGuarantorRules.Row> rows) {
    guarantorRepository.deleteAllByApplicationId(app.getId());
    guarantorRepository.flush();
    for (MemberGuarantorRules.Row row : rows) {
      guarantorRepository.save(
          new ApplicationGuarantor(app.getId(), row.guarantorMemberId(), row.name(), row.phone(), row.email()));
    }
  }

  /** What still needs filling in before they can pay, in plain words. Empty when ready. */
  public List<String> missingBeforePayment(
      MembershipApplication app, Cooperative coop, RegistrationSettings settings, List<ApplicationGuarantor> guarantors) {
    List<String> missing = new ArrayList<>();
    if (app.getGender() == null) missing.add("Choose Male or Female");
    if (app.getDateOfBirth() == null) {
      missing.add("Add your date of birth");
    } else if (Period.between(app.getDateOfBirth(), LocalDate.now()).getYears() < settings.getMinAge()) {
      missing.add("You must be " + settings.getMinAge() + " or older to join");
    }
    if (app.getNin() == null) missing.add("Add your NIN (11 numbers)");
    if (app.getHomeAddress() == null) missing.add("Add your home address");
    if (app.getNextOfKinName() == null || app.getNextOfKinPhone() == null) {
      missing.add("Add your next of kin's name and phone number");
    }
    if (app.getPhotoUrl() == null) missing.add("Add your passport photo");
    if (settings.isRequireIdDocument() && app.getIdDocumentUrl() == null) missing.add("Add a picture of your ID card");
    if (coop.isRequireMemberGuarantors()) {
      List<MemberGuarantorRules.Row> rows =
          guarantors.stream()
              .map(g -> new MemberGuarantorRules.Row(g.getGuarantorMemberId(), g.getName(), g.getPhone(), g.getEmail()))
              .toList();
      String problem = MemberGuarantorRules.ruleProblem(coop, rows);
      if (problem != null) missing.add(problem);
    }
    return missing;
  }

  /** Asks the bank whose account it is (via the co-op's Paystack). Quietly skipped if we can't. */
  public void lookUpBankName(MembershipApplication app) {
    if (app.getAccountNumber() == null || app.getBankCode() == null || app.getBankAccountName() != null) return;
    String secret = gatewayResolver.payoutSecretKeyFor(app.getCooperativeId()).orElse(null);
    if (secret == null) return;
    try {
      PaymentGatewayService.BankResolveResult result =
          paymentGatewayService.resolvePaystackBankAccount(app.getAccountNumber(), app.getBankCode(), secret);
      if (result.success() && result.accountName() != null) {
        app.setBankAccountName(result.accountName());
        applicationRepository.save(app);
      }
    } catch (RuntimeException e) {
      log.warn("Couldn't look up bank account for application {}: {}", app.getReference(), e.getMessage());
    }
  }

  /** Paid (or free): the admin and the member guarantors are told, and the applicant gets a receipt. */
  @Transactional
  public void submit(MembershipApplication app, Cooperative coop, BigDecimal amountPaid) {
    app.submit(amountPaid);
    applicationRepository.save(app);

    notificationService.notifyCoopAdmin(
        coop.getId(),
        "MEMBERSHIP_APPLICATION_RECEIVED",
        "New application to join",
        app.getFullName() + " applied to join (" + app.getReference() + "). Open it to check their details and decide.",
        "/members/applications/" + app.getId());

    for (ApplicationGuarantor guarantor : guarantorsOf(app)) {
      if (!guarantor.isMember()) continue;
      notificationService.notify(
          guarantor.getGuarantorMemberId(),
          "MEMBERSHIP_GUARANTOR_REQUEST",
          "Do you know " + app.getFullName() + "?",
          app.getFullName() + " named you as their guarantor to join " + coop.getName()
              + ". Please open your dashboard and say if you know them.",
          "/dashboard");
    }

    Map<String, String> details = new LinkedHashMap<>();
    details.put("Your application number", app.getReference());
    if (amountPaid != null && amountPaid.signum() > 0) details.put("Amount you paid", ApplicationChecks.naira(amountPaid));
    emailService.sendAccountChangeEmail(
        app.getEmail(),
        app.getFullName(),
        "We received your application to join " + coop.getName(),
        "Your application has been sent",
        "thank you for applying to join " + coop.getName() + ". The co-operative will now check your details. "
            + "We will email you when they decide. You can also check at any time with your application number "
            + "and this email address.",
        details);
  }

  /** Makes the applicant a member: login, profile, guarantors, first savings, welcome email. */
  @Transactional
  public Member approve(
      MembershipApplication app, Cooperative coop, String decidedBy, String requestedMemberId, String note) {
    if (!MembershipApplication.SUBMITTED.equals(app.getStatus())) {
      throw new ApplicationProblem(409, "This application has already been decided.");
    }
    memberRepository.findByEmail(app.getEmail()).ifPresent(existing -> {
      throw new ApplicationProblem(409,
          "The email " + app.getEmail() + " already belongs to " + existing.getFullName() + " (" + existing.getId()
              + "). Each member needs their own email address, so this can't be approved as it is.");
    });

    String memberId;
    if (requestedMemberId != null && !requestedMemberId.isBlank()) {
      memberId = requestedMemberId.trim();
      if (memberRepository.findFirstByIdIgnoreCase(memberId).isPresent()) {
        throw new ApplicationProblem(409, "The Membership ID " + memberId + " is already taken. Choose another one.");
      }
    } else {
      memberId = memberIdGenerator.nextFor(coop);
    }

    List<ApplicationGuarantor> guarantors = guarantorsOf(app);
    String password = CoopDefaultPasswordController.startingPasswordFor(coop);
    Member member =
        new Member(memberId, coop.getId(), "member", passwordEncoder.encode(password), app.getFirstName(), app.getLastName(), app.getEmail());
    member.updateProfile(
        app.getFirstName(),
        app.getLastName(),
        app.getOtherName(),
        app.getGender(),
        app.getPhone(),
        app.getEmail(),
        app.getNin(),
        app.getHomeAddress(),
        app.getCountry(),
        app.getState(),
        app.getCity(),
        null,
        null,
        guarantors.isEmpty() ? null : guarantors.stream().map(ApplicationGuarantor::getName).collect(Collectors.joining(", ")),
        app.getNextOfKinName(),
        app.getNextOfKinPhone(),
        null,
        app.getNextOfKinRelationship(),
        null,
        app.getBankCode(),
        app.getAccountNumber(),
        app.getBankAccountName() != null ? app.getBankAccountName() : app.getAccountName());
    member.setAvatarUrl(app.getPhotoUrl());
    memberRepository.saveAndFlush(member);

    if (!guarantors.isEmpty()) {
      guarantorRules.save(
          member,
          coop,
          guarantors.stream()
              .map(g -> new MemberGuarantorRules.Row(g.getGuarantorMemberId(), g.getName(), g.getPhone(), g.getEmail()))
              .toList());
    }

    BigDecimal firstSavings = app.getFirstSavings() == null ? BigDecimal.ZERO : app.getFirstSavings();
    boolean savingsAdded = false;
    if (firstSavings.signum() > 0 && app.getAmountPaid() != null) {
      SavingsType type = savingsTypeFor(settingsFor(coop.getId()));
      SavingsRecord record =
          new SavingsRecord(
              coop.getId(),
              member.getId(),
              type != null ? type.getId() : null,
              firstSavings,
              firstSavings,
              app.getPaymentGateway() != null ? app.getPaymentGateway() : "Paystack",
              app.getPaymentReference(),
              "Success",
              null);
      if (app.getPaidAt() != null) record.setRecordDate(app.getPaidAt().toLocalDate());
      savingsRecordRepository.save(record);
      savingsAdded = true;
    }

    app.approve(decidedBy, note, member.getId());
    applicationRepository.save(app);

    Map<String, String> details = new LinkedHashMap<>();
    details.put("Your Membership ID (use it to log in)", member.getId());
    details.put("Your password", password);
    if (savingsAdded) details.put("Already in your savings", ApplicationChecks.naira(firstSavings));
    emailService.sendAccountChangeEmail(
        app.getEmail(),
        app.getFullName(),
        "Welcome to " + coop.getName() + " — you are now a member",
        "Your application was approved",
        "good news! " + coop.getName() + " has approved your application (" + app.getReference() + "). "
            + "You are now a member. Log in with the details below, then change your password in Settings. "
            + "Keep this email private.",
        details);
    notificationService.notify(
        member.getId(),
        "MEMBER_ADDED",
        "Welcome to " + coop.getName(),
        savingsAdded
            ? "Your application was approved. Your first savings of " + ApplicationChecks.naira(firstSavings) + " is already in your account."
            : "Your application was approved. Welcome!",
        "/profile");
    return member;
  }

  @Transactional
  public void reject(MembershipApplication app, Cooperative coop, String decidedBy, String reason) {
    if (!MembershipApplication.SUBMITTED.equals(app.getStatus())) {
      throw new ApplicationProblem(409, "This application has already been decided.");
    }
    app.reject(decidedBy, reason);
    applicationRepository.save(app);

    String money =
        app.getAmountPaid() != null && app.getAmountPaid().signum() > 0
            ? " Please contact the co-operative about the " + ApplicationChecks.naira(app.getAmountPaid()) + " you paid."
            : "";
    Map<String, String> details = new LinkedHashMap<>();
    details.put("Your application number", app.getReference());
    details.put("Reason", reason);
    emailService.sendAccountChangeEmail(
        app.getEmail(),
        app.getFullName(),
        "Your application to join " + coop.getName(),
        "Your application was not approved",
        "we are sorry, " + coop.getName() + " did not approve your application. The reason is below." + money
            + " You can apply again when you are ready.",
        details);
  }

  /** The savings type the first savings goes into: the admin's choice, else the first active one. */
  private SavingsType savingsTypeFor(RegistrationSettings settings) {
    if (settings.getSavingsTypeId() != null) {
      SavingsType chosen = savingsTypeRepository.findById(settings.getSavingsTypeId()).orElse(null);
      if (chosen != null && "Active".equals(chosen.getStatus())) return chosen;
    }
    return savingsTypeRepository.findAllByCooperativeIdOrderByCreatedAtAsc(settings.getCooperativeId()).stream()
        .filter(type -> "Active".equals(type.getStatus()))
        .findFirst()
        .orElse(null);
  }
}
