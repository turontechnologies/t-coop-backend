package com.turontechnologies.tcoop.loan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record GuarantorResponseRequest(
    @NotBlank(message = "Missing decision")
        @Pattern(regexp = "Accepted|Rejected", message = "Invalid decision")
        String decision,
    /** Optional proof of income (e.g. a payslip) the guarantor attaches when accepting — ignored
     * on a Rejected decision. */
    String documentUrl) {}
