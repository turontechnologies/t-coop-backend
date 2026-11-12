package com.turontechnologies.tcoop.loan;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /api/v1/cooperatives/{id}/loans/{loanId}/approvals — the loan's committee and progress as
 * the caller sees it, and what the caller can do right now.
 *
 * <p>{@code stage}: "Guarantors" → "Approval" → "Acceptance" (the borrower answers the offer) →
 * "Disbursement" → "Active" / "Completed", or "Rejected" / "Declined".
 */
public record LoanApprovalStatusDto(
    List<Step> chain,
    List<Decision> trail,
    String stage,
    /** 1-based committee position the loan is waiting on, during "Approval". */
    Integer currentStep,
    boolean canApprove,
    boolean canReject,
    /** e.g. "Treasurer", "Admin (covering for Treasurer)" — null when the caller can't act. */
    String actingAs,
    /** The caller is the last approver: their figures become the offer. */
    boolean finalStep,
    /** The borrower can accept or decline the approved terms. */
    boolean canRespondToOffer,
    boolean canDisburse,
    /** The longest repayment period this loan's type allows. */
    int maxMonths,
    BigDecimal minAmount,
    BigDecimal netDisbursement,
    /** The borrower's own account on file, for the offer's "registered account" choice. */
    RegisteredAccount registeredAccount) {

  public record Step(int position, String title, String memberId, String memberName) {}

  public record Decision(
      Integer position,
      String title,
      String decision,
      String memberName,
      boolean covering,
      String note,
      LocalDateTime actedAt,
      BigDecimal suggestedAmount,
      Integer suggestedMonths,
      BigDecimal previousAmount,
      Integer previousMonths) {

    static Decision from(LoanApproval approval, String title) {
      return new Decision(
          approval.getPosition(),
          title,
          approval.getDecision(),
          approval.getMemberName(),
          approval.isCovering(),
          approval.getNote(),
          approval.getActedAt(),
          approval.getSuggestedAmount(),
          approval.getSuggestedMonths(),
          approval.getPreviousAmount(),
          approval.getPreviousMonths());
    }
  }

  /** Account number masked except the last four digits. */
  public record RegisteredAccount(String bankCode, String accountNumberMasked, String accountName) {}
}
