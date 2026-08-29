package com.turontechnologies.tcoop.loan;

import java.math.BigDecimal;

/** What an admin needs to know before initiating the real payout for an "Awaiting Admin" loan —
 * {@code loanAmount} is what the member owes/repays (unchanged by the charge); {@code netDisbursed}
 * is what actually gets transferred, after the combined co-op + platform loans charge. */
public record LoanDisbursementDto(BigDecimal loanAmount, BigDecimal chargeAmount, BigDecimal netDisbursed) {}
