package com.turontechnologies.tcoop.loan;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanCommitteeMemberRepository extends JpaRepository<LoanCommitteeMember, UUID> {

  List<LoanCommitteeMember> findAllByCooperativeIdOrderByPositionAsc(String cooperativeId);

  @Modifying
  @Query("DELETE FROM LoanCommitteeMember c WHERE c.cooperativeId = :cooperativeId")
  void deleteAllForCooperative(@Param("cooperativeId") String cooperativeId);
}
