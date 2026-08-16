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
 * subscription is paid up." Runs after {@code JwtAuthenticationFilter} resolves the caller,
 * before any controller. Read-only requests (GET/HEAD/OPTIONS), everything under
 * {@code /api/v1/auth/**}, and the health check always pass through — login/logout must keep
 * working, and an admin needs to be able to load their own dashboard to see *why* they're
 * blocked. {@code super_admin} is never gated (they're the one who unblocks everyone else, by
 * recording a payment via {@link SubscriptionController}). Every other mutating request from an
 * admin/member whose co-op has no active subscription ({@link Cooperative#hasActiveSubscription})
 * — including one that has never paid at all — is rejected here with 402, before it reaches any
 * controller. See documentation/flows.md for the full lifecycle.
 */
public class SubscriptionGateFilter extends OncePerRequestFilter {

  private static final String MESSAGE =
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
    if (coop == null || coop.hasActiveSubscription()) {
      filterChain.doFilter(request, response);
      return;
    }

    response.setStatus(402);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getWriter(), Map.of("error", MESSAGE));
  }

  private boolean isExempt(HttpServletRequest request) {
    String method = request.getMethod();
    if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
      return true;
    }
    String path = request.getRequestURI();
    // /subscriptions/me/** is the one mutating path a dormant admin needs — it's how they pay
    // their way back in. Every write against it is still verified server-side against the real
    // gateway API (see SubscriptionController/PaymentGatewayService); it's never a free pass to
    // mutate anything else.
    return path.startsWith("/api/v1/auth/")
        || path.equals("/api/health")
        || path.startsWith("/api/v1/subscriptions/me");
  }
}
