package com.turontechnologies.tcoop.subscription;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.auth.EmailDeliveryException;
import com.turontechnologies.tcoop.auth.EmailService;
import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.notification.NotificationService;
import com.turontechnologies.tcoop.settings.PlatformSettings;
import com.turontechnologies.tcoop.settings.PlatformSettingsRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Super-admin-only platform subscription management. Recording a payment here is what keeps a
 * co-op's account usable at all — see {@link SubscriptionGateFilter}, which blocks every
 * mutating request from an admin/member of a co-op with no active subscription platform-wide.
 * Pricing itself comes from {@link SubscriptionPlan} (Payment Settings -> Subscription Plans,
 * managed via {@link SubscriptionPlanController}), not a fixed formula. See
 * documentation/flows.md for the full lifecycle.
 */
@RestController
public class SubscriptionController {

  private static final Logger log = LoggerFactory.getLogger(SubscriptionController.class);

  private static final Integer SETTINGS_SINGLETON_ID = 1;

  private final CooperativeRepository cooperativeRepository;
  private final MemberRepository memberRepository;
  private final SubscriptionPaymentRepository subscriptionPaymentRepository;
  private final SubscriptionPaymentIntentRepository intentRepository;
  private final SubscriptionPlanRepository planRepository;
  private final PlatformSettingsRepository platformSettingsRepository;
  private final PaymentGatewayService paymentGatewayService;
  private final EmailService emailService;
  private final AuditLogService auditLogService;
  private final NotificationService notificationService;

  @Value("${app.frontend-url}")
  private String frontendUrl;

  public SubscriptionController(
      CooperativeRepository cooperativeRepository,
      MemberRepository memberRepository,
      SubscriptionPaymentRepository subscriptionPaymentRepository,
      SubscriptionPaymentIntentRepository intentRepository,
      SubscriptionPlanRepository planRepository,
      PlatformSettingsRepository platformSettingsRepository,
      PaymentGatewayService paymentGatewayService,
      EmailService emailService,
      AuditLogService auditLogService,
      NotificationService notificationService) {
    this.cooperativeRepository = cooperativeRepository;
    this.memberRepository = memberRepository;
    this.subscriptionPaymentRepository = subscriptionPaymentRepository;
    this.intentRepository = intentRepository;
    this.planRepository = planRepository;
    this.platformSettingsRepository = platformSettingsRepository;
    this.paymentGatewayService = paymentGatewayService;
    this.emailService = emailService;
    this.auditLogService = auditLogService;
    this.notificationService = notificationService;
  }

