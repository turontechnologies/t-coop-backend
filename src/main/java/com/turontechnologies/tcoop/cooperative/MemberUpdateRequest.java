package com.turontechnologies.tcoop.cooperative;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** PATCH /api/v1/cooperatives/{id}/members/{memberId} — mirrors the frontend's editMemberSchema
 * exactly; fields it doesn't show (otherName, gender, phone, nin, facebook, twitter) are left
 * untouched on the existing row, same discipline as CooperativeController.update() for an
 * admin's own profile. */
public record MemberUpdateRequest(
    @NotBlank(message = "Enter a first name") String firstName,
    @NotBlank(message = "Enter a last name") String lastName,
    @NotBlank(message = "Enter an email address") String email,
    @NotBlank(message = "Select a role")
        @Pattern(regexp = "Admin|Member", message = "Select a valid role")
        String role,
    @NotBlank(message = "Enter a guarantor") String guarantor,
    @NotBlank(message = "Select a country") String country,
    @NotBlank(message = "Select a state") String state,
    @NotBlank(message = "Select a city") String city,
    @NotBlank(message = "Select a bank") String bankCode,
    @NotBlank(message = "Enter an account number") String accountNumber,
    String accountName) {}
