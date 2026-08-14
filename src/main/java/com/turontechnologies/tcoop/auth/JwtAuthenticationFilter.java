package com.turontechnologies.tcoop.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads "Authorization: Bearer <token>", and if it's a valid JWT, sets the
 * SecurityContext so downstream code can rely on
 * SecurityContextHolder.getContext().getAuthentication(). Requests with no
 * (or an invalid) token simply proceed unauthenticated — SecurityConfig is
 * what actually decides which endpoints require authentication.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;

  public JwtAuthenticationFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");

    if (header != null && header.startsWith("Bearer ")) {
      String token = header.substring("Bearer ".length());
      Optional<String> memberId = jwtService.parseMemberId(token);

      if (memberId.isPresent() && SecurityContextHolder.getContext().getAuthentication() == null) {
        var authentication =
            new UsernamePasswordAuthenticationToken(
                memberId.get(), null, List.of(new SimpleGrantedAuthority("ROLE_AUTHENTICATED")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
      }
    }

    filterChain.doFilter(request, response);
  }
}
