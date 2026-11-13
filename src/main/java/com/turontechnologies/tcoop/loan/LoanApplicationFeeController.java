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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The loan application fee: the admin sets one fee for every loan, or a fee per loan type
 * (Settings -> Loans), and a member pays it online before the loan form opens. The payment is
 * verified with the gateway server-side; a paid fee pays for one application (of the type it was
 * paid for, with per-type fees).
 */
@RestController
public class LoanApplicationFeeController {

  public record FeeCredit(String reference, BigDecimal amount, LocalDateTime paidAt, String loanTypeId) {
    static FeeCredit of(LoanApplicationFee fee) {
      return new FeeCredit(
          fee.getReference(), fee.getAmount(), fee.getPaidAt(),
          fee.getLoanTypeId() == null ? null : fee.getLoanTypeId().toString());
    }
  }

  /** One loan type's fee, and whether the member has already paid it. */
  public record TypeFee(String loanTypeId, String loanTypeName, BigDecimal fee, FeeCredit credit) {}

  /**
   * {@code mode} "General" or "PerType". {@code fee}/{@code credit} are for the general fee, or
   * for {@code loanTypeId} when one was asked about. {@code types} lists every active loan type's
   * fee, for the "choose a loan type" step.
   */
  public record FeeStatus(
      String mode,
      BigDecimal fee,
      FeeCredit credit,
      List<TypeFee> types,
      boolean canPayOnline,
      String merchantName) {}

  public record Initialized(String reference, BigDecimal amount, String gateway, String publicKey) {}

  public record InitializeRequest(String loanTypeId) {}

  public record ConfirmRequest(@NotBlank String reference) {}

  private final CooperativeRepository cooperativeRepository;
  private final MemberRepository memberRepository;
  private final LoanTypeRepository loanTypeRepository;
  private final LoanApplicationFeeRepository feeRepository;
  private final LoanFeeRules feeRules;
  private final CoopGatewayResolver gatewayResolver;
  private final AuditLogService auditLogService;

  public LoanApplicationFeeController(
      CooperativeRepository cooperativeRepository,
      MemberRepository memberRepository,
      LoanTypeRepository loanTypeRepository,
      LoanApplicationFeeRepository feeRepository,
      LoanFeeRules feeRules,
      CoopGatewayResolver gatewayResolver,
      AuditLogService auditLogService) {
    this.cooperativeRepository = cooperativeRepository;
    this.memberRepository = memberRepository;
    this.loanTypeRepository = loanTypeRepository;
    this.feeRepository = feeRepository;
    this.feeRules = feeRules;
    this.gatewayResolver = gatewayResolver;
    this.auditLogService = auditLogService;
  }

  @GetMapping("/api/v1/cooperatives/{id}/loans/application-fee")
  public ResponseEntity<?> status(
      Authentication authentication,
      @PathVariable String id,
      @RequestParam(required = false) String loanTypeId) {
    Member caller = callerIn(authentication, id);
    if (caller == null) return forbidden();
    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) return coopNotFound();

    List<TypeFee> types =
        loanTypeRepository.findAllByCooperativeId(id).stream()
            .filter(type -> "Active".equals(type.getStatus()))
            .map(
                type -> {
                  LoanApplicationFee credit = feeRules.creditFor(coop, caller.getId(), type.getId());
                  return new TypeFee(
                      type.getId().toString(), type.getName(), LoanFeeRules.feeFor(coop, type),
                      credit == null ? null : FeeCredit.of(credit));
                })
            .toList();

    LoanType asked = findType(loanTypeId, id);
    BigDecimal fee = coop.isLoanFeePerType()
        ? (asked == null ? BigDecimal.ZERO : LoanFeeRules.feeFor(coop, asked))
        : coop.getLoanApplicationFee();
    LoanApplicationFee credit =
        coop.isLoanFeePerType() && asked == null ? null : feeRules.creditFor(coop, caller.getId(), asked == null ? null : asked.getId());

    return ResponseEntity.ok(
        new FeeStatus(
            coop.getLoanFeeMode(),
            fee,
            credit == null ? null : FeeCredit.of(credit),
            types,
            gatewayResolver.checkoutFor(id).isPresent(),
            coop.getName()));
  }

  @PostMapping("/api/v1/cooperatives/{id}/loans/application-fee/initialize")
  public ResponseEntity<?> initialize(
      Authentication authentication,
      @PathVariable String id,
      @RequestBody(required = false) InitializeRequest request) {
    Member caller = callerIn(authentication, id);
    if (caller == null) return forbidden();
    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) return coopNotFound();

    LoanType type = request == null ? null : findType(request.loanTypeId(), id);
    if (coop.isLoanFeePerType() && type == null) {
      return ResponseEntity.badRequest().body(Map.of("error", "Choose the loan type you're applying for"));
    }
    BigDecimal fee = LoanFeeRules.feeFor(coop, type);
    if (fee.signum() <= 0) {
      return ResponseEntity.status(409).body(Map.of("error", "There's no application fee for this loan"));
    }
    if (feeRules.creditFor(coop, caller.getId(), type == null ? null : type.getId()) != null) {
      return ResponseEntity.status(409).body(Map.of("error", "You've already paid the application fee. Go ahead and apply."));
    }
    CoopGatewayResolver.Checkout checkout = gatewayResolver.checkoutFor(id).orElse(null);
    if (checkout == null) {
      return ResponseEntity.status(409).body(Map.of("error", "Online payments aren't set up for this co-operative yet."));
    }
    String reference = "LNFEE-" + id + "-" + System.currentTimeMillis();
    LoanApplicationFee intent =
        new LoanApplicationFee(reference, id, caller.getId(), fee, checkout.gateway(), checkout.keyOwner());
    if (coop.isLoanFeePerType()) intent.setLoanTypeId(type.getId());
    feeRepository.save(intent);
    return ResponseEntity.ok(new Initialized(reference, fee, checkout.gateway(), checkout.publicKey()));
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
      return ResponseEntity.ok(FeeCredit.of(fee));
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
    return ResponseEntity.ok(FeeCredit.of(fee));
  }

  private LoanType findType(String typeId, String coopId) {
    if (typeId == null || typeId.isBlank()) return null;
    try {
      LoanType type = loanTypeRepository.findById(UUID.fromString(typeId)).orElse(null);
      return type != null && coopId.equals(type.getCooperativeId()) ? type : null;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private Member callerIn(Authentication authentication, String coopId) {
    Member caller = memberRepository.findById((String) authentication.getPrincipal()).orElse(null);
    return caller != null && coopId.equals(caller.getCooperativeId()) ? caller : null;
  }

  private static ResponseEntity<?> forbidden() {
    return ResponseEntity.status(403).body(Map.of("error", "You can only pay fees in your own co-operative"));
  }

  private static ResponseEntity<?> coopNotFound() {
    return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
  }
}
