package com.turontechnologies.tcoop.member;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, String> {

  Optional<Member> findByEmail(String email);

  long countByRoleIn(java.util.List<String> roles);

  long countByCooperativeIdAndRoleIn(String cooperativeId, java.util.List<String> roles);

  java.util.List<Member> findAllByCooperativeId(String cooperativeId);
}
