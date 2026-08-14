package com.turontechnologies.tcoop.dashboard;

import java.util.List;

public record DashboardSummaryDto(
    List<SummaryCardDto> cards,
    List<ActivityPointDto> chart,
    List<RecentActivityDto> recentActivity) {}
