package com.turontechnologies.tcoop.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Mirrors the frontend's settingsProfileSchema password rules exactly (min 6 characters). */
public record ChangePasswordRequest(
    @NotBlank(message = "Enter your current password") String currentPassword,
    @NotBlank(message = "Enter a new password")
        @Size(min = 6, message = "Password must be at least 6 characters")
        String newPassword) {}
