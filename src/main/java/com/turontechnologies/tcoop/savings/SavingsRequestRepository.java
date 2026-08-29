package com.turontechnologies.tcoop.savings;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingsRequestRepository extends JpaRepository<SavingsRequest, UUID> {

  List<SavingsRequest> findAllByCooperativeIdOrderByRequestedAtDesc(String cooperativeId);

  List<SavingsRequest> findAllByMemberIdOrderByRequestedAtDesc(String memberId);
}
