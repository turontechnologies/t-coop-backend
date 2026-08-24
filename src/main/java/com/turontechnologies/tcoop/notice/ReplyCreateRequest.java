package com.turontechnologies.tcoop.notice;

import jakarta.validation.constraints.NotBlank;

public record ReplyCreateRequest(@NotBlank(message = "Enter a message") String message) {}
