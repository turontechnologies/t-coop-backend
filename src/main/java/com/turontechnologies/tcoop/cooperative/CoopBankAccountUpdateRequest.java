package com.turontechnologies.tcoop.cooperative;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CoopBankAccountUpdateRequest(
    @NotBlank(message = "Select a bank") String bankCode,
    @NotBlank(message = "Enter the account number")
        @Pattern(regexp = "^\\d{10}$", message = "Enter a 10-digit account number")
        String accountNumber,
    String accountName) {}
