package com.turontechnologies.tcoop.member;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, String> {

  Optional<Member> findByEmail(String email);

  /**
   * Login lookup by id in any letter case ("COOP-0002" finds "coop-0002"). Unlike findById, a
   * query builds the entity from the stored row, so getId() is the real id rather than whatever
   * the person typed — everything after login compares ids exactly.
   */
  Optional<Member> findFirstByIdIgnoreCase(String id);

  Optional<Member> findByCooperativeIdAndAlternativeId(String cooperativeId, String alternativeId);

  /** Every member with this alternative ID — it's unique per co-op, so there can be one per co-op. */
  List<Member> findAllByAlternativeId(String alternativeId);

  Optional<Member> findByInviteToken(String inviteToken);

  long countByRoleIn(List<String> roles);

  long countByCooperativeIdAndRoleIn(String cooperativeId, List<String> roles);

  List<Member> findAllByCooperativeId(String cooperativeId);

  List<Member> findAllByRoleOrderByInvitedAtDesc(String role);

  long countByPlatformRoleId(UUID platformRoleId);

  List<Member> findAllByCooperativeIdAndCoopRoleIdIsNotNullOrderByFirstNameAsc(String cooperativeId);

  long countByCoopRoleId(UUID coopRoleId);

  List<Member> findAllByCoopRoleId(UUID coopRoleId);
}
