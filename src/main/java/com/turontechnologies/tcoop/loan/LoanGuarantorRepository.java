package com.turontechnologies.tcoop.loan;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanGuarantorRepository extends JpaRepository<LoanGuarantor, UUID> {

  List<LoanGuarantor> findAllByLoanIdOrderByCreatedAtAsc(UUID loanId);

  List<LoanGuarantor> findAllByLoanIdIn(Collection<UUID> loanIds);

  List<LoanGuarantor> findAllByMemberId(String memberId);

  boolean existsByLoanIdAndMemberId(UUID loanId, String memberId);
}
