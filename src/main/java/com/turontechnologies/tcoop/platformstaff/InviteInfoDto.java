package com.turontechnologies.tcoop.platformstaff;

/** What the accept-invite page shows before the invitee has authenticated at all — just enough
 * context to confirm "yes, this invite is for me," never anything sensitive. */
public record InviteInfoDto(String email, String roleName) {}
