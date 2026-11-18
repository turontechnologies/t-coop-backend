package com.turontechnologies.tcoop.loan;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepaymentClaimRepository extends JpaRepository<LoanRepaymentClaim, UUID> {

  List<LoanRepaymentClaim> findAllByLoanIdOrderBySubmittedAtDesc(UUID loanId);

  List<LoanRepaymentClaim> findAllByCooperativeIdOrderBySubmittedAtDesc(String cooperativeId);

  boolean existsByLoanIdAndStatus(UUID loanId, String status);
}
