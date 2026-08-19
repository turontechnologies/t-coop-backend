package com.turontechnologies.tcoop.savings;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavingsRecordRepository extends JpaRepository<SavingsRecord, UUID> {

  @Query(
      "select coalesce(sum(s.amount), 0) from SavingsRecord s "
          + "where s.cooperativeId = :cooperativeId and s.status = 'Success'")
  BigDecimal sumByCooperative(@Param("cooperativeId") String cooperativeId);

  @Query(
      "select coalesce(sum(s.amount), 0) from SavingsRecord s "
          + "where s.memberId = :memberId and s.status = 'Success'")
  BigDecimal sumByMember(@Param("memberId") String memberId);

  @Query("select coalesce(sum(s.amount), 0) from SavingsRecord s where s.status = 'Success'")
  BigDecimal sumAll();

  List<SavingsRecord> findAllByCooperativeIdOrderByCreatedAtDesc(
      String cooperativeId, Pageable pageable);

  List<SavingsRecord> findAllByCooperativeIdOrderByCreatedAtDesc(String cooperativeId);

  List<SavingsRecord> findAllByMemberIdOrderByCreatedAtDesc(String memberId, Pageable pageable);

  List<SavingsRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
