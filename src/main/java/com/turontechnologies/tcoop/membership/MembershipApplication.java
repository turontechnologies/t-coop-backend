package com.turontechnologies.tcoop.membership;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Someone asking to join a co-op (V64). Draft while they fill it in and pay, Submitted once the
 * joining fee is paid (the admin can now see it), then Approved or Rejected.
 */
@Entity
@Table(name = "membership_applications")
public class MembershipApplication {

  public static final String DRAFT = "Draft";
  public static final String SUBMITTED = "Submitted";
  public static final String APPROVED = "Approved";
  public static final String REJECTED = "Rejected";

  @Id private UUID id;

  private String reference;

  @Column(name = "cooperative_id")
  private String cooperativeId;

  private String status;

  @Column(name = "access_token")
  private String accessToken;

  @Column(name = "email_code_hash")
  private String emailCodeHash;

  @Column(name = "email_code_expires_at")
  private LocalDateTime emailCodeExpiresAt;

  @Column(name = "email_code_attempts")
  private int emailCodeAttempts;

  @Column(name = "email_verified_at")
  private LocalDateTime emailVerifiedAt;

  @Column(name = "first_name")
  private String firstName;

  @Column(name = "last_name")
  private String lastName;

  @Column(name = "other_name")
  private String otherName;

  private String gender;

  @Column(name = "date_of_birth")
  private LocalDate dateOfBirth;

  private String email;
  private String phone;
  private String nin;
  private String occupation;

  @Column(name = "home_address")
  private String homeAddress;

  private String country;
  private String state;
  private String city;

  @Column(name = "next_of_kin_name")
  private String nextOfKinName;

  @Column(name = "next_of_kin_phone")
  private String nextOfKinPhone;

  @Column(name = "next_of_kin_relationship")
  private String nextOfKinRelationship;

  @Column(name = "bank_code")
  private String bankCode;

  @Column(name = "account_number")
  private String accountNumber;

  @Column(name = "account_name")
  private String accountName;

  @Column(name = "bank_account_name")
  private String bankAccountName;

  @Column(name = "photo_url")
  private String photoUrl;

  @Column(name = "id_document_url")
  private String idDocumentUrl;

  @Column(name = "id_document_type")
  private String idDocumentType;

  @Column(name = "form_fee")
  private BigDecimal formFee;

  @Column(name = "first_savings")
  private BigDecimal firstSavings;

  @Column(name = "payment_reference")
  private String paymentReference;

  @Column(name = "payment_gateway")
  private String paymentGateway;

  @Column(name = "payment_key_owner")
  private String paymentKeyOwner;

  @Column(name = "amount_paid")
  private BigDecimal amountPaid;

  @Column(name = "paid_at")
  private LocalDateTime paidAt;

  @Column(name = "submitted_at")
  private LocalDateTime submittedAt;

  @Column(name = "decided_at")
  private LocalDateTime decidedAt;

  @Column(name = "decided_by")
  private String decidedBy;

  @Column(name = "decision_note")
  private String decisionNote;

  @Column(name = "member_id")
  private String memberId;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  protected MembershipApplication() {
    // JPA
  }

  public MembershipApplication(
      String reference, String cooperativeId, String firstName, String lastName, String email, String phone) {
    this.id = UUID.randomUUID();
    this.reference = reference;
    this.cooperativeId = cooperativeId;
    this.status = DRAFT;
    this.firstName = firstName.trim();
    this.lastName = lastName.trim();
    this.email = email.trim().toLowerCase();
    this.phone = phone.trim();
    this.createdAt = LocalDateTime.now();
    this.updatedAt = this.createdAt;
  }

  public void setEmailCode(String codeHash, LocalDateTime expiresAt) {
    this.emailCodeHash = codeHash;
    this.emailCodeExpiresAt = expiresAt;
    this.emailCodeAttempts = 0;
    touch();
  }

  public void countCodeAttempt() {
    this.emailCodeAttempts++;
    touch();
  }

  public void markEmailVerified(String accessToken) {
    this.emailVerifiedAt = LocalDateTime.now();
    this.accessToken = accessToken;
    this.emailCodeHash = null;
    this.emailCodeExpiresAt = null;
    this.emailCodeAttempts = 0;
    touch();
  }

  public void applyDetails(ApplicationDetailsRequest r) {
    this.firstName = r.firstName().trim();
    this.lastName = r.lastName().trim();
    this.otherName = trim(r.otherName());
    this.gender = trim(r.gender());
    this.dateOfBirth = r.dateOfBirth();
    this.phone = r.phone().trim();
    this.nin = trim(r.nin());
    this.occupation = trim(r.occupation());
    this.homeAddress = trim(r.homeAddress());
    this.country = trim(r.country());
    this.state = trim(r.state());
    this.city = trim(r.city());
    this.nextOfKinName = trim(r.nextOfKinName());
    this.nextOfKinPhone = trim(r.nextOfKinPhone());
    this.nextOfKinRelationship = trim(r.nextOfKinRelationship());
    String newAccount = trim(r.accountNumber());
    String newBank = trim(r.bankCode());
    if (!java.util.Objects.equals(newAccount, accountNumber) || !java.util.Objects.equals(newBank, bankCode)) {
      this.bankAccountName = null; // looked up again for the new account
    }
    this.bankCode = newBank;
    this.accountNumber = newAccount;
    this.accountName = trim(r.accountName());
    this.idDocumentType = trim(r.idDocumentType());
    touch();
  }

