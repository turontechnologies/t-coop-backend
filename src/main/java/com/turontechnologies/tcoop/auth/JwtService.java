package com.turontechnologies.tcoop.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.turontechnologies.tcoop.member.Member;

/**
 * Issues and validates the JWTs that every authenticated request carries in
 * its Authorization header. The token's subject is the member's ID; a
 * custom "role" claim carries super_admin/admin/member so authorization
 * checks don't need a database round-trip on every request.
 */
@Service
public class JwtService {

  private final SecretKey signingKey;
  private final long expirationMinutes;

  public JwtService(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.expiration-minutes:60}") long expirationMinutes) {
    this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expirationMinutes = expirationMinutes;
  }

  public String generateToken(Member member) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(member.getId())
        .claim("role", member.getRole())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(expirationMinutes * 60)))
        .signWith(signingKey)
        .compact();
  }

  /** Returns the member ID (JWT subject) if the token is valid and unexpired, empty otherwise. */
  public Optional<String> parseMemberId(String token) {
    try {
      Claims claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
      return Optional.of(claims.getSubject());
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
