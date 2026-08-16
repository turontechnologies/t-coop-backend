package com.turontechnologies.tcoop.subscription;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Super admin's editable subscription price list — Payment Settings -> Subscription Plans.
 * {@link SubscriptionController} reads these (Active ones, of the right type) to decide what a
 * co-op can pay for self-service checkout; the plans themselves carry no reference back to any
 * payment, so deleting one never corrupts payment history (SubscriptionPayment snapshots the
 * label/duration it used at the time, not a foreign key to this table).
 */
@RestController
public class SubscriptionPlanController {

  private final SubscriptionPlanRepository planRepository;
  private final MemberRepository memberRepository;
  private final AuditLogService auditLogService;

  public SubscriptionPlanController(
      SubscriptionPlanRepository planRepository,
      MemberRepository memberRepository,
      AuditLogService auditLogService) {
    this.planRepository = planRepository;
    this.memberRepository = memberRepository;
    this.auditLogService = auditLogService;
  }

  @GetMapping("/api/v1/settings/subscription-plans")
  public ResponseEntity<?> list(Authentication authentication) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    List<SubscriptionPlanDto> plans =
        planRepository.findAllByOrderByTypeAscDurationInDaysAsc().stream()
            .map(SubscriptionPlanDto::from)
            .toList();
    return ResponseEntity.ok(plans);
  }

  @PostMapping("/api/v1/settings/subscription-plans")
  public ResponseEntity<?> create(
      Authentication authentication,
      @Valid @RequestBody SubscriptionPlanCreateRequest request,
      HttpServletRequest httpRequest) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    SubscriptionPlan plan =
        new SubscriptionPlan(
            request.type(), request.label(), request.durationInDays(), request.amount(), "Active");
    planRepository.save(plan);

    logChange(authentication, "Create", plan.getLabel(), httpRequest);
    return ResponseEntity.ok(SubscriptionPlanDto.from(plan));
  }

  @PatchMapping("/api/v1/settings/subscription-plans/{id}")
  public ResponseEntity<?> update(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody SubscriptionPlanUpdateRequest request,
      HttpServletRequest httpRequest) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    SubscriptionPlan plan = findPlan(id);
    if (plan == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that plan"));
    }

    plan.update(request.label(), request.durationInDays(), request.amount(), request.status());
    planRepository.save(plan);

    logChange(authentication, "Update", plan.getLabel(), httpRequest);
    return ResponseEntity.ok(SubscriptionPlanDto.from(plan));
  }

  @DeleteMapping("/api/v1/settings/subscription-plans/{id}")
  public ResponseEntity<?> delete(
      Authentication authentication, @PathVariable String id, HttpServletRequest httpRequest) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    SubscriptionPlan plan = findPlan(id);
    if (plan == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that plan"));
    }

    planRepository.delete(plan);
    logChange(authentication, "Delete", plan.getLabel(), httpRequest);
    return ResponseEntity.ok(Map.of("message", "Plan deleted"));
  }

  private SubscriptionPlan findPlan(String id) {
    try {
      return planRepository.findById(UUID.fromString(id)).orElse(null);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private void logChange(
      Authentication authentication, String action, String resource, HttpServletRequest httpRequest) {
    String callerId = (String) authentication.getPrincipal();
    auditLogService.log(callerId, "super_admin", "Settings", action, resource, "Success", httpRequest);
  }

  private ResponseEntity<?> requireSuperAdmin(Authentication authentication) {
    String callerId = (String) authentication.getPrincipal();
    Member caller = memberRepository.findById(callerId).orElse(null);
    if (caller == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    }
    if (!"super_admin".equals(caller.getRole())) {
      return ResponseEntity.status(403)
          .body(Map.of("error", "Only a super admin can manage subscription plans"));
    }
    return null;
  }
}
