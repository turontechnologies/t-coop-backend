package com.turontechnologies.tcoop.support;

import jakarta.validation.constraints.NotBlank;

public record RaiseTicketRequest(
    @NotBlank(message = "Enter a subject") String subject,
    @NotBlank(message = "Select a category") String category,
    @NotBlank(message = "Enter a description") String description,
    String attachmentUrl) {}
