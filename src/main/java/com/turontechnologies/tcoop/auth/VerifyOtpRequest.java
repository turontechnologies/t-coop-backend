package com.turontechnologies.tcoop.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(
    @NotBlank(message = "This is not a valid email")
        @Email(message = "This is not a valid email")
        String email,
    @NotBlank(message = "Enter the 6-digit code")
        @Pattern(regexp = "\\d{6}", message = "The code should be 6 digits")
        String otp) {}
