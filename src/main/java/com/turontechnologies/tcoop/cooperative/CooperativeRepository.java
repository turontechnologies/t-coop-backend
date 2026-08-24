package com.turontechnologies.tcoop.cooperative;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CooperativeRepository extends JpaRepository<Cooperative, String> {

  long countByStatus(String status);

  List<Cooperative> findAllByOrderByNameAsc();

  /** Used by the daily subscription-expiry reminder job — co-ops whose subscription lapses
   * within the given window (inclusive on both ends). */
  List<Cooperative> findAllBySubscriptionExpiresAtBetween(LocalDate from, LocalDate to);

  /** Co-ops whose subscription has already lapsed as of the given date — used by the same job
   * to send a one-time "expired" notice, separate from the "expiring soon" warning above. */
  List<Cooperative> findAllBySubscriptionExpiresAtBefore(LocalDate date);
}
