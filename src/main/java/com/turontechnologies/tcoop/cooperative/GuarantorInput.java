package com.turontechnologies.tcoop.cooperative;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** One guarantor named when adding a member — they get a real email invite and only actually
 * count once they accept it (see GuarantorInviteController, the public accept/decline side). */
public record GuarantorInput(
    @NotBlank(message = "Enter the guarantor's name") String name,
    @NotBlank(message = "Enter the guarantor's email")
        @Email(message = "Enter a valid email address")
        String email,
    @NotBlank(message = "Enter the guarantor's phone number") String phone) {}
