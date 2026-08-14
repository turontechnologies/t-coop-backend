package com.turontechnologies.tcoop.cooperative;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "cooperatives")
public class Cooperative {

  @Id
  private String id;

  private String name;
  private String status;

  protected Cooperative() {
    // JPA
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getStatus() {
    return status;
  }
}
