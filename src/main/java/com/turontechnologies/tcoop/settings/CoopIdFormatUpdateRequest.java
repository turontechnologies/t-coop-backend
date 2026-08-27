package com.turontechnologies.tcoop.settings;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CoopIdFormatUpdateRequest(
    @NotBlank(message = "Enter a prefix")
        @Pattern(regexp = "^[A-Za-z0-9]{1,20}$", message = "Letters and numbers only, up to 20 characters")
        String prefix,
    @Min(value = 1, message = "Enter at least 1 digit") @Max(value = 10, message = "Enter at most 10 digits")
        int padding,
    @NotBlank(message = "Select an ID type")
        @Pattern(regexp = "NUMERIC|ALPHA|ALPHANUMERIC", message = "Select a valid ID type")
        String type) {}
