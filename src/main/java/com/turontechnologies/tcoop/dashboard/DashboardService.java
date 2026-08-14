package com.turontechnologies.tcoop.dashboard;

import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.loan.LoanRecord;
import com.turontechnologies.tcoop.loan.LoanRecordRepository;
import com.turontechnologies.tcoop.loan.LoanType;
import com.turontechnologies.tcoop.loan.LoanTypeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.savings.SavingsRecord;
import com.turontechnologies.tcoop.savings.SavingsRecordRepository;
import com.turontechnologies.tcoop.savings.SavingsType;
import com.turontechnologies.tcoop.savings.SavingsTypeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Builds {@code GET /api/v1/dashboard/summary}. Cards and recentActivity come from real
 * aggregated savings/loan records. The chart has no real hourly-analytics table behind it — it
 * distributes each scope's real totals across the same illustrative hourly shape the frontend's
 * old static mock used, so the numbers are honest sums rather than fully invented ones. Dividends
 * are not a real ledger either; they're shown as 2% of savings, matching the frontend's existing
 * SAVINGS_EARNINGS_RATE convention.
 */
@Service
public class DashboardService {

  private static final BigDecimal DIVIDEND_RATE = new BigDecimal("0.02");
  private static final int RECENT_ACTIVITY_LIMIT = 4;

  private static final String[] HOURS = {
    "00", "03", "06", "08", "09", "12", "15", "18", "21", "23"
  };
  private static final int[] SAVINGS_WEIGHTS = {120, 260, 620, 730, 700, 560, 600, 480, 260, 180};
  private static final int[] LOANS_WEIGHTS = {180, 340, 780, 969, 900, 640, 660, 560, 340, 220};
  private static final int[] DIVIDENDS_WEIGHTS = {90, 160, 420, 540, 560, 480, 520, 430, 240, 160};

  private final MemberRepository memberRepository;
  private final CooperativeRepository cooperativeRepository;
  private final SavingsRecordRepository savingsRecordRepository;
  private final SavingsTypeRepository savingsTypeRepository;
  private final LoanRecordRepository loanRecordRepository;
  private final LoanTypeRepository loanTypeRepository;

  public DashboardService(
      MemberRepository memberRepository,
      CooperativeRepository cooperativeRepository,
      SavingsRecordRepository savingsRecordRepository,
      SavingsTypeRepository savingsTypeRepository,
      LoanRecordRepository loanRecordRepository,
      LoanTypeRepository loanTypeRepository) {
    this.memberRepository = memberRepository;
    this.cooperativeRepository = cooperativeRepository;
    this.savingsRecordRepository = savingsRecordRepository;
    this.savingsTypeRepository = savingsTypeRepository;
    this.loanRecordRepository = loanRecordRepository;
    this.loanTypeRepository = loanTypeRepository;
  }

  public DashboardSummaryDto getSummary(Member caller) {
    return switch (caller.getRole()) {
      case "super_admin" -> platformWideSummary();
      case "admin" -> cooperativeSummary(caller.getCooperativeId(), "Total");
      default -> memberSummary(caller.getCooperativeId(), caller.getId(), "My");
    };
  }

