package com.turontechnologies.tcoop.subscription;

import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.settings.PlatformSettingsRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * The super admin's subscription grace period (Settings -> Payment Settings -> Fees & Charges):
 * how many days a co-op keeps working after its subscription expires before it is blocked.
 */
@Component
public class SubscriptionGrace {

  private final PlatformSettingsRepository settingsRepository;

  public SubscriptionGrace(PlatformSettingsRepository settingsRepository) {
    this.settingsRepository = settingsRepository;
  }

  public int days() {
    return settingsRepository.findById(1).map(settings -> settings.getSubscriptionGraceDays()).orElse(0);
  }

  /** Active, or expired but still inside the grace period. */
  public boolean canOperate(Cooperative coop) {
    return coop.canOperate(days());
  }

  public boolean inGrace(Cooperative coop) {
    return coop.isInGracePeriod(days());
  }

  /** The last day the co-op can still work; null if it has never subscribed. */
  public LocalDate graceEndsAt(Cooperative coop) {
    return coop.getSubscriptionExpiresAt() == null ? null : coop.getSubscriptionExpiresAt().plusDays(days());
  }
}
