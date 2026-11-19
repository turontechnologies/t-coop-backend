package com.turontechnologies.tcoop.membership;

import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.cooperative.MemberGuarantorRules;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The automatic checks an admin sees on an application, and the score they add up to. Each check
 * has a weight; a pass earns all of it, a "check" (warning) half, a fail none. Checks that don't
 * apply have no weight. Nothing here is a final verdict: the admin decides.
 *
 * <p>The NIN is checked for shape and for being used by someone else, not against government
 * records (that needs a paid identity service).
 */
@Component
public class ApplicationChecks {

  public static final String PASS = "pass";
  public static final String WARN = "warn";
  public static final String FAIL = "fail";
  public static final String INFO = "info";

  /** One line on the admin's checklist. */
  public record Check(String key, String label, String status, String detail, int weight) {}

  /** The checklist, the score out of 100 and a plain summary of it. */
  public record Result(int score, String level, String levelLabel, List<Check> checks) {}

  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

  private final MemberRepository memberRepository;
  private final MembershipApplicationRepository applicationRepository;

  public ApplicationChecks(MemberRepository memberRepository, MembershipApplicationRepository applicationRepository) {
    this.memberRepository = memberRepository;
    this.applicationRepository = applicationRepository;
  }

  public Result run(
      MembershipApplication app,
      Cooperative coop,
      RegistrationSettings settings,
      List<ApplicationGuarantor> guarantors) {
    List<Check> checks = new ArrayList<>();
    checks.add(email(app));
    checks.add(payment(app));
    checks.add(ninShape(app));
    checks.add(ninUnused(app));
    checks.add(contactUnused(app));
    checks.add(age(app, settings));
    checks.add(photo(app));
    checks.add(idDocument(app, settings));
    checks.add(guarantorRules(coop, guarantors));
    checks.add(guarantorAnswers(guarantors));
    checks.add(bankName(app));
    checks.add(nextOfKin(app));
    checks.add(history(app));

    double earned = 0;
    int total = 0;
    for (Check check : checks) {
      if (check.weight() == 0) continue;
      total += check.weight();
      if (PASS.equals(check.status())) earned += check.weight();
      else if (WARN.equals(check.status())) earned += check.weight() / 2.0;
    }
    int score = total == 0 ? 0 : (int) Math.round(earned * 100 / total);
    String level = score >= 80 ? "high" : score >= 50 ? "medium" : "low";
    String label =
        switch (level) {
          case "high" -> "Looks genuine";
          case "medium" -> "Check carefully";
          default -> "High risk";
        };
    return new Result(score, level, label, checks);
  }

  private Check email(MembershipApplication app) {
    return app.getEmailVerifiedAt() != null
        ? new Check("email", "Email address confirmed", PASS, "They typed in the code we sent to " + app.getEmail() + ".", 10)
        : new Check("email", "Email address confirmed", FAIL, "They never confirmed their email address.", 10);
  }

  private Check payment(MembershipApplication app) {
    BigDecimal due = nz(app.getFormFee()).add(nz(app.getFirstSavings()));
    if (due.signum() == 0) {
      return new Check("payment", "Joining fee", INFO, "There was no joining fee to pay.", 0);
    }
    if (app.getAmountPaid() != null && app.getAmountPaid().compareTo(due) >= 0) {
      return new Check(
          "payment", "Joining fee paid", PASS,
          "Paid " + naira(app.getAmountPaid()) + " online. The payment company confirmed it (ref " + app.getPaymentReference() + ").",
          15);
    }
    return new Check("payment", "Joining fee paid", FAIL, "We have no confirmed payment of " + naira(due) + ".", 15);
  }

  private Check ninShape(MembershipApplication app) {
    String nin = app.getNin();
    if (nin == null) return new Check("ninShape", "NIN looks right", FAIL, "No NIN was given.", 10);
    if (!nin.matches("\\d{11}")) {
      return new Check("ninShape", "NIN looks right", FAIL, "A NIN has 11 numbers. This one is \"" + nin + "\".", 10);
    }
    if (nin.chars().distinct().count() <= 2 || "0123456789".contains(nin.substring(0, 10)) || "9876543210".contains(nin.substring(0, 10))) {
      return new Check("ninShape", "NIN looks right", WARN, nin + " looks made up (repeated or counting numbers). Ask to see their NIN slip.", 10);
    }
    return new Check(
        "ninShape", "NIN looks right", PASS,
        nin + " has 11 numbers. Compare it with their NIN slip or ID card; it isn't checked with the government.", 10);
  }

