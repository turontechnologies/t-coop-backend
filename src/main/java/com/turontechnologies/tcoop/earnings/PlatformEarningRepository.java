package com.turontechnologies.tcoop.earnings;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformEarningRepository extends JpaRepository<PlatformEarning, UUID> {

  boolean existsBySourceAndReference(String source, String reference);

  /** Totals per co-op and source for a period. */
  interface Total {
    String getCooperativeId();

    String getSource();

    BigDecimal getAmount();

    long getCount();
  }

  @Query(
      "SELECT e.cooperativeId AS cooperativeId, e.source AS source, SUM(e.amount) AS amount,"
          + " COUNT(e) AS count FROM PlatformEarning e"
          + " WHERE e.createdAt >= :from AND e.createdAt < :to"
          + " GROUP BY e.cooperativeId, e.source")
  List<Total> totals(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

  List<PlatformEarning> findAllByCooperativeIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
      String cooperativeId, LocalDateTime from, LocalDateTime to);
}
