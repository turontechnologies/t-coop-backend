package com.turontechnologies.tcoop.platformstaff;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record PlatformRoleCreateRequest(
    @NotBlank(message = "Enter a role name") String name,
    @NotEmpty(message = "Select at least one permission") List<String> permissions) {}
