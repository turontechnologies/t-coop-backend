package com.turontechnologies.tcoop.membership;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.turontechnologies.tcoop.auth.EmailDeliveryException;
import com.turontechnologies.tcoop.auth.EmailService;
import com.turontechnologies.tcoop.coopgateway.CoopGatewayResolver;
import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.cooperative.MemberGuarantorRules;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.subscription.PaymentGatewayService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The public side of joining a co-op — no login (SecurityConfig lets {@code /api/v1/apply/**}
 * through). The applicant proves their email with a code, which gives them a private key for this
 * one application; every later step needs that key in the {@code X-Application-Token} header.
 */
@RestController
public class PublicApplicationController {

  private static final String TOKEN_HEADER = "X-Application-Token";
  private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
  private static final Set<String> PHOTO_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
  private static final Set<String> ID_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "application/pdf");
  private static final String REFERENCE_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final int MAX_CODE_ATTEMPTS = 5;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final CooperativeRepository cooperativeRepository;
  private final MemberRepository memberRepository;
  private final RegistrationSettingsRepository settingsRepository;
  private final MembershipApplicationRepository applicationRepository;
  private final MembershipApplicationService service;
  private final MemberGuarantorRules guarantorRules;
  private final CoopGatewayResolver gatewayResolver;
  private final PaymentGatewayService paymentGatewayService;
  private final EmailService emailService;
  private final PasswordEncoder passwordEncoder;
  private final Cloudinary cloudinary;

  public PublicApplicationController(
      CooperativeRepository cooperativeRepository,
      MemberRepository memberRepository,
      RegistrationSettingsRepository settingsRepository,
      MembershipApplicationRepository applicationRepository,
      MembershipApplicationService service,
      MemberGuarantorRules guarantorRules,
      CoopGatewayResolver gatewayResolver,
      PaymentGatewayService paymentGatewayService,
      EmailService emailService,
      PasswordEncoder passwordEncoder,
      Cloudinary cloudinary) {
    this.paymentGatewayService = paymentGatewayService;
    this.cooperativeRepository = cooperativeRepository;
    this.memberRepository = memberRepository;
    this.settingsRepository = settingsRepository;
    this.applicationRepository = applicationRepository;
    this.service = service;
    this.guarantorRules = guarantorRules;
    this.gatewayResolver = gatewayResolver;
    this.emailService = emailService;
    this.passwordEncoder = passwordEncoder;
    this.cloudinary = cloudinary;
  }

  public record CodeRequest(@NotBlank(message = "Enter the code from your email") String code) {}

  public record ConfirmPaymentRequest(@NotBlank String reference) {}

  public record VerifiedResponse(String token, ApplicationViews.ApplicantView application) {}

  public record StartResponse(UUID applicationId, String email, boolean resumed) {}

  /** Either a checkout to open, or (no fee / already paid) the submitted application. */
  public record PayResponse(
      boolean submitted,
      String reference,
      BigDecimal amount,
      String gateway,
      String publicKey,
      String email,
      ApplicationViews.ApplicantView application) {}

  public record GuarantorLookup(String memberId, String name) {}

  @ExceptionHandler(MembershipApplicationService.ApplicationProblem.class)
  public ResponseEntity<?> problem(MembershipApplicationService.ApplicationProblem e) {
    return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
  }

  /** Co-ops taking applications, found by name or co-op ID. */
  @GetMapping("/api/v1/apply/cooperatives")
  public ResponseEntity<?> cooperatives(@RequestParam(required = false) String search) {
    String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
    List<ApplicationViews.CoopListing> listings =
        settingsRepository.findAllByOpenTrue().stream()
            .map(settings -> cooperativeRepository.findById(settings.getCooperativeId()).orElse(null))
            .filter(coop -> coop != null && service.isAcceptingApplications(coop, service.settingsFor(coop.getId())))
            .filter(coop -> query.isEmpty()
                || coop.getName().toLowerCase(Locale.ROOT).contains(query)
                || coop.getId().equalsIgnoreCase(query))
            .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
            .limit(50)
            .map(ApplicationViews.CoopListing::of)
            .toList();
    return ResponseEntity.ok(listings);
  }

  /** The rules, what to have ready and the fee — shown before the form. */
  @GetMapping("/api/v1/apply/cooperatives/{coopId}")
  public ResponseEntity<?> cooperative(@PathVariable String coopId) {
    Cooperative coop = findCoop(coopId);
    return ResponseEntity.ok(service.applyInfo(coop));
  }

  /** Step 1: name, email and phone. Sends a code to the email. Carries on an unfinished one. */
  @PostMapping("/api/v1/apply/cooperatives/{coopId}/start")
  public ResponseEntity<?> start(@PathVariable String coopId, @Valid @RequestBody ApplicationDetailsRequest.Start request) {
    Cooperative coop = findCoop(coopId);
    RegistrationSettings settings = service.settingsFor(coop.getId());
    if (!service.isAcceptingApplications(coop, settings)) {
      throw new MembershipApplicationService.ApplicationProblem(409, coop.getName() + " isn't taking applications right now.");
    }
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    Member existing = memberRepository.findByEmail(email).orElse(null);
    if (existing != null) {
      throw new MembershipApplicationService.ApplicationProblem(409,
          "This email address already belongs to a member. If it's yours, log in instead, or use a different email.");
    }

    List<MembershipApplication> earlier =
        applicationRepository.findAllByCooperativeIdAndEmailOrderByCreatedAtDesc(coop.getId(), email);
    MembershipApplication waiting =
        earlier.stream().filter(a -> MembershipApplication.SUBMITTED.equals(a.getStatus())).findFirst().orElse(null);
    if (waiting != null) {
      throw new MembershipApplicationService.ApplicationProblem(409,
          "You already applied (application number " + waiting.getReference() + "). Please wait for the co-operative "
              + "to decide. You can check its progress with your application number.");
    }

    MembershipApplication app = earlier.stream().filter(MembershipApplication::isDraft).findFirst().orElse(null);
    boolean resumed = app != null;
    if (app == null) {
      app = new MembershipApplication(newReference(), coop.getId(), request.firstName(), request.lastName(), email, request.phone());
    } else if (app.getEmailCodeExpiresAt() != null
        && app.getEmailCodeExpiresAt().minusMinutes(10).plusSeconds(60).isAfter(LocalDateTime.now())) {
      throw new MembershipApplicationService.ApplicationProblem(429, "We just sent you a code. Please wait a minute before asking for another.");
    }

    String code = String.format("%06d", RANDOM.nextInt(1_000_000));
    app.setEmailCode(passwordEncoder.encode(code), LocalDateTime.now().plusMinutes(10));
    applicationRepository.save(app);
    try {
      emailService.sendApplicationCodeEmail(email, request.firstName() + " " + request.lastName(), coop.getName(), code);
    } catch (EmailDeliveryException e) {
      throw new MembershipApplicationService.ApplicationProblem(502,
          "We couldn't send the code to " + email + ". Check the email address and try again.");
    }
    return ResponseEntity.ok(new StartResponse(app.getId(), email, resumed));
  }

  /** Step 2: the code from the email. Returns the private key for this application. */
  @PostMapping("/api/v1/apply/applications/{applicationId}/verify")
  public ResponseEntity<?> verify(@PathVariable String applicationId, @Valid @RequestBody CodeRequest request) {
    MembershipApplication app = findApplication(applicationId);
    if (!app.isDraft()) {
      throw new MembershipApplicationService.ApplicationProblem(409, "This application has already been sent.");
    }
    if (app.getEmailCodeHash() == null || app.getEmailCodeExpiresAt() == null
        || app.getEmailCodeExpiresAt().isBefore(LocalDateTime.now())) {
      throw new MembershipApplicationService.ApplicationProblem(410, "This code has run out. Ask for a new one.");
    }
    if (app.getEmailCodeAttempts() >= MAX_CODE_ATTEMPTS) {
      throw new MembershipApplicationService.ApplicationProblem(429, "Too many wrong tries. Ask for a new code.");
    }
    if (!passwordEncoder.matches(request.code().trim(), app.getEmailCodeHash())) {
      app.countCodeAttempt();
      applicationRepository.save(app);
      int left = MAX_CODE_ATTEMPTS - app.getEmailCodeAttempts();
      throw new MembershipApplicationService.ApplicationProblem(400,
          "That code isn't right. " + (left > 0 ? left + " tr" + (left == 1 ? "y" : "ies") + " left." : "Ask for a new code."));
    }
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    app.markEmailVerified(HexFormat.of().formatHex(bytes));
    applicationRepository.save(app);
    Cooperative coop = findCoop(app.getCooperativeId());
    return ResponseEntity.ok(new VerifiedResponse(app.getAccessToken(), service.view(app, coop)));
  }

  @GetMapping("/api/v1/apply/applications/{applicationId}")
  public ResponseEntity<?> get(@PathVariable String applicationId, @RequestHeader(TOKEN_HEADER) String token) {
    MembershipApplication app = authorised(applicationId, token, false);
    return ResponseEntity.ok(service.view(app, findCoop(app.getCooperativeId())));
  }

  /** Saves the form as they go. Guarantors are only replaced when sent. */
  @PutMapping("/api/v1/apply/applications/{applicationId}")
  public ResponseEntity<?> save(
      @PathVariable String applicationId,
      @RequestHeader(TOKEN_HEADER) String token,
      @Valid @RequestBody ApplicationDetailsRequest request) {
    MembershipApplication app = authorised(applicationId, token, true);
    Cooperative coop = findCoop(app.getCooperativeId());
    if (request.guarantors() != null) {
      MemberGuarantorRules.Result checked = guarantorRules.check(coop, request.guarantors(), null, null);
      if (checked.error() != null) {
        return ResponseEntity.badRequest().body(Map.of("error", checked.error(), "field", "guarantors"));
      }
      service.saveGuarantors(app, checked.rows() == null ? List.of() : checked.rows());
    }
    app.applyDetails(request);
    applicationRepository.save(app);
    return ResponseEntity.ok(service.view(app, coop));
  }

  /** The passport photo ({@code kind=photo}) or ID card ({@code kind=id}). */
  @PostMapping(value = "/api/v1/apply/applications/{applicationId}/files", consumes = "multipart/form-data")
  public ResponseEntity<?> upload(
      @PathVariable String applicationId,
      @RequestHeader(TOKEN_HEADER) String token,
      @RequestParam("kind") String kind,
      @RequestParam("file") MultipartFile file) {
    MembershipApplication app = authorised(applicationId, token, true);
    boolean photo = "photo".equals(kind);
    if (!photo && !"id".equals(kind)) {
      return ResponseEntity.badRequest().body(Map.of("error", "Say whether this is your photo or your ID card."));
    }
    if (file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Choose a file first."));
    if (!(photo ? PHOTO_TYPES : ID_TYPES).contains(file.getContentType())) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", photo ? "Your photo must be a JPG, PNG or WEBP picture." : "Your ID card must be a picture (JPG, PNG) or a PDF."));
    }
    if (file.getSize() > MAX_FILE_BYTES) {
      return ResponseEntity.badRequest().body(Map.of("error", "The file is too big. It must be 5 MB or smaller."));
    }
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> result =
          cloudinary.uploader().upload(
              file.getBytes(),
              ObjectUtils.asMap("folder", "t-coop/applications", "resource_type", photo ? "image" : "auto"));
      String url = (String) result.get("secure_url");
      if (photo) app.setPhotoUrl(url);
      else app.setIdDocumentUrl(url);
      applicationRepository.save(app);
      return ResponseEntity.ok(Map.of("url", url));
    } catch (Exception e) {
      return ResponseEntity.status(502).body(Map.of("error", "We couldn't save your file. Please try again."));
    }
  }

  /** Checks a guarantor's Membership ID and shows a short name, so the applicant knows it's right. */
  @GetMapping("/api/v1/apply/applications/{applicationId}/guarantor-lookup")
  public ResponseEntity<?> guarantorLookup(
      @PathVariable String applicationId, @RequestHeader(TOKEN_HEADER) String token, @RequestParam String memberId) {
    MembershipApplication app = authorised(applicationId, token, true);
    String wanted = memberId == null ? "" : memberId.trim();
    Member member = wanted.isEmpty() ? null : memberRepository.findFirstByIdIgnoreCase(wanted).orElse(null);
    boolean coopMember =
        member != null
            && app.getCooperativeId().equals(member.getCooperativeId())
            && ("member".equals(member.getRole()) || "admin".equals(member.getRole()));
    if (!coopMember) {
      return ResponseEntity.status(404).body(Map.of("error", "No member of this co-operative has the ID \"" + wanted + "\"."));
    }
    if (!"Active".equals(member.getStatus())) {
      return ResponseEntity.status(409).body(Map.of("error", "That member isn't active, so they can't be your guarantor."));
    }
    String lastInitial = member.getLastName() == null || member.getLastName().isBlank() ? "" : " " + member.getLastName().charAt(0) + ".";
    return ResponseEntity.ok(new GuarantorLookup(member.getId(), member.getFirstName() + lastInitial));
  }

  /** Nigerian banks, for the bank account part of the form. */
  @GetMapping("/api/v1/apply/banks")
  public ResponseEntity<?> banks() {
    PaymentGatewayService.BankListResult result = paymentGatewayService.listPaystackBanks(service.platformPaystackSecret());
    if (!result.success()) return ResponseEntity.status(502).body(Map.of("error", "We couldn't load the list of banks."));
    return ResponseEntity.ok(
        Map.of("banks", result.banks().stream().map(bank -> Map.of("name", bank.name(), "code", bank.code())).toList()));
  }

  public record ResolveAccountRequest(
      @NotBlank(message = "Enter your account number") String accountNumber,
      @NotBlank(message = "Choose your bank") String bankCode) {}

  /** Shows the applicant the name on the account they typed, so they can see it's right. */
  @PostMapping("/api/v1/apply/applications/{applicationId}/resolve-account")
  public ResponseEntity<?> resolveAccount(
      @PathVariable String applicationId,
      @RequestHeader(TOKEN_HEADER) String token,
      @Valid @RequestBody ResolveAccountRequest request) {
    authorised(applicationId, token, true);
    if (!request.accountNumber().matches("\\d{10}")) {
      return ResponseEntity.badRequest().body(Map.of("error", "Your account number must be exactly 10 numbers."));
    }
    PaymentGatewayService.BankResolveResult result =
        paymentGatewayService.resolvePaystackBankAccount(request.accountNumber(), request.bankCode(), service.platformPaystackSecret());
    if (!result.success()) {
      return ResponseEntity.status(422).body(Map.of("error", "We couldn't find that account. Check the number and the bank."));
    }
    return ResponseEntity.ok(Map.of("accountName", result.accountName()));
  }

  /** Last step: everything filled in, then pay (or send, when there's no fee). */
  @PostMapping("/api/v1/apply/applications/{applicationId}/pay")
  public ResponseEntity<?> pay(@PathVariable String applicationId, @RequestHeader(TOKEN_HEADER) String token) {
    MembershipApplication app = authorised(applicationId, token, true);
    Cooperative coop = findCoop(app.getCooperativeId());
    RegistrationSettings settings = service.settingsFor(coop.getId());
    if (!service.isAcceptingApplications(coop, settings)) {
      throw new MembershipApplicationService.ApplicationProblem(409, coop.getName() + " isn't taking applications right now.");
    }
    List<String> missing = service.missingBeforePayment(app, coop, settings, service.guarantorsOf(app));
    if (!missing.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("error", "Please finish your form first: " + String.join("; ", missing) + ".", "missing", missing));
    }
    service.lookUpBankName(app);

    // Paid already but the page closed before we heard back: check before charging again.
    if (app.getPaymentReference() != null) {
      PaymentGatewayService.VerificationResult earlier =
          gatewayResolver.verify(coop.getId(), app.getPaymentGateway(), app.getPaymentKeyOwner(), app.getPaymentReference());
      BigDecimal due = nz(app.getFormFee()).add(nz(app.getFirstSavings()));
      if (earlier.success() && earlier.amountPaid() != null && earlier.amountPaid().compareTo(due) >= 0) {
        service.submit(app, coop, earlier.amountPaid());
        return ResponseEntity.ok(new PayResponse(true, null, null, null, null, app.getEmail(), service.view(app, coop)));
      }
    }

    BigDecimal total = settings.totalFee();
    if (total.signum() == 0) {
      app.startPayment(BigDecimal.ZERO, BigDecimal.ZERO, null, null, null);
      service.submit(app, coop, BigDecimal.ZERO);
      return ResponseEntity.ok(new PayResponse(true, null, null, null, null, app.getEmail(), service.view(app, coop)));
    }
    CoopGatewayResolver.Checkout checkout =
        gatewayResolver.checkoutFor(coop.getId())
            .orElseThrow(() -> new MembershipApplicationService.ApplicationProblem(409,
                "This co-operative can't take payments online yet. Please contact them."));
    String reference = "JOIN-" + app.getReference() + "-" + System.currentTimeMillis();
    app.startPayment(settings.getFormFee(), settings.getFirstSavings(), reference, checkout.gateway(), checkout.keyOwner());
    applicationRepository.save(app);
    return ResponseEntity.ok(
        new PayResponse(false, reference, total, checkout.gateway(), checkout.publicKey(), app.getEmail(), null));
  }

  /** After the payment window closes with success: we ask the payment company ourselves. */
  @PostMapping("/api/v1/apply/applications/{applicationId}/pay/confirm")
  public ResponseEntity<?> confirm(
      @PathVariable String applicationId,
      @RequestHeader(TOKEN_HEADER) String token,
      @Valid @RequestBody ConfirmPaymentRequest request) {
    MembershipApplication app = authorised(applicationId, token, false);
    Cooperative coop = findCoop(app.getCooperativeId());
    if (!app.isDraft()) return ResponseEntity.ok(service.view(app, coop));
    if (app.getPaymentReference() == null || !app.getPaymentReference().equals(request.reference())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that payment."));
    }
    PaymentGatewayService.VerificationResult result =
        gatewayResolver.verify(coop.getId(), app.getPaymentGateway(), app.getPaymentKeyOwner(), app.getPaymentReference());
    if (!result.success()) {
      return ResponseEntity.status(402)
          .body(Map.of("error", result.message() != null ? result.message() : "We couldn't confirm your payment."));
    }
    BigDecimal due = nz(app.getFormFee()).add(nz(app.getFirstSavings()));
    if (result.amountPaid() == null || result.amountPaid().compareTo(due) < 0) {
      return ResponseEntity.status(402).body(Map.of("error", "The amount paid is less than the joining fee."));
    }
    service.submit(app, coop, result.amountPaid());
    return ResponseEntity.ok(service.view(app, coop));
  }

  /** "Where is my application?" — needs the application number and the email it was sent with. */
  @GetMapping("/api/v1/apply/status")
  public ResponseEntity<?> status(@RequestParam String reference, @RequestParam String email) {
    MembershipApplication app =
        applicationRepository.findByReference(reference.trim().toUpperCase(Locale.ROOT))
            .filter(a -> a.getEmail().equalsIgnoreCase(email.trim()))
            .orElse(null);
    if (app == null) {
      return ResponseEntity.status(404)
          .body(Map.of("error", "We couldn't find an application with that number and email address. Check both and try again."));
    }
    String coopName = cooperativeRepository.findById(app.getCooperativeId()).map(Cooperative::getName).orElse("the co-operative");
    String message =
        switch (app.getStatus()) {
          case MembershipApplication.DRAFT -> "You haven't finished this application yet. Go back to the form to finish it and pay.";
          case MembershipApplication.SUBMITTED -> coopName + " is checking your details. We will email you when they decide.";
          case MembershipApplication.APPROVED -> "You are now a member! We emailed your login details to " + app.getEmail() + ".";
          default -> coopName + " did not approve this application. You can apply again when you are ready.";
        };
    return ResponseEntity.ok(
        new ApplicationViews.StatusView(
            app.getReference(), app.getStatus(), coopName, app.getSubmittedAt(), app.getDecidedAt(),
            MembershipApplication.REJECTED.equals(app.getStatus()) ? app.getDecisionNote() : null, message));
  }

  private Cooperative findCoop(String coopId) {
    Cooperative coop = coopId == null ? null : cooperativeRepository.findById(coopId.trim()).orElse(null);
    if (coop == null) {
      Cooperative byCase =
          cooperativeRepository.findAll().stream()
              .filter(c -> c.getId().equalsIgnoreCase(coopId == null ? "" : coopId.trim()))
              .findFirst()
              .orElse(null);
      if (byCase == null) throw new MembershipApplicationService.ApplicationProblem(404, "We couldn't find that co-operative.");
      return byCase;
    }
    return coop;
  }

  private MembershipApplication findApplication(String applicationId) {
    try {
      return applicationRepository.findById(UUID.fromString(applicationId))
          .orElseThrow(() -> new MembershipApplicationService.ApplicationProblem(404, "We couldn't find that application."));
    } catch (IllegalArgumentException e) {
      throw new MembershipApplicationService.ApplicationProblem(404, "We couldn't find that application.");
    }
  }

  /** The application, if the key matches. {@code mustBeDraft}: it can still be changed. */
  private MembershipApplication authorised(String applicationId, String token, boolean mustBeDraft) {
    MembershipApplication app = findApplication(applicationId);
    if (app.getAccessToken() == null || token == null
        || !java.security.MessageDigest.isEqual(app.getAccessToken().getBytes(), token.getBytes())) {
      throw new MembershipApplicationService.ApplicationProblem(401, "Please confirm your email again to carry on with this application.");
    }
    if (mustBeDraft && !app.isDraft()) {
      throw new MembershipApplicationService.ApplicationProblem(409, "This application has already been sent, so it can't be changed.");
    }
    return app;
  }

  private String newReference() {
    for (int attempt = 0; attempt < 20; attempt++) {
      StringBuilder ref = new StringBuilder("APP-");
      for (int i = 0; i < 6; i++) ref.append(REFERENCE_LETTERS.charAt(RANDOM.nextInt(REFERENCE_LETTERS.length())));
      if (applicationRepository.findByReference(ref.toString()).isEmpty()) return ref.toString();
    }
    throw new IllegalStateException("Couldn't make a unique application number");
  }

  private static BigDecimal nz(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
