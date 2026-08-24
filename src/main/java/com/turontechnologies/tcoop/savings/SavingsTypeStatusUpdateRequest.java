package com.turontechnologies.tcoop.savings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SavingsTypeStatusUpdateRequest(
    @NotBlank(message = "status is required")
        @Pattern(regexp = "Active|Inactive", message = "status must be Active or Inactive")
        String status) {}