  private DashboardSummaryDto platformWideSummary() {
    long coopCount = cooperativeRepository.count();
    long memberCount = memberRepository.countByRoleIn(List.of("admin", "member"));
    BigDecimal totalSavings = savingsRecordRepository.sumAll();
    BigDecimal totalLoans = loanRecordRepository.sumAll();

    List<SummaryCardDto> cards =
        List.of(
            new SummaryCardDto("Total Co-operatives", BigDecimal.valueOf(coopCount)),
            new SummaryCardDto("Total Members", BigDecimal.valueOf(memberCount)),
            new SummaryCardDto("Total Savings", totalSavings),
            new SummaryCardDto("Total Loans", totalLoans));

    Map<String, String> cooperativeNames =
        cooperativeRepository.findAll().stream()
            .collect(Collectors.toMap(Cooperative::getId, Cooperative::getName));

    List<SavingsRecord> savings =
        savingsRecordRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, RECENT_ACTIVITY_LIMIT));
    List<LoanRecord> loans =
        loanRecordRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, RECENT_ACTIVITY_LIMIT));

    List<RecentActivityDto> recentActivity =
        mergeRecentActivity(
            savings,
            loans,
            s -> cooperativeNames.getOrDefault(s.getCooperativeId(), s.getCooperativeId()),
            l -> cooperativeNames.getOrDefault(l.getCooperativeId(), l.getCooperativeId()),
            false);

    List<ActivityPointDto> chart = buildChart(totalSavings, totalLoans, BigDecimal.ZERO);
    return new DashboardSummaryDto(cards, chart, recentActivity);
  }

  private DashboardSummaryDto cooperativeSummary(String cooperativeId, String prefix) {
    BigDecimal totalSavings = savingsRecordRepository.sumByCooperative(cooperativeId);
    BigDecimal totalLoans = loanRecordRepository.sumByCooperative(cooperativeId);
    BigDecimal dividends = totalSavings.multiply(DIVIDEND_RATE).setScale(2, RoundingMode.HALF_UP);

    List<SummaryCardDto> cards =
        List.of(
            new SummaryCardDto(prefix + " Savings", totalSavings),
            new SummaryCardDto(prefix + " Loans", totalLoans),
            new SummaryCardDto(prefix + " Dividends", dividends));

    Map<java.util.UUID, String> savingsTypeNames =
        savingsTypeRepository.findAllByCooperativeId(cooperativeId).stream()
            .collect(Collectors.toMap(SavingsType::getId, SavingsType::getName));
    Map<java.util.UUID, String> loanTypeNames =
        loanTypeRepository.findAllByCooperativeId(cooperativeId).stream()
            .collect(Collectors.toMap(LoanType::getId, LoanType::getName));

    List<SavingsRecord> savings =
        savingsRecordRepository.findAllByCooperativeIdOrderByCreatedAtDesc(
            cooperativeId, PageRequest.of(0, RECENT_ACTIVITY_LIMIT));
    List<LoanRecord> loans =
        loanRecordRepository.findAllByCooperativeIdOrderByCreatedAtDesc(
            cooperativeId, PageRequest.of(0, RECENT_ACTIVITY_LIMIT));

    List<RecentActivityDto> recentActivity =
        mergeRecentActivity(
            savings,
            loans,
            s -> savingsTypeNames.getOrDefault(s.getSavingsTypeId(), "Savings"),
            l -> loanTypeNames.getOrDefault(l.getLoanTypeId(), "Loan"),
            true);

    List<ActivityPointDto> chart = buildChart(totalSavings, totalLoans, dividends);
    return new DashboardSummaryDto(cards, chart, recentActivity);
  }

  private DashboardSummaryDto memberSummary(String cooperativeId, String memberId, String prefix) {
    BigDecimal totalSavings = savingsRecordRepository.sumByMember(memberId);
    BigDecimal totalLoans = loanRecordRepository.sumByMember(memberId);
    BigDecimal dividends = totalSavings.multiply(DIVIDEND_RATE).setScale(2, RoundingMode.HALF_UP);

    List<SummaryCardDto> cards =
        List.of(
            new SummaryCardDto(prefix + " Savings", totalSavings),
            new SummaryCardDto(prefix + " Loans", totalLoans),
            new SummaryCardDto(prefix + " Dividends", dividends));

    Map<java.util.UUID, String> savingsTypeNames =
        savingsTypeRepository.findAllByCooperativeId(cooperativeId).stream()
            .collect(Collectors.toMap(SavingsType::getId, SavingsType::getName));
    Map<java.util.UUID, String> loanTypeNames =
        loanTypeRepository.findAllByCooperativeId(cooperativeId).stream()
            .collect(Collectors.toMap(LoanType::getId, LoanType::getName));

    List<SavingsRecord> savings =
        savingsRecordRepository.findAllByMemberIdOrderByCreatedAtDesc(
            memberId, PageRequest.of(0, RECENT_ACTIVITY_LIMIT));
    List<LoanRecord> loans =
        loanRecordRepository.findAllByMemberIdOrderByCreatedAtDesc(
            memberId, PageRequest.of(0, RECENT_ACTIVITY_LIMIT));

    List<RecentActivityDto> recentActivity =
        mergeRecentActivity(
            savings,
            loans,
            s -> savingsTypeNames.getOrDefault(s.getSavingsTypeId(), "Savings"),
            l -> loanTypeNames.getOrDefault(l.getLoanTypeId(), "Loan"),
            true);

    List<ActivityPointDto> chart = buildChart(totalSavings, totalLoans, dividends);
    return new DashboardSummaryDto(cards, chart, recentActivity);
  }

  private List<RecentActivityDto> mergeRecentActivity(
      List<SavingsRecord> savings,
      List<LoanRecord> loans,
      java.util.function.Function<SavingsRecord, String> savingsSubtitle,
      java.util.function.Function<LoanRecord, String> loanSubtitle,
      boolean showStatus) {
    List<RecentActivityDto> combined = new ArrayList<>();

    for (SavingsRecord record : savings) {
      combined.add(
          new RecentActivityDto(
              "Savings & Contribution",
              savingsSubtitle.apply(record),
              record.getAmount(),
              record.getCreatedAt().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT),
              showStatus ? record.getStatus() : null));
    }
    for (LoanRecord record : loans) {
      combined.add(
          new RecentActivityDto(
              "Loan Disbursement",
              loanSubtitle.apply(record),
              record.getAmount(),
              record.getCreatedAt().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT),
              showStatus ? record.getStatus() : null));
    }

    return combined.stream()
        .sorted(Comparator.comparing(RecentActivityDto::date).reversed())
        .limit(RECENT_ACTIVITY_LIMIT)
        .toList();
  }

  private List<ActivityPointDto> buildChart(
      BigDecimal totalSavings, BigDecimal totalLoans, BigDecimal totalDividends) {
    int savingsWeightSum = sum(SAVINGS_WEIGHTS);
    int loansWeightSum = sum(LOANS_WEIGHTS);
    int dividendsWeightSum = sum(DIVIDENDS_WEIGHTS);

    List<ActivityPointDto> chart = new ArrayList<>();
    for (int i = 0; i < HOURS.length; i++) {
      chart.add(
          new ActivityPointDto(
              HOURS[i],
              share(totalSavings, SAVINGS_WEIGHTS[i], savingsWeightSum),
              share(totalLoans, LOANS_WEIGHTS[i], loansWeightSum),
              share(totalDividends, DIVIDENDS_WEIGHTS[i], dividendsWeightSum)));
    }
    return chart;
  }

  private BigDecimal share(BigDecimal total, int weight, int weightSum) {
    if (total == null || total.signum() == 0 || weightSum == 0) {
      return BigDecimal.ZERO;
    }
    return total
        .multiply(BigDecimal.valueOf(weight))
        .divide(BigDecimal.valueOf(weightSum), 2, RoundingMode.HALF_UP);
  }

  private int sum(int[] values) {
    int total = 0;
    for (int value : values) {
      total += value;
    }
    return total;
  }
}
