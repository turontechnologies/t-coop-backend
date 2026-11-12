package com.turontechnologies.tcoop.dashboard;

import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.loan.DividendCalculator;
import com.turontechnologies.tcoop.loan.LoanBalances;
import com.turontechnologies.tcoop.loan.LoanEligibilityService;
import com.turontechnologies.tcoop.loan.LoanRecord;
import com.turontechnologies.tcoop.loan.LoanRecordRepository;
import com.turontechnologies.tcoop.loan.LoanRepayment;
import com.turontechnologies.tcoop.loan.LoanRepaymentRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Builds {@code GET /api/v1/dashboard/summary}. Every figure uses the same definitions as the
 * rest of the app, so the dashboard always matches the Savings and Loans pages:
 *
 * <ul>
 *   <li><b>Savings</b>: successful savings records (deposits minus withdrawals and Within-loan
 *       payouts) — what's actually in the account.
 *   <li><b>Loans</b>: what's still owed on running loans, principal + interest
 *       ({@link LoanBalances#outstanding}).
 *   <li><b>Dividends</b>: by the co-op's own dividend method ({@link DividendCalculator}).
 *   <li><b>Loan Eligibility</b>: {@link LoanEligibilityService} — the same numbers the loan form
 *       and the application check use.
 *   <li><b>Chart</b>: real daily totals for the last 30 days (deposits, loans paid out, dividends
 *       earned that day).
 *   <li><b>Activity rate</b>: for a co-op, the share of its members who saved or repaid a loan in
 *       the last 30 days; for a member, the share of the last 6 months they saved in; for the
 *       platform, the share of co-ops with any activity in the last 30 days.
 * </ul>
 */
@Service
public class DashboardService {

  private static final int RECENT_ACTIVITY_LIMIT = 4;
  private static final int CHART_DAYS = 30;
  /** Enough history for 30 days of chart without loading everything. */
  private static final int HISTORY_LIMIT = 5000;
  private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

  private final MemberRepository memberRepository;
  private final CooperativeRepository cooperativeRepository;
  private final SavingsRecordRepository savingsRecordRepository;
  private final SavingsTypeRepository savingsTypeRepository;
  private final LoanRecordRepository loanRecordRepository;
  private final LoanTypeRepository loanTypeRepository;
  private final com.turontechnologies.tcoop.coopstaff.CoopPermissions coopPermissions;
  private final LoanEligibilityService eligibilityService;
  private final LoanRepaymentRepository repaymentRepository;

  public DashboardService(
      MemberRepository memberRepository,
      CooperativeRepository cooperativeRepository,
      SavingsRecordRepository savingsRecordRepository,
      SavingsTypeRepository savingsTypeRepository,
      LoanRecordRepository loanRecordRepository,
      LoanTypeRepository loanTypeRepository,
      com.turontechnologies.tcoop.coopstaff.CoopPermissions coopPermissions,
      LoanEligibilityService eligibilityService,
      LoanRepaymentRepository repaymentRepository) {
    this.memberRepository = memberRepository;
    this.cooperativeRepository = cooperativeRepository;
    this.savingsRecordRepository = savingsRecordRepository;
    this.savingsTypeRepository = savingsTypeRepository;
    this.loanRecordRepository = loanRecordRepository;
    this.loanTypeRepository = loanTypeRepository;
    this.coopPermissions = coopPermissions;
    this.eligibilityService = eligibilityService;
    this.repaymentRepository = repaymentRepository;
  }

  public DashboardSummaryDto getSummary(Member caller) {
    if ("super_admin".equals(caller.getRole())) return platformWideSummary();
    String coopId = caller.getCooperativeId();
    // Someone holding a co-op role sees co-op-wide figures only if that role lets them see
    // other members' savings or loans; otherwise just their own, like any member.
    if (caller.getCoopRoleId() != null && coopId != null) {
      boolean seesMembers =
          coopPermissions.allows(caller, coopId, "Savings & Contributions", "Members Savings", false)
              || coopPermissions.allows(caller, coopId, "Loans", "Members Loans", false);
      return seesMembers ? cooperativeSummary(coopId, "Total") : memberSummary(coopId, caller, "My");
    }
    return "admin".equals(caller.getRole())
        ? cooperativeSummary(coopId, "Total")
        : memberSummary(coopId, caller, "My");
  }

  /* ─────────────────────────────── Platform ─────────────────────────────── */

  private DashboardSummaryDto platformWideSummary() {
    long coopCount = cooperativeRepository.count();
    long memberCount = memberRepository.countByRoleIn(List.of("admin", "member"));
    BigDecimal totalSavings = savingsRecordRepository.sumAll();
    List<SavingsRecord> recentSavings = savingsRecordRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, HISTORY_LIMIT));
    List<LoanRecord> recentLoans = loanRecordRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, HISTORY_LIMIT));
    BigDecimal totalLoans = owed(loanRecordRepository.findAll());

    List<SummaryCardDto> cards =
        List.of(
            new SummaryCardDto("Total Co-operatives", BigDecimal.valueOf(coopCount)),
            new SummaryCardDto("Total Members", BigDecimal.valueOf(memberCount)),
            new SummaryCardDto("Total Savings", totalSavings),
            new SummaryCardDto("Total Loans", totalLoans));

    Map<String, String> cooperativeNames =
        cooperativeRepository.findAll().stream().collect(Collectors.toMap(Cooperative::getId, Cooperative::getName));
    List<RecentActivityDto> recentActivity =
        mergeRecentActivity(
            recentSavings.stream().limit(RECENT_ACTIVITY_LIMIT).toList(),
            recentLoans.stream().limit(RECENT_ACTIVITY_LIMIT).toList(),
            s -> cooperativeNames.getOrDefault(s.getCooperativeId(), s.getCooperativeId()),
            l -> cooperativeNames.getOrDefault(l.getCooperativeId(), l.getCooperativeId()),
            false);

    LocalDateTime since = LocalDateTime.now().minusDays(CHART_DAYS);
    Set<String> activeCoops = new HashSet<>();
    recentSavings.stream()
        .filter(s -> s.getCreatedAt().isAfter(since))
        .forEach(s -> activeCoops.add(s.getCooperativeId()));
    recentLoans.stream()
        .filter(l -> l.getCreatedAt().isAfter(since))
        .forEach(l -> activeCoops.add(l.getCooperativeId()));

    List<ActivityPointDto> chart = chart(recentSavings, recentLoans, day -> BigDecimal.ZERO);
    return new DashboardSummaryDto(
        cards, chart, recentActivity, List.of(),
        percent(activeCoops.size(), coopCount),
        "of co-operatives were active in the last 30 days");
  }

  /* ─────────────────────────────── Co-operative ─────────────────────────────── */

  private DashboardSummaryDto cooperativeSummary(String cooperativeId, String prefix) {
    // "admin" + "member" — the same roster the Members Directory counts.
    long memberCount = memberRepository.countByCooperativeIdAndRoleIn(cooperativeId, List.of("admin", "member"));
    Cooperative coop = cooperativeRepository.findById(cooperativeId).orElse(null);
    BigDecimal totalSavings = savingsRecordRepository.sumByCooperative(cooperativeId);
    List<LoanRecord> loans = loanRecordRepository.findAllByCooperativeIdOrderByCreatedAtDesc(cooperativeId);
    List<SavingsRecord> savings = savingsRecordRepository.findAllByCooperativeIdOrderByCreatedAtDesc(cooperativeId);
    BigDecimal totalLoans = owed(loans);
    BigDecimal dividends =
        coop == null ? BigDecimal.ZERO : DividendCalculator.forCooperative(coop, totalSavings, DividendCalculator.interestEarned(loans));

    List<SummaryCardDto> cards =
        List.of(
            new SummaryCardDto(prefix + " Savings", totalSavings),
            new SummaryCardDto(prefix + " Loans", totalLoans),
            new SummaryCardDto(prefix + " Dividends", dividends),
            new SummaryCardDto("Total Members", BigDecimal.valueOf(memberCount)));

    List<RecentActivityDto> recentActivity =
        mergeRecentActivity(
            savings.stream().limit(RECENT_ACTIVITY_LIMIT).toList(),
            loans.stream().limit(RECENT_ACTIVITY_LIMIT).toList(),
            savingsTypeName(cooperativeId),
            loanTypeName(cooperativeId),
            true);

    // Members who saved or repaid in the last 30 days, out of everyone on the roster.
    LocalDateTime since = LocalDateTime.now().minusDays(CHART_DAYS);
    Set<String> active = new HashSet<>();
    savings.stream()
        .filter(s -> "Success".equals(s.getStatus()) && s.getAmount().signum() > 0 && s.getCreatedAt().isAfter(since))
        .forEach(s -> active.add(s.getMemberId()));
    List<LoanRepayment> repayments = repaymentRepository.findAllByCooperativeIdOrderByCreatedAtAsc(cooperativeId);
    repayments.stream().filter(r -> r.getCreatedAt().isAfter(since)).forEach(r -> active.add(r.getMemberId()));

    List<ActivityPointDto> chart =
        chart(savings, loans, dividendsPerDay(coop, savings, repayments, loans, null, totalSavings));
    return new DashboardSummaryDto(
        cards, chart, recentActivity, List.of(),
        percent(active.size(), memberCount),
        "of members saved or repaid a loan in the last 30 days");
  }

  /* ─────────────────────────────── Member ─────────────────────────────── */

  private DashboardSummaryDto memberSummary(String cooperativeId, Member member, String prefix) {
    String memberId = member.getId();
    Cooperative coop = cooperativeRepository.findById(cooperativeId).orElse(null);
    BigDecimal totalSavings = savingsRecordRepository.sumByMember(memberId);
    List<LoanRecord> myLoans = loanRecordRepository.findAllByCooperativeIdAndMemberIdOrderByCreatedAtDesc(cooperativeId, memberId);
    List<SavingsRecord> mySavings = savingsRecordRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId, PageRequest.of(0, HISTORY_LIMIT));
    BigDecimal myLoansOwed = owed(myLoans);

    BigDecimal dividends = BigDecimal.ZERO;
    List<LoanRepayment> coopRepayments = List.of();
    List<LoanRecord> coopLoans = List.of();
    BigDecimal coopSavings = BigDecimal.ZERO;
    if (coop != null) {
      coopLoans = loanRecordRepository.findAllByCooperativeIdOrderByCreatedAtDesc(cooperativeId);
      coopSavings = savingsRecordRepository.sumByCooperative(cooperativeId);
      dividends = DividendCalculator.forMember(coop, totalSavings, coopSavings, DividendCalculator.interestEarned(coopLoans));
      if ("InterestShare".equals(coop.getDividendMethod())) {
        coopRepayments = repaymentRepository.findAllByCooperativeIdOrderByCreatedAtAsc(cooperativeId);
      }
    }

    // The same numbers the loan form and the application check use.
    List<LoanTypeEligibilityDto> loanEligibilityByType = List.of();
    BigDecimal loanEligibility = BigDecimal.ZERO;
    if (coop != null) {
      LoanEligibilityService.Result result = eligibilityService.evaluate(coop, member);
      loanEligibilityByType =
          result.perType().stream()
              .map(t -> new LoanTypeEligibilityDto(t.type().getId().toString(), t.type().getName(), t.timesTwo(), t.within()))
              .toList();
      loanEligibility = result.best();
    }

    List<SummaryCardDto> cards =
        List.of(
            new SummaryCardDto(prefix + " Savings", totalSavings),
            new SummaryCardDto(prefix + " Loans", myLoansOwed),
            new SummaryCardDto(prefix + " Dividends", dividends),
            new SummaryCardDto("Loan Eligibility", loanEligibility));

    List<RecentActivityDto> recentActivity =
        mergeRecentActivity(
            mySavings.stream().limit(RECENT_ACTIVITY_LIMIT).toList(),
            myLoans.stream().limit(RECENT_ACTIVITY_LIMIT).toList(),
            savingsTypeName(cooperativeId),
            loanTypeName(cooperativeId),
            true);

    // The share of the last 6 months in which the member saved.
    Set<String> monthsSaved = new HashSet<>();
    LocalDate sixMonthsAgo = LocalDate.now().withDayOfMonth(1).minusMonths(5);
    mySavings.stream()
        .filter(s -> "Success".equals(s.getStatus()) && s.getAmount().signum() > 0)
        .map(s -> s.getCreatedAt().toLocalDate())
        .filter(d -> !d.isBefore(sixMonthsAgo))
        .forEach(d -> monthsSaved.add(d.getYear() + "-" + d.getMonthValue()));

    List<ActivityPointDto> chart =
        chart(
            mySavings,
            myLoans,
            dividendsPerDay(coop, mySavings, coopRepayments, coopLoans, totalSavings, coopSavings));
    return new DashboardSummaryDto(
        cards, chart, recentActivity, loanEligibilityByType,
        percent(monthsSaved.size(), 6),
        "of the last 6 months you saved in");
  }

  /* ─────────────────────────────── Shared ─────────────────────────────── */

  private static BigDecimal owed(Iterable<LoanRecord> loans) {
    BigDecimal total = BigDecimal.ZERO;
    for (LoanRecord loan : loans) total = total.add(LoanBalances.outstanding(loan));
    return total.setScale(2, RoundingMode.HALF_UP);
  }

  private static BigDecimal percent(long part, long whole) {
    if (whole <= 0) return BigDecimal.ZERO.setScale(2);
    return BigDecimal.valueOf(part * 100.0 / whole).setScale(2, RoundingMode.HALF_UP).min(BigDecimal.valueOf(100));
  }

  /**
   * Dividends earned each day, by the co-op's method. Savings share: the rate on that day's
   * deposits. Interest share: the rate on the interest repaid that day, times the member's share
   * of the co-op's savings when {@code memberSavings} is given.
   */
  private static Function<LocalDate, BigDecimal> dividendsPerDay(
      Cooperative coop,
      List<SavingsRecord> savings,
      List<LoanRepayment> repayments,
      List<LoanRecord> loans,
      BigDecimal memberSavings,
      BigDecimal coopSavings) {
    if (coop == null || "None".equals(coop.getDividendMethod())) return day -> BigDecimal.ZERO;
    BigDecimal rate = coop.getDividendRate().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
    if ("InterestShare".equals(coop.getDividendMethod())) {
      Map<UUID, LoanRecord> byId = loans.stream().collect(Collectors.toMap(LoanRecord::getId, Function.identity(), (a, b) -> a));
      Map<LocalDate, BigDecimal> interestByDay = new LinkedHashMap<>();
      for (LoanRepayment repayment : repayments) {
        LoanRecord loan = byId.get(repayment.getLoanId());
        if (loan == null || loan.getTotalRepayment().signum() <= 0) continue;
        BigDecimal interestShare =
            loan.getTotalRepayment().subtract(loan.getAmount())
                .divide(loan.getTotalRepayment(), 6, RoundingMode.HALF_UP);
        interestByDay.merge(repayment.getCreatedAt().toLocalDate(), repayment.getAmount().multiply(interestShare), BigDecimal::add);
      }
      BigDecimal share =
          memberSavings == null || coopSavings.signum() <= 0
              ? BigDecimal.ONE
              : memberSavings.max(BigDecimal.ZERO).divide(coopSavings, 6, RoundingMode.HALF_UP);
      return day -> interestByDay.getOrDefault(day, BigDecimal.ZERO).multiply(rate).multiply(share).setScale(2, RoundingMode.HALF_UP);
    }
    Map<LocalDate, BigDecimal> depositsByDay = depositsByDay(savings);
    return day -> depositsByDay.getOrDefault(day, BigDecimal.ZERO).multiply(rate).setScale(2, RoundingMode.HALF_UP);
  }

  private static Map<LocalDate, BigDecimal> depositsByDay(List<SavingsRecord> savings) {
    Map<LocalDate, BigDecimal> byDay = new LinkedHashMap<>();
    for (SavingsRecord record : savings) {
      if (!"Success".equals(record.getStatus()) || record.getAmount().signum() <= 0) continue;
      byDay.merge(record.getCreatedAt().toLocalDate(), record.getAmount(), BigDecimal::add);
    }
    return byDay;
  }

  /** The last 30 days: deposits, loans paid out, and dividends earned, per day. */
  private static List<ActivityPointDto> chart(
      List<SavingsRecord> savings, List<LoanRecord> loans, Function<LocalDate, BigDecimal> dividends) {
    Map<LocalDate, BigDecimal> deposits = depositsByDay(savings);
    Map<LocalDate, BigDecimal> payouts = new LinkedHashMap<>();
    for (LoanRecord loan : loans) {
      if (loan.getDisbursedAt() == null) continue;
      payouts.merge(loan.getDisbursedAt().toLocalDate(), loan.getAmount(), BigDecimal::add);
    }
    List<ActivityPointDto> points = new ArrayList<>();
    LocalDate today = LocalDate.now();
    for (int i = CHART_DAYS - 1; i >= 0; i--) {
      LocalDate day = today.minusDays(i);
      points.add(
          new ActivityPointDto(
              day.format(DAY_LABEL),
              deposits.getOrDefault(day, BigDecimal.ZERO),
              payouts.getOrDefault(day, BigDecimal.ZERO),
              dividends.apply(day)));
    }
    return points;
  }

  private Function<SavingsRecord, String> savingsTypeName(String cooperativeId) {
    Map<UUID, String> names =
        savingsTypeRepository.findAllByCooperativeId(cooperativeId).stream()
            .collect(Collectors.toMap(SavingsType::getId, SavingsType::getName));
    return s -> names.getOrDefault(s.getSavingsTypeId(), "Savings");
  }

  private Function<LoanRecord, String> loanTypeName(String cooperativeId) {
    Map<UUID, String> names =
        loanTypeRepository.findAllByCooperativeId(cooperativeId).stream()
            .collect(Collectors.toMap(LoanType::getId, LoanType::getName));
    return l -> names.getOrDefault(l.getLoanTypeId(), "Loan");
  }

  private List<RecentActivityDto> mergeRecentActivity(
      List<SavingsRecord> savings,
      List<LoanRecord> loans,
      Function<SavingsRecord, String> savingsSubtitle,
      Function<LoanRecord, String> loanSubtitle,
      boolean showStatus) {
    List<RecentActivityDto> combined = new ArrayList<>();
    for (SavingsRecord record : savings) {
      combined.add(
          new RecentActivityDto(
              record.getAmount().signum() < 0 ? "Savings withdrawal" : "Savings & Contribution",
              savingsSubtitle.apply(record),
              record.getAmount().abs(),
              record.getCreatedAt().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT),
              showStatus ? record.getStatus() : null));
    }
    for (LoanRecord record : loans) {
      boolean paidOut = record.getDisbursedAt() != null;
      LocalDateTime when = paidOut ? record.getDisbursedAt() : record.getCreatedAt();
      combined.add(
          new RecentActivityDto(
              paidOut ? "Loan Disbursement" : "Loan Application",
              loanSubtitle.apply(record),
              record.getAmount(),
              when.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT),
              showStatus ? record.getStatus() : null));
    }
    return combined.stream()
        .sorted(Comparator.comparing(RecentActivityDto::date).reversed())
        .limit(RECENT_ACTIVITY_LIMIT)
        .toList();
  }
}
