package com.turontechnologies.tcoop.coopstaff;

import jakarta.validation.constraints.NotBlank;

public record UpdateCoopUserRoleRequest(@NotBlank(message = "Select a role") String roleId) {}
