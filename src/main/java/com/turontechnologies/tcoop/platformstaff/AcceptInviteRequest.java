package com.turontechnologies.tcoop.platformstaff;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInviteRequest(
    @NotBlank(message = "Invite token is required") String token,
    @NotBlank(message = "Enter a first name") String firstName,
    @NotBlank(message = "Enter a last name") String lastName,
    @NotBlank(message = "Enter a password")
        @Size(min = 6, message = "Password must be at least 6 characters")
        String password) {}
