package com.turontechnologies.tcoop.platformstaff;

import jakarta.validation.constraints.NotBlank;

public record UserRoleUpdateRequest(@NotBlank(message = "Select a role") String roleId) {}
