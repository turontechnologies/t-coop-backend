package com.turontechnologies.tcoop.earnings;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.coopgateway.CoopGatewayResolver;
import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Super admin only: each co-operative's platform fees, and what the platform has earned from
 * each co-op, broken down by where it came from.
 */
@RestController
public class PlatformEarningsController {

  public record CoopPlatformFees(
      String cooperativeId,
      String cooperativeName,
      String status,
      String savingsChargeType,
      BigDecimal savingsChargeAmount,
      String loansChargeType,
      BigDecimal loansChargeAmount,
      String withdrawalFeeType,
      BigDecimal withdrawalFeeAmount,
      String loanApplicationFeeType,
      BigDecimal loanApplicationFeeAmount,
      int subscriptionGraceDays,
      LocalDate subscriptionExpiresAt,
      LocalDate graceEndsAt,
      boolean inGracePeriod,
      /** True when the co-op has connected its own gateway, so members can pay online. */
      boolean onlinePayments) {

    static CoopPlatformFees of(Cooperative coop, boolean onlinePayments) {
      return new CoopPlatformFees(
          coop.getId(),
          coop.getName(),
          coop.getStatus(),
          coop.getPlatformSavingsChargeType(),
          coop.getPlatformSavingsChargeAmount(),
          coop.getPlatformLoansChargeType(),
          coop.getPlatformLoansChargeAmount(),
          coop.getPlatformWithdrawalFeeType(),
          coop.getPlatformWithdrawalFeeAmount(),
          coop.getPlatformLoanFeeType(),
          coop.getPlatformLoanFeeAmount(),
          coop.getSubscriptionGraceDays(),
          coop.getSubscriptionExpiresAt(),
          coop.graceEndsAt(),
          coop.isInGracePeriod(),
          onlinePayments);
    }
  }

  public record CoopPlatformFeesUpdate(
      @Pattern(regexp = "Fixed|Percentage", message = "Select a valid savings charge type") String savingsChargeType,
      @NotNull @DecimalMin(value = "0", message = "Enter an amount of 0 or more") BigDecimal savingsChargeAmount,
      @Pattern(regexp = "Fixed|Percentage", message = "Select a valid loans charge type") String loansChargeType,
      @NotNull @DecimalMin(value = "0", message = "Enter an amount of 0 or more") BigDecimal loansChargeAmount,
      @Pattern(regexp = "Fixed|Percentage", message = "Select a valid withdrawal fee type") String withdrawalFeeType,
      @NotNull @DecimalMin(value = "0", message = "Enter an amount of 0 or more") BigDecimal withdrawalFeeAmount,
      @Pattern(regexp = "Fixed|Percentage", message = "Select a valid loan application fee type") String loanApplicationFeeType,
      @NotNull @DecimalMin(value = "0", message = "Enter an amount of 0 or more") BigDecimal loanApplicationFeeAmount,
      @Min(value = 0, message = "Enter 0 or more days")
          @Max(value = 365, message = "The grace period can be at most 365 days")
          int subscriptionGraceDays) {}

  /** One co-op's earnings for the period, with the amount from each source. */
  public record CoopEarnings(
      String cooperativeId,
      String cooperativeName,
      BigDecimal total,
      long count,
      Map<String, BigDecimal> bySource) {}

  public record EarningsSummary(
      LocalDate from,
      LocalDate to,
      BigDecimal total,
      Map<String, BigDecimal> bySource,
      List<CoopEarnings> cooperatives) {}

  public record EarningEntry(
      String id,
      String source,
      String memberId,
      String memberName,
      BigDecimal baseAmount,
      BigDecimal amount,
      String reference,
      LocalDateTime createdAt) {}

  private static final List<String> SOURCES =
      List.of(
          PlatformEarning.SAVINGS_CHARGE,
          PlatformEarning.LOAN_CHARGE,
          PlatformEarning.WITHDRAWAL_FEE,
          PlatformEarning.LOAN_APPLICATION_FEE);

  private final MemberRepository memberRepository;
  private final CooperativeRepository cooperativeRepository;
  private final PlatformEarningRepository earningRepository;
  private final AuditLogService auditLogService;
  private final CoopGatewayResolver gatewayResolver;

  public PlatformEarningsController(
      MemberRepository memberRepository,
      CooperativeRepository cooperativeRepository,
      PlatformEarningRepository earningRepository,
      AuditLogService auditLogService,
      CoopGatewayResolver gatewayResolver) {
    this.memberRepository = memberRepository;
    this.cooperativeRepository = cooperativeRepository;
    this.earningRepository = earningRepository;
    this.auditLogService = auditLogService;
    this.gatewayResolver = gatewayResolver;
  }

  @GetMapping("/api/v1/platform-fees")
  public ResponseEntity<?> listFees(Authentication authentication) {
    Member caller = superAdmin(authentication);
    if (caller == null) return forbidden();
    return ResponseEntity.ok(
        cooperativeRepository.findAll().stream()
            .sorted(Comparator.comparing(Cooperative::getName, String.CASE_INSENSITIVE_ORDER))
            .map(coop -> CoopPlatformFees.of(coop, gatewayResolver.checkoutFor(coop.getId()).isPresent()))
            .toList());
  }

