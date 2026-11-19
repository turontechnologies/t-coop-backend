package com.turontechnologies.tcoop.savings;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.coopgateway.CoopGatewayResolver;
import com.turontechnologies.tcoop.coopstaff.CoopPermissions;
import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.earnings.PlatformEarning;
import com.turontechnologies.tcoop.earnings.PlatformFees;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.notification.NotificationService;
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
  private final CoopGatewayResolver gatewayResolver;
  private final PaymentGatewayService paymentGatewayService;
  private final AuditLogService auditLogService;
  private final CoopPermissions coopPermissions;
  private final PlatformFees platformFees;
  private final NotificationService notificationService;

  public SavingsSelfServiceController(
      NotificationService notificationService,
      PlatformFees platformFees,
      CooperativeRepository cooperativeRepository,
      MemberRepository memberRepository,
      SavingsTypeRepository savingsTypeRepository,
      SavingsRecordRepository savingsRecordRepository,
      SavingsPaymentIntentRepository intentRepository,
      SavingsRequestRepository savingsRequestRepository,
      PlatformSettingsRepository platformSettingsRepository,
      CoopGatewayResolver gatewayResolver,
      PaymentGatewayService paymentGatewayService,
      AuditLogService auditLogService,
      CoopPermissions coopPermissions) {
    this.cooperativeRepository = cooperativeRepository;
    this.memberRepository = memberRepository;
    this.savingsTypeRepository = savingsTypeRepository;
    this.savingsRecordRepository = savingsRecordRepository;
    this.intentRepository = intentRepository;
    this.savingsRequestRepository = savingsRequestRepository;
    this.platformSettingsRepository = platformSettingsRepository;
    this.gatewayResolver = gatewayResolver;
    this.paymentGatewayService = paymentGatewayService;
    this.auditLogService = auditLogService;
    this.coopPermissions = coopPermissions;
    this.platformFees = platformFees;
    this.notificationService = notificationService;
  }

  @PostMapping("/api/v1/cooperatives/{id}/savings/deposits/initialize")
  public ResponseEntity<?> initializeDeposit(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody InitializeSavingsDepositRequest request) {
    var access = requireMemberOfCoop(authentication, id);
    if (access.error() != null) return access.error();
    Member member = access.caller();
    ResponseEntity<?> denied = requireSavingsWrite(member, id, "My Savings");
    if (denied != null) return denied;

    SavingsType type = findType(request.savingsTypeId(), id);
    if (type == null || !"Active".equals(type.getStatus())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that savings type"));
    }
    if (request.amount().compareTo(type.getMinAmount()) < 0
        || request.amount().compareTo(type.getMaxAmount()) > 0) {
      return ResponseEntity.status(409)
          .body(Map.of("error", "Amount must be between the type's minimum and maximum"));
    }

    CoopGatewayResolver.Checkout checkout = gatewayResolver.checkoutFor(id).orElse(null);
    if (checkout == null) {
      return ResponseEntity.status(409)
          .body(Map.of("error", "Online payments aren't set up for this co-operative yet."));
    }
    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }

    String reference = "SAV-" + id + "-" + System.currentTimeMillis();
    intentRepository.save(
        new SavingsPaymentIntent(
            reference, id, member.getId(), type.getId(), request.amount(), checkout.gateway(), checkout.keyOwner()));

    BigDecimal chargeAmount = savingsChargeFor(coop, platformSettings(), request.amount());
    return ResponseEntity.ok(
        new InitializeSavingsDepositResponseDto(
            reference,
            request.amount(),
            checkout.gateway(),
            checkout.publicKey(),
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
    ResponseEntity<?> denied = requireSavingsWrite(member, id, "My Savings");
    if (denied != null) return denied;

    SavingsPaymentIntent intent = intentRepository.findById(request.reference()).orElse(null);
    if (intent == null || !intent.getCooperativeId().equals(id) || !intent.getMemberId().equals(member.getId())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that payment."));
    }
    if ("Confirmed".equals(intent.getStatus())) {
      return ResponseEntity.status(409).body(Map.of("error", "That payment was already confirmed."));
    }

    PlatformSettings settings = platformSettings();
    PaymentGatewayService.VerificationResult verification =
        gatewayResolver.verify(id, intent.getGateway(), intent.getKeyOwner(), intent.getReference());

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
            intent.getGateway(),
            intent.getReference(),
            "Success",
            null);
    savingsRecordRepository.save(record);
    platformFees.record(
        id, member.getId(), PlatformEarning.SAVINGS_CHARGE, intent.getAmount(),
        PlatformFees.savingsCharge(coop, intent.getAmount()), intent.getReference());

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
    ResponseEntity<?> denied = requireSavingsWrite(access.caller(), id, "Members Savings");
    if (denied != null) return denied;

    Member target = memberRepository.findById(request.memberId()).orElse(null);
    if (target == null || !id.equals(target.getCooperativeId())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that member"));
    }
    SavingsType type = findType(request.savingsTypeId(), id);
    if (type == null || !"Active".equals(type.getStatus())) {
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
    platformFees.record(
        id, target.getId(), PlatformEarning.SAVINGS_CHARGE, request.amount(),
        PlatformFees.savingsCharge(coop, request.amount()), transactionId);

    auditLogService.log(
        access.caller().getId(), access.caller().getRole(), "Savings", "Create", target.getFullName(), "Success", httpRequest);

    return ResponseEntity.ok(
        new SavingsDepositResultDto(
            SavingsRecordDto.from(record, target.getFullName(), type.getName()), request.amount(), chargeAmount));
  }

  @PostMapping("/api/v1/cooperatives/{id}/savings/withdrawals")
  @org.springframework.transaction.annotation.Transactional
  public ResponseEntity<?> requestWithdrawal(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody SavingsWithdrawalRequest request,
      HttpServletRequest httpRequest) {
    var access = requireMemberOfCoop(authentication, id);
    if (access.error() != null) return access.error();
    Member member = access.caller();
    ResponseEntity<?> denied = requireSavingsWrite(member, id, "My Savings");
    if (denied != null) return denied;

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
    // Withdrawals already waiting for a decision are spoken for.
    final SavingsType scope = type;
    BigDecimal pending =
        savingsRequestRepository.findAllByCooperativeIdOrderByRequestedAtDesc(id).stream()
            .filter(r -> member.getId().equals(r.getMemberId()))
            .filter(r -> "Pending".equals(r.getStatus()) && "Withdrawal".equals(r.getRequestType()))
            .filter(r -> scope == null || scope.getId().equals(r.getSavingsTypeId()))
            .map(SavingsRequest::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal free = available.subtract(pending).max(BigDecimal.ZERO);
    if (request.amount().compareTo(free) > 0) {
      return ResponseEntity.status(409)
          .body(
              Map.of(
                  "error",
                  pending.signum() > 0
                      ? "You can withdraw up to " + naira(free) + " — " + naira(pending)
                          + " is already in a withdrawal request waiting for approval"
                      : "That amount exceeds your available balance"));
    }

    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }
    BigDecimal coopFee = feeFor(coop.getWithdrawalFeeType(), coop.getWithdrawalFeeAmount(), request.amount());
    BigDecimal platformFee = PlatformFees.withdrawalFee(coop, request.amount());
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
    savingsRequest.setPlatformFeeAmount(platformFee);
    savingsRequestRepository.save(savingsRequest);

    auditLogService.log(
        member.getId(), member.getRole(), "Savings", "Create", "Withdrawal request", "Success", httpRequest);
    if (!member.getId().equals(id)) {
      notificationService.notifyCoopAdmin(
          id,
          "SAVINGS_WITHDRAWAL_REQUESTED",
          "New withdrawal request",
          member.getFullName() + " asked to withdraw " + naira(request.amount())
              + " from " + (type != null ? type.getName() : "their savings") + ".",
          "/savings");
    }

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

    boolean isStaff =
        "super_admin".equals(caller.getRole()) || coopPermissions.isStaff(caller, id);
    String effectiveMemberId = isStaff ? memberId : caller.getId();
    if (coopPermissions.isStaff(caller, id)) {
      String tab =
          effectiveMemberId != null && effectiveMemberId.equals(caller.getId())
              ? "My Savings"
              : "Request";
      if (!coopPermissions.allows(caller, id, "Savings & Contributions", tab, false)) {
        return ResponseEntity.status(403)
            .body(Map.of("error", "Your role doesn't have read access to this savings view"));
      }
    }

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
                            : typeNames.getOrDefault(request.getSavingsTypeId(), "Unknown type"),
                        "Pending".equals(request.getStatus()) ? balanceFor(request) : null))
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
    ResponseEntity<?> denied = requireSavingsWrite(access.caller(), id, "Request");
    if (denied != null) return denied;

    SavingsRequest savingsRequest = findRequest(requestId);
    if (savingsRequest == null || !id.equals(savingsRequest.getCooperativeId())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that withdrawal request"));
    }
    if (!"Pending".equals(savingsRequest.getStatus())) {
      return ResponseEntity.status(409).body(Map.of("error", "That request was already resolved"));
    }
    if (!"Withdrawal".equals(savingsRequest.getRequestType())) {
      return ResponseEntity.status(409).body(Map.of("error", "Only withdrawal requests can be decided here"));
    }

    String payoutMethod = request.method() == null || request.method().isBlank() ? "Online" : request.method();
    if ("Approved".equals(request.status()) && !"Online".equals(payoutMethod)) {
      // The co-op already paid the member itself (bank transfer, cheque or cash): record it, with proof.
      String receipt = request.receiptUrl() == null ? "" : request.receiptUrl().trim();
      if (!"Cash".equals(payoutMethod) && receipt.isEmpty()) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", "Upload the " + ("Cheque".equals(payoutMethod) ? "cheque" : "transfer receipt") + " before approving."));
      }
      if (!receipt.isEmpty() && !receipt.startsWith("https://") && !receipt.startsWith("http://")) {
        return ResponseEntity.badRequest().body(Map.of("error", "That upload didn't work. Please upload it again."));
      }
      BigDecimal available =
          savingsRequest.getSavingsTypeId() != null
              ? savingsRecordRepository.sumByMemberAndSavingsType(savingsRequest.getMemberId(), savingsRequest.getSavingsTypeId())
              : savingsRecordRepository.sumByMember(savingsRequest.getMemberId());
      if (savingsRequest.getAmount().compareTo(available) > 0) {
        return ResponseEntity.status(409)
            .body(Map.of("error", "The member's savings no longer cover this withdrawal. Decline it instead."));
      }
      String reference = request.reference() == null || request.reference().isBlank() ? null : request.reference().trim();
      String transactionId =
          "wd-" + savingsRequest.getId().toString().toLowerCase().substring(0, 8) + (reference != null ? "-" + reference : "");
      savingsRequest.setBalances(available, available.subtract(savingsRequest.getAmount()));
      BigDecimal balanceAfter =
          savingsRecordRepository.sumByMember(savingsRequest.getMemberId()).subtract(savingsRequest.getAmount());
      savingsRecordRepository.save(
          new SavingsRecord(
              id, savingsRequest.getMemberId(), savingsRequest.getSavingsTypeId(), savingsRequest.getAmount().negate(),
              balanceAfter, "Transfer".equals(payoutMethod) ? "Bank Transfer" : payoutMethod,
              transactionId.length() > 100 ? transactionId.substring(0, 100) : transactionId, "Success",
              receipt.isEmpty() ? null : receipt));
      platformFees.record(
          id, savingsRequest.getMemberId(), PlatformEarning.WITHDRAWAL_FEE, savingsRequest.getAmount(),
          savingsRequest.getPlatformFeeAmount(), transactionId);
      savingsRequest.setPayout(payoutMethod, reference, receipt.isEmpty() ? null : receipt);
    } else if ("Approved".equals(request.status())) {
      savingsRequest.setPayout("Online", null, null);
      // Pay the member out before recording anything — same rule as loan disbursement: the
      // withdrawal only counts as approved once Paystack has actually accepted the transfer.
      Member payee = memberRepository.findById(savingsRequest.getMemberId()).orElse(null);
      String payeeName = payee != null ? payee.getFullName() : "This member";
      if (payee == null || !notBlank(payee.getBankCode()) || !notBlank(payee.getAccountNumber())) {
        return ResponseEntity.status(409)
            .body(Map.of("error", payeeName + " hasn't added a verified bank account yet, so the withdrawal can't be paid out."));
      }
      String payoutKey = gatewayResolver.payoutSecretKeyFor(id).orElse(null);
      if (payoutKey == null) {
        return ResponseEntity.status(409)
            .body(Map.of("error", "No Paystack account is set up to pay out withdrawals. Connect one in Settings → Integrations."));
      }
      Cooperative coop = cooperativeRepository.findById(id).orElse(null);
      BigDecimal payout =
          savingsRequest.getNetAmount() != null ? savingsRequest.getNetAmount() : savingsRequest.getAmount();
      // Fixed per request, so approving again after a hiccup can never pay the member twice.
      String reference = "wd-" + savingsRequest.getId().toString().toLowerCase();

      PaymentGatewayService.TransferResult transfer =
          paymentGatewayService.initiatePaystackTransfer(
              payoutKey,
              notBlank(payee.getAccountName()) ? payee.getAccountName() : payeeName,
              payee.getAccountNumber(),
              payee.getBankCode(),
              payout,
              (coop != null ? coop.getName() : "T-Coop") + " savings withdrawal",
              reference);
      if (!transfer.success()) {
        return ResponseEntity.status(502)
            .body(Map.of("error", transfer.message() != null ? transfer.message() : "The payout didn't go through."));
      }

      BigDecimal balanceBefore = balanceFor(savingsRequest);
      savingsRequest.setBalances(balanceBefore, balanceBefore.subtract(savingsRequest.getAmount()));
      BigDecimal balanceAfter =
          savingsRecordRepository.sumByMember(savingsRequest.getMemberId()).subtract(savingsRequest.getAmount());
      SavingsRecord record =
          new SavingsRecord(
              id,
              savingsRequest.getMemberId(),
              savingsRequest.getSavingsTypeId(),
              savingsRequest.getAmount().negate(),
              balanceAfter,
              "Paystack",
              reference,
              "Success",
              null);
      savingsRecordRepository.save(record);
      platformFees.record(
          id, savingsRequest.getMemberId(), PlatformEarning.WITHDRAWAL_FEE, savingsRequest.getAmount(),
          savingsRequest.getPlatformFeeAmount(), reference);
    }
    savingsRequest.resolve(request.status(), request.reason());
    savingsRequestRepository.save(savingsRequest);
    notifyWithdrawalDecision(savingsRequest);

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

  /** The savings a withdrawal comes out of: that savings type's balance, or all of the member's savings. */
  private BigDecimal balanceFor(SavingsRequest request) {
    return request.getSavingsTypeId() != null
        ? savingsRecordRepository.sumByMemberAndSavingsType(request.getMemberId(), request.getSavingsTypeId())
        : savingsRecordRepository.sumByMember(request.getMemberId());
  }

  /** Tells the member their withdrawal was approved (and what reached their bank) or declined. */
  private void notifyWithdrawalDecision(SavingsRequest savingsRequest) {
    BigDecimal paid =
        savingsRequest.getNetAmount() != null ? savingsRequest.getNetAmount() : savingsRequest.getAmount();
    if ("Approved".equals(savingsRequest.getStatus())) {
      notificationService.notify(
          savingsRequest.getMemberId(),
          "SAVINGS_WITHDRAWAL_APPROVED",
          "Your withdrawal was approved",
          "Your withdrawal of " + naira(savingsRequest.getAmount()) + " was approved. "
              + naira(paid) + switch (savingsRequest.getPayoutMethod() == null ? "Online" : savingsRequest.getPayoutMethod()) {
                case "Transfer" -> " was paid to you by bank transfer.";
                case "Cheque" -> " was paid to you by cheque.";
                case "Cash" -> " was paid to you in cash.";
                default -> " has been sent to your bank account.";
              }
              + (savingsRequest.getBalanceAfter() != null
                  ? " Your savings are now " + naira(savingsRequest.getBalanceAfter()) + "."
                  : ""),
          "/savings");
    } else {
      String reason = savingsRequest.getDeclineReason();
      notificationService.notify(
          savingsRequest.getMemberId(),
          "SAVINGS_WITHDRAWAL_DECLINED",
          "Your withdrawal was declined",
          "Your withdrawal request of " + naira(savingsRequest.getAmount()) + " was declined."
              + (reason != null ? " Reason: " + reason : "") + " Your savings are unchanged.",
          "/savings");
    }
  }

  private static String naira(BigDecimal amount) {
    return com.turontechnologies.tcoop.notification.Amounts.naira(amount);
  }

  /* ─────────────────────────────── Bulk upload ─────────────────────────────── */

  /** One row of a savings bulk upload, as the spreadsheet had it. */
  public record BulkSavingsRow(
      Integer row, String memberId, String savingsType, BigDecimal amount, String date) {}

  public record BulkSavingsRequest(
      @jakarta.validation.constraints.NotNull List<BulkSavingsRow> rows, boolean dryRun) {}

  public record BulkRowError(int row, String message) {}

  public record BulkSavingsLine(
      int row, String memberId, String memberName, String savingsType, BigDecimal amount,
      BigDecimal charge, BigDecimal net, java.time.LocalDate date) {}

  public record BulkSavingsResult(
      boolean dryRun, int imported, BigDecimal totalAmount, List<BulkSavingsLine> lines,
      List<BulkRowError> errors) {}

  /**
   * Savings for many members at once — Savings → Bulk upload. Every row is checked first; if any
   * row is wrong nothing is saved, so a file is never half-imported. {@code dryRun} checks
   * without saving, for the preview. Each row is recorded like a teller upload (charges apply).
   */
  @PostMapping("/api/v1/cooperatives/{id}/savings/bulk")
  @org.springframework.transaction.annotation.Transactional
  public ResponseEntity<?> bulkSavings(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody BulkSavingsRequest request,
      HttpServletRequest httpRequest) {
    var access = requireCoopAccess(authentication, id);
    if (access.error() != null) return access.error();
    ResponseEntity<?> denied = requireSavingsWrite(access.caller(), id, "Members Savings");
    if (denied != null) return denied;
    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }
    if (request.rows().isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("error", "The file has no rows to upload"));
    }
    if (request.rows().size() > 2000) {
      return ResponseEntity.badRequest().body(Map.of("error", "Upload at most 2,000 rows at a time"));
    }

    Map<String, Member> members = new HashMap<>();
    for (Member m : memberRepository.findAllByCooperativeId(id)) members.put(m.getId().toUpperCase(), m);
    Map<String, SavingsType> typesByName = new HashMap<>();
    for (SavingsType t : savingsTypeRepository.findAllByCooperativeIdOrderByCreatedAtAsc(id)) {
      if ("Active".equals(t.getStatus())) typesByName.put(t.getName().trim().toLowerCase(), t);
    }

    List<BulkRowError> errors = new java.util.ArrayList<>();
    List<BulkSavingsLine> lines = new java.util.ArrayList<>();
    List<Object[]> valid = new java.util.ArrayList<>(); // {Member, SavingsType, amount, date, charge}
    int index = 0;
    for (BulkSavingsRow row : request.rows()) {
      index++;
      int rowNumber = row.row() != null ? row.row() : index + 1;
      String memberId = row.memberId() == null ? "" : row.memberId().trim();
      Member member = members.get(memberId.toUpperCase());
      if (memberId.isEmpty()) {
        errors.add(new BulkRowError(rowNumber, "Membership ID is missing"));
        continue;
      }
      if (member == null) {
        errors.add(new BulkRowError(rowNumber, "No member " + memberId + " in this co-operative"));
        continue;
      }
      String typeName = row.savingsType() == null ? "" : row.savingsType().trim();
      SavingsType type =
          typeName.isEmpty() && typesByName.size() == 1
              ? typesByName.values().iterator().next()
              : typesByName.get(typeName.toLowerCase());
      if (type == null) {
        errors.add(
            new BulkRowError(
                rowNumber,
                typeName.isEmpty()
                    ? "Savings Type is missing"
                    : "\"" + typeName + "\" isn't an active savings type in this co-operative"));
        continue;
      }
      if (row.amount() == null || row.amount().signum() <= 0) {
        errors.add(new BulkRowError(rowNumber, "Amount must be more than zero"));
        continue;
      }
      BigDecimal amount = row.amount().setScale(2, RoundingMode.HALF_UP);
      java.time.LocalDate date;
      try {
        date = row.date() == null || row.date().isBlank()
            ? java.time.LocalDate.now()
            : java.time.LocalDate.parse(row.date().trim());
      } catch (java.time.format.DateTimeParseException e) {
        errors.add(new BulkRowError(rowNumber, "Date must be written as YYYY-MM-DD"));
        continue;
      }
      if (date.isAfter(java.time.LocalDate.now())) {
        errors.add(new BulkRowError(rowNumber, "Date can't be in the future"));
        continue;
      }
      BigDecimal charge = savingsChargeFor(coop, platformSettings(), amount);
      lines.add(
          new BulkSavingsLine(
              rowNumber, member.getId(), member.getFullName(), type.getName(), amount, charge,
              amount.subtract(charge), date));
      valid.add(new Object[] {member, type, amount, date, charge});
    }

    BigDecimal total = lines.stream().map(BulkSavingsLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (request.dryRun() || !errors.isEmpty()) {
      return ResponseEntity.status(request.dryRun() || errors.isEmpty() ? 200 : 422)
          .body(new BulkSavingsResult(true, 0, total, lines, errors));
    }

    String batch = Long.toString(System.currentTimeMillis(), 36).toUpperCase();
    Map<String, BigDecimal> balances = new HashMap<>();
    int n = 0;
    for (Object[] v : valid) {
      n++;
      Member member = (Member) v[0];
      SavingsType type = (SavingsType) v[1];
      BigDecimal amount = (BigDecimal) v[2];
      java.time.LocalDate date = (java.time.LocalDate) v[3];
      BigDecimal net = amount.subtract((BigDecimal) v[4]);
      BigDecimal balance =
          balances.computeIfAbsent(member.getId(), memberKey -> savingsRecordRepository.sumByMember(memberKey)).add(net);
      balances.put(member.getId(), balance);
      String transactionId = "BULK-" + id + "-" + batch + "-" + n;
      SavingsRecord record =
          new SavingsRecord(id, member.getId(), type.getId(), net, balance, "Bulk Upload", transactionId, "Success", null);
      record.setRecordDate(date);
      savingsRecordRepository.save(record);
      platformFees.record(
          id, member.getId(), PlatformEarning.SAVINGS_CHARGE, amount, PlatformFees.savingsCharge(coop, amount), transactionId);
    }

    auditLogService.log(
        access.caller().getId(), access.caller().getRole(), "Savings", "Import",
        valid.size() + " savings rows", "Success", httpRequest);
    return ResponseEntity.ok(new BulkSavingsResult(false, valid.size(), total, lines, List.of()));
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
    // The platform's rate is now set per co-op by the super admin (V55).
    return coopCharge.add(PlatformFees.savingsCharge(coop, depositAmount));
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

  private ResponseEntity<?> requireSavingsWrite(Member caller, String cooperativeId, String tab) {
    if (!coopPermissions.isStaff(caller, cooperativeId)
        || coopPermissions.allows(
            caller, cooperativeId, "Savings & Contributions", tab, true)) {
      return null;
    }
    return ResponseEntity.status(403)
        .body(Map.of("error", "Your role doesn't have write access to this savings action"));
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
    boolean isCoopStaff = coopPermissions.isStaff(caller, cooperativeId);
    if (isCoopStaff && cooperativeId.equals(caller.getCooperativeId())) {
      return new CoopAccess(caller, null);
    }
    return new CoopAccess(
        null, ResponseEntity.status(403).body(Map.of("error", "You can only manage your own co-operative's savings")));
  }
}
