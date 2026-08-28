package com.turontechnologies.tcoop.cooperative;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/** {@code currency}/{@code withdrawalFeeAmount}/{@code memberIdPrefix}/{@code memberIdPadding}
 * are all optional (null = leave unchanged) — this endpoint is shared by the super admin's
 * original "Edit Co-operative" form (which never sends any of them) and the admin's newer
 * Co-operative Settings tab (which does); making them required would have broken the former. */
public record CooperativeUpdateRequest(
    @NotBlank(message = "Enter the co-operative name") String name,
    @NotBlank(message = "Enter the admin's first name") String adminFirstName,
    @NotBlank(message = "Enter the admin's last name") String adminLastName,
    @NotBlank(message = "This is not a valid email")
        @Email(message = "This is not a valid email")
        String contactEmail,
    @NotBlank(message = "Enter a valid phone number")
        @Pattern(regexp = "^[\\d+\\s-]{7,}$", message = "Enter a valid phone number")
        String contactPhone,
    @NotBlank(message = "Enter the co-operative's address") String address,
    @NotBlank(message = "Select a country") String country,
    @NotBlank(message = "Select a state") String state,
    @NotBlank(message = "Select a city") String city,
    String currency,
    @DecimalMin(value = "0", message = "Enter an amount of 0 or more") BigDecimal withdrawalFeeAmount,
    @Pattern(regexp = "Fixed|Percentage", message = "Select a valid withdrawal fee type")
        String withdrawalFeeType,
    @Pattern(regexp = "^[A-Za-z0-9]{1,20}$", message = "Letters and numbers only, up to 20 characters")
        String memberIdPrefix,
    @Min(value = 1, message = "Enter at least 1 digit") @Max(value = 10, message = "Enter at most 10 digits")
        Integer memberIdPadding,
    @Pattern(regexp = "NUMERIC|ALPHA|ALPHANUMERIC", message = "Select a valid ID type") String memberIdType,
    @Min(value = 1, message = "Enter at least 1 guarantor") @Max(value = 10, message = "Enter at most 10 guarantors")
        Integer minGuarantors) {}
