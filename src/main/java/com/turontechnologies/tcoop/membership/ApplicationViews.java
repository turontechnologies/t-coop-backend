package com.turontechnologies.tcoop.membership;

import com.turontechnologies.tcoop.cooperative.Cooperative;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** What the join pages, the admin's review screen and the guarantor's card receive. */
public final class ApplicationViews {

  private ApplicationViews() {}

  /** A co-op someone can apply to. */
  public record CoopListing(String id, String name, String logoUrl, String address) {
    static CoopListing of(Cooperative coop) {
      return new CoopListing(coop.getId(), coop.getName(), coop.getLogoUrl(), coop.getAddress());
    }
  }

  /** The guarantor rules, in numbers the form can explain. */
  public record GuarantorRules(boolean required, int min, String source, int fromCoop, int fromOutside) {
    static GuarantorRules of(Cooperative coop) {
      return new GuarantorRules(
          coop.isRequireMemberGuarantors(),
          coop.isRequireMemberGuarantors() ? Math.max(1, coop.getMinGuarantors()) : 0,
          coop.getMemberGuarantorSource(),
          coop.getMemberGuarantorsFromCoop(),
          coop.getMemberGuarantorsFromOutside());
    }
  }

  /** Everything shown before the form: the rules, what to have ready and the fee. */
  public record CoopApplyInfo(
      String id,
      String name,
      String logoUrl,
      String address,
      boolean open,
      String rules,
      List<String> requirements,
      BigDecimal formFee,
      BigDecimal firstSavings,
      BigDecimal totalFee,
      String currency,
      String savingsTypeName,
      boolean requireIdDocument,
      int minAge,
      GuarantorRules guarantors,
      boolean canPayOnline) {}

  public record GuarantorView(
      UUID id, String memberId, String name, String phone, String email, String status, LocalDateTime respondedAt) {
    static GuarantorView of(ApplicationGuarantor g) {
      return new GuarantorView(
          g.getId(), g.getGuarantorMemberId(), g.getName(), g.getPhone(), g.getEmail(), g.getStatus(), g.getRespondedAt());
    }
  }

  /** The applicant's own view of their form, to carry on where they stopped. */
  public record ApplicantView(
      UUID id,
      String reference,
      String status,
      String cooperativeId,
      String cooperativeName,
      String firstName,
      String lastName,
      String otherName,
      String gender,
      LocalDate dateOfBirth,
      String email,
      String phone,
      String nin,
      String occupation,
      String homeAddress,
      String country,
      String state,
      String city,
      String nextOfKinName,
      String nextOfKinPhone,
      String nextOfKinRelationship,
      String bankCode,
      String accountNumber,
      String accountName,
      String photoUrl,
      String idDocumentUrl,
      String idDocumentType,
      List<GuarantorView> guarantors,
      BigDecimal formFee,
      BigDecimal firstSavings,
      BigDecimal totalFee,
      boolean paymentStarted,
      LocalDateTime submittedAt,
      String decisionNote) {

    static ApplicantView of(
        MembershipApplication a, Cooperative coop, RegistrationSettings settings, List<ApplicationGuarantor> guarantors) {
      boolean fixed = !a.isDraft() && a.getFormFee() != null;
      BigDecimal formFee = fixed ? a.getFormFee() : settings.getFormFee();
      BigDecimal firstSavings = fixed ? a.getFirstSavings() : settings.getFirstSavings();
      return new ApplicantView(
          a.getId(), a.getReference(), a.getStatus(), coop.getId(), coop.getName(),
          a.getFirstName(), a.getLastName(), a.getOtherName(), a.getGender(), a.getDateOfBirth(),
          a.getEmail(), a.getPhone(), a.getNin(), a.getOccupation(), a.getHomeAddress(),
          a.getCountry(), a.getState(), a.getCity(),
          a.getNextOfKinName(), a.getNextOfKinPhone(), a.getNextOfKinRelationship(),
          a.getBankCode(), a.getAccountNumber(), a.getAccountName(),
          a.getPhotoUrl(), a.getIdDocumentUrl(), a.getIdDocumentType(),
          guarantors.stream().map(GuarantorView::of).toList(),
          formFee, firstSavings, formFee.add(firstSavings),
          a.getPaymentReference() != null, a.getSubmittedAt(),
          ApplicationViews.REJECTED_NOTE_VISIBLE.contains(a.getStatus()) ? a.getDecisionNote() : null);
    }
  }

  static final List<String> REJECTED_NOTE_VISIBLE = List.of(MembershipApplication.REJECTED);

  /** "Where is my application?" — for anyone with the reference and email. */
  public record StatusView(
      String reference,
      String status,
      String cooperativeName,
      LocalDateTime submittedAt,
      LocalDateTime decidedAt,
      String note,
      String message) {}

  /** One row on the admin's list. */
  public record AdminListItem(
      UUID id,
      String reference,
      String status,
      String fullName,
      String email,
      String phone,
      String photoUrl,
      LocalDateTime submittedAt,
      LocalDateTime decidedAt,
      int score,
      String level,
      String levelLabel,
      int guarantorsConfirmed,
      int guarantorsAsked,
      BigDecimal amountPaid,
      String memberId) {}

  /** Everything on the admin's review screen. */
  public record AdminDetail(
      ApplicantView application,
      LocalDateTime createdAt,
      LocalDateTime emailVerifiedAt,
      String bankAccountName,
      String paymentReference,
      String paymentGateway,
      BigDecimal amountPaid,
      LocalDateTime paidAt,
      LocalDateTime decidedAt,
      String decidedBy,
      String decisionNote,
      String memberId,
      ApplicationChecks.Result checks,
      String savingsTypeName,
      String nextMemberId) {}

  /** A guarantor's "do you know this person?" card. */
  public record VouchRequest(
      UUID id,
      String applicationReference,
      String applicantName,
      String applicantPhone,
      String applicantPhotoUrl,
      String cooperativeName,
      LocalDateTime submittedAt) {}
}
