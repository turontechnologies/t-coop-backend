package com.turontechnologies.tcoop.subscription;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, UUID> {

  List<SubscriptionPayment> findAllByCooperativeIdOrderByPaymentDateDesc(String cooperativeId);

  Optional<SubscriptionPayment> findFirstByCooperativeIdOrderByPaymentDateDesc(String cooperativeId);

  @Query(
      "select coalesce(sum(p.amountPaid), 0) from SubscriptionPayment p "
          + "where p.cooperativeId = :cooperativeId")
  BigDecimal sumByCooperative(@Param("cooperativeId") String cooperativeId);

  long countByCooperativeId(String cooperativeId);

  /** Sequential per-day payment ref generation — e.g. SUB-20260816-0001. */
  @Query(
      "select count(p) from SubscriptionPayment p where p.paymentDate = :paymentDate")
  long countByPaymentDate(@Param("paymentDate") LocalDate paymentDate);
}
