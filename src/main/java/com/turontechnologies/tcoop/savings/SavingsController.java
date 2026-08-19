package com.turontechnologies.tcoop.savings;

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
 * Super-admin-only savings oversight — the real backend behind the "Savings & Contributions" tab
 * on a co-op's detail page (Members Savings breakdown, per-type record drill-down, single-record
 * detail). Read-only by design: the flows that actually CREATE a savings record (an admin's
 * "Upload Teller", a member's real Paystack "+ New Savings") haven't been cut over from the
 * frontend's mock store to this backend yet — see documentation/flows.md's savings oversight
 * section. Nothing here is admin/member self-service; that stays exactly as documented in
 * t-coop-app/documentation/savings-page.md until a separate task builds it against this backend.
 */
@RestController
public class SavingsController {

  private final CooperativeRepository cooperativeRepository;
  private final MemberRepository memberRepository;
  private final SavingsTypeRepository savingsTypeRepository;
  private final SavingsRecordRepository savingsRecordRepository;

  public SavingsController(
      CooperativeRepository cooperativeRepository,
      MemberRepository memberRepository,
      SavingsTypeRepository savingsTypeRepository,
      SavingsRecordRepository savingsRecordRepository) {
    this.cooperativeRepository = cooperativeRepository;
    this.memberRepository = memberRepository;
    this.savingsTypeRepository = savingsTypeRepository;
    this.savingsRecordRepository = savingsRecordRepository;
  }

  /** The "Members Savings" breakdown table — one row per savings type, with its live total. */
  @GetMapping("/api/v1/cooperatives/{id}/savings/types")
  public ResponseEntity<?> types(Authentication authentication, @PathVariable String id) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;
    if (!cooperativeRepository.existsById(id)) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that co-operative"));
    }

    List<SavingsType> types = savingsTypeRepository.findAllByCooperativeIdOrderByCreatedAtAsc(id);
    List<SavingsRecord> records = savingsRecordRepository.findAllByCooperativeIdOrderByCreatedAtDesc(id);

    Map<UUID, BigDecimal> totalsByType = new HashMap<>();
    for (SavingsRecord record : records) {
      if (!"Success".equals(record.getStatus())) continue;
      totalsByType.merge(record.getSavingsTypeId(), record.getAmount(), BigDecimal::add);
    }

    List<SavingsTypeSummaryDto> dtos =
        types.stream()
            .map(type -> SavingsTypeSummaryDto.from(type, totalsByType.getOrDefault(type.getId(), BigDecimal.ZERO)))
            .toList();
    return ResponseEntity.ok(dtos);
  }

  /**
   * All of one co-op's savings records, newest first — the per-type drill-down table.
   * Every filter is optional; {@code type} matches a savings type's name (not id), so the
   * frontend's existing name-based route params (e.g. /savings/Basic%20Savings) need no change.
   */
  @GetMapping("/api/v1/cooperatives/{id}/savings")
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

    List<SavingsRecordDto> dtos =
        savingsRecordRepository.findAllByCooperativeIdOrderByCreatedAtDesc(id).stream()
            .filter(record -> memberId == null || memberId.equals(record.getMemberId()))
            .filter(record -> type == null || type.equals(typeNames.get(record.getSavingsTypeId())))
            .filter(record -> status == null || status.equals(record.getStatus()))
            .filter(record -> from == null || !record.getRecordDate().isBefore(from))
            .filter(record -> to == null || !record.getRecordDate().isAfter(to))
            .map(
                record ->
                    SavingsRecordDto.from(
                        record,
                        memberNames.getOrDefault(record.getMemberId(), "Unknown member"),
                        typeNames.getOrDefault(record.getSavingsTypeId(), "Unknown type")))
            .toList();
    return ResponseEntity.ok(dtos);
  }

  /** A single savings transaction's full detail. */
  @GetMapping("/api/v1/savings/{recordId}")
  public ResponseEntity<?> record(Authentication authentication, @PathVariable String recordId) {
    var forbidden = requireSuperAdmin(authentication);
    if (forbidden != null) return forbidden;

    SavingsRecord record = findRecord(recordId);
    if (record == null) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that savings record"));
    }

    Member member = memberRepository.findById(record.getMemberId()).orElse(null);
    SavingsType type = savingsTypeRepository.findById(record.getSavingsTypeId()).orElse(null);
    return ResponseEntity.ok(
        SavingsRecordDto.from(
            record,
            member != null ? member.getFullName() : "Unknown member",
            type != null ? type.getName() : "Unknown type"));
  }

  private SavingsRecord findRecord(String id) {
    try {
      return savingsRecordRepository.findById(UUID.fromString(id)).orElse(null);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private Map<UUID, String> typeNamesFor(String cooperativeId) {
    Map<UUID, String> names = new HashMap<>();
    for (SavingsType type : savingsTypeRepository.findAllByCooperativeIdOrderByCreatedAtAsc(cooperativeId)) {
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
          .body(Map.of("error", "Only a super admin can view savings oversight"));
    }
    return null;
  }
}
