package com.turontechnologies.tcoop.member;

import java.time.LocalDateTime;

/** One row of a member's guarantor list, with real accept/decline status — GET
 * /api/v1/cooperatives/{id}/members/{memberId}/guarantors (CooperativeController). */
public record MemberGuarantorDto(
    String id, String name, String email, String phone, String status, LocalDateTime respondedAt) {

  public static MemberGuarantorDto from(MemberGuarantor guarantor) {
    return new MemberGuarantorDto(
        guarantor.getId().toString(),
        guarantor.getName(),
        guarantor.getEmail(),
        guarantor.getPhone(),
        guarantor.getStatus(),
        guarantor.getRespondedAt());
  }
}
