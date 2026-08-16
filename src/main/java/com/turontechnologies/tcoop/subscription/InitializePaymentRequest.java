package com.turontechnologies.tcoop.subscription;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record InitializePaymentRequest(
    @NotBlank(message = "Select a subscription plan") String planId,
    @NotBlank(message = "Select a payment method")
        @Pattern(regexp = "Paystack|Flutterwave", message = "Select a valid payment method")
        String gateway) {}
