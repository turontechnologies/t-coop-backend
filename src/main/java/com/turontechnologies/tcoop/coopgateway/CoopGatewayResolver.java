package com.turontechnologies.tcoop.coopgateway;

import com.turontechnologies.tcoop.settings.PlatformSettings;
import com.turontechnologies.tcoop.settings.PlatformSettingsRepository;
import com.turontechnologies.tcoop.subscription.PaymentGatewayService;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Decides which gateway a member's savings deposit, loan repayment or loan application fee goes
 * through: the co-op's own (Settings -> Integrations, Paystack preferred when both are on). The
 * platform's own keys are kept for payments to the platform (subscriptions) and are no longer a
 * fallback for co-op payments; they are only used here to verify payments started on them
 * before that change.
 */
@Service
public class CoopGatewayResolver {

  public static final String PAYSTACK = "Paystack";
  public static final String FLUTTERWAVE = "Flutterwave";
  public static final String OWNER_COOPERATIVE = "Cooperative";
  public static final String OWNER_PLATFORM = "Platform";

  private static final Integer SETTINGS_SINGLETON_ID = 1;

  private final CooperativePaymentGatewayRepository gatewayRepository;
  private final PlatformSettingsRepository platformSettingsRepository;
  private final PaymentGatewayService paymentGatewayService;

  public CoopGatewayResolver(
      CooperativePaymentGatewayRepository gatewayRepository,
      PlatformSettingsRepository platformSettingsRepository,
      PaymentGatewayService paymentGatewayService) {
    this.gatewayRepository = gatewayRepository;
    this.platformSettingsRepository = platformSettingsRepository;
    this.paymentGatewayService = paymentGatewayService;
  }

  /** Everything a checkout needs to start, plus what to store on the intent for confirm. */
  public record Checkout(String gateway, String keyOwner, String publicKey) {}

  /** Empty when neither the co-op nor the platform has a usable gateway. */
  public Optional<Checkout> checkoutFor(String cooperativeId) {
    CooperativePaymentGateway coop = gatewayRepository.findById(cooperativeId).orElse(null);
    if (coop != null) {
      if (coop.isPaystackEnabled()
          && notBlank(coop.getPaystackPublicKey())
          && notBlank(coop.getPaystackSecretKey())) {
        return Optional.of(new Checkout(PAYSTACK, OWNER_COOPERATIVE, coop.getPaystackPublicKey()));
      }
      if (coop.isFlutterwaveEnabled()
          && notBlank(coop.getFlutterwavePublicKey())
          && notBlank(coop.getFlutterwaveSecretKey())) {
        return Optional.of(
            new Checkout(FLUTTERWAVE, OWNER_COOPERATIVE, coop.getFlutterwavePublicKey()));
      }
    }

    // No fallback to the platform's keys: those are only for payments to the platform itself
    // (subscriptions). A co-op's money goes through the co-op's own gateway.
    return Optional.empty();
  }

  /** The Paystack secret a loan or withdrawal payout is sent from: the co-op's own Paystack
   * account only. Empty when the co-op hasn't connected one. Only Paystack — there's no
   * Flutterwave payout path. */
  public Optional<String> payoutSecretKeyFor(String cooperativeId) {
    CooperativePaymentGateway coop = gatewayRepository.findById(cooperativeId).orElse(null);
    if (coop != null && coop.isPaystackEnabled() && notBlank(coop.getPaystackSecretKey())) {
      return Optional.of(coop.getPaystackSecretKey());
    }
    return Optional.empty();
  }

  /** Verifies against the same gateway and account the intent was started with. */
  public PaymentGatewayService.VerificationResult verify(
      String cooperativeId, String gateway, String keyOwner, String reference) {
    String secretKey = secretKeyFor(cooperativeId, gateway, keyOwner);
    return FLUTTERWAVE.equals(gateway)
        ? paymentGatewayService.verifyFlutterwave(reference, secretKey)
        : paymentGatewayService.verifyPaystack(reference, secretKey);
  }

  private String secretKeyFor(String cooperativeId, String gateway, String keyOwner) {
    if (OWNER_COOPERATIVE.equals(keyOwner)) {
      CooperativePaymentGateway coop = gatewayRepository.findById(cooperativeId).orElse(null);
      if (coop == null) return null;
      return FLUTTERWAVE.equals(gateway) ? coop.getFlutterwaveSecretKey() : coop.getPaystackSecretKey();
    }
    PlatformSettings platform = platformSettings();
    return FLUTTERWAVE.equals(gateway)
        ? platform.getFlutterwaveSecretKey()
        : platform.getPaystackSecretKey();
  }

  private PlatformSettings platformSettings() {
    return platformSettingsRepository
        .findById(SETTINGS_SINGLETON_ID)
        .orElseThrow(() -> new IllegalStateException("Platform settings row is missing"));
  }

  private static boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }
}
