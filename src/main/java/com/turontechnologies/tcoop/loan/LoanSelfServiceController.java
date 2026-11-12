package com.turontechnologies.tcoop.loan;

import com.turontechnologies.tcoop.audit.AuditLogService;
import com.turontechnologies.tcoop.coopgateway.CoopGatewayResolver;
import com.turontechnologies.tcoop.coopstaff.CoopOffices;
import com.turontechnologies.tcoop.coopstaff.CoopPermissions;
import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.loan.LoanCommittee.Step;
import com.turontechnologies.tcoop.loan.LoanOfferRequests.Disburse;
import com.turontechnologies.tcoop.loan.LoanOfferRequests.OfferResponse;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.notification.NotificationService;
import com.turontechnologies.tcoop.savings.SavingsRecord;
import com.turontechnologies.tcoop.savings.SavingsRecordRepository;
import com.turontechnologies.tcoop.settings.PlatformSettings;
import com.turontechnologies.tcoop.settings.PlatformSettingsRepository;
import com.turontechnologies.tcoop.subscription.PaymentGatewayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * A member's loan, from application to payout.
 *
 * <ol>
 *   <li>The member applies: type, amount, how many months to repay over (up to the type's
 *       maximum), and for a Times two loan their guarantors. The co-op's own rules apply
 *       (minimum amount, eligibility; see Settings -> Loans).
 *   <li>Guarantors accept (Times two only).
 *   <li>The co-op's loan committee approves in the order the admin set. Each approver suggests
 *       an amount and repayment period; the last one's figures are the approved terms.
 *   <li>The borrower accepts or declines the approved terms. Accepting chooses where the money is
 *       paid: their registered account, or another account that must be in their registered
 *       name.
 *   <li>An officer pays it out, online (Paystack transfer) or by cheque, and it becomes Active.
 * </ol>
 *
 * Nobody approves a loan they borrow or guarantee: the co-op admin covers that step.
 */
@RestController
public class LoanSelfServiceController {

  private static final Integer SETTINGS_SINGLETON_ID = 1;

  static final String AWAITING_GUARANTOR = "Awaiting Guarantor";
  static final String AWAITING_APPROVAL = "Awaiting Approval";
  static final String AWAITING_ACCEPTANCE = "Awaiting Acceptance";
  static final String PENDING_DISBURSEMENT = "Pending Disbursement";

  /** Loans still being decided or paid out. */
  public static final Set<String> IN_PROGRESS =
      Set.of(AWAITING_GUARANTOR, AWAITING_APPROVAL, AWAITING_ACCEPTANCE, PENDING_DISBURSEMENT);

  private final CooperativeRepository cooperativeRepository;
  private final MemberRepository memberRepository;
  private final LoanTypeRepository loanTypeRepository;
  private final LoanRecordRepository loanRecordRepository;
  private final LoanApprovalRepository loanApprovalRepository;
  private final LoanGuarantorRepository loanGuarantorRepository;
  private final SavingsRecordRepository savingsRecordRepository;
  private final PlatformSettingsRepository platformSettingsRepository;
  private final LoanCommittee loanCommittee;
  private final LoanApplicationFeeRepository feeRepository;
  private final LoanEligibilityService eligibilityService;
  private final CoopPermissions coopPermissions;
  private final CoopGatewayResolver gatewayResolver;
  private final PaymentGatewayService paymentGatewayService;
  private final NotificationService notificationService;
  private final AuditLogService auditLogService;

  public LoanSelfServiceController(
      CooperativeRepository cooperativeRepository,
      MemberRepository memberRepository,
      LoanTypeRepository loanTypeRepository,
      LoanRecordRepository loanRecordRepository,
      LoanApprovalRepository loanApprovalRepository,
      LoanGuarantorRepository loanGuarantorRepository,
      SavingsRecordRepository savingsRecordRepository,
      PlatformSettingsRepository platformSettingsRepository,
      LoanCommittee loanCommittee,
      LoanApplicationFeeRepository feeRepository,
      LoanEligibilityService eligibilityService,
      CoopPermissions coopPermissions,
      CoopGatewayResolver gatewayResolver,
      PaymentGatewayService paymentGatewayService,
      NotificationService notificationService,
      AuditLogService auditLogService) {
    this.cooperativeRepository = cooperativeRepository;
    this.memberRepository = memberRepository;
    this.loanTypeRepository = loanTypeRepository;
    this.loanRecordRepository = loanRecordRepository;
    this.loanApprovalRepository = loanApprovalRepository;
    this.loanGuarantorRepository = loanGuarantorRepository;
    this.savingsRecordRepository = savingsRecordRepository;
    this.platformSettingsRepository = platformSettingsRepository;
    this.loanCommittee = loanCommittee;
    this.feeRepository = feeRepository;
    this.eligibilityService = eligibilityService;
    this.coopPermissions = coopPermissions;
    this.gatewayResolver = gatewayResolver;
    this.paymentGatewayService = paymentGatewayService;
    this.notificationService = notificationService;
    this.auditLogService = auditLogService;
  }

  /** What the member will actually receive: principal minus the co-op + platform loans charge. */
  @GetMapping("/api/v1/cooperatives/{id}/loans/{loanId}/disbursement")
  public ResponseEntity<?> disbursementPreview(
      Authentication authentication, @PathVariable String id, @PathVariable String loanId) {
    var access = requireMemberOfCoop(authentication, id);
    if (access.error() != null) return access.error();

    LoanRecord record = findRecord(loanId);
    if (record == null || !id.equals(record.getCooperativeId())) return loanNotFound();
    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) return coopNotFound();

