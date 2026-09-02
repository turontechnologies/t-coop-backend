package com.turontechnologies.tcoop.support;

import jakarta.validation.constraints.NotBlank;

public record ReplyRequest(@NotBlank(message = "Enter a message") String message, String attachmentUrl) {}
