package com.turontechnologies.tcoop.cooperative;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** POST /api/v1/cooperatives/{id}/transfer-admin — hands the "admin" identity (the Member row
 * whose id equals the co-op's own id — see Cooperative's class docs) over to a new person. The
 * outgoing admin doesn't lose access to the co-op; they become a regular member under a new,
 * auto-generated membership id, keeping every profile field they had. */
public record TransferAdminRequest(
    @NotBlank(message = "Enter the new admin's first name") String newFirstName,
    @NotBlank(message = "Enter the new admin's last name") String newLastName,
    @NotBlank(message = "Enter a valid email address") @Email(message = "Enter a valid email address")
        String newEmail,
    @NotBlank(message = "Enter a valid phone number") String newPhone) {}
