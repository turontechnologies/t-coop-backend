package com.turontechnologies.tcoop.profile;

import com.turontechnologies.tcoop.member.Member;

/** Matches the profile shape in api-contracts.md §4. */
public record ProfileDto(
    String membershipId,
    String accountNumber,
    String bankCode,
    String accountName,
    String nin,
    String firstName,
    String lastName,
    String otherName,
    String gender,
    String phone,
    String email,
    String homeAddress,
    String country,
    String state,
    String city,
    String facebook,
    String twitter,
    String guarantor) {

  public static ProfileDto from(Member member) {
    return new ProfileDto(
        member.getId(),
        member.getAccountNumber(),
        member.getBankCode(),
        member.getAccountName(),
        member.getNin(),
        member.getFirstName(),
        member.getLastName(),
        member.getOtherName(),
        member.getGender(),
        member.getPhone(),
        member.getEmail(),
        member.getHomeAddress(),
        member.getCountry(),
        member.getState(),
        member.getCity(),
        member.getFacebook(),
        member.getTwitter(),
        member.getGuarantor());
  }
}
