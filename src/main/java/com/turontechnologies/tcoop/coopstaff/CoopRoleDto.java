package com.turontechnologies.tcoop.coopstaff;

import java.time.LocalDateTime;
import java.util.List;

public record CoopRoleDto(
    String id, String name, List<String> permissions, String status, LocalDateTime dateAdded) {

  public static CoopRoleDto from(CoopRole role) {
    return new CoopRoleDto(
        role.getId().toString(), role.getName(), role.getPermissions(), role.getStatus(), role.getCreatedAt());
  }
}
