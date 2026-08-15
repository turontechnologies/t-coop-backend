package com.turontechnologies.tcoop.cooperative;

import jakarta.validation.constraints.Pattern;

public record CooperativeStatusUpdateRequest(
    @Pattern(regexp = "Active|Disabled", message = "Status must be Active or Disabled")
        String status) {}