  private Check ninUnused(MembershipApplication app) {
    String nin = app.getNin();
    if (nin == null) return new Check("ninUnused", "NIN not used by someone else", INFO, "No NIN to check.", 0);
    List<Member> owners = memberRepository.findAllByNin(nin);
    if (!owners.isEmpty()) {
      Member owner = owners.get(0);
      String who =
          app.getCooperativeId().equals(owner.getCooperativeId())
              ? owner.getFullName() + " (" + owner.getId() + "), already a member here"
              : "a member of another co-operative";
      return new Check("ninUnused", "NIN not used by someone else", FAIL, "This NIN already belongs to " + who + ".", 15);
    }
    boolean otherApplication =
        applicationRepository.findAllByCooperativeIdAndNinOrderByCreatedAtDesc(app.getCooperativeId(), nin).stream()
            .anyMatch(other -> !other.getId().equals(app.getId())
                && (MembershipApplication.SUBMITTED.equals(other.getStatus()) || MembershipApplication.APPROVED.equals(other.getStatus()))
                && !other.getEmail().equalsIgnoreCase(app.getEmail()));
    if (otherApplication) {
      return new Check("ninUnused", "NIN not used by someone else", FAIL, "Another application with a different email address uses this NIN.", 15);
    }
    return new Check("ninUnused", "NIN not used by someone else", PASS, "No member or other applicant has this NIN.", 15);
  }

  private Check contactUnused(MembershipApplication app) {
    List<String> problems = new ArrayList<>();
    memberRepository.findByEmail(app.getEmail())
        .ifPresent(member -> problems.add("the email belongs to " + describe(member, app)));
    String phone = phoneKey(app.getPhone());
    if (phone != null) {
      memberRepository.findAllByCooperativeId(app.getCooperativeId()).stream()
          .filter(member -> phone.equals(phoneKey(member.getPhone())))
          .findFirst()
          .ifPresent(member -> problems.add("the phone number belongs to " + describe(member, app)));
    }
    if (problems.isEmpty()) {
      return new Check("contactUnused", "Email and phone are new", PASS, "No member is using this email address or phone number.", 10);
    }
    return new Check("contactUnused", "Email and phone are new", FAIL, capitalise(String.join(", and ", problems)) + ".", 10);
  }

  private Check age(MembershipApplication app, RegistrationSettings settings) {
    int minAge = settings.getMinAge();
    String label = "Old enough to join (" + minAge + " or older)";
    LocalDate dob = app.getDateOfBirth();
    if (dob == null) return new Check("age", label, FAIL, "No date of birth was given.", 10);
    int years = Period.between(dob, LocalDate.now()).getYears();
    if (dob.isAfter(LocalDate.now()) || years > 110) {
      return new Check("age", label, FAIL, "The date of birth (" + DAY.format(dob) + ") can't be right.", 10);
    }
    if (years < minAge) {
      return new Check("age", label, FAIL, "They are " + years + " years old.", 10);
    }
    return new Check("age", label, PASS, "They are " + years + " years old (born " + DAY.format(dob) + ").", 10);
  }

  private Check photo(MembershipApplication app) {
    return app.getPhotoUrl() != null
        ? new Check("photo", "Passport photo added", PASS, "Compare the photo with their ID card.", 5)
        : new Check("photo", "Passport photo added", FAIL, "No photo was added.", 5);
  }

  private Check idDocument(MembershipApplication app, RegistrationSettings settings) {
    String type = app.getIdDocumentType() != null ? app.getIdDocumentType() : "ID card";
    if (app.getIdDocumentUrl() != null) {
      return new Check("idDocument", "ID card added", PASS, type + " added. Check that the name, photo and NIN match.", 10);
    }
    return settings.isRequireIdDocument()
        ? new Check("idDocument", "ID card added", FAIL, "No ID card was added.", 10)
        : new Check("idDocument", "ID card added", INFO, "Not asked for by your co-operative.", 0);
  }

  private Check guarantorRules(Cooperative coop, List<ApplicationGuarantor> guarantors) {
    String label = "Guarantors meet your rules";
    if (!coop.isRequireMemberGuarantors() && guarantors.isEmpty()) {
      return new Check("guarantorRules", label, INFO, "Your co-operative doesn't ask for guarantors.", 0);
    }
    for (ApplicationGuarantor guarantor : guarantors) {
      if (!guarantor.isMember()) continue;
      Member member = memberRepository.findById(guarantor.getGuarantorMemberId()).orElse(null);
      if (member == null || !coop.getId().equals(member.getCooperativeId()) || !"Active".equals(member.getStatus())) {
        return new Check("guarantorRules", label, FAIL, guarantor.getName() + " is no longer an active member.", 10);
      }
    }
    if (coop.isRequireMemberGuarantors()) {
      List<MemberGuarantorRules.Row> rows =
          guarantors.stream()
              .map(g -> new MemberGuarantorRules.Row(g.getGuarantorMemberId(), g.getName(), g.getPhone(), g.getEmail()))
              .toList();
      String problem = MemberGuarantorRules.ruleProblem(coop, rows);
      if (problem != null) return new Check("guarantorRules", label, FAIL, problem, 10);
    }
    String names = guarantors.stream().map(ApplicationGuarantor::getName).collect(Collectors.joining(", "));
    return new Check("guarantorRules", label, PASS, guarantors.size() + " named: " + names + ".", 10);
  }

