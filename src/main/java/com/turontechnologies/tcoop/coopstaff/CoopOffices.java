package com.turontechnologies.tcoop.coopstaff;

import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * The four fixed officer offices every co-op has — President, Vice President, Treasurer,
 * Secretary — each an ordinary {@link CoopRole} with {@code office} set, so assigning one is the
 * same User Management flow as any other role. The loan approval chain (see
 * LoanSelfServiceController) runs Vice President -> Treasurer -> Secretary; the President can
 * reject at any stage. One holder per office.
 */
@Service
public class CoopOffices {

  public static final String PRESIDENT = "PRESIDENT";
  public static final String VICE_PRESIDENT = "VICE_PRESIDENT";
  public static final String TREASURER = "TREASURER";
  public static final String SECRETARY = "SECRETARY";

  /** Office -> role name, in seniority order. */
  public static final Map<String, String> NAMES = new LinkedHashMap<>();

  static {
    NAMES.put(PRESIDENT, "President");
    NAMES.put(VICE_PRESIDENT, "Vice President");
    NAMES.put(TREASURER, "Treasurer");
    NAMES.put(SECRETARY, "Secretary");
  }

  /** What a new office role can reach — the loan Requests queue to act on, plus everything a
   * regular member already uses for themselves. Same encoding as the frontend's
   * lib/permissions.ts; the admin can widen or narrow it like any other role. */
  public static final List<String> DEFAULT_PERMISSIONS =
      List.of(
          "Dashboard::read",
          "Members Directory::read",
          "Loans::Requests::write",
          "Loans::Members Loans::read",
          "Loans::My Loans::write",
          "Savings & Contributions::My Savings::write",
          "Notice Board::read",
          "Support::write",
          "Settings::Profile::write");

  private final CoopRoleRepository coopRoleRepository;
  private final MemberRepository memberRepository;

  public CoopOffices(CoopRoleRepository coopRoleRepository, MemberRepository memberRepository) {
    this.coopRoleRepository = coopRoleRepository;
    this.memberRepository = memberRepository;
  }

  public static String nameOf(String office) {
    return NAMES.getOrDefault(office, office);
  }

  /** Creates whichever of the four office roles this co-op doesn't have yet — new co-ops get
   * them on creation; V40 backfilled every co-op that existed before. */
  public void ensureOfficeRoles(String cooperativeId) {
    for (Map.Entry<String, String> entry : NAMES.entrySet()) {
      if (coopRoleRepository.findByCooperativeIdAndOffice(cooperativeId, entry.getKey()).isPresent()) {
        continue;
      }
      if (coopRoleRepository.existsByCooperativeIdAndNameIgnoreCase(cooperativeId, entry.getValue())) {
        continue;
      }
      coopRoleRepository.save(
          new CoopRole(cooperativeId, entry.getValue(), DEFAULT_PERMISSIONS, entry.getKey()));
    }
  }

  /** The active member currently holding this office, if anyone. */
  public Optional<Member> holderOf(String cooperativeId, String office) {
    return coopRoleRepository
        .findByCooperativeIdAndOffice(cooperativeId, office)
        .filter(role -> "Active".equals(role.getStatus()))
        .flatMap(
            role ->
                memberRepository.findAllByCoopRoleId(role.getId()).stream()
                    .filter(member -> "Active".equals(member.getStatus()))
                    .findFirst());
  }

  /** The office this member holds in their co-op, or null if none. */
  public String officeOf(Member member) {
    if (member.getCoopRoleId() == null) return null;
    return coopRoleRepository
        .findById(member.getCoopRoleId())
        .filter(role -> "Active".equals(role.getStatus()))
        .filter(role -> role.getCooperativeId().equals(member.getCooperativeId()))
        .map(CoopRole::getOffice)
        .orElse(null);
  }
}
