package com.turontechnologies.tcoop.platformstaff;

import jakarta.validation.constraints.NotBlank;

public record InviteUserRequest(
    @NotBlank(message = "Enter an email address") String email,
    @NotBlank(message = "Select a role") String roleId) {}
