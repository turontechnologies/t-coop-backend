package com.turontechnologies.tcoop.platformstaff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A reusable, named set of module permissions for platform staff (Settings -> User Management ->
 * Roles) — e.g. "Support" -> {Notice Board, Support}. Assigning a {@link
 * com.turontechnologies.tcoop.member.Member} (role {@code "support"}) to a role is a live
 * reference (member.platformRoleId), not a snapshot — editing a role's permissions immediately
 * changes what every staff member assigned to it can see, matching the frontend's original mock
 * behavior.
 */
@Entity
@Table(name = "platform_roles")
public class PlatformRole {

  @Id private UUID id;

  private String name;

  /** Stored as a comma-separated list of PERMISSION_MODULES names — simple enough that a real
   * join table would be over-engineering for a handful of fixed module strings. */
  private String permissions;

  private String status;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  protected PlatformRole() {
    // JPA
  }

  public PlatformRole(String name, List<String> permissions) {
    this.id = UUID.randomUUID();
    this.name = name;
    this.permissions = String.join(",", permissions);
    this.status = "Active";
    this.createdAt = LocalDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public List<String> getPermissions() {
    return permissions == null || permissions.isBlank()
        ? List.of()
        : List.of(permissions.split(","));
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
