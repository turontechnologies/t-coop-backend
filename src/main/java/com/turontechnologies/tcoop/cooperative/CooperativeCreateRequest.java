package com.turontechnologies.tcoop.cooperative;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Mirrors the frontend's addCooperativeSchema exactly. */
public record CooperativeCreateRequest(
    @NotBlank(message = "Enter a co-op ID") String coopId,
    @NotBlank(message = "Enter the co-operative name") String coopName,
    @NotBlank(message = "Enter the admin's first name") String adminFirstName,
    @NotBlank(message = "Enter the admin's last name") String adminLastName,
    @NotBlank(message = "This is not a valid email")
        @Email(message = "This is not a valid email")
        String contactEmail,
    @NotBlank(message = "Enter a valid phone number")
        @Pattern(regexp = "^[\\d+\\s-]{7,}$", message = "Enter a valid phone number")
        String contactPhone,
    @NotBlank(message = "Enter the co-operative's address") String address,
    @NotBlank(message = "Select a country") String country,
    @NotBlank(message = "Select a state") String state,
    @NotBlank(message = "Select a city") String city) {}
