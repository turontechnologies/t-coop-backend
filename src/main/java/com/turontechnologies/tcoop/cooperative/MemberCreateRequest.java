package com.turontechnologies.tcoop.cooperative;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/** POST /api/v1/cooperatives/{id}/members — an admin adding a real member to their own co-op
 * (or a super admin adding one to any co-op); the new member gets the same platform default
 * password every co-op admin starts with. Each entry in {@code guarantors} gets a real email
 * invite and only counts once accepted — CooperativeController.validateGuarantors enforces the
 * co-op's own configured minimum count and that at least one guarantor's email matches an
 * existing member of this co-op. */
public record MemberCreateRequest(
    @NotBlank(message = "Enter a membership ID") String membershipId,
    @NotBlank(message = "Enter a first name") String firstName,
    @NotBlank(message = "Enter a last name") String lastName,
    String otherName,
    String gender,
    @NotBlank(message = "Enter a phone number") String phone,
    @NotBlank(message = "Enter an email address") String email,
    String homeAddress,
    @NotBlank(message = "Select a country") String country,
    String state,
    String city,
    String facebook,
    String twitter,
    @NotEmpty(message = "Enter at least the co-op's required guarantors") @Valid
        List<GuarantorInput> guarantors,
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
