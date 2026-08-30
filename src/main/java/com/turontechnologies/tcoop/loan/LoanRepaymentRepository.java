package com.turontechnologies.tcoop.loan;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanRepaymentRepository extends JpaRepository<LoanRepayment, UUID> {

  List<LoanRepayment> findAllByLoanIdOrderByCreatedAtDesc(UUID loanId);

  int countByLoanId(UUID loanId);

  @Query("select coalesce(sum(r.amount), 0) from LoanRepayment r where r.loanId = :loanId")
  BigDecimal sumByLoan(@Param("loanId") UUID loanId);
}
