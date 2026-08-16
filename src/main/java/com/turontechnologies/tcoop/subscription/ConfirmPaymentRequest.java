package com.turontechnologies.tcoop.subscription;

import jakarta.validation.constraints.NotBlank;

public record ConfirmPaymentRequest(@NotBlank(message = "Missing payment reference") String reference) {}
