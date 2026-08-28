package com.turontechnologies.tcoop.profile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Mirrors the frontend's profileSchema (t-coop-app/src/lib/validations/profile.schema.ts) —
 * kept in sync deliberately so a request that passes client-side validation never fails here,
 * and a request that skips the client (curl, a future non-web client) still can't corrupt data.
 */
public record ProfileUpdateRequest(
    @NotBlank(message = "Enter a valid account number")
        @Pattern(regexp = "\\d{10}", message = "Account number must be 10 digits")
        String accountNumber,
    @NotBlank(message = "Select a bank") String bankCode,
    String accountName,
    @NotBlank(message = "Enter a valid NIN")
        @Pattern(regexp = "\\d{11}", message = "NIN must be 11 digits")
        String nin,
    @NotBlank(message = "Enter your first name") String firstName,
    @NotBlank(message = "Enter your last name") String lastName,
    String otherName,
    @NotBlank(message = "Select a gender")
        @Pattern(regexp = "Male|Female|Other", message = "Select a valid gender")
        String gender,
    @NotBlank(message = "Enter a valid phone number")
        @Pattern(regexp = "^[\\d+\\s-]{7,}$", message = "Enter a valid phone number")
        String phone,
    @NotBlank(message = "Enter a valid email address")
        @Email(message = "Enter a valid email address")
        String email,
    @NotBlank(message = "Enter your home address") String homeAddress,
    @NotBlank(message = "Select a country") String country,
    @NotBlank(message = "Select a state") String state,
    @NotBlank(message = "Select a city") String city,
    String facebook,
    String twitter,
    String guarantor,
    String nextOfKinName,
    String nextOfKinPhone,
    String nextOfKinEmail,
    String nextOfKinRelationship,
    String nextOfKinAuthorityLevel) {}
