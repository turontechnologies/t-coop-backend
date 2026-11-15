package com.turontechnologies.tcoop.subscription;

import com.turontechnologies.tcoop.cooperative.Cooperative;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Each co-op's subscription grace period (V56), set by the super admin per co-op under Settings ->
 * Payment Settings: how many days a co-op keeps working after its subscription expires.
 */
@Component
public class SubscriptionGrace {

  public int days(Cooperative coop) {
    return coop.getSubscriptionGraceDays();
  }

  /** Active, or expired but still inside the grace period. */
  public boolean canOperate(Cooperative coop) {
    return coop.canOperate();
  }

  public boolean inGrace(Cooperative coop) {
    return coop.isInGracePeriod();
  }

  /** The last day the co-op can still work; null if it has never subscribed. */
  public LocalDate graceEndsAt(Cooperative coop) {
    return coop.graceEndsAt();
  }
}
