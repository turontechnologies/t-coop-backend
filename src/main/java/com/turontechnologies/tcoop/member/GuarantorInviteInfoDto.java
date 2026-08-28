package com.turontechnologies.tcoop.member;

/** GET /api/v1/guarantor-invites/{token} — what the public accept/decline page shows before the
 * guarantor decides. */
public record GuarantorInviteInfoDto(
    String guarantorName, String memberName, String cooperativeName, String status) {}
