package com.turontechnologies.tcoop.membership;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * One co-op's rules for people applying to join (V64): whether applications are open, the
 * joining fee (form fee + first savings), the co-op's rules and what to have ready.
 */
@Entity
@Table(name = "registration_settings")
public class RegistrationSettings {

  /** Shown when the admin hasn't written their own list. */
  public static final List<String> DEFAULT_REQUIREMENTS =
      List.of(
          "A recent passport photograph (a clear photo of your face)",
          "Your NIN (National Identification Number — 11 digits)",
          "A valid ID card: National ID, voter's card, driver's licence or international passport",
          "Your guarantor's Membership ID — they must already be a member of this co-operative",
          "Your next of kin's name and phone number",
          "Your bank account details, for when the co-operative pays you",
          "Money for the joining fee, paid online with your card or bank");

  @Id
  @Column(name = "cooperative_id")
  private String cooperativeId;

  @Column(name = "is_open")
  private boolean open;

  @Column(name = "form_fee")
  private BigDecimal formFee = BigDecimal.ZERO;

  @Column(name = "first_savings")
  private BigDecimal firstSavings = BigDecimal.ZERO;

  @Column(name = "savings_type_id")
  private UUID savingsTypeId;

  private String rules;

  private String requirements;

  @Column(name = "require_id_document")
  private boolean requireIdDocument = true;

  @Column(name = "min_age")
  private int minAge = 18;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  protected RegistrationSettings() {
    // JPA
  }

  public RegistrationSettings(String cooperativeId) {
    this.cooperativeId = cooperativeId;
    this.updatedAt = LocalDateTime.now();
  }

  public void update(
      boolean open,
      BigDecimal formFee,
      BigDecimal firstSavings,
      UUID savingsTypeId,
      String rules,
      String requirements,
      boolean requireIdDocument,
      int minAge) {
    this.open = open;
    this.formFee = formFee;
    this.firstSavings = firstSavings;
    this.savingsTypeId = savingsTypeId;
    this.rules = blankToNull(rules);
    this.requirements = blankToNull(requirements);
    this.requireIdDocument = requireIdDocument;
    this.minAge = minAge;
    this.updatedAt = LocalDateTime.now();
  }

  /** The admin's list, one item per line, or the default list. */
  public List<String> requirementList() {
    if (requirements == null || requirements.isBlank()) return DEFAULT_REQUIREMENTS;
    return Arrays.stream(requirements.split("\\r?\\n"))
        .map(String::trim)
        .map(line -> line.replaceFirst("^[-*•]\\s*", ""))
        .filter(line -> !line.isEmpty())
        .toList();
  }

  public BigDecimal totalFee() {
    return getFormFee().add(getFirstSavings());
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public String getCooperativeId() {
    return cooperativeId;
  }

  public boolean isOpen() {
    return open;
  }

  public BigDecimal getFormFee() {
    return formFee == null ? BigDecimal.ZERO : formFee;
  }

  public BigDecimal getFirstSavings() {
    return firstSavings == null ? BigDecimal.ZERO : firstSavings;
  }

  public UUID getSavingsTypeId() {
    return savingsTypeId;
  }

  public String getRules() {
    return rules;
  }

  public String getRequirements() {
    return requirements;
  }

  public boolean isRequireIdDocument() {
    return requireIdDocument;
  }

  public int getMinAge() {
    return minAge;
  }
}
