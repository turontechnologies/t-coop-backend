package com.turontechnologies.tcoop.notification;

import jakarta.validation.constraints.NotBlank;

public record UnregisterPushTokenRequest(@NotBlank(message = "Missing push token") String token) {}
