package com.turontechnologies.tcoop.loan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "loan_types")
public class LoanType {

  @Id private UUID id;

  @Column(name = "cooperative_id")
  private String cooperativeId;

  private String name;

  protected LoanType() {
    // JPA
  }

  public UUID getId() {
    return id;
  }

  public String getCooperativeId() {
    return cooperativeId;
  }

  public String getName() {
    return name;
  }
}
