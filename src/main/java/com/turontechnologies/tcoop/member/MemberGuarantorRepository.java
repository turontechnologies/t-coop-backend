package com.turontechnologies.tcoop.member;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberGuarantorRepository extends JpaRepository<MemberGuarantor, UUID> {

  List<MemberGuarantor> findAllByMemberId(String memberId);

  Optional<MemberGuarantor> findByAcceptToken(String acceptToken);
}
