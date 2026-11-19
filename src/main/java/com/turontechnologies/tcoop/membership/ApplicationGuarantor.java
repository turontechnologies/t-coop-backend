package com.turontechnologies.tcoop.membership;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Someone an applicant named as their guarantor. A co-op member is asked in the app whether they
 * know the applicant ("Pending" until they answer); someone from outside is just recorded
 * ("NotAsked").
 */
@Entity
@Table(name = "membership_application_guarantors")
public class ApplicationGuarantor {

  @Id private UUID id;

  @Column(name = "application_id")
  private UUID applicationId;

  @Column(name = "guarantor_member_id")
  private String guarantorMemberId;

  private String name;
  private String phone;
  private String email;
  private String status;

  @Column(name = "responded_at")
  private LocalDateTime respondedAt;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  protected ApplicationGuarantor() {
    // JPA
  }

  public ApplicationGuarantor(
      UUID applicationId, String guarantorMemberId, String name, String phone, String email) {
    this.id = UUID.randomUUID();
    this.applicationId = applicationId;
    this.guarantorMemberId = guarantorMemberId;
    this.name = name;
    this.phone = phone;
    this.email = email;
    this.status = guarantorMemberId != null ? "Pending" : "NotAsked";
    this.createdAt = LocalDateTime.now();
  }

  public void respond(boolean confirmed) {
    this.status = confirmed ? "Confirmed" : "Declined";
    this.respondedAt = LocalDateTime.now();
  }

  public boolean isMember() {
    return guarantorMemberId != null;
  }

  public UUID getId() {
    return id;
  }

  public UUID getApplicationId() {
    return applicationId;
  }

  public String getGuarantorMemberId() {
    return guarantorMemberId;
  }

  public String getName() {
    return name;
  }

  public String getPhone() {
    return phone;
  }

  public String getEmail() {
    return email;
  }

  public String getStatus() {
    return status;
  }

  public LocalDateTime getRespondedAt() {
    return respondedAt;
  }
}
