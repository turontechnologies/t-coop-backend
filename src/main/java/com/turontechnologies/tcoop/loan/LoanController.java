package com.turontechnologies.tcoop.loan;

import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Super-admin-only loans oversight — mirrors SavingsController exactly. Read-only: the flows
 * that actually create a loan record (guarantor acceptance, admin approval/disbursement) haven't
 * been cut over from the frontend's mock store to this backend yet — see
 * t-coop-app/documentation/loans-page.md.
 */
@RestController
public class LoanController {

  private final CooperativeRepository cooperativeRepository;
  private final MemberRepository memberRepository;
  private final LoanTypeRepository loanTypeRepository;
  private final LoanRecordRepository loanRecordRepository;

  public LoanController(
      CooperativeRepository cooperativeRepository,
      MemberRepository memberRepository,
      LoanTypeRepository loanTypeRepository,
      LoanRecordRepository loanRecordRepository) {
    this.cooperativeRepository = cooperativeRepository;
    this.memberRepository = memberRepository;
    this.loanTypeRepository = loanTypeRepository;
    this.loanRecordRepository = loanRecordRepository;
  }

  /** The "Loans" breakdown table — one row per loan type, with its live "Earnings on Loan". */
  @GetMapping("/api/v1/cooperatives/{id}/loans/types")
  public ResponseEntity<?> types(Authentication authentication, @PathVariable String id) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;
    if (!cooperativeRepository.existsById(id)) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }

    List<LoanType> types = loanTypeRepository.findAllByCooperativeIdOrderByCreatedAtAsc(id);
    List<LoanRecord> records = loanRecordRepository.findAllByCooperativeIdOrderByCreatedAtDesc(id);

    Map<UUID, BigDecimal> earningsByType = new HashMap<>();
    for (LoanRecord record : records) {
      if ("Rejected".equals(record.getStatus())) continue;
      BigDecimal earned = record.getTotalRepayment().subtract(record.getAmount());
      earningsByType.merge(record.getLoanTypeId(), earned, BigDecimal::add);
    }

    List<LoanTypeSummaryDto> dtos =
        types.stream()
            .map(type -> LoanTypeSummaryDto.from(type, earningsByType.getOrDefault(type.getId(), BigDecimal.ZERO)))
            .toList();
    return ResponseEntity.ok(dtos);
  }

  /** All of one co-op's loan records, newest first — the per-type drill-down table. Every
   * filter is optional; {@code type} matches a loan type's name (not id). */
  @GetMapping("/api/v1/cooperatives/{id}/loans")
  public ResponseEntity<?> records(
      Authentication authentication,
      @PathVariable String id,
      @RequestParam(required = false) String memberId,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;
    if (!cooperativeRepository.existsById(id)) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }

    Map<UUID, String> typeNames = typeNamesFor(id);
    Map<String, String> memberNames = memberNamesFor(id);

    List<LoanRecordDto> dtos =
        loanRecordRepository.findAllByCooperativeIdOrderByCreatedAtDesc(id).stream()
            .filter(record -> memberId == null || memberId.equals(record.getMemberId()))
            .filter(record -> type == null || type.equals(typeNames.get(record.getLoanTypeId())))
            .filter(record -> status == null || status.equals(record.getStatus()))
            .filter(record -> from == null || !record.getLoanDate().isBefore(from))
            .filter(record -> to == null || !record.getLoanDate().isAfter(to))
            .map(
                record ->
                    LoanRecordDto.from(
                        record,
                        memberNames.getOrDefault(record.getMemberId(), "Unknown member"),
                        typeNames.getOrDefault(record.getLoanTypeId(), "Unknown type")))
            .toList();
    return ResponseEntity.ok(dtos);
  }

  /** A single loan's full detail. */
  @GetMapping("/api/v1/loans/{recordId}")
  public ResponseEntity<?> record(Authentication authentication, @PathVariable String recordId) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    LoanRecord record = findRecord(recordId);
    if (record == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that loan record"));
    }

    Member member = memberRepository.findById(record.getMemberId()).orElse(null);
    LoanType type = loanTypeRepository.findById(record.getLoanTypeId()).orElse(null);
    return ResponseEntity.ok(
        LoanRecordDto.from(
            record,
            member != null ? member.getFullName() : "Unknown member",
            type != null ? type.getName() : "Unknown type"));
  }

  private LoanRecord findRecord(String id) {
    try {
      return loanRecordRepository.findById(UUID.fromString(id)).orElse(null);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private Map<UUID, String> typeNamesFor(String cooperativeId) {
    Map<UUID, String> names = new HashMap<>();
    for (LoanType type : loanTypeRepository.findAllByCooperativeIdOrderByCreatedAtAsc(cooperativeId)) {
      names.put(type.getId(), type.getName());
    }
    return names;
  }

  private Map<String, String> memberNamesFor(String cooperativeId) {
    Map<String, String> names = new HashMap<>();
    for (Member member : memberRepository.findAllByCooperativeId(cooperativeId)) {
      names.put(member.getId(), member.getFullName());
    }
    return names;
  }

  private ResponseEntity<?> requireSuperAdmin(Authentication authentication) {
    String callerId = (String) authentication.getPrincipal();
    Member caller = memberRepository.findById(callerId).orElse(null);
    if (caller == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    }
    if (!"super_admin".equals(caller.getRole())) {
      return ResponseEntity.status(403)
          .body(Map.of("error", "Only a super admin can view loans oversight"));
    }
    return null;
  }
}
