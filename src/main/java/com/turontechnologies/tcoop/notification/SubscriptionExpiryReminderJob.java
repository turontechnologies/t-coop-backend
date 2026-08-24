package com.turontechnologies.tcoop.notification;

import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily check for co-ops whose subscription needs the admin's attention — warns them before it
 * lapses, then again (once) after it actually has. Runs at 08:00 UTC. Dedup against re-sending
 * the same warning every day this runs is handled by {@link NotificationService#alreadyNotifiedForExpiry}
 * keyed on the co-op's exact current {@code subscriptionExpiresAt} — a renewal changes that date,
 * which naturally opens the door to a fresh warning next cycle without needing to track "did we
 * already warn this period" separately.
 */
@Component
public class SubscriptionExpiryReminderJob {

  private static final Logger log = LoggerFactory.getLogger(SubscriptionExpiryReminderJob.class);

  /** How many days out to start warning. */
  private static final int WARNING_WINDOW_DAYS = 7;

  private final CooperativeRepository cooperativeRepository;
  private final NotificationService notificationService;

  public SubscriptionExpiryReminderJob(
      CooperativeRepository cooperativeRepository, NotificationService notificationService) {
    this.cooperativeRepository = cooperativeRepository;
    this.notificationService = notificationService;
  }

  @Scheduled(cron = "0 0 8 * * *")
  public void run() {
    LocalDate today = LocalDate.now();
    warnExpiringSoon(today);
    warnAlreadyExpired(today);
  }

  private void warnExpiringSoon(LocalDate today) {
    for (Cooperative coop :
        cooperativeRepository.findAllBySubscriptionExpiresAtBetween(
            today, today.plusDays(WARNING_WINDOW_DAYS))) {
      LocalDate expiresAt = coop.getSubscriptionExpiresAt();
      if (notificationService.alreadyNotifiedForExpiry(coop.getId(), "SUBSCRIPTION_EXPIRING", expiresAt)) {
        continue;
      }
      long daysRemaining = ChronoUnit.DAYS.between(today, expiresAt);
      String dayWord = daysRemaining == 1 ? "day" : "days";
      notificationService.notifyCoopAdminAboutExpiry(
          coop.getId(),
          "SUBSCRIPTION_EXPIRING",
          "Subscription expiring soon",
          "%s's subscription expires in %d %s (on %s). Renew to avoid any interruption."
              .formatted(coop.getName(), daysRemaining, dayWord, expiresAt),
          "/support",
          expiresAt);
      log.info("Sent expiring-soon notification to {} ({} days left)", coop.getId(), daysRemaining);
    }
  }

  private void warnAlreadyExpired(LocalDate today) {
    for (Cooperative coop : cooperativeRepository.findAllBySubscriptionExpiresAtBefore(today)) {
      LocalDate expiresAt = coop.getSubscriptionExpiresAt();
      if (notificationService.alreadyNotifiedForExpiry(coop.getId(), "SUBSCRIPTION_EXPIRED", expiresAt)) {
        continue;
      }
      notificationService.notifyCoopAdminAboutExpiry(
          coop.getId(),
          "SUBSCRIPTION_EXPIRED",
          "Subscription expired",
          "%s's subscription expired on %s. Renew now to restore access."
              .formatted(coop.getName(), expiresAt),
          "/support",
          expiresAt);
      log.info("Sent expired notification to {}", coop.getId());
    }
  }
}
