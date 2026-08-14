package com.turontechnologies.tcoop.auth;

import com.turontechnologies.tcoop.member.Member;

/** Matches the `member` shape in api-contracts.md §1. */
public record MemberDto(String id, String name, String email, String role, String avatarUrl) {

  public static MemberDto from(Member member) {
    return new MemberDto(
        member.getId(),
        member.getFullName(),
        member.getEmail(),
        member.getRole(),
        member.getAvatarUrl());
  }
}
