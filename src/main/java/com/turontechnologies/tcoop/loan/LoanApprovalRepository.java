package com.turontechnologies.tcoop.loan;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanApprovalRepository extends JpaRepository<LoanApproval, UUID> {

  List<LoanApproval> findAllByLoanIdOrderByActedAtAsc(UUID loanId);
}
