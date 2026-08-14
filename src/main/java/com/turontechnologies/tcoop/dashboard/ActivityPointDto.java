package com.turontechnologies.tcoop.dashboard;

import java.math.BigDecimal;

public record ActivityPointDto(
    String hour, BigDecimal savings, BigDecimal loans, BigDecimal dividends) {}
