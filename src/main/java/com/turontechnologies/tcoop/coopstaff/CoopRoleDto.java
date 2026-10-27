package com.turontechnologies.tcoop.coopstaff;

import java.time.LocalDateTime;
import java.util.List;

/** {@code office} is non-null only on the four fixed officer roles (see CoopOffices). */
public record CoopRoleDto(
    String id,
    String name,
    List<String> permissions,
    String status,
    String office,
    LocalDateTime dateAdded) {

  public static CoopRoleDto from(CoopRole role) {
    return new CoopRoleDto(
        role.getId().toString(),
        role.getName(),
        role.getPermissions(),
        role.getStatus(),
        role.getOffice(),
        role.getCreatedAt());
  }
}