  private Check guarantorAnswers(List<ApplicationGuarantor> guarantors) {
    String label = "Guarantors say they know them";
    List<ApplicationGuarantor> members = guarantors.stream().filter(ApplicationGuarantor::isMember).toList();
    if (members.isEmpty()) {
      return new Check("guarantorAnswers", label, INFO, "No guarantor is a member, so no one was asked.", 0);
    }
    List<String> declined = members.stream().filter(g -> "Declined".equals(g.getStatus())).map(ApplicationGuarantor::getName).toList();
    long confirmed = members.stream().filter(g -> "Confirmed".equals(g.getStatus())).count();
    if (!declined.isEmpty()) {
      return new Check("guarantorAnswers", label, FAIL, String.join(", ", declined) + " said they don't know this person or won't stand for them.", 15);
    }
    if (confirmed == members.size()) {
      return new Check("guarantorAnswers", label, PASS, "All " + confirmed + " said yes in the app.", 15);
    }
    List<String> waiting = members.stream().filter(g -> "Pending".equals(g.getStatus())).map(ApplicationGuarantor::getName).toList();
    return new Check(
        "guarantorAnswers", label, WARN,
        confirmed + " of " + members.size() + " said yes. Still waiting for " + String.join(", ", waiting) + ".", 15);
  }

  private Check bankName(MembershipApplication app) {
    String label = "Bank account is in their name";
    if (app.getAccountNumber() == null) {
      return new Check("bankName", label, INFO, "No bank account was given.", 0);
    }
    if (app.getBankAccountName() == null) {
      return new Check("bankName", label, INFO, "We couldn't ask the bank for the account name (connect Paystack to check this).", 0);
    }
    if (namesMatch(app, app.getBankAccountName())) {
      return new Check("bankName", label, PASS, "The bank says the account belongs to " + app.getBankAccountName() + ".", 10);
    }
    return new Check(
        "bankName", label, FAIL,
        "The bank says the account belongs to " + app.getBankAccountName() + ", not " + app.getFullName() + ".", 10);
  }

  private Check nextOfKin(MembershipApplication app) {
    return app.getNextOfKinName() != null && app.getNextOfKinPhone() != null
        ? new Check("nextOfKin", "Next of kin given", PASS,
            app.getNextOfKinName() + (app.getNextOfKinRelationship() != null ? " (" + app.getNextOfKinRelationship().toLowerCase() + ")" : "")
                + ", " + app.getNextOfKinPhone() + ".", 5)
        : new Check("nextOfKin", "Next of kin given", FAIL, "No next of kin name and phone number.", 5);
  }

  private Check history(MembershipApplication app) {
    List<MembershipApplication> before = new ArrayList<>();
    before.addAll(applicationRepository.findAllByCooperativeIdAndEmailOrderByCreatedAtDesc(app.getCooperativeId(), app.getEmail()));
    if (app.getNin() != null) {
      before.addAll(applicationRepository.findAllByCooperativeIdAndNinOrderByCreatedAtDesc(app.getCooperativeId(), app.getNin()));
    }
    MembershipApplication rejected =
        before.stream()
            .filter(other -> !other.getId().equals(app.getId()) && MembershipApplication.REJECTED.equals(other.getStatus()))
            .findFirst()
            .orElse(null);
    if (rejected == null) {
      return new Check("history", "Not turned down before", PASS, "No earlier application was turned down.", 5);
    }
    String when = rejected.getDecidedAt() != null ? " on " + DAY.format(rejected.getDecidedAt()) : "";
    String why = rejected.getDecisionNote() != null ? " Reason: " + rejected.getDecisionNote() : "";
    return new Check(
        "history", "Not turned down before", WARN,
        "An earlier application (" + rejected.getReference() + ") was turned down" + when + "." + why, 5);
  }

  /** At least two of the applicant's names (or all of them, if they gave fewer) are in the bank's name. */
  static boolean namesMatch(MembershipApplication app, String bankName) {
    Set<String> bankWords = words(bankName);
    List<String> names =
        java.util.stream.Stream.of(app.getFirstName(), app.getLastName(), app.getOtherName())
            .filter(java.util.Objects::nonNull)
            .flatMap(name -> words(name).stream())
            .distinct()
            .toList();
    long found = names.stream().filter(bankWords::contains).count();
    return found >= Math.min(2, names.size());
  }

  private static Set<String> words(String value) {
    return Arrays.stream(value.toLowerCase(Locale.ROOT).split("[^a-z]+"))
        .filter(word -> word.length() > 1)
        .collect(Collectors.toSet());
  }

  /** The last 10 numbers, so 0803… and +234803… match. */
  static String phoneKey(String phone) {
    if (phone == null) return null;
    String digits = phone.replaceAll("\\D", "");
    if (digits.length() < 7) return null;
    return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
  }

  private static String describe(Member member, MembershipApplication app) {
    return app.getCooperativeId().equals(member.getCooperativeId())
        ? member.getFullName() + " (" + member.getId() + ")"
        : "someone in another co-operative";
  }

  private static String capitalise(String value) {
    return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
  }

  private static BigDecimal nz(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  static String naira(BigDecimal amount) {
    return "₦" + String.format(Locale.ENGLISH, "%,.2f", amount);
  }
}
