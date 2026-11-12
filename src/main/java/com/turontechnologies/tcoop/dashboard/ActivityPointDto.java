package com.turontechnologies.tcoop.dashboard;

import java.math.BigDecimal;

/** One day on the dashboard chart: {@code label} is the day, e.g. "12 Nov". */
public record ActivityPointDto(
    String label, BigDecimal savings, BigDecimal loans, BigDecimal dividends) {}
