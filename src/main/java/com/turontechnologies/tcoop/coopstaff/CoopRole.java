package com.turontechnologies.tcoop.coopstaff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A reusable, named set of module permissions scoped to ONE co-operative — the admin's own
 * Settings -> User Management -> Roles, mirroring PlatformRole exactly (see that class) but
 * per-co-op instead of platform-wide. Assigning a {@link com.turontechnologies.tcoop.member.Member}
 * (role stays "member") is a live reference (member.coopRoleId), not a snapshot.
 */
@Entity
@Table(name = "coop_roles")
public class CoopRole {

  @Id private UUID id;

  @Column(name = "cooperative_id")
  private String cooperativeId;

  private String name;

  /** Comma-separated PERMISSION_MODULES names, same convention as PlatformRole.permissions. */
  private String permissions;

  private String status;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  protected CoopRole() {
    // JPA
  }

  public CoopRole(String cooperativeId, String name, List<String> permissions) {
    this.id = UUID.randomUUID();
    this.cooperativeId = cooperativeId;
    this.name = name;
    this.permissions = String.join(",", permissions);
    this.status = "Active";
    this.createdAt = LocalDateTime.now();
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

  public List<String> getPermissions() {
    return permissions == null || permissions.isBlank() ? List.of() : List.of(permissions.split(","));
  }

  public String getStatus() {
    return status;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void update(String name, List<String> permissions) {
    this.name = name;
    this.permissions = String.join(",", permissions);
  }

  public void setStatus(String status) {
    this.status = status;
  }
}