  @GetMapping("/api/v1/subscriptions")
  public ResponseEntity<?> list(Authentication authentication) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    List<SubscriptionSummaryDto> rows =
        cooperativeRepository.findAllByOrderByNameAsc().stream().map(this::toSummary).toList();
    return ResponseEntity.ok(rows);
  }

  @GetMapping("/api/v1/subscriptions/summary")
  public ResponseEntity<?> summary(Authentication authentication) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    BigDecimal total =
        cooperativeRepository.findAll().stream()
            .map(coop -> subscriptionPaymentRepository.sumByCooperative(coop.getId()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return ResponseEntity.ok(new PlatformSubscriptionSummaryDto(total));
  }

  @GetMapping("/api/v1/cooperatives/{id}/subscriptions")
  public ResponseEntity<?> history(Authentication authentication, @PathVariable String id) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    if (!cooperativeRepository.existsById(id)) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }

    List<SubscriptionPaymentDto> payments =
        subscriptionPaymentRepository.findAllByCooperativeIdOrderByPaymentDateDesc(id).stream()
            .map(SubscriptionPaymentDto::from)
            .toList();
    return ResponseEntity.ok(payments);
  }

  @PostMapping("/api/v1/cooperatives/{id}/subscriptions")
  public ResponseEntity<?> recordPayment(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody SubscriptionPaymentCreateRequest request,
      HttpServletRequest httpRequest) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }

    SubscriptionPlan plan = findPlan(request.planId());
    if (plan == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that subscription plan"));
    }

    SubscriptionPayment payment =
        applyPayment(coop, request.amountPaid(), "Manual", plan.getLabel(), plan.getDurationInDays());

    auditLogService.log(
        adminIdOf(authentication),
        "super_admin",
        "Subscriptions",
        "Create",
        coop.getName(),
        "Success",
        httpRequest);

    return ResponseEntity.ok(
        new SubscriptionPaymentCreatedDto(
            SubscriptionPaymentDto.from(payment), coop.getSubscriptionExpiresAt()));
  }

  // ---------------------------------------------------------------------------------------
  // Self-service (the signed-in admin, for their own co-op) — Paystack/Flutterwave checkout.
  // This is the one path a dormant co-op can still use: SubscriptionGateFilter exempts every
  // /api/v1/subscriptions/me/** route so an admin locked out of everything else can still pay
  // their way back in. See documentation/flows.md for the full initialize -> confirm sequence.
  // ---------------------------------------------------------------------------------------

  @GetMapping("/api/v1/subscriptions/me")
  public ResponseEntity<?> mySubscription(Authentication authentication) {
    var result = requireAdminWithCoop(authentication);
    if (result.error() != null) return result.error();
    Member admin = result.admin();
    Cooperative coop = result.coop();
    PlatformSettings settings = platformSettings();

    List<MySubscriptionDto.GatewayOption> gateways = new ArrayList<>();
    if (settings.isPaystackEnabled() && notBlank(settings.getPaystackPublicKey())) {
      gateways.add(new MySubscriptionDto.GatewayOption("Paystack", settings.getPaystackPublicKey()));
    }
    if (settings.isFlutterwaveEnabled() && notBlank(settings.getFlutterwavePublicKey())) {
      gateways.add(
          new MySubscriptionDto.GatewayOption("Flutterwave", settings.getFlutterwavePublicKey()));
    }
    if (settings.isOpayEnabled() && notBlank(settings.getOpayPublicKey())) {
      gateways.add(new MySubscriptionDto.GatewayOption("Opay", settings.getOpayPublicKey()));
    }

    List<SubscriptionPlanDto> availablePlans =
        planRepository
            .findAllByTypeAndStatusOrderByDurationInDaysAsc(planTypeFor(coop), "Active")
            .stream()
            .map(SubscriptionPlanDto::from)
            .toList();

    return ResponseEntity.ok(
        new MySubscriptionDto(
            coop.getId(),
            coop.getName(),
            admin.getFullName(),
            coop.hasActiveSubscription() ? "Active" : "Overdue",
            coop.getSubscriptionCycle(),
            coop.getSubscriptionExpiresAt(),
            availablePlans,
            gateways));
  }

  @GetMapping("/api/v1/subscriptions/me/history")
  public ResponseEntity<?> myHistory(Authentication authentication) {
    var result = requireAdminWithCoop(authentication);
    if (result.error() != null) return result.error();

    List<SubscriptionPaymentDto> payments =
        subscriptionPaymentRepository
            .findAllByCooperativeIdOrderByPaymentDateDesc(result.coop().getId())
            .stream()
            .map(SubscriptionPaymentDto::from)
            .toList();
    return ResponseEntity.ok(payments);
  }

  @PostMapping("/api/v1/subscriptions/me/initialize")
  public ResponseEntity<?> initializePayment(
      Authentication authentication, @Valid @RequestBody InitializePaymentRequest request) {
    var result = requireAdminWithCoop(authentication);
    if (result.error() != null) return result.error();
    Cooperative coop = result.coop();
    PlatformSettings settings = platformSettings();

    SubscriptionPlan plan = findPlan(request.planId());
    if (plan == null || !"Active".equals(plan.getStatus())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that subscription plan"));
    }
    if (!plan.getType().equals(planTypeFor(coop))) {
      return ResponseEntity.status(409)
          .body(Map.of("error", "That plan isn't available for your co-operative right now."));
    }

    String publicKey = null;
    String checkoutUrl = null;
    String reference = generateTransactionReference(coop.getId());

    if ("Paystack".equals(request.gateway())) {
      if (!settings.isPaystackEnabled() || !notBlank(settings.getPaystackPublicKey())) {
        return ResponseEntity.status(409)
            .body(Map.of("error", "Paystack isn't set up for this platform yet."));
      }
      publicKey = settings.getPaystackPublicKey();
    } else if ("Flutterwave".equals(request.gateway())) {
      if (!settings.isFlutterwaveEnabled() || !notBlank(settings.getFlutterwavePublicKey())) {
        return ResponseEntity.status(409)
            .body(Map.of("error", "Flutterwave isn't set up for this platform yet."));
      }
      publicKey = settings.getFlutterwavePublicKey();
    } else {
      if (!settings.isOpayEnabled()
          || !notBlank(settings.getOpayPublicKey())
          || !notBlank(settings.getOpaySecretKey())
          || !notBlank(settings.getOpayMerchantId())) {
        return ResponseEntity.status(409)
            .body(Map.of("error", "OPay isn't set up for this platform yet."));
      }
      PaymentGatewayService.OpayCheckoutResult checkout =
          paymentGatewayService.createOpayCheckout(
              reference,
              plan.getAmount(),
              frontendUrl + "/support?opay_reference=" + reference,
              "Subscription - " + plan.getLabel(),
              coop.getName() + " subscription (" + plan.getLabel() + ")",
              result.admin().getEmail(),
              settings.getOpayPublicKey(),
              settings.getOpaySecretKey(),
              settings.getOpayMerchantId());
      if (!checkout.success()) {
        return ResponseEntity.status(502)
            .body(Map.of("error", checkout.message() != null ? checkout.message() : "Couldn't start that OPay payment."));
      }
      checkoutUrl = checkout.cashierUrl();
    }

    intentRepository.save(
        new SubscriptionPaymentIntent(
            reference,
            coop.getId(),
            plan.getAmount(),
            plan.getLabel(),
            plan.getDurationInDays(),
            request.gateway()));

    return ResponseEntity.ok(
        new InitializePaymentResponseDto(
            reference, plan.getAmount(), request.gateway(), publicKey, checkoutUrl));
  }

  @PostMapping("/api/v1/subscriptions/me/confirm")
  public ResponseEntity<?> confirmPayment(
      Authentication authentication,
      @Valid @RequestBody ConfirmPaymentRequest request,
      HttpServletRequest httpRequest) {
    var result = requireAdminWithCoop(authentication);
    if (result.error() != null) return result.error();
    Member admin = result.admin();
    Cooperative coop = result.coop();

    SubscriptionPaymentIntent intent = intentRepository.findById(request.reference()).orElse(null);
    if (intent == null || !intent.getCooperativeId().equals(coop.getId())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that payment."));
    }
    if ("Confirmed".equals(intent.getStatus())) {
      return ResponseEntity.status(409).body(Map.of("error", "That payment was already confirmed."));
    }

    PlatformSettings settings = platformSettings();
    PaymentGatewayService.VerificationResult verification =
        switch (intent.getGateway()) {
          case "Paystack" -> paymentGatewayService.verifyPaystack(
              intent.getReference(), settings.getPaystackSecretKey());
          case "Opay" -> paymentGatewayService.verifyOpay(
              intent.getReference(), settings.getOpaySecretKey(), settings.getOpayMerchantId());
          default -> paymentGatewayService.verifyFlutterwave(
              intent.getReference(), settings.getFlutterwaveSecretKey());
        };

    if (!verification.success()) {
      intent.setStatus("Failed");
      intentRepository.save(intent);
      return ResponseEntity.status(402)
          .body(Map.of("error", verification.message() != null ? verification.message() : "Payment verification failed."));
    }
    // The gateway confirms real money moved, but only for exactly the amount *we* decided to
    // charge when this intent was created — a manipulated client-side amount can't sneak
    // through even if it somehow got past the gateway's own checkout page.
    if (verification.amountPaid().compareTo(intent.getAmount()) < 0) {
      intent.setStatus("Failed");
      intentRepository.save(intent);
      return ResponseEntity.status(402)
          .body(Map.of("error", "The amount paid doesn't match what was expected."));
    }

    intent.setStatus("Confirmed");
    intentRepository.save(intent);

    SubscriptionPayment payment =
        applyPayment(
            coop, intent.getAmount(), intent.getGateway(), intent.getCycle(), intent.getDurationInDays());

    auditLogService.log(
        admin.getId(), "admin", "Subscriptions", "Create", coop.getName(), "Success", httpRequest);

    return ResponseEntity.ok(
        new SubscriptionReceiptDto(
            coop.getId(),
            coop.getName(),
            admin.getFullName(),
            payment.getPaymentRef(),
            payment.getAmountPaid(),
            payment.getMethod(),
            payment.getPaymentDate(),
            payment.getType(),
            payment.getCycle(),
            payment.getStatus(),
            coop.getSubscriptionExpiresAt()));
  }

  /**
   * Shared by both the super admin's manual entry and the self-service gateway confirmation —
   * creates the payment row, extends the co-op's subscription, and emails the receipt. The
   * caller has already decided the amount is trustworthy (a super admin typing it in, or a
   * verified gateway transaction) before reaching here.
   */
  private SubscriptionPayment applyPayment(
      Cooperative coop, BigDecimal amount, String method, String cycle, int durationInDays) {
    String type = coop.getSubscriptionExpiresAt() == null ? "New Subscription" : "Renewal";
    // Renewing before the current period lapses extends from the existing expiry (no time
    // lost); renewing after it lapsed (or subscribing for the first time) starts counting from
    // today instead of stacking onto a date that's already in the past.
    LocalDate periodStart =
        coop.hasActiveSubscription() ? coop.getSubscriptionExpiresAt() : LocalDate.now();
    LocalDate newExpiresAt = periodStart.plusDays(durationInDays);

    SubscriptionPayment payment =
        new SubscriptionPayment(
            coop.getId(),
            generatePaymentRef(),
            amount,
            method,
            LocalDate.now(),
            type,
            cycle,
            "Active",
            newExpiresAt);
    subscriptionPaymentRepository.save(payment);

    coop.recordSubscriptionPayment(cycle, newExpiresAt);
    cooperativeRepository.save(coop);

    Member admin = memberRepository.findById(coop.getId()).orElse(null);
    if (admin != null) {
      try {
        emailService.sendSubscriptionReceiptEmail(
            admin.getEmail(), admin.getFullName(), coop.getName(), amount, type, cycle, newExpiresAt);
      } catch (EmailDeliveryException e) {
        log.warn(
            "Subscription payment recorded for {} but receipt email to {} failed: {}",
            coop.getId(),
            admin.getEmail(),
            e.getMessage());
      }
    }

    notificationService.notifyCoopAdmin(
        coop.getId(),
        "SUBSCRIPTION_RENEWED",
        "Subscription active",
        "Payment received — %s's subscription is active until %s (%s)."
            .formatted(coop.getName(), newExpiresAt, cycle),
        "/support");

    return payment;
  }

  /** "New Subscription" until a co-op's first payment ever lands, "Renewal" from then on —
   * matches the same auto-detection applyPayment already does, so the plans offered always
   * match what a payment against this co-op would actually be classified as. */
  private String planTypeFor(Cooperative coop) {
    return coop.getSubscriptionExpiresAt() == null ? "New Subscription" : "Renewal";
  }

  private SubscriptionPlan findPlan(String id) {
    try {
      return planRepository.findById(UUID.fromString(id)).orElse(null);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private record AdminLookup(Member admin, Cooperative coop, ResponseEntity<?> error) {}

  private AdminLookup requireAdminWithCoop(Authentication authentication) {
    String callerId = (String) authentication.getPrincipal();
    Member caller = memberRepository.findById(callerId).orElse(null);
    if (caller == null) {
      return new AdminLookup(null, null, ResponseEntity.status(401).body(Map.of("error", "Member no longer exists")));
    }
    if (!"admin".equals(caller.getRole())) {
      return new AdminLookup(
          null, null, ResponseEntity.status(403).body(Map.of("error", "Only a co-op admin can manage its subscription")));
    }
    Cooperative coop =
        caller.getCooperativeId() == null
            ? null
            : cooperativeRepository.findById(caller.getCooperativeId()).orElse(null);
    if (coop == null) {
      return new AdminLookup(
          null, null, ResponseEntity.status(404).body(Map.of("error", "We couldn't find your co-operative")));
    }
    return new AdminLookup(caller, coop, null);
  }

  private PlatformSettings platformSettings() {
    return platformSettingsRepository
        .findById(SETTINGS_SINGLETON_ID)
        .orElseThrow(() -> new IllegalStateException("Platform settings row is missing"));
  }

  private boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }

  private SubscriptionSummaryDto toSummary(Cooperative coop) {
    BigDecimal revenueEarned = subscriptionPaymentRepository.sumByCooperative(coop.getId());
    LocalDate lastPaymentDate =
        subscriptionPaymentRepository
            .findFirstByCooperativeIdOrderByPaymentDateDesc(coop.getId())
            .map(SubscriptionPayment::getPaymentDate)
            .orElse(null);
    return new SubscriptionSummaryDto(
        coop.getId(),
        coop.getName(),
        revenueEarned,
        coop.getSubscriptionFee(),
        coop.getSubscriptionCycle(),
        lastPaymentDate,
        coop.getSubscriptionExpiresAt(),
        coop.hasActiveSubscription() ? "Active" : "Overdue");
  }

  /** The internal display reference on a *confirmed* SubscriptionPayment row — only ever
   * generated once per actual payment, so a same-day sequential counter is safe here. */
  private String generatePaymentRef() {
    LocalDate today = LocalDate.now();
    long n = subscriptionPaymentRepository.countByPaymentDate(today) + 1;
    return "SUB-" + today.toString().replace("-", "") + "-" + String.format("%04d", n);
  }

  /** The reference handed to Paystack/Flutterwave for one checkout *attempt* — initialize can be
   * called many times before (or instead of) a successful confirm, so this must be unique per
   * call regardless of how many payments have actually been recorded today. Gateways reject a
   * reused reference outright, so a same-day sequential counter (like generatePaymentRef above)
   * would collide across retries/reopened checkouts before the first one is ever confirmed. */
  private String generateTransactionReference(String cooperativeId) {
    return "SUB-" + cooperativeId + "-" + System.currentTimeMillis();
  }

  private String adminIdOf(Authentication authentication) {
    return (String) authentication.getPrincipal();
  }

  private ResponseEntity<?> requireSuperAdmin(Authentication authentication) {
    String callerId = (String) authentication.getPrincipal();
    Member caller = memberRepository.findById(callerId).orElse(null);
    if (caller == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    }
    if (!"super_admin".equals(caller.getRole())) {
      return ResponseEntity.status(403)
          .body(Map.of("error", "Only a super admin can manage subscriptions"));
    }
    return null;
  }
}
