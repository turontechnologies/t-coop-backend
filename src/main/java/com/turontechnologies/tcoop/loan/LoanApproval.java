package com.turontechnologies.tcoop.loan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/** One officer's decision on a loan — the loan's approval trail (see V40). */
@Entity
@Table(name = "loan_approvals")
public class LoanApproval {

  @Id private UUID id;

  @Column(name = "loan_id")
  private UUID loanId;

  /** The office the decision was made as (CoopOffices.*). */
  private String step;

  /** "Approved" or "Rejected". */
  private String decision;

  @Column(name = "member_id")
  private String memberId;

  @Column(name = "member_name")
  private String memberName;

  /** True when the co-op admin acted because the office was vacant. */
  private boolean covering;

  private String note;

  @Column(name = "acted_at")
  private LocalDateTime actedAt;

  protected LoanApproval() {
    // JPA
  }

  public LoanApproval(
      UUID loanId,
      String step,
      String decision,
      String memberId,
      String memberName,
      boolean covering,
      String note) {
    this.id = UUID.randomUUID();
    this.loanId = loanId;
    this.step = step;
    this.decision = decision;
    this.memberId = memberId;
    this.memberName = memberName;
    this.covering = covering;
    this.note = note;
    this.actedAt = LocalDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getLoanId() {
    return loanId;
  }

  public String getStep() {
    return step;
  }

  public String getDecision() {
    return decision;
  }

  public String getMemberId() {
    return memberId;
  }

  public String getMemberName() {
    return memberName;
  }

  public boolean isCovering() {
    return covering;
  }

  public String getNote() {
    return note;
  }

  public LocalDateTime getActedAt() {
    return actedAt;
  }
}
