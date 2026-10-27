package com.turontechnologies.tcoop.loan;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/v1/cooperatives/{id}/loans/{loanId}/approvals — the loan's approval chain as the
 * caller sees it: who holds each office, what's been decided so far, and whether the caller
 * can act on the current step right now (and in what capacity).
 */
public record LoanApprovalStatusDto(
    List<Step> chain,
    List<Decision> trail,
    String currentStep,
    boolean canApprove,
    boolean canReject,
    /** e.g. "Treasurer", "Admin (covering for Treasurer)", "President" — null when the caller can't act. */
    String actingAs,
    BigDecimal netDisbursement) {

  /** One office in the chain. {@code holderName} is null when the office is vacant (the co-op
   * admin covers it). */
  public record Step(String office, String label, String holderName) {}

  public record Decision(
      String office,
      String label,
      String decision,
      String memberName,
      boolean covering,
      String note,
      LocalDateTime actedAt) {

    static Decision from(LoanApproval approval, String label) {
      return new Decision(
          approval.getStep(),
          label,
          approval.getDecision(),
          approval.getMemberName(),
          approval.isCovering(),
          approval.getNote(),
          approval.getActedAt());
    }
  }
}
