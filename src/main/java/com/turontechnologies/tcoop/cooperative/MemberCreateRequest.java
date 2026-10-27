package com.turontechnologies.tcoop.cooperative;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** POST /api/v1/cooperatives/{id}/members — an admin adding a real member to their own co-op
 * (or a super admin adding one to any co-op); the new member gets the same platform default
 * password every co-op admin starts with. No guarantor is needed to join — a member only names
 * one when applying for a loan (see LoanSelfServiceController). Next-of-kin details are all
 * optional. */
public record MemberCreateRequest(
    @NotBlank(message = "Enter a membership ID") String membershipId,
    @NotBlank(message = "Enter a first name") String firstName,
    @NotBlank(message = "Enter a last name") String lastName,
    String otherName,
    String gender,
    @NotBlank(message = "Enter a phone number") String phone,
    @NotBlank(message = "Enter an email address") String email,
    @Pattern(regexp = "^$|^\\d{11}$", message = "NIN must be 11 digits") String nin,
    String homeAddress,
    @NotBlank(message = "Select a country") String country,
    String state,
    String city,
    String facebook,
    String twitter,
    String nextOfKinName,
    String nextOfKinPhone,
    String nextOfKinEmail,
    String nextOfKinRelationship,
    String nextOfKinAuthorityLevel,
    String bankCode,
    String accountNumber,
    String accountName,
    @NotBlank(message = "Select a role")
        @Pattern(regexp = "Admin|Member", message = "Select a valid role")
        String role) {}
