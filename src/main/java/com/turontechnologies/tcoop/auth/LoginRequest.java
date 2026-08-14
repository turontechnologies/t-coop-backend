package com.turontechnologies.tcoop.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank String membershipId, @NotBlank String password, Boolean keepLoggedIn) {}
