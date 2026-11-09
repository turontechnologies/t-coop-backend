package com.turontechnologies.tcoop.loan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/** One guarantor on a loan. A Times two loan needs at least two; each accepts or declines on
 * their own, and the loan only reaches the officers once every one has accepted. */
@Entity
@Table(name = "loan_guarantors")
public class LoanGuarantor {

  public static final String PENDING = "Pending";
  public static final String ACCEPTED = "Accepted";
  public static final String DECLINED = "Declined";

  @Id private UUID id;

  @Column(name = "loan_id")
  private UUID loanId;

  @Column(name = "member_id")
  private String memberId;

  @Column(name = "member_name")
  private String memberName;

  private String status;

  @Column(name = "document_url")
  private String documentUrl;

  @Column(name = "responded_at")
  private LocalDateTime respondedAt;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  protected LoanGuarantor() {
    // JPA
  }

  public LoanGuarantor(UUID loanId, String memberId, String memberName, String status) {
    this.id = UUID.randomUUID();
    this.loanId = loanId;
    this.memberId = memberId;
    this.memberName = memberName;
    this.status = status;
    this.createdAt = LocalDateTime.now();
    if (!PENDING.equals(status)) this.respondedAt = LocalDateTime.now();
  }

  public void accept(String documentUrl) {
    this.status = ACCEPTED;
    this.documentUrl = documentUrl;
    this.respondedAt = LocalDateTime.now();
  }

  public void decline() {
    this.status = DECLINED;
    this.respondedAt = LocalDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getLoanId() {
    return loanId;
  }

  public String getMemberId() {
    return memberId;
  }

  public String getMemberName() {
    return memberName;
  }

  public String getStatus() {
    return status;
  }

  public String getDocumentUrl() {
    return documentUrl;
  }

  public LocalDateTime getRespondedAt() {
    return respondedAt;
  }
}
