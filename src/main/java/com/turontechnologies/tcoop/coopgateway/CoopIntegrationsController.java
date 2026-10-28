package com.turontechnologies.tcoop.coopgateway;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.coopstaff.CoopPermissions;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backs the co-op admin's own Settings -> Integrations tab: the co-op's Paystack/Flutterwave
 * credentials that {@link CoopGatewayResolver} routes member deposits and loan repayments
 * through. Strictly the co-op's own — the co-op admin, or co-op staff whose role was granted the
 * Settings -> Integrations tab (read to view, write to save). Super admins and platform staff are
 * deliberately excluded: these keys move money into the co-op's account, not the platform's.
 */
@RestController
public class CoopIntegrationsController {

  private static final String MODULE = "Settings";
  private static final String TAB = "Integrations";

  private final MemberRepository memberRepository;
  private final CoopPermissions coopPermissions;
  private final CooperativeRepository cooperativeRepository;
  private final CooperativePaymentGatewayRepository gatewayRepository;
  private final AuditLogService auditLogService;

  public CoopIntegrationsController(
      MemberRepository memberRepository,
      CoopPermissions coopPermissions,
      CooperativeRepository cooperativeRepository,
      CooperativePaymentGatewayRepository gatewayRepository,
      AuditLogService auditLogService) {
    this.memberRepository = memberRepository;
    this.coopPermissions = coopPermissions;
    this.cooperativeRepository = cooperativeRepository;
    this.gatewayRepository = gatewayRepository;
    this.auditLogService = auditLogService;
  }

  @GetMapping("/api/v1/cooperatives/{id}/integrations")
  public ResponseEntity<?> get(Authentication authentication, @PathVariable String id) {
    var access = requireTabAccess(authentication, id, false);
    if (access.error() != null) return access.error();
    return ResponseEntity.ok(CoopIntegrationsDto.from(gatewayRepository.findById(id).orElse(null)));
  }

  @PatchMapping("/api/v1/cooperatives/{id}/integrations")
  public ResponseEntity<?> update(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody CoopIntegrationsUpdateRequest request,
      HttpServletRequest httpRequest) {
    var access = requireTabAccess(authentication, id, true);
    if (access.error() != null) return access.error();
    if (!cooperativeRepository.existsById(id)) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }

    CooperativePaymentGateway gateway =
        gatewayRepository.findById(id).orElseGet(() -> new CooperativePaymentGateway(id));

    String paystackPublic = trimToNull(request.paystackPublicKey());
    String paystackSecret =
        keepOrReplace(gateway.getPaystackSecretKey(), request.paystackSecretKey());
    if (request.paystackEnabled()) {
      String problem =
          keyPairProblem("Paystack", paystackPublic, paystackSecret, "pk_", "sk_", "_test_");
      if (problem != null) return ResponseEntity.badRequest().body(Map.of("error", problem));
    }

    String flutterwavePublic = trimToNull(request.flutterwavePublicKey());
    String flutterwaveSecret =
        keepOrReplace(gateway.getFlutterwaveSecretKey(), request.flutterwaveSecretKey());
    if (request.flutterwaveEnabled()) {
      String problem =
          keyPairProblem(
              "Flutterwave", flutterwavePublic, flutterwaveSecret, "FLWPUBK", "FLWSECK", "_TEST");
      if (problem != null) return ResponseEntity.badRequest().body(Map.of("error", problem));
    }

    gateway.updatePaystack(request.paystackEnabled(), paystackPublic, paystackSecret);
    gateway.updateFlutterwave(request.flutterwaveEnabled(), flutterwavePublic, flutterwaveSecret);
    gatewayRepository.save(gateway);

    Member caller = access.caller();
    auditLogService.log(
        caller.getId(), caller.getRole(), MODULE, "Update", "Payment Integrations", "Success", httpRequest);
    return ResponseEntity.ok(CoopIntegrationsDto.from(gateway));
  }

  /** Catches the common copy-paste mistakes (keys swapped, missing, or test mixed with live)
   * before they turn into a member's failed payment. Null when the pair looks right. */
  private static String keyPairProblem(
      String name,
      String publicKey,
      String secretKey,
      String publicPrefix,
      String secretPrefix,
      String testMarker) {
    if (publicKey == null) return "Enter your " + name + " public key to turn it on";
    if (secretKey == null) return "Enter your " + name + " secret key to turn it on";
    if (!publicKey.startsWith(publicPrefix)) {
      return "That doesn't look like a " + name + " public key (it should start with " + publicPrefix + ")";
    }
    if (!secretKey.startsWith(secretPrefix)) {
      return "That doesn't look like a " + name + " secret key (it should start with " + secretPrefix + ")";
    }
    if (publicKey.contains(testMarker) != secretKey.contains(testMarker)) {
      return "Your " + name + " public and secret keys must both be test keys or both be live keys";
    }
    return null;
  }

  private static String keepOrReplace(String saved, String submitted) {
    String next = trimToNull(submitted);
    return next != null ? next : saved;
  }

  private static String trimToNull(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private record Access(Member caller, ResponseEntity<?> error) {}

  private Access requireTabAccess(Authentication authentication, String cooperativeId, boolean write) {
    String callerId = (String) authentication.getPrincipal();
    Member caller = memberRepository.findById(callerId).orElse(null);
    if (caller == null) {
      return new Access(null, ResponseEntity.status(401).body(Map.of("error", "Member no longer exists")));
    }
    if (!coopPermissions.allows(caller, cooperativeId, MODULE, TAB, write)) {
      return forbidden();
    }
    return new Access(caller, null);
  }

  private static Access forbidden() {
    return new Access(
        null,
        ResponseEntity.status(403)
            .body(Map.of("error", "You don't have access to this co-operative's integrations")));
  }
}
