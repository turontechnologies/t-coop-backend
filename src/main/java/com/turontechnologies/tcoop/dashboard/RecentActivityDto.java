package com.turontechnologies.tcoop.dashboard;

import java.math.BigDecimal;

public record RecentActivityDto(
    String title, String subtitle, BigDecimal amount, String date, String status) {}
