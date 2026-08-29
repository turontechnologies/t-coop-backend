package com.turontechnologies.tcoop.savings;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.settings.PlatformSettings;
import com.turontechnologies.tcoop.settings.PlatformSettingsRepository;
import com.turontechnologies.tcoop.subscription.PaymentGatewayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The real backend behind a member's own "+ New Savings" / "Withdraw" actions, and an admin's
 * "Upload Teller" / withdrawal-request decisions — the self-service counterpart to
 * {@link SavingsController}'s read-only oversight. Deposits mirror
 * {@code SubscriptionController}'s initialize/confirm pattern exactly (see that class's own
 * javadoc); withdrawals use the {@code savings_requests} table that's existed, unused, since V1.
 */
@RestController
public class SavingsSelfServiceController {

  private static final Integer SETTINGS_SINGLETON_ID = 1;

  private final CooperativeRepository cooperativeRepository;
  private final MemberRepository memberRepository;
  private final SavingsTypeRepository savingsTypeRepository;
  private final SavingsRecordRepository savingsRecordRepository;
  private final SavingsPaymentIntentRepository intentRepository;
  private final SavingsRequestRepository savingsRequestRepository;
  private final PlatformSettingsRepository platformSettingsRepository;
  private final PaymentGatewayService paymentGatewayService;
  private final AuditLogService auditLogService;

  public SavingsSelfServiceController(
      CooperativeRepository cooperativeRepository,
      MemberRepository memberRepository,
      SavingsTypeRepository savingsTypeRepository,
      SavingsRecordRepository savingsRecordRepository,
      SavingsPaymentIntentRepository intentRepository,
      SavingsRequestRepository savingsRequestRepository,
      PlatformSettingsRepository platformSettingsRepository,
      PaymentGatewayService paymentGatewayService,
      AuditLogService auditLogService) {
    this.cooperativeRepository = cooperativeRepository;
    this.memberRepository = memberRepository;
    this.savingsTypeRepository = savingsTypeRepository;
    this.savingsRecordRepository = savingsRecordRepository;
    this.intentRepository = intentRepository;
    this.savingsRequestRepository = savingsRequestRepository;
    this.platformSettingsRepository = platformSettingsRepository;
    this.paymentGatewayService = paymentGatewayService;
    this.auditLogService = auditLogService;
  }

  @PostMapping("/api/v1/cooperatives/{id}/savings/deposits/initialize")
  public ResponseEntity<?> initializeDeposit(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody InitializeSavingsDepositRequest request) {
    var access = requireMemberOfCoop(authentication, id);
    if (access.error() != null) return access.error();
    Member member = access.caller();

    SavingsType type = findType(request.savingsTypeId(), id);
    if (type == null || !"Active".equals(type.getStatus())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that savings type"));
    }
    if (request.amount().compareTo(type.getMinAmount()) < 0
        || request.amount().compareTo(type.getMaxAmount()) > 0) {
      return ResponseEntity.status(409)
          .body(Map.of("error", "Amount must be between the type's minimum and maximum"));
    }

    PlatformSettings settings = platformSettings();
    if (!settings.isPaystackEnabled() || !notBlank(settings.getPaystackPublicKey())) {
      return ResponseEntity.status(409).body(Map.of("error", "Paystack isn't set up for this platform yet."));
    }
    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }

    String reference = "SAV-" + id + "-" + System.currentTimeMillis();
    intentRepository.save(
        new SavingsPaymentIntent(reference, id, member.getId(), type.getId(), request.amount()));

