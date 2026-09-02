package com.turontechnologies.tcoop.support;

import jakarta.validation.constraints.NotBlank;

public record ResolveRequest(@NotBlank(message = "Enter a resolution note") String resolutionNote) {}
