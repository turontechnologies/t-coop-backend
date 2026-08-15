package com.turontechnologies.tcoop.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
    @NotBlank(message = "Missing reset token") String resetToken,
    @NotBlank(message = "Enter a new password")
        @Size(min = 6, message = "Password must be at least 6 characters")
        String newPassword) {}