  @PatchMapping("/api/v1/platform-fees/{cooperativeId}")
  public ResponseEntity<?> updateFees(
      Authentication authentication,
      @PathVariable String cooperativeId,
      @Valid @RequestBody CoopPlatformFeesUpdate request,
      HttpServletRequest httpRequest) {
    Member caller = superAdmin(authentication);
    if (caller == null) return forbidden();
    Cooperative coop = cooperativeRepository.findById(cooperativeId).orElse(null);
    if (coop == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }
    for (var pct :
        List.of(
            Map.entry(request.savingsChargeType(), request.savingsChargeAmount()),
            Map.entry(request.loansChargeType(), request.loansChargeAmount()),
            Map.entry(request.withdrawalFeeType(), request.withdrawalFeeAmount()),
            Map.entry(request.loanApplicationFeeType(), request.loanApplicationFeeAmount()))) {
      if ("Percentage".equals(pct.getKey()) && pct.getValue().compareTo(BigDecimal.valueOf(100)) > 0) {
        return ResponseEntity.badRequest().body(Map.of("error", "A percentage can be at most 100%"));
      }
    }
    coop.setPlatformFees(
        request.savingsChargeType(),
        request.savingsChargeAmount(),
        request.loansChargeType(),
        request.loansChargeAmount(),
        request.withdrawalFeeType(),
        request.withdrawalFeeAmount(),
        request.loanApplicationFeeType(),
        request.loanApplicationFeeAmount());
    coop.setSubscriptionGraceDays(request.subscriptionGraceDays());
    cooperativeRepository.save(coop);
    auditLogService.log(
        caller.getId(), caller.getRole(), "Settings", "Update",
        "Platform fees: " + coop.getName(), "Success", httpRequest);
    return ResponseEntity.ok(CoopPlatformFees.of(coop, gatewayResolver.checkoutFor(coop.getId()).isPresent()));
  }

  /** Totals per co-op for the period (default: this month so far). */
  @GetMapping("/api/v1/platform-earnings")
  public ResponseEntity<?> summary(
      Authentication authentication,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    if (superAdmin(authentication) == null) return forbidden();
    LocalDate start = from != null ? from : LocalDate.now().withDayOfMonth(1);
    LocalDate end = to != null ? to : LocalDate.now();
    if (end.isBefore(start)) {
      return ResponseEntity.badRequest().body(Map.of("error", "The end date must be on or after the start date"));
    }

    Map<String, String> names = new HashMap<>();
    for (Cooperative coop : cooperativeRepository.findAll()) names.put(coop.getId(), coop.getName());

    Map<String, Map<String, BigDecimal>> perCoop = new LinkedHashMap<>();
    Map<String, Long> counts = new HashMap<>();
    Map<String, BigDecimal> overall = emptySources();
    for (PlatformEarningRepository.Total row :
        earningRepository.totals(start.atStartOfDay(), end.plusDays(1).atStartOfDay())) {
      perCoop.computeIfAbsent(row.getCooperativeId(), key -> emptySources())
          .merge(row.getSource(), row.getAmount(), BigDecimal::add);
      counts.merge(row.getCooperativeId(), row.getCount(), Long::sum);
      overall.merge(row.getSource(), row.getAmount(), BigDecimal::add);
    }

    List<CoopEarnings> coops = new ArrayList<>();
    // Every co-op is listed, even one that earned nothing in the period.
    for (Map.Entry<String, String> coop : names.entrySet()) {
      Map<String, BigDecimal> bySource = perCoop.getOrDefault(coop.getKey(), emptySources());
      coops.add(
          new CoopEarnings(
              coop.getKey(), coop.getValue(), sum(bySource), counts.getOrDefault(coop.getKey(), 0L), bySource));
    }
    coops.sort(
        Comparator.comparing(CoopEarnings::total).reversed()
            .thenComparing(CoopEarnings::cooperativeName, String.CASE_INSENSITIVE_ORDER));
    return ResponseEntity.ok(new EarningsSummary(start, end, sum(overall), overall, coops));
  }

  /** Every fee earned from one co-op in the period, newest first. */
  @GetMapping("/api/v1/platform-earnings/{cooperativeId}")
  public ResponseEntity<?> entries(
      Authentication authentication,
      @PathVariable String cooperativeId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    if (superAdmin(authentication) == null) return forbidden();
    LocalDate start = from != null ? from : LocalDate.now().withDayOfMonth(1);
    LocalDate end = to != null ? to : LocalDate.now();
    Map<String, String> memberNames = new HashMap<>();
    for (Member member : memberRepository.findAllByCooperativeId(cooperativeId)) {
      memberNames.put(member.getId(), member.getFullName());
    }
    return ResponseEntity.ok(
        earningRepository
            .findAllByCooperativeIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                cooperativeId, start.atStartOfDay(), end.plusDays(1).atStartOfDay())
            .stream()
            .map(
                e ->
                    new EarningEntry(
                        e.getId().toString(),
                        e.getSource(),
                        e.getMemberId(),
                        e.getMemberId() == null ? null : memberNames.get(e.getMemberId()),
                        e.getBaseAmount(),
                        e.getAmount(),
                        e.getReference(),
                        e.getCreatedAt()))
            .toList());
  }

  private static Map<String, BigDecimal> emptySources() {
    Map<String, BigDecimal> map = new LinkedHashMap<>();
    for (String source : SOURCES) map.put(source, BigDecimal.ZERO);
    return map;
  }

  private static BigDecimal sum(Map<String, BigDecimal> bySource) {
    return bySource.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private Member superAdmin(Authentication authentication) {
    Member caller = memberRepository.findById((String) authentication.getPrincipal()).orElse(null);
    return caller != null && "super_admin".equals(caller.getRole()) ? caller : null;
  }

  private static ResponseEntity<?> forbidden() {
    return ResponseEntity.status(403).body(Map.of("error", "Only a super admin can see platform earnings"));
  }
}
