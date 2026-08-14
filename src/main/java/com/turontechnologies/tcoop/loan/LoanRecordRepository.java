package com.turontechnologies.tcoop.loan;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanRecordRepository extends JpaRepository<LoanRecord, UUID> {

  @Query(
      "select coalesce(sum(l.amount), 0) from LoanRecord l "
          + "where l.cooperativeId = :cooperativeId and l.status in ('Active', 'Completed')")
  BigDecimal sumByCooperative(@Param("cooperativeId") String cooperativeId);

  @Query(
      "select coalesce(sum(l.amount), 0) from LoanRecord l "
          + "where l.memberId = :memberId and l.status in ('Active', 'Completed')")
  BigDecimal sumByMember(@Param("memberId") String memberId);

  @Query("select coalesce(sum(l.amount), 0) from LoanRecord l where l.status in ('Active', 'Completed')")
  BigDecimal sumAll();

  List<LoanRecord> findAllByCooperativeIdOrderByCreatedAtDesc(
      String cooperativeId, Pageable pageable);

  List<LoanRecord> findAllByMemberIdOrderByCreatedAtDesc(String memberId, Pageable pageable);

  List<LoanRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
