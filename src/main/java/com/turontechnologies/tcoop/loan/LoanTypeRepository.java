package com.turontechnologies.tcoop.loan;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanTypeRepository extends JpaRepository<LoanType, UUID> {

  List<LoanType> findAllByCooperativeId(String cooperativeId);

  List<LoanType> findAllByCooperativeIdOrderByCreatedAtAsc(String cooperativeId);

  long countByCooperativeId(String cooperativeId);
}
