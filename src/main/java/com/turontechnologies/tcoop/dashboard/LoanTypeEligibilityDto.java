package com.turontechnologies.tcoop.dashboard;

import java.math.BigDecimal;

public record LoanTypeEligibilityDto(
    String loanTypeId, String loanTypeName, BigDecimal timesTwo, BigDecimal within) {}