  public void setPhotoUrl(String photoUrl) {
    this.photoUrl = photoUrl;
    touch();
  }

  public void setIdDocumentUrl(String idDocumentUrl) {
    this.idDocumentUrl = idDocumentUrl;
    touch();
  }

  public void setBankAccountName(String bankAccountName) {
    this.bankAccountName = bankAccountName;
  }

  public void startPayment(
      BigDecimal formFee, BigDecimal firstSavings, String reference, String gateway, String keyOwner) {
    this.formFee = formFee;
    this.firstSavings = firstSavings;
    this.paymentReference = reference;
    this.paymentGateway = gateway;
    this.paymentKeyOwner = keyOwner;
    touch();
  }

  /** Paid (or nothing to pay): the admin can now see it. */
  public void submit(BigDecimal amountPaid) {
    this.amountPaid = amountPaid;
    this.paidAt = amountPaid != null && amountPaid.signum() > 0 ? LocalDateTime.now() : null;
    this.submittedAt = LocalDateTime.now();
    this.status = SUBMITTED;
    touch();
  }

  public void approve(String decidedBy, String note, String memberId) {
    this.status = APPROVED;
    this.decidedBy = decidedBy;
    this.decidedAt = LocalDateTime.now();
    this.decisionNote = trim(note);
    this.memberId = memberId;
    this.accessToken = null;
    touch();
  }

  public void reject(String decidedBy, String reason) {
    this.status = REJECTED;
    this.decidedBy = decidedBy;
    this.decidedAt = LocalDateTime.now();
    this.decisionNote = trim(reason);
    this.accessToken = null;
    touch();
  }

  public boolean isDraft() {
    return DRAFT.equals(status);
  }

  public String getFullName() {
    return firstName + " " + lastName;
  }

  private void touch() {
    this.updatedAt = LocalDateTime.now();
  }

  private static String trim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public UUID getId() {
    return id;
  }

  public String getReference() {
    return reference;
  }

  public String getCooperativeId() {
    return cooperativeId;
  }

  public String getStatus() {
    return status;
  }

  public String getAccessToken() {
    return accessToken;
  }

  public String getEmailCodeHash() {
    return emailCodeHash;
  }

  public LocalDateTime getEmailCodeExpiresAt() {
    return emailCodeExpiresAt;
  }

  public int getEmailCodeAttempts() {
    return emailCodeAttempts;
  }

  public LocalDateTime getEmailVerifiedAt() {
    return emailVerifiedAt;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public String getOtherName() {
    return otherName;
  }

  public String getGender() {
    return gender;
  }

  public LocalDate getDateOfBirth() {
    return dateOfBirth;
  }

  public String getEmail() {
    return email;
  }

  public String getPhone() {
    return phone;
  }

  public String getNin() {
    return nin;
  }

  public String getOccupation() {
    return occupation;
  }

  public String getHomeAddress() {
    return homeAddress;
  }

  public String getCountry() {
    return country;
  }

  public String getState() {
    return state;
  }

  public String getCity() {
    return city;
  }

  public String getNextOfKinName() {
    return nextOfKinName;
  }

  public String getNextOfKinPhone() {
    return nextOfKinPhone;
  }

  public String getNextOfKinRelationship() {
    return nextOfKinRelationship;
  }

  public String getBankCode() {
    return bankCode;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public String getAccountName() {
    return accountName;
  }

  public String getBankAccountName() {
    return bankAccountName;
  }

  public String getPhotoUrl() {
    return photoUrl;
  }

  public String getIdDocumentUrl() {
    return idDocumentUrl;
  }

  public String getIdDocumentType() {
    return idDocumentType;
  }

  public BigDecimal getFormFee() {
    return formFee;
  }

  public BigDecimal getFirstSavings() {
    return firstSavings;
  }

  public String getPaymentReference() {
    return paymentReference;
  }

  public String getPaymentGateway() {
    return paymentGateway;
  }

  public String getPaymentKeyOwner() {
    return paymentKeyOwner;
  }

  public BigDecimal getAmountPaid() {
    return amountPaid;
  }

  public LocalDateTime getPaidAt() {
    return paidAt;
  }

  public LocalDateTime getSubmittedAt() {
    return submittedAt;
  }

  public LocalDateTime getDecidedAt() {
    return decidedAt;
  }

  public String getDecidedBy() {
    return decidedBy;
  }

  public String getDecisionNote() {
    return decisionNote;
  }

  public String getMemberId() {
    return memberId;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
