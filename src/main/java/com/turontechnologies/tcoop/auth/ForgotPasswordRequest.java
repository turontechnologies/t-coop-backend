package com.turontechnologies.tcoop.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
    @NotBlank(message = "This is not a valid email")
        @Email(message = "This is not a valid email")
        String email) {}
