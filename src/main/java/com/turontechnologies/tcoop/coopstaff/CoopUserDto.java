package com.turontechnologies.tcoop.coopstaff;

import com.turontechnologies.tcoop.member.Member;

/** One row of the admin's own Settings -> User Management -> Users — an existing member of this
 * co-op who's been assigned a CoopRole. `role` is the assigned role's name; `status` is the
 * member's own account status (Active/Inactive), not an invite state — there is no "Invited"
 * state here since this member already has a working account. */
public record CoopUserDto(String id, String name, String email, String role, String status) {

  public static CoopUserDto from(Member member, String roleName) {
    String name = member.getFirstName().isBlank() ? member.getEmail() : member.getFullName();
    return new CoopUserDto(member.getId(), name, member.getEmail(), roleName, member.getStatus());
  }
}
