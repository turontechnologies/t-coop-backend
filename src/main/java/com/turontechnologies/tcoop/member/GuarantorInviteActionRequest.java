package com.turontechnologies.tcoop.member;

import jakarta.validation.constraints.NotBlank;

public record GuarantorInviteActionRequest(@NotBlank(message = "Missing invite token") String token) {}
