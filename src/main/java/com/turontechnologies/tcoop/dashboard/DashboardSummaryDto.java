package com.turontechnologies.tcoop.dashboard;

import java.math.BigDecimal;
import java.util.List;

public record DashboardSummaryDto(
    List<SummaryCardDto> cards,
    List<ActivityPointDto> chart,
    List<RecentActivityDto> recentActivity,
    List<LoanTypeEligibilityDto> loanEligibilityByType,
    /** 0-100, see DashboardService for what it measures in each scope. */
    BigDecimal activityRate,
    /** What the rate means, e.g. "of members saved or repaid a loan in the last 30 days". */
    String activityLabel,
    /** Loans not yet paid out; null on the platform-wide view. */
    LoanPipelineDto loanPipeline) {}
