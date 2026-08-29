package com.turontechnologies.tcoop.subscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The single enforcement point for "no co-op can do anything on the platform until its
 * subscription is paid up" — and, independently, "a disabled co-op can't do anything at all."
 * Runs after {@code JwtAuthenticationFilter} resolves the caller, before any controller.
 * Read-only requests (GET/HEAD/OPTIONS), everything under {@code /api/v1/auth/**}, and the
 * health check always pass through — login/logout must keep working (AuthController.login
 * separately refuses a disabled co-op's members outright, and a lapsed-subscription co-op's
 * members too — see there), and an admin needs to be able to load their own dashboard to see
 * *why* they're blocked. {@code super_admin} is never gated (they're the one who unblocks
 * everyone else — reactivating a disabled co-op, or recording a payment via
 * {@link SubscriptionController}). Every other mutating request from an admin/member whose co-op
 * is disabled, or has no active subscription ({@link Cooperative#hasActiveSubscription}) —
 * including one that has never paid at all — is rejected here, before it reaches any controller.
 * This is the defense-in-depth half: it catches a session that started before the co-op was
 * disabled/lapsed and is still holding a valid JWT. See documentation/flows.md for the full
 * lifecycle.
 */
public class SubscriptionGateFilter extends OncePerRequestFilter {

  private static final String DISABLED_MESSAGE =
      "Your co-operative has been disabled. Please contact Turon Technologies for assistance.";
  private static final String EXPIRED_MESSAGE =
      "Your co-operative's subscription has expired. Please ask your super admin to renew it "
          + "before continuing.";

  private final MemberRepository memberRepository;
  private final CooperativeRepository cooperativeRepository;
  private final ObjectMapper objectMapper;

  public SubscriptionGateFilter(
      MemberRepository memberRepository,
      CooperativeRepository cooperativeRepository,
      ObjectMapper objectMapper) {
    this.memberRepository = memberRepository;
    this.cooperativeRepository = cooperativeRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (isExempt(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof String memberId)) {
      // No resolved caller -- let the request through; SecurityConfig's authorizeHttpRequests
      // is what actually rejects unauthenticated access, this filter only adds a further gate
      // on top of an already-identified caller.
      filterChain.doFilter(request, response);
      return;
    }

    Member caller = memberRepository.findById(memberId).orElse(null);
    if (caller == null || "super_admin".equals(caller.getRole())) {
      filterChain.doFilter(request, response);
      return;
    }

    String cooperativeId = caller.getCooperativeId();
    Cooperative coop =
        cooperativeId == null ? null : cooperativeRepository.findById(cooperativeId).orElse(null);
    if (coop == null) {
      filterChain.doFilter(request, response);
      return;
    }

    if ("Disabled".equals(coop.getStatus())) {
      reject(response, 403, DISABLED_MESSAGE);
      return;
    }
    if (!coop.hasActiveSubscription()) {
      reject(response, 402, EXPIRED_MESSAGE);
      return;
    }

    filterChain.doFilter(request, response);
  }

  private void reject(HttpServletResponse response, int status, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getWriter(), Map.of("error", message));
  }

  private boolean isExempt(HttpServletRequest request) {
    String method = request.getMethod();
    if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
      return true;
    }
    String path = request.getRequestURI();
    // /subscriptions/me/** is the one mutating path a dormant admin needs to pay their way back
    // in; /notifications/** has to stay interactive too — a co-op whose subscription has lapsed
    // is exactly the co-op most likely to have a "your subscription expired" notification sitting
    // unread, and marking it read (or read-all) must not itself be blocked by the very thing it's
    // telling them about. .../logo is exempt too — a brand-new co-op has no active subscription
    // yet (nothing to renew), so without this its admin could never set up their own branding
    // without a super admin doing it for them first. None of these are a free pass to mutate
    // anything else — every write against them is scoped to the caller's own data (see
    // SubscriptionController/NotificationController/CooperativeController.uploadLogo).
    return path.startsWith("/api/v1/auth/")
        || path.equals("/api/health")
        || path.startsWith("/api/v1/subscriptions/me")
        || path.startsWith("/api/v1/notifications")
        || (path.startsWith("/api/v1/cooperatives/") && path.endsWith("/logo"));
  }
}
