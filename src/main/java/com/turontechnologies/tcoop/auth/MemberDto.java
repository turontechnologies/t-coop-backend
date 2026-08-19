package com.turontechnologies.tcoop.auth;

import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.member.Member;
import java.time.LocalDate;
import java.util.List;

/** Matches the `member` shape in api-contracts.md §1. */
public record MemberDto(
    String id,
    String name,
    String email,
    String role,
    String avatarUrl,
    Boolean subscriptionActive,
    LocalDate subscriptionExpiresAt,
    List<String> permissionModules) {

  /**
   * {@code coop} should be the caller's own co-operative when {@code member.role} is
   * {@code "admin"} — {@code subscriptionActive}/{@code subscriptionExpiresAt} are only ever
   * populated in that case, null otherwise, so the frontend only shows a renewal banner to the
   * one role that can act on it. {@code permissionModules} is only ever non-null for role
   * {@code "support"} — the frontend uses it to filter which nav items a platform-staff member
   * sees, resolved from their assigned PlatformRole (null here just means "not a support user,"
   * not "no permissions").
   */
  public static MemberDto from(Member member, Cooperative coop) {
    return from(member, coop, null);
  }

  public static MemberDto from(Member member, Cooperative coop, List<String> permissionModules) {
    boolean isAdmin = "admin".equals(member.getRole()) && coop != null;
    return new MemberDto(
        member.getId(),
        member.getFullName(),
        member.getEmail(),
        member.getRole(),
        member.getAvatarUrl(),
        isAdmin ? coop.hasActiveSubscription() : null,
        isAdmin ? coop.getSubscriptionExpiresAt() : null,
        permissionModules);
  }
}
