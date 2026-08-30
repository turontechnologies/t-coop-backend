package com.turontechnologies.tcoop.loan;

import java.math.BigDecimal;

public record NextInstallmentDto(int installmentNumber, BigDecimal amount) {}
