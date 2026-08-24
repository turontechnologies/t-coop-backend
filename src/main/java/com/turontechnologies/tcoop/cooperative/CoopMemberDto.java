package com.turontechnologies.tcoop.cooperative;

import com.turontechnologies.tcoop.member.Member;

/**
 * One row of the super-admin co-op oversight Members tab AND the admin-facing Members Directory —
 * GET/POST/PATCH /api/v1/cooperatives/{id}/members(/{memberId}(/status)), all real
 * (CooperativeController). Carries the full profile, not just the summary fields the list/header
 * card show by default — "View Full Profile" (CoopMemberHeaderCard) reads the rest of this same
 * object rather than needing a second fetch.
 */
public record CoopMemberDto(
    String id,
    String firstName,
    String lastName,
    String otherName,
    String gender,
    String email,
    String phone,
    String nin,
    String homeAddress,
    String role,
    String status,
    String guarantor,
    String country,
    String state,
    String city,
    String facebook,
    String twitter,
    String bankCode,
    String accountNumber,
    String accountName,
    String avatarUrl) {

  public static CoopMemberDto from(Member member) {
    return new CoopMemberDto(
        member.getId(),
        member.getFirstName(),
        member.getLastName(),
        member.getOtherName(),
        member.getGender(),
        member.getEmail(),
        member.getPhone(),
        member.getNin(),
        member.getHomeAddress(),
        member.getRole(),
        member.getStatus(),
        member.getGuarantor(),
        member.getCountry(),
        member.getState(),
        member.getCity(),
        member.getFacebook(),
        member.getTwitter(),
        member.getBankCode(),
        member.getAccountNumber(),
        member.getAccountName(),
        member.getAvatarUrl());
  }
}
