package com.turontechnologies.tcoop.audit;

import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * The safety net for the audit log: every signed-in create/update/delete request that its
 * controller didn't log itself is logged here, with the module and action worked out from the
 * URL and "Failed" when the request didn't succeed. So a new endpoint, or one that forgot to
 * call {@link AuditLogService#log}, still shows up in the super admin's Activity logs.
 */
@Component
public class AuditTrailInterceptor implements HandlerInterceptor {

  private static final Logger log = LoggerFactory.getLogger(AuditTrailInterceptor.class);

  private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

  /** Background housekeeping, not user activity: marking notifications read, phone push tokens. */
  private static final Set<String> SKIPPED = Set.of("notifications", "push-tokens");

  private static final Map<String, String> MODULES =
      Map.ofEntries(
          Map.entry("auth", "Authentication"),
          Map.entry("password-reset", "Authentication"),
          Map.entry("cooperatives", "Co-operatives"),
          Map.entry("members", "Members"),
          Map.entry("savings", "Savings"),
          Map.entry("loans", "Loans"),
          Map.entry("loan-settings", "Loans"),
          Map.entry("loan-fees", "Loans"),
          Map.entry("guarantor-invites", "Loans"),
          Map.entry("subscriptions", "Subscriptions"),
          Map.entry("subscription-plans", "Subscriptions"),
          Map.entry("notices", "Notices"),
          Map.entry("meetings", "Notices"),
          Map.entry("settings", "Settings"),
          Map.entry("platform-fees", "Settings"),
          Map.entry("integrations", "Settings"),
          Map.entry("profile", "Settings"),
          Map.entry("uploads", "Settings"),
          Map.entry("platform-users", "Users"),
          Map.entry("platform-roles", "Users"),
          Map.entry("platform-invites", "Users"),
          Map.entry("coop-users", "Users"),
          Map.entry("coop-roles", "Users"),
          Map.entry("support", "Support"),
          Map.entry("support-tickets", "Support"));

  private final AuditLogService auditLogService;
  private final MemberRepository memberRepository;

  public AuditTrailInterceptor(AuditLogService auditLogService, MemberRepository memberRepository) {
    this.auditLogService = auditLogService;
    this.memberRepository = memberRepository;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    try {
      if (!WRITE_METHODS.contains(request.getMethod())) return;
      if (request.getAttribute(AuditLogService.LOGGED_ATTRIBUTE) != null) return;

      String path = request.getRequestURI().replaceFirst("^/api/v1/?", "");
      String[] parts = path.split("/");
      if (parts.length == 0 || parts[0].isBlank() || SKIPPED.contains(parts[0])) return;

      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth == null || !(auth.getPrincipal() instanceof String actorId) || "anonymousUser".equals(actorId)) {
        return;
      }
      Member actor = memberRepository.findById(actorId).orElse(null);
      if (actor == null) return;

      boolean failed = ex != null || response.getStatus() >= 400;
      auditLogService.log(
          actor.getId(),
          actor.getRole(),
          moduleFor(parts[0]),
          actionFor(request.getMethod(), parts),
          path,
          failed ? "Failed" : "Success",
          request);
    } catch (RuntimeException e) {
      // The audit trail must never break the request it's recording.
      log.warn("Couldn't write audit log entry for {} {}", request.getMethod(), request.getRequestURI(), e);
    }
  }

  static String moduleFor(String segment) {
    String known = MODULES.get(segment);
    if (known != null) return known;
    if (segment.startsWith("loan")) return "Loans";
    if (segment.startsWith("saving")) return "Savings";
    if (segment.startsWith("platform-") || segment.startsWith("coop-")) return "Settings";
    String words = segment.replace('-', ' ');
    return words.isEmpty() ? "Other" : Character.toUpperCase(words.charAt(0)) + words.substring(1);
  }

  static String actionFor(String method, String[] parts) {
    String last = parts[parts.length - 1].toLowerCase(Locale.ROOT);
    for (var rule :
        List.of(
            Map.entry("approve", "Approve"),
            Map.entry("accept", "Approve"),
            Map.entry("decline", "Decline"),
            Map.entry("reject", "Decline"),
            Map.entry("cancel", "Decline"),
            Map.entry("pay", "Payment"),
            Map.entry("confirm", "Payment"),
            Map.entry("disburse", "Payment"),
            Map.entry("repay", "Payment"),
            Map.entry("login", "Login"),
            Map.entry("logout", "Logout"))) {
      if (last.contains(rule.getKey())) return rule.getValue();
    }
    return switch (method) {
      case "POST" -> "Create";
      case "DELETE" -> "Delete";
      default -> "Update";
    };
  }
}
