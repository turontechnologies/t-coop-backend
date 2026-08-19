package com.turontechnologies.tcoop.platformstaff;

import com.turontechnologies.tcoop.member.Member;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Matches the frontend's PlatformUser shape — `role` is the assigned PlatformRole's *name*
 * (matched by name in the mock, kept the same here so the table/permission-blocked-delete logic
 * didn't need to change), not its id. `status` is "Active"|"Inactive"|"Invited". */
public record PlatformUserDto(
    String id, String name, String email, String role, LocalDate dateAdded, String status) {

  public static PlatformUserDto from(Member member, String roleName) {
    String name = member.getFirstName().isBlank() ? member.getEmail() : member.getFullName();
    LocalDateTime addedAt =
        member.getInvitedAt() != null ? member.getInvitedAt() : member.getAcceptedAt();
    LocalDate dateAdded = addedAt != null ? addedAt.toLocalDate() : LocalDate.now();
    return new PlatformUserDto(
        member.getId(), name, member.getEmail(), roleName, dateAdded, member.getStatus());
  }
}
