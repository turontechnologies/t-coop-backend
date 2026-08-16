package com.turontechnologies.tcoop.subscription;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

  List<SubscriptionPlan> findAllByOrderByTypeAscDurationInDaysAsc();

  List<SubscriptionPlan> findAllByTypeAndStatusOrderByDurationInDaysAsc(String type, String status);
}
