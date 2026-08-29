package com.turontechnologies.tcoop.savings;

import jakarta.validation.constraints.NotBlank;

public record ConfirmSavingsDepositRequest(
    @NotBlank(message = "Missing payment reference") String reference) {}
