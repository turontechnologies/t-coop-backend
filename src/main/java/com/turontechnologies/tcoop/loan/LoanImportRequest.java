package com.turontechnologies.tcoop.loan;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * One row of an admin's loan import — an existing loan for a member of the co-op.
 *
 * @param startDate when it was paid out (yyyy-MM-dd); today when blank
 * @param repaymentsMade installments the member has already paid; 0 when null
 */
public record LoanImportRequest(
    @NotBlank String memberId,
    @NotBlank String loanTypeId,
    @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
    String loanMode,
    String guarantorMemberId,
    String startDate,
    @Min(0) Integer repaymentsMade) {}
