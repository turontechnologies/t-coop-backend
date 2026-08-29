package com.turontechnologies.tcoop.savings;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavingsRecordRepository extends JpaRepository<SavingsRecord, UUID> {

  /** CooperativeController.transferAdmin uses this both ways — moving the outgoing admin's own
   * savings history onto their new membership id, and (when the incoming admin was already a
   * member) moving theirs onto the co-op's admin id — so a person's savings history follows them
   * through either side of a handover instead of staying stranded under an id someone else now
   * holds. */
  @Modifying
  @Query("update SavingsRecord s set s.memberId = :newMemberId where s.memberId = :oldMemberId")
  void reassignMember(@Param("oldMemberId") String oldMemberId, @Param("newMemberId") String newMemberId);

  @Query(
      "select coalesce(sum(s.amount), 0) from SavingsRecord s "
          + "where s.cooperativeId = :cooperativeId and s.status = 'Success'")
  BigDecimal sumByCooperative(@Param("cooperativeId") String cooperativeId);

  @Query(
      "select coalesce(sum(s.amount), 0) from SavingsRecord s "
          + "where s.memberId = :memberId and s.status = 'Success'")
  BigDecimal sumByMember(@Param("memberId") String memberId);

  @Query(
      "select coalesce(sum(s.amount), 0) from SavingsRecord s "
          + "where s.memberId = :memberId and s.savingsTypeId = :savingsTypeId and s.status = 'Success'")
  BigDecimal sumByMemberAndSavingsType(
      @Param("memberId") String memberId, @Param("savingsTypeId") UUID savingsTypeId);

  @Query("select coalesce(sum(s.amount), 0) from SavingsRecord s where s.status = 'Success'")
  BigDecimal sumAll();

  List<SavingsRecord> findAllByCooperativeIdOrderByCreatedAtDesc(
      String cooperativeId, Pageable pageable);

  List<SavingsRecord> findAllByCooperativeIdOrderByCreatedAtDesc(String cooperativeId);

  List<SavingsRecord> findAllByMemberIdOrderByCreatedAtDesc(String memberId, Pageable pageable);

  List<SavingsRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
