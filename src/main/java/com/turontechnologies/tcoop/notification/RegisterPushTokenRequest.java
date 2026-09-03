package com.turontechnologies.tcoop.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterPushTokenRequest(
    @NotBlank(message = "Missing push token") String token,
    @NotBlank(message = "Missing platform")
        @Pattern(regexp = "^(ios|android)$", message = "platform must be ios or android")
        String platform) {}