    BigDecimal chargeAmount = savingsChargeFor(coop, settings, request.amount());
    return ResponseEntity.ok(
        new InitializeSavingsDepositResponseDto(
            reference,
            request.amount(),
            settings.getPaystackPublicKey(),
            chargeAmount,
            request.amount().subtract(chargeAmount)));
  }

  @PostMapping("/api/v1/cooperatives/{id}/savings/deposits/confirm")
  public ResponseEntity<?> confirmDeposit(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody ConfirmSavingsDepositRequest request,
      HttpServletRequest httpRequest) {
    var access = requireMemberOfCoop(authentication, id);
    if (access.error() != null) return access.error();
    Member member = access.caller();

    SavingsPaymentIntent intent = intentRepository.findById(request.reference()).orElse(null);
    if (intent == null || !intent.getCooperativeId().equals(id) || !intent.getMemberId().equals(member.getId())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that payment."));
    }
    if ("Confirmed".equals(intent.getStatus())) {
      return ResponseEntity.status(409).body(Map.of("error", "That payment was already confirmed."));
    }

    PlatformSettings settings = platformSettings();
    PaymentGatewayService.VerificationResult verification =
        paymentGatewayService.verifyPaystack(intent.getReference(), settings.getPaystackSecretKey());

    if (!verification.success()) {
      intent.setStatus("Failed");
      intentRepository.save(intent);
      return ResponseEntity.status(402)
          .body(Map.of("error", verification.message() != null ? verification.message() : "Payment verification failed."));
    }
    if (verification.amountPaid().compareTo(intent.getAmount()) < 0) {
      intent.setStatus("Failed");
      intentRepository.save(intent);
      return ResponseEntity.status(402)
          .body(Map.of("error", "The amount paid doesn't match what was expected."));
    }

    intent.setStatus("Confirmed");
    intentRepository.save(intent);

    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }
    BigDecimal chargeAmount = savingsChargeFor(coop, settings, intent.getAmount());
    BigDecimal netAmount = intent.getAmount().subtract(chargeAmount);

    BigDecimal balanceAfter = savingsRecordRepository.sumByMember(member.getId()).add(netAmount);
    SavingsRecord record =
        new SavingsRecord(
            id,
            member.getId(),
            intent.getSavingsTypeId(),
            netAmount,
            balanceAfter,
            "Paystack",
            intent.getReference(),
            "Success",
            null);
    savingsRecordRepository.save(record);

    auditLogService.log(
        member.getId(), member.getRole(), "Savings", "Create", "Deposit", "Success", httpRequest);

    SavingsType type = savingsTypeRepository.findById(intent.getSavingsTypeId()).orElse(null);
    return ResponseEntity.ok(
        new SavingsDepositResultDto(
            SavingsRecordDto.from(record, member.getFullName(), type != null ? type.getName() : "Unknown type"),
            intent.getAmount(),
            chargeAmount));
  }

  /** Admin/coop-staff "Upload Teller" — no Paystack, the money already changed hands offline. */
  @PostMapping("/api/v1/cooperatives/{id}/savings/deposits/manual")
  public ResponseEntity<?> manualDeposit(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody ManualSavingsDepositRequest request,
      HttpServletRequest httpRequest) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();

    Member target = memberRepository.findById(request.memberId()).orElse(null);
    if (target == null || !id.equals(target.getCooperativeId())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that member"));
    }
    SavingsType type = findType(request.savingsTypeId(), id);
    if (type == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that savings type"));
    }
    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }
    BigDecimal chargeAmount = savingsChargeFor(coop, platformSettings(), request.amount());
    BigDecimal netAmount = request.amount().subtract(chargeAmount);

    BigDecimal balanceAfter = savingsRecordRepository.sumByMember(target.getId()).add(netAmount);
    String transactionId = "MANUAL-" + id + "-" + System.currentTimeMillis();
    SavingsRecord record =
        new SavingsRecord(
            id,
            target.getId(),
            type.getId(),
            netAmount,
            balanceAfter,
            "Manual Upload",
            transactionId,
            "Success",
            request.receiptUrl());
    savingsRecordRepository.save(record);

    auditLogService.log(
        access.caller().getId(), access.caller().getRole(), "Savings", "Create", target.getFullName(), "Success", httpRequest);

    return ResponseEntity.ok(
        new SavingsDepositResultDto(
            SavingsRecordDto.from(record, target.getFullName(), type.getName()), request.amount(), chargeAmount));
  }

  @PostMapping("/api/v1/cooperatives/{id}/savings/withdrawals")
  public ResponseEntity<?> requestWithdrawal(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody SavingsWithdrawalRequest request,
      HttpServletRequest httpRequest) {
    var access = requireMemberOfCoop(authentication, id);
    if (access.error() != null) return access.error();
    Member member = access.caller();

    SavingsType type = null;
    BigDecimal available;
    if (request.savingsTypeId() != null && !request.savingsTypeId().isBlank()) {
      type = findType(request.savingsTypeId(), id);
      if (type == null) {
        return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that savings type"));
      }
      available = savingsRecordRepository.sumByMemberAndSavingsType(member.getId(), type.getId());
    } else {
      available = savingsRecordRepository.sumByMember(member.getId());
    }
    if (request.amount().compareTo(available) > 0) {
      return ResponseEntity.status(409).body(Map.of("error", "That amount exceeds your available balance"));
    }

    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }
    PlatformSettings settings = platformSettings();

    BigDecimal coopFee = feeFor(coop.getWithdrawalFeeType(), coop.getWithdrawalFeeAmount(), request.amount());
    BigDecimal platformFee =
        feeFor(settings.getWithdrawalFeeType(), settings.getWithdrawalFeeAmount(), request.amount());
    BigDecimal feeAmount = coopFee.add(platformFee);
    BigDecimal netAmount = request.amount().subtract(feeAmount);
    BigDecimal feePercent =
        request.amount().signum() > 0
            ? feeAmount.multiply(BigDecimal.valueOf(100)).divide(request.amount(), 4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

    SavingsRequest savingsRequest =
        new SavingsRequest(
            id,
            member.getId(),
            "Withdrawal",
            type != null ? type.getId() : null,
            request.amount(),
            request.note(),
            feePercent,
            feeAmount,
            netAmount);
    savingsRequestRepository.save(savingsRequest);

    auditLogService.log(
        member.getId(), member.getRole(), "Savings", "Create", "Withdrawal request", "Success", httpRequest);

    return ResponseEntity.ok(
        SavingsRequestDto.from(savingsRequest, member.getFullName(), type != null ? type.getName() : "Total Savings"));
  }

  /** Admin/coop-staff sees their whole co-op's withdrawal requests; a member sees only their own
   * via {@code ?memberId=}, enforced server-side (not just a UI filter) below. */
  @GetMapping("/api/v1/cooperatives/{id}/savings/withdrawals")
  public ResponseEntity<?> listWithdrawals(
      Authentication authentication, @PathVariable String id, @RequestParam(required = false) String memberId) {
    var access = requireMemberOfCoop(authentication, id);
    if (access.error() != null) return access.error();
    Member caller = access.caller();

    boolean isStaff = "admin".equals(caller.getRole()) || "super_admin".equals(caller.getRole()) || caller.getCoopRoleId() != null;
    String effectiveMemberId = isStaff ? memberId : caller.getId();

    Map<UUID, String> typeNames = typeNamesFor(id);
    Map<String, String> memberNames = memberNamesFor(id);

    List<SavingsRequestDto> dtos =
        savingsRequestRepository.findAllByCooperativeIdOrderByRequestedAtDesc(id).stream()
            .filter(request -> effectiveMemberId == null || effectiveMemberId.equals(request.getMemberId()))
            .map(
                request ->
                    SavingsRequestDto.from(
                        request,
                        memberNames.getOrDefault(request.getMemberId(), "Unknown member"),
                        request.getSavingsTypeId() == null
                            ? "Total Savings"
                            : typeNames.getOrDefault(request.getSavingsTypeId(), "Unknown type")))
            .toList();
    return ResponseEntity.ok(dtos);
  }

  /** Approve is called by the frontend only after a real Paystack Transfer payout has already
   * succeeded — {@code transferReference} is that transfer's own reference, becoming this
   * withdrawal's transaction id. Decline just marks the request Declined; no money moved, so no
   * record. */
  @PatchMapping("/api/v1/cooperatives/{id}/savings/withdrawals/{requestId}")
  public ResponseEntity<?> decideWithdrawal(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String requestId,
      @Valid @RequestBody SavingsWithdrawalDecisionRequest request,
      HttpServletRequest httpRequest) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();

    SavingsRequest savingsRequest = findRequest(requestId);
    if (savingsRequest == null || !id.equals(savingsRequest.getCooperativeId())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that withdrawal request"));
    }
    if (!"Pending".equals(savingsRequest.getStatus())) {
      return ResponseEntity.status(409).body(Map.of("error", "That request was already resolved"));
    }

    if ("Approved".equals(request.status())) {
      if (!notBlank(request.transferReference())) {
        return ResponseEntity.badRequest().body(Map.of("error", "Missing transfer reference"));
      }
      BigDecimal balanceAfter =
          savingsRecordRepository.sumByMember(savingsRequest.getMemberId()).subtract(savingsRequest.getAmount());
      SavingsRecord record =
          new SavingsRecord(
              id,
              savingsRequest.getMemberId(),
              savingsRequest.getSavingsTypeId(),
              savingsRequest.getAmount().negate(),
              balanceAfter,
              "Manual Upload",
              request.transferReference(),
              "Success",
              null);
      savingsRecordRepository.save(record);
    }
    savingsRequest.resolve(request.status());
    savingsRequestRepository.save(savingsRequest);

    auditLogService.log(
        access.caller().getId(),
        access.caller().getRole(),
        "Savings",
        "Update",
        "Withdrawal request",
        "Approved".equals(request.status()) ? "Success" : "Warning",
        httpRequest);

    Map<String, String> memberNames = memberNamesFor(id);
    Map<UUID, String> typeNames = typeNamesFor(id);
    return ResponseEntity.ok(
        SavingsRequestDto.from(
            savingsRequest,
            memberNames.getOrDefault(savingsRequest.getMemberId(), "Unknown member"),
            savingsRequest.getSavingsTypeId() == null
                ? "Total Savings"
                : typeNames.getOrDefault(savingsRequest.getSavingsTypeId(), "Unknown type")));
  }

  private BigDecimal feeFor(String type, BigDecimal feeAmount, BigDecimal withdrawalAmount) {
    if ("Fixed".equals(type)) return feeAmount;
    return withdrawalAmount.multiply(feeAmount).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
  }

  /** The co-op's own savings-charge rate combined with the platform's — same "co-op rate +
   * platform rate" mechanics as the withdrawal fee above, applied to every deposit regardless of
   * method (Paystack or manual teller upload), since it's a standing cooperative operating cost
   * rather than a payment-processing-specific fee. */
  private BigDecimal savingsChargeFor(Cooperative coop, PlatformSettings settings, BigDecimal depositAmount) {
    BigDecimal coopCharge = feeFor(coop.getSavingsChargeType(), coop.getSavingsChargeAmount(), depositAmount);
    BigDecimal platformCharge = feeFor(settings.getSavingsChargeType(), settings.getSavingsChargeAmount(), depositAmount);
    return coopCharge.add(platformCharge);
  }

  private SavingsType findType(String typeId, String cooperativeId) {
    try {
      SavingsType type = savingsTypeRepository.findById(UUID.fromString(typeId)).orElse(null);
      return type != null && cooperativeId.equals(type.getCooperativeId()) ? type : null;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private SavingsRequest findRequest(String id) {
    try {
      return savingsRequestRepository.findById(UUID.fromString(id)).orElse(null);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private Map<UUID, String> typeNamesFor(String cooperativeId) {
    Map<UUID, String> names = new HashMap<>();
    for (SavingsType type : savingsTypeRepository.findAllByCooperativeIdOrderByCreatedAtAsc(cooperativeId)) {
      names.put(type.getId(), type.getName());
    }
    return names;
  }

  private Map<String, String> memberNamesFor(String cooperativeId) {
    Map<String, String> names = new HashMap<>();
    for (Member member : memberRepository.findAllByCooperativeId(cooperativeId)) {
      names.put(member.getId(), member.getFullName());
    }
    return names;
  }

  private boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }

  private PlatformSettings platformSettings() {
    return platformSettingsRepository
        .findById(SETTINGS_SINGLETON_ID)
        .orElseThrow(() -> new IllegalStateException("Platform settings row is missing"));
  }

  private record CoopAccess(Member caller, ResponseEntity<?> error) {}

  /** Any member of this co-op — including a plain member acting on their own savings, unlike
   * {@link #requireCoopAccess} which is admin/coop-staff/super-admin only. */
  private CoopAccess requireMemberOfCoop(Authentication authentication, String cooperativeId) {
    String callerId = (String) authentication.getPrincipal();
    Member caller = memberRepository.findById(callerId).orElse(null);
    if (caller == null) {
      return new CoopAccess(null, ResponseEntity.status(401).body(Map.of("error", "Member no longer exists")));
    }
    if ("super_admin".equals(caller.getRole())) {
      return new CoopAccess(caller, null);
    }
    if (cooperativeId.equals(caller.getCooperativeId())) {
      return new CoopAccess(caller, null);
    }
    return new CoopAccess(
        null, ResponseEntity.status(403).body(Map.of("error", "You can only manage your own co-operative's savings")));
  }

  /** See CooperativeController's requireCoopAccess for the full reasoning. */
  private CoopAccess requireCoopAccess(Authentication authentication, String cooperativeId) {
    String callerId = (String) authentication.getPrincipal();
    Member caller = memberRepository.findById(callerId).orElse(null);
    if (caller == null) {
      return new CoopAccess(null, ResponseEntity.status(401).body(Map.of("error", "Member no longer exists")));
    }
    if ("super_admin".equals(caller.getRole())) {
      return new CoopAccess(caller, null);
    }
    boolean isCoopStaff = "admin".equals(caller.getRole()) || caller.getCoopRoleId() != null;
    if (isCoopStaff && cooperativeId.equals(caller.getCooperativeId())) {
      return new CoopAccess(caller, null);
    }
    return new CoopAccess(
        null, ResponseEntity.status(403).body(Map.of("error", "You can only manage your own co-operative's savings")));
  }
}
