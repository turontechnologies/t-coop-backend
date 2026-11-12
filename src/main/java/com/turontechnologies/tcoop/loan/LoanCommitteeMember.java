package com.turontechnologies.tcoop.loan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One seat on a co-op's loan committee (V48). The admin decides who sits on it and in what order
 * they approve; a loan goes through them from position 1 to the last. Each suggests an amount and
 * repayment period, and the last one's figures are the terms offered to the borrower.
 */
@Entity
@Table(name = "loan_committee_members")
public class LoanCommitteeMember {

  @Id private UUID id;

  @Column(name = "cooperative_id")
  private String cooperativeId;

  /** 1-based approval order. */
  private int position;

  @Column(name = "member_id")
  private String memberId;

  /** What this approver is called on a loan, e.g. "Treasurer". */
  private String title;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  protected LoanCommitteeMember() {
    // JPA
  }

  public LoanCommitteeMember(String cooperativeId, int position, String memberId, String title) {
    this.id = UUID.randomUUID();
    this.cooperativeId = cooperativeId;
    this.position = position;
    this.memberId = memberId;
    this.title = title;
    this.createdAt = LocalDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public String getCooperativeId() {
    return cooperativeId;
  }

  public int getPosition() {
    return position;
  }

  public String getMemberId() {
    return memberId;
  }

  public String getTitle() {
    return title;
  }
}
