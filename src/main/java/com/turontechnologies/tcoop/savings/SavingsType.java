package com.turontechnologies.tcoop.savings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "savings_types")
public class SavingsType {

  @Id private UUID id;

  @Column(name = "cooperative_id")
  private String cooperativeId;

  private String name;

  protected SavingsType() {
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
