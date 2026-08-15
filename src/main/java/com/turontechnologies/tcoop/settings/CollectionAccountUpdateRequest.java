package com.turontechnologies.tcoop.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Mirrors the frontend's collectionAccountSchema exactly. */
public record CollectionAccountUpdateRequest(
    @NotBlank(message = "Select a bank") String bankCode,
    @NotBlank(message = "Enter a 10-digit account number")
        @Pattern(regexp = "\\d{10}", message = "Enter a 10-digit account number")
        String accountNumber,
    String accountName) {}
