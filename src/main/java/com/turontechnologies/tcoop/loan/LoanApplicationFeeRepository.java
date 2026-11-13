package com.turontechnologies.tcoop.loan;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanApplicationFeeRepository extends JpaRepository<LoanApplicationFee, String> {

  /** A paid fee not yet spent on an application. */
  Optional<LoanApplicationFee> findFirstByCooperativeIdAndMemberIdAndStatusOrderByPaidAtAsc(
      String cooperativeId, String memberId, String status);

  /** Every paid-but-unused fee of a member (one per loan type at most, in practice). */
  List<LoanApplicationFee> findAllByCooperativeIdAndMemberIdAndStatusOrderByPaidAtAsc(
      String cooperativeId, String memberId, String status);
}
