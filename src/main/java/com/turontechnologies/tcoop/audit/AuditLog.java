package com.turontechnologies.tcoop.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "actor_id")
  private String actorId;

  @Column(name = "actor_role")
  private String actorRole;

  private String module;
  private String action;
  private String resource;
  private String status;

  @Column(name = "ip_address")
  private String ipAddress;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  protected AuditLog() {
    // JPA
  }

  public AuditLog(
      String actorId,
      String actorRole,
      String module,
      String action,
      String resource,
      String status,
      String ipAddress) {
    this.actorId = actorId;
    this.actorRole = actorRole;
    this.module = module;
    this.action = action;
    this.resource = resource;
    this.status = status;
    this.ipAddress = ipAddress;
    this.createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
  }
}
