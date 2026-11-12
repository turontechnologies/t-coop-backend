package com.turontechnologies.tcoop.loan;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.coopgateway.CoopGatewayResolver;
import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.subscription.PaymentGatewayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The loan application fee: the admin sets it (Settings -> Loans), and a member pays it online
 * before the loan form opens. The payment is verified with the gateway server-side; a paid fee
 * stays on file until an application uses it.
 */
@RestController
public class LoanApplicationFeeController {

  public record FeeCredit(String reference, BigDecimal amount, LocalDateTime paidAt) {}

  /** {@code fee} 0 means no fee. {@code credit} is a paid fee waiting to be used. */
  public record FeeStatus(BigDecimal fee, FeeCredit credit, boolean canPayOnline, String merchantName) {}

  public record Initialized(String reference, BigDecimal amount, String gateway, String publicKey) {}

  public record ConfirmRequest(@NotBlank String reference) {}

  private final CooperativeRepository cooperativeRepository;
  private final MemberRepository memberRepository;
  private final LoanApplicationFeeRepository feeRepository;
  private final CoopGatewayResolver gatewayResolver;
  private final AuditLogService auditLogService;

  public LoanApplicationFeeController(
      CooperativeRepository cooperativeRepository,
      MemberRepository memberRepository,
      LoanApplicationFeeRepository feeRepository,
      CoopGatewayResolver gatewayResolver,
      AuditLogService auditLogService) {
    this.cooperativeRepository = cooperativeRepository;
    this.memberRepository = memberRepository;
    this.feeRepository = feeRepository;
    this.gatewayResolver = gatewayResolver;
    this.auditLogService = auditLogService;
  }

  @GetMapping("/api/v1/cooperatives/{id}/loans/application-fee")
  public ResponseEntity<?> status(Authentication authentication, @PathVariable String id) {
    Member caller = callerIn(authentication, id);
    if (caller == null) return forbidden();
    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    LoanApplicationFee credit = unusedCredit(id, caller.getId());
    return ResponseEntity.ok(
        new FeeStatus(
            coop.getLoanApplicationFee(),
            credit == null ? null : new FeeCredit(credit.getReference(), credit.getAmount(), credit.getPaidAt()),
            gatewayResolver.checkoutFor(id).isPresent(),
            coop.getName()));
  }

  @PostMapping("/api/v1/cooperatives/{id}/loans/application-fee/initialize")
  public ResponseEntity<?> initialize(Authentication authentication, @PathVariable String id) {
    Member caller = callerIn(authentication, id);
    if (caller == null) return forbidden();
    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    if (coop.getLoanApplicationFee().signum() <= 0) {
      return ResponseEntity.status(409).body(Map.of("error", "This co-operative doesn't charge a loan application fee"));
    }
    if (unusedCredit(id, caller.getId()) != null) {
      return ResponseEntity.status(409).body(Map.of("error", "You've already paid the application fee. Go ahead and apply."));
    }
    CoopGatewayResolver.Checkout checkout = gatewayResolver.checkoutFor(id).orElse(null);
    if (checkout == null) {
      return ResponseEntity.status(409).body(Map.of("error", "Online payments aren't set up for this co-operative yet."));
    }
    String reference = "LNFEE-" + id + "-" + System.currentTimeMillis();
    feeRepository.save(
        new LoanApplicationFee(reference, id, caller.getId(), coop.getLoanApplicationFee(), checkout.gateway(), checkout.keyOwner()));
    return ResponseEntity.ok(new Initialized(reference, coop.getLoanApplicationFee(), checkout.gateway(), checkout.publicKey()));
  }

  @PostMapping("/api/v1/cooperatives/{id}/loans/application-fee/confirm")
  public ResponseEntity<?> confirm(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody ConfirmRequest request,
      HttpServletRequest httpRequest) {
    Member caller = callerIn(authentication, id);
    if (caller == null) return forbidden();
    LoanApplicationFee fee = feeRepository.findById(request.reference()).orElse(null);
    if (fee == null || !fee.getMemberId().equals(caller.getId()) || !fee.getCooperativeId().equals(id)) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that payment."));
    }
    if (LoanApplicationFee.PAID.equals(fee.getStatus()) || LoanApplicationFee.USED.equals(fee.getStatus())) {
      return ResponseEntity.ok(new FeeCredit(fee.getReference(), fee.getAmount(), fee.getPaidAt()));
    }
    PaymentGatewayService.VerificationResult verification =
        gatewayResolver.verify(id, fee.getGateway(), fee.getKeyOwner(), fee.getReference());
    if (!verification.success() || verification.amountPaid().compareTo(fee.getAmount()) < 0) {
      fee.markFailed();
      feeRepository.save(fee);
      return ResponseEntity.status(402)
          .body(
              Map.of(
                  "error",
                  !verification.success() && verification.message() != null
                      ? verification.message()
                      : "The payment didn't go through."));
    }
    fee.markPaid();
    feeRepository.save(fee);
    auditLogService.log(caller.getId(), caller.getRole(), "Loans", "Create", "Loan application fee", "Success", httpRequest);
    return ResponseEntity.ok(new FeeCredit(fee.getReference(), fee.getAmount(), fee.getPaidAt()));
  }

  LoanApplicationFee unusedCredit(String coopId, String memberId) {
    return feeRepository
        .findFirstByCooperativeIdAndMemberIdAndStatusOrderByPaidAtAsc(coopId, memberId, LoanApplicationFee.PAID)
        .orElse(null);
  }

  private Member callerIn(Authentication authentication, String coopId) {
    Member caller = memberRepository.findById((String) authentication.getPrincipal()).orElse(null);
    return caller != null && coopId.equals(caller.getCooperativeId()) ? caller : null;
  }

  private static ResponseEntity<?> forbidden() {
    return ResponseEntity.status(403).body(Map.of("error", "You can only pay fees in your own co-operative"));
  }
}
