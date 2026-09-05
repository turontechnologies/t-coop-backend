package com.turontechnologies.tcoop.subscription;

import com.turontechnologies.tcoop.settings.PlatformSettings;
import com.turontechnologies.tcoop.settings.PlatformSettingsRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Real bank list + account-name verification for any signed-in caller (admin or member — both
 * enter a personal or co-op bank account somewhere). Web already has this via its own Next.js
 * {@code /api/paystack/banks} and {@code /api/paystack/resolve-account} route handlers, which use
 * a standalone env var and never touch this backend — this is the equivalent for mobile (and any
 * other native client), using the platform's own configured Paystack key from Settings ->
 * Integrations instead of a separate env var, so it stays in sync with whatever key the super
 * admin actually has entered.
 */
@RestController
public class PaystackUtilityController {

  private static final Integer SETTINGS_SINGLETON_ID = 1;

  private final PaymentGatewayService paymentGatewayService;
  private final PlatformSettingsRepository platformSettingsRepository;

  public PaystackUtilityController(
      PaymentGatewayService paymentGatewayService, PlatformSettingsRepository platformSettingsRepository) {
    this.paymentGatewayService = paymentGatewayService;
    this.platformSettingsRepository = platformSettingsRepository;
  }

  public record ResolveAccountRequest(
      @NotBlank(message = "Enter the account number") String accountNumber,
      @NotBlank(message = "Select a bank") String bankCode) {}

  @GetMapping("/api/v1/paystack/banks")
  public ResponseEntity<?> banks() {
    PaymentGatewayService.BankListResult result =
        paymentGatewayService.listPaystackBanks(platformSettings().getPaystackSecretKey());
    if (!result.success()) {
      return ResponseEntity.status(502).body(Map.of("error", result.message()));
    }
    List<Map<String, String>> banks =
        result.banks().stream().map(bank -> Map.of("name", bank.name(), "code", bank.code())).toList();
    return ResponseEntity.ok(Map.of("banks", banks));
  }

  @PostMapping("/api/v1/paystack/resolve-account")
  public ResponseEntity<?> resolveAccount(@Valid @RequestBody ResolveAccountRequest request) {
    PaymentGatewayService.BankResolveResult result =
        paymentGatewayService.resolvePaystackBankAccount(
            request.accountNumber(), request.bankCode(), platformSettings().getPaystackSecretKey());
    if (!result.success()) {
      return ResponseEntity.status(422).body(Map.of("error", result.message()));
    }
    return ResponseEntity.ok(Map.of("accountName", result.accountName()));
  }

  private PlatformSettings platformSettings() {
    return platformSettingsRepository
        .findById(SETTINGS_SINGLETON_ID)
        .orElseThrow(() -> new IllegalStateException("Platform settings row is missing"));
  }
}
