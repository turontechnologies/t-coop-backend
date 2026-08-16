package com.turontechnologies.tcoop.subscription;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/** Type is never editable — delete and re-add if a plan needs to move between New/Renewal. */
public record SubscriptionPlanUpdateRequest(
    @NotBlank(message = "Enter a name for this plan") String label,
    @NotNull(message = "Enter how many days this plan lasts")
        @Min(value = 1, message = "Duration must be at least 1 day")
        Integer durationInDays,
    @NotNull(message = "Enter an amount")
        @DecimalMin(value = "0.01", message = "Enter an amount greater than zero")
        BigDecimal amount,
    @NotBlank(message = "Select a status")
        @Pattern(regexp = "Active|Inactive", message = "Select a valid status")
        String status) {}
