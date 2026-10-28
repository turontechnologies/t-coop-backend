package com.turontechnologies.tcoop.loan;

import com.turontechnologies.tcoop.coopstaff.CoopPermissions;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.savings.SavingsRecordRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * A member's full borrowing record — outstanding balance, repayment punctuality, and every loan
 * with its repayments — so the officers approving a request (Vice President, Treasurer,
 * Secretary) can see at a glance whether the member already owes money and how reliably they pay.
 * Co-op staff can view any member of their co-op; a member can view their own.
 */
@RestController
public class LoanHistoryController {

  private static final List<String> PENDING =
      List.of("Awaiting Guarantor", "Awaiting Vice President", "Awaiting Treasurer", "Awaiting Secretary");

  private final MemberRepository memberRepository;
  private final LoanRecordRepository loanRecordRepository;
  private final LoanRepaymentRepository loanRepaymentRepository;
  private final LoanTypeRepository loanTypeRepository;
  private final SavingsRecordRepository savingsRecordRepository;
  private final CoopPermissions coopPermissions;

  public LoanHistoryController(
      MemberRepository memberRepository,
      LoanRecordRepository loanRecordRepository,
      LoanRepaymentRepository loanRepaymentRepository,
      LoanTypeRepository loanTypeRepository,
      SavingsRecordRepository savingsRecordRepository,
      CoopPermissions coopPermissions) {
    this.memberRepository = memberRepository;
    this.loanRecordRepository = loanRecordRepository;
    this.loanRepaymentRepository = loanRepaymentRepository;
    this.loanTypeRepository = loanTypeRepository;
    this.savingsRecordRepository = savingsRecordRepository;
    this.coopPermissions = coopPermissions;
  }

  @GetMapping("/api/v1/cooperatives/{id}/members/{memberId}/loan-history")
  public ResponseEntity<?> history(
      Authentication authentication, @PathVariable String id, @PathVariable String memberId) {
    Member caller = memberRepository.findById((String) authentication.getPrincipal()).orElse(null);
    if (caller == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    }
    boolean self = caller.getId().equals(memberId) && id.equals(caller.getCooperativeId());
    if (!self && !coopPermissions.isStaff(caller, id)) {
      return ResponseEntity.status(403).body(Map.of("error", "You can't view this member's loan history"));
    }
    Member member = memberRepository.findById(memberId).orElse(null);
    if (member == null || !id.equals(member.getCooperativeId())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that member in this co-operative"));
    }

    Map<UUID, String> typeNames = new HashMap<>();
    for (LoanType type : loanTypeRepository.findAllByCooperativeId(id)) {
      typeNames.put(type.getId(), type.getName());
    }

    LocalDate today = LocalDate.now();
    List<LoanHistoryDto.Loan> loans = new ArrayList<>();
    int active = 0, completed = 0, rejected = 0, pending = 0;
    int repaymentsMade = 0, onTime = 0, late = 0, overdue = 0;
    BigDecimal outstandingTotal = BigDecimal.ZERO, borrowed = BigDecimal.ZERO, repaidTotal = BigDecimal.ZERO;
    LocalDate lastRepayment = null;

    for (LoanRecord loan : loanRecordRepository.findAllByCooperativeIdAndMemberIdOrderByCreatedAtDesc(id, memberId)) {
      switch (loan.getStatus()) {
        case "Active" -> active++;
        case "Completed" -> completed++;
        case "Rejected" -> rejected++;
        default -> {
          if (PENDING.contains(loan.getStatus())) pending++;
        }
      }
      boolean paidOut = "Active".equals(loan.getStatus()) || "Completed".equals(loan.getStatus());
      if (paidOut) borrowed = borrowed.add(loan.getAmount());

      List<LoanRepayment> payments = new ArrayList<>(loanRepaymentRepository.findAllByLoanIdOrderByCreatedAtDesc(loan.getId()));
      payments.sort(Comparator.comparingInt(LoanRepayment::getInstallmentNumber));
      BigDecimal repaid = BigDecimal.ZERO;
      List<LoanHistoryDto.Repayment> repaymentRows = new ArrayList<>();
      for (LoanRepayment payment : payments) {
        repaid = repaid.add(payment.getAmount());
        long daysLate = LoanSchedule.daysLate(loan, payment.getInstallmentNumber(), payment.getRepaymentDate());
        if (daysLate == 0) onTime++;
        else late++;
        repaymentsMade++;
        if (lastRepayment == null || payment.getRepaymentDate().isAfter(lastRepayment)) {
          lastRepayment = payment.getRepaymentDate();
        }
        repaymentRows.add(
            new LoanHistoryDto.Repayment(
                payment.getInstallmentNumber(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getRepaymentDate(),
                LoanSchedule.dueDate(loan, payment.getInstallmentNumber()),
                daysLate));
      }
      repaidTotal = repaidTotal.add(repaid);

      BigDecimal outstanding = LoanSchedule.outstanding(loan, repaid);
      outstandingTotal = outstandingTotal.add(outstanding);
      int loanOverdue = LoanSchedule.overdueInstallments(loan, today);
      overdue += loanOverdue;
      LocalDate nextDue =
          LoanSchedule.isRunning(loan) && loan.getRepaymentsMade() < loan.getNumberOfRepayments()
              ? LoanSchedule.dueDate(loan, loan.getRepaymentsMade() + 1)
              : null;

      loans.add(
          new LoanHistoryDto.Loan(
              loan.getId().toString(),
              typeNames.getOrDefault(loan.getLoanTypeId(), "Unknown type"),
              loan.getLoanMode(),
              loan.getStatus(),
              loan.getAmount(),
              loan.getTotalRepayment(),
              repaid,
              outstanding,
              loan.getNumberOfRepayments(),
              loan.getRepaymentsMade(),
              loanOverdue,
              loan.getLoanDate(),
              loan.getDisbursedAt() != null ? loan.getDisbursedAt().toLocalDate() : null,
              nextDue,
              repaymentRows));
    }

    Integer onTimeRate = repaymentsMade == 0 ? null : Math.round(onTime * 100f / repaymentsMade);
    return ResponseEntity.ok(
        new LoanHistoryDto(
            member.getId(),
            member.getFullName(),
            savingsRecordRepository.sumByMember(memberId),
            new LoanHistoryDto.Summary(
                active,
                completed,
                rejected,
                pending,
                outstandingTotal,
                borrowed,
                repaidTotal,
                repaymentsMade,
                onTime,
                late,
                onTimeRate,
                overdue,
                lastRepayment),
            loans));
  }
}
