package com.turontechnologies.tcoop.platformstaff;

import java.time.LocalDateTime;
import java.util.List;

public record PlatformRoleDto(
    String id, String name, List<String> permissions, String status, LocalDateTime dateAdded) {

  public static PlatformRoleDto from(PlatformRole role) {
    return new PlatformRoleDto(
        role.getId().toString(),
        role.getName(),
        role.getPermissions(),
        role.getStatus(),
        role.getCreatedAt());
  }
}
