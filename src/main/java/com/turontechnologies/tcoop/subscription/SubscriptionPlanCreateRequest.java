package com.turontechnologies.tcoop.subscription;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record SubscriptionPlanCreateRequest(
    @NotBlank(message = "Select New Subscription or Renewal")
        @Pattern(
            regexp = "New Subscription|Renewal",
            message = "Select New Subscription or Renewal")
        String type,
    @NotBlank(message = "Enter a name for this plan") String label,
    @NotNull(message = "Enter how many days this plan lasts")
        @Min(value = 1, message = "Duration must be at least 1 day")
        Integer durationInDays,
    @NotNull(message = "Enter an amount")
        @DecimalMin(value = "0.01", message = "Enter an amount greater than zero")
        BigDecimal amount) {}
