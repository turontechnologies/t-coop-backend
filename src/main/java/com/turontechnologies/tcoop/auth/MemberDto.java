package com.turontechnologies.tcoop.auth;

import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.member.Member;
import java.time.LocalDate;

/** Matches the `member` shape in api-contracts.md §1. */
public record MemberDto(
    String id,
    String name,
    String email,
    String role,
    String avatarUrl,
    Boolean subscriptionActive,
    LocalDate subscriptionExpiresAt) {

  /**
   * {@code coop} should be the caller's own co-operative when {@code member.role} is
   * {@code "admin"} — {@code subscriptionActive}/{@code subscriptionExpiresAt} are only ever
   * populated in that case, null otherwise, so the frontend only shows a renewal banner to the
   * one role that can act on it.
   */
  public static MemberDto from(Member member, Cooperative coop) {
    boolean isAdmin = "admin".equals(member.getRole()) && coop != null;
    return new MemberDto(
        member.getId(),
        member.getFullName(),
        member.getEmail(),
        member.getRole(),
        member.getAvatarUrl(),
        isAdmin ? coop.hasActiveSubscription() : null,
        isAdmin ? coop.getSubscriptionExpiresAt() : null);
  }
}
