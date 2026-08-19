package com.turontechnologies.tcoop.savings;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingsTypeRepository extends JpaRepository<SavingsType, UUID> {

  List<SavingsType> findAllByCooperativeId(String cooperativeId);

  List<SavingsType> findAllByCooperativeIdOrderByCreatedAtAsc(String cooperativeId);

  long countByCooperativeId(String cooperativeId);
}