    BigDecimal chargeAmount = loansChargeFor(coop, platformSettings(), record.getAmount());
    return ResponseEntity.ok(
        new LoanDisbursementDto(record.getAmount(), chargeAmount, record.getAmount().subtract(chargeAmount)));
  }

  /* ─────────────────────────────── Apply ─────────────────────────────── */

  @PostMapping("/api/v1/cooperatives/{id}/loans")
  public ResponseEntity<?> apply(
      Authentication authentication,
      @PathVariable String id,
      @Valid @RequestBody LoanApplicationRequest request,
      HttpServletRequest httpRequest) {
    var access = requireMemberOfCoop(authentication, id);
    if (access.error() != null) return access.error();
    Member member = access.caller();
    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) return coopNotFound();

    LoanType type = findType(request.loanTypeId(), id);
    if (type == null || !"Active".equals(type.getStatus())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that loan type"));
    }

    String mode = notBlank(request.loanMode()) ? request.loanMode() : LoanEligibility.TIMES_TWO;
    if (!LoanEligibility.isValidMode(mode)) {
      return ResponseEntity.badRequest().body(Map.of("error", "Choose \"Times two\" or \"Within\""));
    }
    boolean within = LoanEligibility.WITHIN.equals(mode);

    int maxMonths = type.getDurationMonths();
    int months = request.durationMonths() == null ? maxMonths : request.durationMonths();
    if (months < 1 || months > maxMonths) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "Choose a repayment period between 1 and " + maxMonths + " months for this loan type"));
    }
    if (coop.getLoanMinAmount().signum() > 0 && request.amount().compareTo(coop.getLoanMinAmount()) < 0) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "The smallest loan you can apply for is " + naira(coop.getLoanMinAmount())));
    }

    // The application fee is paid before the form opens; a paid one waits here until it's used.
    LoanApplicationFee feeCredit = null;
    if (coop.getLoanApplicationFee().signum() > 0) {
      feeCredit =
          feeRepository
              .findFirstByCooperativeIdAndMemberIdAndStatusOrderByPaidAtAsc(id, member.getId(), LoanApplicationFee.PAID)
              .orElse(null);
      if (feeCredit == null) {
        return ResponseEntity.status(402)
            .body(Map.of("error", "Pay the " + naira(coop.getLoanApplicationFee()) + " application fee before applying for a loan"));
      }
    }

    // The co-op's eligibility rules and limits (Settings -> Loans): the same numbers the
    // dashboard and the loan form show.
    LoanEligibilityService.Result eligibilityResult = eligibilityService.evaluate(coop, member);
    if (eligibilityResult.problem() != null) {
      return ResponseEntity.status(409).body(Map.of("error", eligibilityResult.problem()));
    }

    // A Within loan is secured by the member's own savings, so it has no guarantor. A Times two
    // loan needs at least the co-op's minimum number of guarantors.
    List<Member> guarantors = new ArrayList<>();
    if (!within) {
      int minGuarantors = coop.getLoanMinGuarantors();
      LinkedHashSet<String> ids = new LinkedHashSet<>();
      if (request.guarantorMemberIds() != null) {
        request.guarantorMemberIds().stream().filter(this::notBlank).map(String::trim).forEach(ids::add);
      }
      if (notBlank(request.guarantorMemberId())) ids.add(request.guarantorMemberId().trim());
      if (ids.size() < minGuarantors) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", "Select at least " + minGuarantors + " guarantor" + (minGuarantors == 1 ? "" : "s")));
      }
      for (String guarantorId : ids) {
        Member guarantor = memberRepository.findById(guarantorId).orElse(null);
        if (guarantor == null || !id.equals(guarantor.getCooperativeId())) {
          return ResponseEntity.badRequest().body(Map.of("error", "Every guarantor must be a member of this co-operative"));
        }
        if (!"Active".equals(guarantor.getStatus())) {
          return ResponseEntity.badRequest()
              .body(Map.of("error", guarantor.getFullName() + " isn't an active member, so they can't guarantee a loan"));
        }
        if (guarantor.getId().equals(member.getId())) {
          return ResponseEntity.status(409).body(Map.of("error", "You can't guarantee your own loan"));
        }
        guarantors.add(guarantor);
      }
    }

    BigDecimal eligibility = eligibilityResult.limit(type, mode);
    if (request.amount().compareTo(eligibility) > 0) {
      return ResponseEntity.status(409)
          .body(Map.of("error", "The most you can borrow on this loan type is " + naira(eligibility)));
    }

    LoanEligibility.Terms terms = LoanEligibility.terms(type, request.amount(), months);
    LoanRecord record =
        new LoanRecord(
            id,
            member.getId(),
            type.getId(),
            request.amount(),
            terms.interestRate(),
            months,
            months,
            terms.monthlyRepayment(),
            terms.totalRepayment(),
            guarantors.isEmpty() ? null : guarantors.get(0).getId(),
            guarantors.isEmpty() ? null : guarantors.stream().map(Member::getFullName).collect(Collectors.joining(", ")),
            guarantors.isEmpty() ? AWAITING_APPROVAL : AWAITING_GUARANTOR,
            mode);
    if (guarantors.isEmpty()) record.setApprovalStep(1);
    if (feeCredit != null) record.setApplicationFee(feeCredit.getAmount(), feeCredit.getReference());
    loanRecordRepository.save(record);
    if (feeCredit != null) {
      feeCredit.markUsed(record.getId());
      feeRepository.save(feeCredit);
    }
    for (Member guarantor : guarantors) {
      loanGuarantorRepository.save(
          new LoanGuarantor(record.getId(), guarantor.getId(), guarantor.getFullName(), LoanGuarantor.PENDING));
      notificationService.notify(
          guarantor.getId(),
          "LOAN_GUARANTOR_REQUEST",
          "You've been asked to guarantee a loan",
          member.getFullName() + " has asked you to guarantee a " + type.getName() + " loan.",
          "/loans/request/" + record.getId());
    }
    if (guarantors.isEmpty()) notifyApprover(record, member.getFullName(), type.getName());

    auditLogService.log(member.getId(), member.getRole(), "Loans", "Create", type.getName(), "Success", httpRequest);
    return ResponseEntity.ok(dtoOf(record));
  }

  /** What the signed-in member can borrow right now, per loan type and mode, and why not when
   * a rule rules them out. The loan form and the dashboard both read this. */
  @GetMapping("/api/v1/cooperatives/{id}/loans/eligibility")
  public ResponseEntity<?> eligibility(Authentication authentication, @PathVariable String id) {
    var access = requireMemberOfCoop(authentication, id);
    if (access.error() != null) return access.error();
    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) return coopNotFound();
    LoanEligibilityService.Result result = eligibilityService.evaluate(coop, access.caller());
    return ResponseEntity.ok(
        Map.of(
            "savings", result.savings(),
            "problem", result.problem() == null ? "" : result.problem(),
            "best", result.best(),
            "tiedTimesTwo", result.tiedTimesTwo(),
            "tiedWithin", result.tiedWithin(),
            "perType",
                result.perType().stream()
                    .map(
                        t ->
                            Map.of(
                                "loanTypeId", t.type().getId().toString(),
                                "loanTypeName", t.type().getName(),
                                "timesTwo", t.timesTwo(),
                                "within", t.within(),
                                "maxMonths", t.type().getDurationMonths()))
                    .toList()));
  }

  /* ─────────────────────────────── Guarantors ─────────────────────────────── */

  @PatchMapping("/api/v1/cooperatives/{id}/loans/{loanId}/guarantor-response")
  public ResponseEntity<?> guarantorResponse(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String loanId,
      @Valid @RequestBody GuarantorResponseRequest request,
      HttpServletRequest httpRequest) {
    var access = requireMemberOfCoop(authentication, id);
    if (access.error() != null) return access.error();
    Member caller = access.caller();

    LoanRecord record = findRecord(loanId);
    if (record == null || !id.equals(record.getCooperativeId())) return loanNotFound();
    List<LoanGuarantor> guarantors = guarantorsOf(record);
    LoanGuarantor mine =
        guarantors.stream().filter(g -> g.getMemberId().equals(caller.getId())).findFirst().orElse(null);
    if (mine == null) {
      return ResponseEntity.status(403).body(Map.of("error", "You aren't one of this loan's guarantors"));
    }
    if (!AWAITING_GUARANTOR.equals(record.getStatus())) {
      return ResponseEntity.status(409).body(Map.of("error", "This loan already moved past guarantor review"));
    }
    if (!LoanGuarantor.PENDING.equals(mine.getStatus())) {
      return ResponseEntity.status(409).body(Map.of("error", "You've already responded to this request"));
    }

    Member applicant = memberRepository.findById(record.getMemberId()).orElse(null);
    String applicantName = applicant != null ? applicant.getFullName() : "A member";
    String typeName = typeNameOf(record);

    if ("Accepted".equals(request.decision())) {
      mine.accept(notBlank(request.documentUrl()) ? request.documentUrl() : null);
      loanGuarantorRepository.save(mine);
      if (notBlank(request.documentUrl()) && record.getGuarantorDocumentUrl() == null) {
        record.setGuarantorDocumentUrl(request.documentUrl());
      }
      // The loan reaches the committee only once every guarantor has accepted.
      if (guarantors.stream().allMatch(g -> LoanGuarantor.ACCEPTED.equals(g.getStatus()))) {
        record.setStatus(AWAITING_APPROVAL);
        record.setApprovalStep(1);
        record.setGuarantorAcceptedAt(LocalDateTime.now());
        loanRecordRepository.save(record);
        notifyApprover(record, applicantName, typeName);
      } else {
        loanRecordRepository.save(record);
      }
    } else {
      mine.decline();
      loanGuarantorRepository.save(mine);
      record.setStatus("Rejected");
      record.setRejectionReason("Guarantor declined to stand for this loan (" + mine.getMemberName() + ").");
      loanRecordRepository.save(record);
      if (applicant != null) {
        notificationService.notify(
            applicant.getId(),
            "LOAN_GUARANTOR_REJECTED",
            "Your loan application was declined",
            mine.getMemberName() + " declined to stand as guarantor for your " + typeName + " loan application.",
            "/loans/request/" + record.getId());
      }
    }

    auditLogService.log(caller.getId(), caller.getRole(), "Loans", "Update", typeName, "Success", httpRequest);
    return ResponseEntity.ok(dtoOf(record));
  }

  /* ─────────────────────────────── Committee ─────────────────────────────── */

  /** The committee, the decisions so far, and what the caller can do on this loan right now. */
  @GetMapping("/api/v1/cooperatives/{id}/loans/{loanId}/approvals")
  public ResponseEntity<?> approvals(
      Authentication authentication, @PathVariable String id, @PathVariable String loanId) {
    var access = requireMemberOfCoop(authentication, id);
    if (access.error() != null) return access.error();
    Member caller = access.caller();

    LoanRecord record = findRecord(loanId);
    if (record == null || !id.equals(record.getCooperativeId())) return loanNotFound();
    List<Step> steps = loanCommittee.stepsOf(id);
    if (!canView(caller, record, steps)) {
      return ResponseEntity.status(403).body(Map.of("error", "You can't view this loan's approvals"));
    }

    List<LoanApprovalStatusDto.Step> chain =
        steps.stream()
            .map(s -> new LoanApprovalStatusDto.Step(s.position(), s.title(), s.member().getId(), s.member().getFullName()))
            .toList();
    List<LoanApprovalStatusDto.Decision> trail =
        loanApprovalRepository.findAllByLoanIdOrderByActedAtAsc(record.getId()).stream()
            .map(a -> LoanApprovalStatusDto.Decision.from(a, a.getPosition() != null ? a.getStep() : legacyTitle(a.getStep())))
            .toList();

    Step current = loanCommittee.currentStep(steps, record);
    Actor actor = current == null ? null : actorFor(current, record);
    boolean isActor = actor != null && actor.member().getId().equals(caller.getId());
    boolean canReject = isActor || (canOverride(caller, record) && isOpen(record));
    boolean isBorrower = caller.getId().equals(record.getMemberId());

    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    LoanType type = loanTypeRepository.findById(record.getLoanTypeId()).orElse(null);
    BigDecimal net =
        coop == null ? null : record.getAmount().subtract(loansChargeFor(coop, platformSettings(), record.getAmount()));

    LoanApprovalStatusDto.RegisteredAccount registered = null;
    if (isBorrower) {
      Member borrower = caller;
      if (notBlank(borrower.getAccountNumber())) {
        registered =
            new LoanApprovalStatusDto.RegisteredAccount(
                borrower.getBankCode(), mask(borrower.getAccountNumber()), borrower.getAccountName());
      }
    }

    return ResponseEntity.ok(
        new LoanApprovalStatusDto(
            chain,
            trail,
            stageOf(record),
            current == null ? null : current.position(),
            isActor,
            canReject,
            isActor ? actor.label(current) : canReject ? "Admin" : null,
            isActor && current.position() == steps.size(),
            isBorrower && AWAITING_ACCEPTANCE.equals(record.getStatus()),
            PENDING_DISBURSEMENT.equals(record.getStatus()) && canDisburse(caller, record),
            type == null ? record.getRequestedMonths() : type.getDurationMonths(),
            coop == null ? BigDecimal.ZERO : coop.getLoanMinAmount(),
            net,
            registered));
  }

  /**
   * A committee member's decision on the step the loan is waiting on. Approving needs the amount
   * and repayment period they suggest — never more than was asked for, never longer than the loan
   * type allows. The last approver's figures are offered to the borrower.
   */
  @PatchMapping("/api/v1/cooperatives/{id}/loans/{loanId}/decision")
  public ResponseEntity<?> decide(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String loanId,
      @Valid @RequestBody LoanDecisionRequest request,
      HttpServletRequest httpRequest) {
    var access = requireMemberOfCoop(authentication, id);
    if (access.error() != null) return access.error();
    Member caller = access.caller();

    LoanRecord record = findRecord(loanId);
    if (record == null || !id.equals(record.getCooperativeId())) return loanNotFound();
    if (caller.getId().equals(record.getMemberId())) {
      return ResponseEntity.status(403).body(Map.of("error", "You can't approve or reject your own loan"));
    }

    List<Step> steps = loanCommittee.stepsOf(id);
    Step current = loanCommittee.currentStep(steps, record);
    Actor actor = current == null ? null : actorFor(current, record);
    boolean isActor = actor != null && actor.member().getId().equals(caller.getId());
    boolean approving = "Approved".equals(request.decision());

    Member applicant = memberRepository.findById(record.getMemberId()).orElse(null);
    String applicantName = applicant != null ? applicant.getFullName() : "Unknown member";
    String typeName = typeNameOf(record);

    if (!approving) {
      if (!isActor && !(canOverride(caller, record) && isOpen(record))) {
        return ResponseEntity.status(403).body(Map.of("error", cannotActReason(caller, record, current, actor)));
      }
      if (!notBlank(request.rejectionReason())) {
        return ResponseEntity.badRequest().body(Map.of("error", "Give a reason so the member knows what to do next"));
      }
      String reason = request.rejectionReason().trim();
      String title = isActor ? current.title() : "Admin";
      record.setStatus("Rejected");
      record.setRejectionReason(reason);
      record.setApprovalStep(null);
      loanRecordRepository.save(record);
      loanApprovalRepository.save(
          LoanApproval.committee(
              record.getId(), current == null ? 0 : current.position(), title, "Rejected",
              caller.getId(), caller.getFullName(), isActor && actor.covering(), reason, null, null, null, null));
      if (applicant != null) {
        notificationService.notify(
            applicant.getId(),
            "LOAN_DECISION",
            "Your loan was declined",
            "Your " + typeName + " loan application was declined: " + reason,
            "/loans/request/" + record.getId());
      }
      auditLogService.log(caller.getId(), caller.getRole(), "Loans", "Update", typeName, "Warning", httpRequest);
      return ResponseEntity.ok(dtoOf(record));
    }

    if (!isActor) {
      return ResponseEntity.status(403).body(Map.of("error", cannotActReason(caller, record, current, actor)));
    }
    LoanType type = loanTypeRepository.findById(record.getLoanTypeId()).orElse(null);
    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (type == null || coop == null) return loanNotFound();

    BigDecimal amount = request.suggestedAmount();
    Integer months = request.suggestedMonths();
    if (amount == null || amount.signum() <= 0) {
      return ResponseEntity.badRequest().body(Map.of("error", "Enter the amount you're approving"));
    }
    if (months == null) {
      return ResponseEntity.badRequest().body(Map.of("error", "Enter the repayment period you're approving"));
    }
    amount = amount.setScale(2, RoundingMode.HALF_UP);
    if (amount.compareTo(record.getRequestedAmount()) > 0) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "You can't approve more than the " + naira(record.getRequestedAmount()) + " requested"));
    }
    if (coop.getLoanMinAmount().signum() > 0 && amount.compareTo(coop.getLoanMinAmount()) < 0) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "The smallest loan is " + naira(coop.getLoanMinAmount())));
    }
    if (months < 1 || months > type.getDurationMonths()) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "The repayment period must be between 1 and " + type.getDurationMonths() + " months"));
    }

    BigDecimal previousAmount = record.getAmount();
    int previousMonths = record.getDurationMonths();
    LoanEligibility.Terms terms = LoanEligibility.terms(type, amount, months);
    record.applyTerms(amount, months, terms.interestRate(), terms.monthlyRepayment(), terms.totalRepayment());

    loanApprovalRepository.save(
        LoanApproval.committee(
            record.getId(), current.position(), current.title(), "Approved",
            caller.getId(), caller.getFullName(), actor.covering(),
            notBlank(request.note()) ? request.note().trim() : null,
            amount, months, previousAmount, previousMonths));

    boolean finalStep = current.position() >= steps.size();
    if (finalStep) {
      record.setStatus(AWAITING_ACCEPTANCE);
      record.setApprovalStep(null);
      loanRecordRepository.save(record);
      if (applicant != null) {
        notificationService.notify(
            applicant.getId(),
            "LOAN_DECISION",
            "Your loan was approved: accept the offer",
            "Your " + typeName + " loan was approved for " + naira(amount) + " over " + months + " month"
                + (months == 1 ? "" : "s") + ". Accept it and choose where the money should be paid.",
            "/loans/request/" + record.getId());
      }
    } else {
      record.setApprovalStep(current.position() + 1);
      loanRecordRepository.save(record);
      notifyApprover(record, applicantName, typeName);
    }

    auditLogService.log(caller.getId(), caller.getRole(), "Loans", "Update", typeName, "Success", httpRequest);
    return ResponseEntity.ok(dtoOf(record));
  }

  /** Loans waiting on the caller as a committee member right now (or covering for one). */
  @GetMapping("/api/v1/cooperatives/{id}/loans/awaiting-me")
  public ResponseEntity<?> awaitingMe(Authentication authentication, @PathVariable String id) {
    var access = requireMemberOfCoop(authentication, id);
    if (access.error() != null) return access.error();
    Member caller = access.caller();
    List<Step> steps = loanCommittee.stepsOf(id);
    List<LoanRecordDto> waiting =
        loanRecordRepository.findAllByCooperativeIdOrderByCreatedAtDesc(id).stream()
            .filter(record -> AWAITING_APPROVAL.equals(record.getStatus()))
            .filter(
                record -> {
                  Step step = loanCommittee.currentStep(steps, record);
                  Actor actor = step == null ? null : actorFor(step, record);
                  return actor != null && actor.member().getId().equals(caller.getId());
                })
            .map(this::dtoOf)
            .toList();
    return ResponseEntity.ok(waiting);
  }

  /* ─────────────────────────────── Borrower's answer ─────────────────────────────── */

  /**
   * The borrower accepts or declines the approved terms. Accepting chooses the payout account:
   * their registered one, or another account, which must be in their registered name (the bank
   * is asked for the real account name when Paystack is connected).
   */
  @PostMapping("/api/v1/cooperatives/{id}/loans/{loanId}/offer-response")
  public ResponseEntity<?> offerResponse(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String loanId,
      @Valid @RequestBody OfferResponse request,
      HttpServletRequest httpRequest) {
    var access = requireMemberOfCoop(authentication, id);
    if (access.error() != null) return access.error();
    Member caller = access.caller();

    LoanRecord record = findRecord(loanId);
    if (record == null || !id.equals(record.getCooperativeId())) return loanNotFound();
    if (!caller.getId().equals(record.getMemberId())) {
      return ResponseEntity.status(403).body(Map.of("error", "Only the borrower can accept or decline this loan"));
    }
    if (!AWAITING_ACCEPTANCE.equals(record.getStatus())) {
      return ResponseEntity.status(409).body(Map.of("error", "This loan isn't waiting for your answer"));
    }
    String typeName = typeNameOf(record);

    if ("Declined".equals(request.decision())) {
      record.declineOffer();
      record.setStatus("Declined");
      record.setRejectionReason(
          notBlank(request.reason()) ? "Declined by the borrower: " + request.reason().trim() : "Declined by the borrower.");
      loanRecordRepository.save(record);
      notificationService.notifyCoopAdmin(
          id,
          "LOAN_DECISION",
          "A loan offer was declined",
          caller.getFullName() + " declined the approved " + typeName + " loan.",
          "/loans/request/" + record.getId());
      auditLogService.log(caller.getId(), caller.getRole(), "Loans", "Update", typeName, "Warning", httpRequest);
      return ResponseEntity.ok(dtoOf(record));
    }

    if (!notBlank(request.accountType())) {
      return ResponseEntity.badRequest().body(Map.of("error", "Choose where the money should be paid"));
    }
    if ("Registered".equals(request.accountType())) {
      if (!notBlank(caller.getAccountNumber()) || !notBlank(caller.getBankCode())) {
        return ResponseEntity.status(409)
            .body(Map.of("error", "You haven't added a bank account to your profile. Choose another account, or add one to your profile first."));
      }
      record.acceptOffer(
          "Registered",
          caller.getBankCode(),
          notBlank(request.bankName()) ? request.bankName().trim() : null,
          caller.getAccountNumber(),
          notBlank(caller.getAccountName()) ? caller.getAccountName() : caller.getFullName(),
          true);
    } else {
      if (!notBlank(request.bankCode()) || !notBlank(request.accountNumber())) {
        return ResponseEntity.badRequest().body(Map.of("error", "Choose the bank and enter the 10-digit account number"));
      }
      String key = gatewayResolver.payoutSecretKeyFor(id).orElse(platformSettings().getPaystackSecretKey());
      PaymentGatewayService.BankResolveResult resolved =
          paymentGatewayService.resolvePaystackBankAccount(request.accountNumber(), request.bankCode(), key);
      String accountName;
      boolean verified;
      if (resolved.success()) {
        accountName = resolved.accountName();
        verified = true;
      } else if (key == null || key.isBlank()) {
        // No way to ask the bank: fall back to the name the member typed, flagged as unverified.
        if (!notBlank(request.accountName())) {
          return ResponseEntity.badRequest().body(Map.of("error", "Enter the name on the account"));
        }
        accountName = request.accountName().trim();
        verified = false;
      } else {
        return ResponseEntity.status(422)
            .body(Map.of("error", resolved.message() != null ? resolved.message() : "We couldn't verify that account."));
      }
      if (!nameMatches(accountName, caller)) {
        return ResponseEntity.status(422)
            .body(
                Map.of(
                    "error",
                    "This account is in the name \"" + accountName + "\". It must be in your registered name, "
                        + caller.getFullName() + "."));
      }
      record.acceptOffer(
          "Other",
          request.bankCode().trim(),
          notBlank(request.bankName()) ? request.bankName().trim() : null,
          request.accountNumber(),
          accountName,
          verified);
    }
    record.setStatus(PENDING_DISBURSEMENT);
    loanRecordRepository.save(record);
    notificationService.notifyCoopAdmin(
        id,
        "LOAN_APPROVAL_REQUEST",
        "A loan is ready to pay out",
        caller.getFullName() + " accepted the approved " + typeName + " loan of " + naira(record.getAmount())
            + ". It's ready to be disbursed.",
        "/loans/request/" + record.getId());
    auditLogService.log(caller.getId(), caller.getRole(), "Loans", "Update", typeName, "Success", httpRequest);
    return ResponseEntity.ok(dtoOf(record));
  }

  /**
   * True when the account name is the member's own: it must contain both their first and last
   * names (any order, any case; middle names and initials are allowed).
   */
  static boolean nameMatches(String accountName, Member member) {
    Set<String> tokens = tokens(accountName);
    Set<String> first = tokens(member.getFirstName());
    Set<String> last = tokens(member.getLastName());
    return !first.isEmpty() && !last.isEmpty() && tokens.containsAll(first) && tokens.containsAll(last);
  }

  private static Set<String> tokens(String value) {
    if (value == null) return Set.of();
    return Arrays.stream(value.toLowerCase(Locale.ROOT).split("[^\\p{L}]+"))
        .filter(token -> !token.isBlank())
        .collect(Collectors.toSet());
  }

  /* ─────────────────────────────── Disbursement ─────────────────────────────── */

  /**
   * Pays an accepted loan out: online (Paystack transfer to the account the borrower chose) or
   * by cheque (a photo or scan of it). The transfer reference is fixed per loan, so a retry can
   * never pay twice.
   */
  @PostMapping("/api/v1/cooperatives/{id}/loans/{loanId}/disburse")
  public ResponseEntity<?> disburse(
      Authentication authentication,
      @PathVariable String id,
      @PathVariable String loanId,
      @Valid @RequestBody Disburse request,
      HttpServletRequest httpRequest) {
    var access = requireMemberOfCoop(authentication, id);
    if (access.error() != null) return access.error();
    Member caller = access.caller();

    LoanRecord record = findRecord(loanId);
    if (record == null || !id.equals(record.getCooperativeId())) return loanNotFound();
    if (!canDisburse(caller, record)) {
      return ResponseEntity.status(403).body(Map.of("error", "You can't pay out this loan"));
    }
    if (!PENDING_DISBURSEMENT.equals(record.getStatus())) {
      return ResponseEntity.status(409).body(Map.of("error", "This loan isn't waiting to be paid out"));
    }
    Cooperative coop = cooperativeRepository.findById(id).orElse(null);
    if (coop == null) return coopNotFound();
    Member applicant = memberRepository.findById(record.getMemberId()).orElse(null);
    String applicantName = applicant != null ? applicant.getFullName() : "Unknown member";
    String typeName = typeNameOf(record);

    // A Within loan comes out of the member's savings; they may have withdrawn since applying.
    if (record.isWithin()
        && savingsRecordRepository.sumByMember(record.getMemberId()).compareTo(record.getAmount()) < 0) {
      return ResponseEntity.status(409)
          .body(Map.of("error", applicantName + "'s savings no longer cover this Within loan, so it can't be paid out."));
    }
    BigDecimal net = record.getAmount().subtract(loansChargeFor(coop, platformSettings(), record.getAmount()));
    String reference;
    String transferStatus;

    if ("Online".equals(request.method())) {
      String bankCode = notBlank(record.getPayoutBankCode()) ? record.getPayoutBankCode() : applicant == null ? null : applicant.getBankCode();
      String accountNumber =
          notBlank(record.getPayoutAccountNumber()) ? record.getPayoutAccountNumber() : applicant == null ? null : applicant.getAccountNumber();
      String accountName = notBlank(record.getPayoutAccountName()) ? record.getPayoutAccountName() : applicantName;
      if (!notBlank(bankCode) || !notBlank(accountNumber)) {
        return ResponseEntity.status(409)
            .body(Map.of("error", "There's no bank account to pay this loan into. Pay it by cheque instead."));
      }
      String payoutKey = gatewayResolver.payoutSecretKeyFor(id).orElse(null);
      if (payoutKey == null) {
        return ResponseEntity.status(409)
            .body(Map.of("error", "No Paystack account is set up to pay out loans. Connect one in Settings → Integrations, or pay by cheque."));
      }
      reference = "loan-" + record.getId().toString().toLowerCase();
      PaymentGatewayService.TransferResult transfer =
          paymentGatewayService.initiatePaystackTransfer(
              payoutKey, accountName, accountNumber, bankCode, net,
              coop.getName() + " loan disbursement — " + typeName, reference);
      if (!transfer.success()) {
        return ResponseEntity.status(502)
            .body(Map.of("error", transfer.message() != null ? transfer.message() : "The payout didn't go through."));
      }
      transferStatus = transfer.status();
    } else {
      if (!notBlank(request.chequeUrl())) {
        return ResponseEntity.badRequest().body(Map.of("error", "Upload the cheque before disbursing"));
      }
      reference = "cheque-" + (notBlank(request.chequeNumber()) ? request.chequeNumber().trim() : record.getId().toString().substring(0, 8));
      transferStatus = "Cheque";
    }

    record.markDisbursed(reference, transferStatus, net);
    record.setDisbursementDetails(
        request.method(),
        "Cheque".equals(request.method()) ? request.chequeUrl() : null,
        "Cheque".equals(request.method()) && notBlank(request.chequeNumber()) ? request.chequeNumber().trim() : null,
        caller.getFullName());
    record.setStatus("Active");
    loanRecordRepository.save(record);

    if (record.isWithin()) {
      // The loan is borrowed from the member's own savings: take the principal out of them.
      BigDecimal balanceAfter = savingsRecordRepository.sumByMember(record.getMemberId()).subtract(record.getAmount());
      savingsRecordRepository.save(
          new SavingsRecord(id, record.getMemberId(), null, record.getAmount().negate(), balanceAfter,
              "Loan Disbursement", reference, "Success", null));
    }
    if (applicant != null) {
      notificationService.notify(
          applicant.getId(),
          "LOAN_DISBURSED",
          "Your loan has been paid out",
          "Cheque".equals(request.method())
              ? "Your " + typeName + " loan of " + naira(net) + " has been paid by cheque."
              : "Your " + typeName + " loan was paid out: " + naira(net) + " has been sent to your bank account.",
          "/loans/" + record.getId());
    }
    auditLogService.log(caller.getId(), caller.getRole(), "Loans", "Update", typeName, "Success", httpRequest);
    return ResponseEntity.ok(dtoOf(record));
  }

  /* ─────────────────────────────── Who can do what ─────────────────────────────── */

  /** Who acts on a step, and whether they're covering for the committee member. */
  private record Actor(Member member, boolean covering) {
    String label(Step step) {
      return covering ? "Admin (covering for " + step.title() + ")" : step.title();
    }
  }

  /**
   * Who acts on this step. Nobody approves a loan they borrow or guarantee, so if the committee
   * member is a party to it, the co-op admin covers; if the admin is too, the committee member
   * acts anyway (as long as they aren't the borrower), so a loan can never get stuck.
   */
  private Actor actorFor(Step step, LoanRecord record) {
    Member holder = step.member();
    if (!isPartyTo(holder.getId(), record)) return new Actor(holder, false);
    Member admin = memberRepository.findById(record.getCooperativeId()).orElse(null);
    if (admin != null && !isPartyTo(admin.getId(), record)) return new Actor(admin, true);
    if (!holder.getId().equals(record.getMemberId())) return new Actor(holder, false);
    if (admin != null && !admin.getId().equals(record.getMemberId())) return new Actor(admin, true);
    return null;
  }

  private String cannotActReason(Member caller, LoanRecord record, Step current, Actor actor) {
    if (AWAITING_GUARANTOR.equals(record.getStatus())) return "This loan is still waiting for its guarantors to accept";
    if (current == null) return "This loan is no longer waiting for a committee decision";
    String who = actor == null ? "someone else" : actor.member().getFullName();
    if (isPartyTo(caller.getId(), record)) {
      return "You're a guarantor on this loan, so " + who + " handles the " + current.title() + " step";
    }
    return "This loan is waiting for the " + current.title() + " (" + who + "), not you";
  }

  /** The co-op admin can reject a loan at any stage before it's paid out. */
  private boolean canOverride(Member caller, LoanRecord record) {
    return isCooperativeAdmin(caller, record.getCooperativeId()) && !caller.getId().equals(record.getMemberId());
  }

  private static boolean isOpen(LoanRecord record) {
    return IN_PROGRESS.contains(record.getStatus());
  }

  private boolean canDisburse(Member caller, LoanRecord record) {
    if (caller.getId().equals(record.getMemberId())) return false;
    String coopId = record.getCooperativeId();
    return isCooperativeAdmin(caller, coopId)
        || (coopId.equals(caller.getCooperativeId()) && coopPermissions.allows(caller, coopId, "Loans", "Requests", true));
  }

  /** The borrower, a guarantor, a committee member, or staff who can read loan requests. */
  private boolean canView(Member caller, LoanRecord record, List<Step> steps) {
    String coopId = record.getCooperativeId();
    return "super_admin".equals(caller.getRole())
        || isPartyTo(caller.getId(), record)
        || isCooperativeAdmin(caller, coopId)
        || steps.stream().anyMatch(s -> s.member().getId().equals(caller.getId()))
        || (caller.getCoopRoleId() != null && coopPermissions.allows(caller, coopId, "Loans", "Requests", false));
  }

  /** The borrower, or any of the loan's guarantors. */
  private boolean isPartyTo(String memberId, LoanRecord record) {
    return memberId.equals(record.getMemberId())
        || memberId.equals(record.getGuarantorId())
        || loanGuarantorRepository.existsByLoanIdAndMemberId(record.getId(), memberId);
  }

  private static String stageOf(LoanRecord record) {
    return switch (record.getStatus()) {
      case AWAITING_GUARANTOR -> "Guarantors";
      case AWAITING_APPROVAL -> "Approval";
      case AWAITING_ACCEPTANCE -> "Acceptance";
      case PENDING_DISBURSEMENT -> "Disbursement";
      default -> record.getStatus();
    };
  }

  private void notifyApprover(LoanRecord record, String applicantName, String typeName) {
    List<Step> steps = loanCommittee.stepsOf(record.getCooperativeId());
    Step step = loanCommittee.currentStep(steps, record);
    String link = "/loans/request/" + record.getId();
    Actor actor = step == null ? null : actorFor(step, record);
    if (actor != null) {
      notificationService.notify(
          actor.member().getId(),
          "LOAN_APPROVAL_REQUEST",
          "A loan needs your approval",
          applicantName + "'s " + typeName + " loan is waiting for your approval as " + step.title()
              + (actor.covering() ? " (you're covering that step)." : "."),
          link);
    } else {
      notificationService.notifyCoopAdmin(
          record.getCooperativeId(),
          "LOAN_APPROVAL_REQUEST",
          "A loan needs approval",
          applicantName + "'s " + typeName + " loan is waiting for the loan committee.",
          link);
    }
  }

  /* ─────────────────────────────── Helpers ─────────────────────────────── */

  private LoanRecordDto dtoOf(LoanRecord record) {
    Member applicant = memberRepository.findById(record.getMemberId()).orElse(null);
    List<Step> steps = loanCommittee.stepsOf(record.getCooperativeId());
    return LoanRecordDto.from(
        record,
        applicant != null ? applicant.getFullName() : "Unknown member",
        typeNameOf(record),
        guarantorsOf(record),
        loanCommittee.stepTitle(steps, record));
  }

  /** Decisions made under the old fixed chain stored the office code. */
  private static String legacyTitle(String step) {
    String name = CoopOffices.NAMES.get(step);
    return name != null ? name : step;
  }

  private static String mask(String accountNumber) {
    if (accountNumber == null || accountNumber.length() <= 4) return accountNumber;
    return "•".repeat(accountNumber.length() - 4) + accountNumber.substring(accountNumber.length() - 4);
  }

  private static String naira(BigDecimal amount) {
    NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
    format.setMaximumFractionDigits(2);
    return "₦" + format.format(amount);
  }

  private List<LoanGuarantor> guarantorsOf(LoanRecord record) {
    return loanGuarantorRepository.findAllByLoanIdOrderByCreatedAtAsc(record.getId());
  }

  private String typeNameOf(LoanRecord record) {
    return loanTypeRepository.findById(record.getLoanTypeId()).map(LoanType::getName).orElse("Unknown type");
  }

  private LoanType findType(String typeId, String cooperativeId) {
    try {
      LoanType type = loanTypeRepository.findById(UUID.fromString(typeId)).orElse(null);
      return type != null && cooperativeId.equals(type.getCooperativeId()) ? type : null;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private LoanRecord findRecord(String id) {
    try {
      return loanRecordRepository.findById(UUID.fromString(id)).orElse(null);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }

  private static ResponseEntity<?> loanNotFound() {
    return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that loan"));
  }

  private static ResponseEntity<?> coopNotFound() {
    return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
  }

  private BigDecimal feeFor(String type, BigDecimal feeAmount, BigDecimal amount) {
    if ("Fixed".equals(type)) return feeAmount;
    return amount.multiply(feeAmount).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
  }

  /** The co-op's own loans-charge rate combined with the platform's, deducted from disbursement.
   * The member still owes and repays the full approved amount. */
  private BigDecimal loansChargeFor(Cooperative coop, PlatformSettings settings, BigDecimal loanAmount) {
    BigDecimal coopCharge = feeFor(coop.getLoansChargeType(), coop.getLoansChargeAmount(), loanAmount);
    BigDecimal platformCharge = feeFor(settings.getLoansChargeType(), settings.getLoansChargeAmount(), loanAmount);
    return coopCharge.add(platformCharge);
  }

  private PlatformSettings platformSettings() {
    return platformSettingsRepository
        .findById(SETTINGS_SINGLETON_ID)
        .orElseThrow(() -> new IllegalStateException("Platform settings row is missing"));
  }

  private record CoopAccess(Member caller, ResponseEntity<?> error) {}

  private static boolean isCooperativeAdmin(Member caller, String cooperativeId) {
    return "admin".equals(caller.getRole())
        && cooperativeId.equals(caller.getId())
        && cooperativeId.equals(caller.getCooperativeId());
  }

  private CoopAccess requireMemberOfCoop(Authentication authentication, String cooperativeId) {
    String callerId = (String) authentication.getPrincipal();
    Member caller = memberRepository.findById(callerId).orElse(null);
    if (caller == null) {
      return new CoopAccess(null, ResponseEntity.status(401).body(Map.of("error", "Member no longer exists")));
    }
    if ("super_admin".equals(caller.getRole()) || cooperativeId.equals(caller.getCooperativeId())) {
      return new CoopAccess(caller, null);
    }
    return new CoopAccess(
        null, ResponseEntity.status(403).body(Map.of("error", "You can only manage your own co-operative's loans")));
  }
}
