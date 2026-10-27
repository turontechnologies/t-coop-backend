package com.turontechnologies.tcoop.loan;

import java.math.BigDecimal;

/** What a loan's disbursement will look like — {@code loanAmount} is what the member owes/repays
 * (unchanged by the charge); {@code netDisbursed} is what actually gets transferred when the
 * Secretary approves, after the combined co-op + platform loans charge. */
public record LoanDisbursementDto(BigDecimal loanAmount, BigDecimal chargeAmount, BigDecimal netDisbursed) {}
