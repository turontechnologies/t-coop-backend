package com.turontechnologies.tcoop.member;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, String> {

  Optional<Member> findByEmail(String email);

  Optional<Member> findByInviteToken(String inviteToken);

  long countByRoleIn(List<String> roles);

  long countByCooperativeIdAndRoleIn(String cooperativeId, List<String> roles);

  List<Member> findAllByCooperativeId(String cooperativeId);

  List<Member> findAllByRoleOrderByInvitedAtDesc(String role);

  long countByPlatformRoleId(UUID platformRoleId);
}
