package com.turontechnologies.tcoop.cooperative;

import com.turontechnologies.tcoop.member.Member;

/**
 * One row of the super-admin co-op oversight Members tab — GET /api/v1/cooperatives/{id}/members.
 * Read-only: there's no real backend endpoint yet to edit a member's profile or toggle their
 * status from this oversight view (that's still the frontend's mock `useCoopStore` — see
 * documentation/flows.md), so the frontend must not offer those actions against this data.
 */
public record CoopMemberDto(
    String id,
    String firstName,
    String lastName,
    String email,
    String role,
    String status,
    String guarantor,
    String country,
    String state,
    String city,
    String bankCode,
    String accountNumber,
    String accountName) {

  public static CoopMemberDto from(Member member) {
    return new CoopMemberDto(
        member.getId(),
        member.getFirstName(),
        member.getLastName(),
        member.getEmail(),
        member.getRole(),
        member.getStatus(),
        member.getGuarantor(),
        member.getCountry(),
        member.getState(),
        member.getCity(),
        member.getBankCode(),
        member.getAccountNumber(),
        member.getAccountName());
  }
}
