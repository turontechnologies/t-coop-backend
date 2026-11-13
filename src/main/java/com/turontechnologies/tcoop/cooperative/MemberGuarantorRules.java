package com.turontechnologies.tcoop.cooperative;

import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberGuarantor;
import com.turontechnologies.tcoop.member.MemberGuarantorRepository;
import com.turontechnologies.tcoop.member.MemberRepository;
import com.turontechnologies.tcoop.notification.NotificationService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The co-op's new-member guarantor rules (Settings -> Co-operative -> New members): when guarantors
 * are required, at least {@code minGuarantors} of them, and depending on the source setting, only
 * co-op members ("CoopOnly") or a minimum number from inside and from outside the co-op ("Mix").
 * Every guarantor is checked whether or not they're required: co-op guarantors must be active
 * members of this co-op, outsiders need a name and a phone number.
 */
@Component
public class MemberGuarantorRules {

  /** A guarantor that passed the checks, ready to store. */
  public record Row(String guarantorMemberId, String name, String phone, String email) {}

  /**
   * {@code error} is set when the list breaks a rule; otherwise {@code rows} are the guarantors to
   * store (null = keep what's on file) and {@code summary} the names for the member's record.
   */
  public record Result(String error, String summary, List<Row> rows) {
    static Result error(String message) {
      return new Result(message, null, null);
    }
  }

  private static final Pattern PHONE = Pattern.compile("^\\+?[0-9][0-9 ()-]{6,19}$");
  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  private final MemberRepository memberRepository;
  private final MemberGuarantorRepository guarantorRepository;
  private final NotificationService notificationService;

  public MemberGuarantorRules(
      MemberRepository memberRepository,
      MemberGuarantorRepository guarantorRepository,
      NotificationService notificationService) {
    this.memberRepository = memberRepository;
    this.guarantorRepository = guarantorRepository;
    this.notificationService = notificationService;
  }

  /**
   * Checks a guarantor list against the co-op's rules. {@code legacyName} is the old single
   * free-text guarantor, used only when no list was sent. {@code selfId} is the member being
   * edited (null when adding), who can't guarantee themselves.
   */
  public Result check(Cooperative coop, List<MemberGuarantorInput> inputs, String legacyName, String selfId) {
    boolean required = coop.isRequireMemberGuarantors();
    if (inputs == null) {
      if (required) {
        return Result.error(requiredMessage(coop));
      }
      String name = legacyName == null || legacyName.isBlank() ? null : legacyName.trim();
      return new Result(null, name, null);
    }

    List<Row> rows = new ArrayList<>();
    Set<String> seenMembers = new HashSet<>();
    Set<String> seenPhones = new HashSet<>();
    int position = 0;
    for (MemberGuarantorInput input : inputs) {
      position++;
      String label = "Guarantor " + position;
      if (input == null) continue;
      if (input.fromCoop()) {
        String memberId = input.memberId().trim();
        Member guarantor = memberRepository.findById(memberId).orElse(null);
        if (guarantor == null || !coop.getId().equals(guarantor.getCooperativeId())) {
          return Result.error(label + " isn't a member of this co-operative. Pick someone from the list.");
        }
        if (memberId.equals(selfId)) {
          return Result.error("A member can't be their own guarantor. Remove " + guarantor.getFullName() + ".");
        }
        if (!"Active".equals(guarantor.getStatus())) {
          return Result.error(guarantor.getFullName() + " isn't an active member, so they can't be a guarantor.");
        }
        if (!seenMembers.add(memberId)) {
          return Result.error(guarantor.getFullName() + " is listed more than once.");
        }
        rows.add(new Row(memberId, guarantor.getFullName(), guarantor.getPhone(), guarantor.getEmail()));
      } else {
        String name = trim(input.name());
        String phone = trim(input.phone());
        String email = trim(input.email());
        if (name == null && phone == null && email == null) continue; // an empty row the admin left
        if (name == null) return Result.error(label + ": enter the guarantor's full name.");
        if (phone == null) return Result.error(label + " (" + name + "): enter a phone number.");
        if (!PHONE.matcher(phone).matches()) {
          return Result.error(label + " (" + name + "): \"" + phone + "\" doesn't look like a phone number.");
        }
        if (email != null && !EMAIL.matcher(email).matches()) {
          return Result.error(label + " (" + name + "): \"" + email + "\" isn't a valid email address.");
        }
        if (!seenPhones.add(phone.replaceAll("[^0-9]", ""))) {
          return Result.error(name + " has the same phone number as another guarantor.");
        }
        rows.add(new Row(null, name, phone, email));
      }
    }

    if (required) {
      String problem = ruleProblem(coop, rows);
      if (problem != null) return Result.error(problem);
    }
    String summary = rows.isEmpty() ? null : rows.stream().map(Row::name).collect(Collectors.joining(", "));
    return new Result(null, summary, rows);
  }

  /** Replaces the member's guarantors with {@code rows} and tells new co-op guarantors. */
  @Transactional
  public void save(Member member, Cooperative coop, List<Row> rows) {
    List<MemberGuarantor> existing = guarantorRepository.findAllByMemberId(member.getId());
    Set<String> alreadyGuaranteeing =
        existing.stream()
            .map(MemberGuarantor::getGuarantorMemberId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
    guarantorRepository.deleteAll(existing);
    guarantorRepository.flush();
    for (Row row : rows) {
      guarantorRepository.save(
          MemberGuarantor.named(member.getId(), coop.getId(), row.guarantorMemberId(), row.name(), row.email(), row.phone()));
      if (row.guarantorMemberId() != null && !alreadyGuaranteeing.contains(row.guarantorMemberId())) {
        notificationService.notify(
            row.guarantorMemberId(),
            "MEMBER_GUARANTOR",
            "You're a guarantor for " + member.getFullName(),
            coop.getName() + " recorded you as a guarantor for " + member.getFullName() + " (" + member.getId() + ").",
            "/members/" + member.getId());
      }
    }
  }

  /** The first rule the list breaks, in the order an admin would fix them; null when it passes. */
  static String ruleProblem(Cooperative coop, List<Row> rows) {
    int min = Math.max(1, coop.getMinGuarantors());
    long fromCoop = rows.stream().filter(r -> r.guarantorMemberId() != null).count();
    long fromOutside = rows.size() - fromCoop;
    String source = coop.getMemberGuarantorSource();

    if ("CoopOnly".equals(source) && fromOutside > 0) {
      String outsider = rows.stream().filter(r -> r.guarantorMemberId() == null).findFirst().map(Row::name).orElse("");
      return "Guarantors must be members of this co-operative. Replace " + outsider + " with a member.";
    }
    if (rows.size() < min) {
      return "Add at least " + min + " guarantor" + (min == 1 ? "" : "s") + " — you've added " + rows.size() + ".";
    }
    if ("Mix".equals(source)) {
      int needCoop = coop.getMemberGuarantorsFromCoop();
      int needOutside = coop.getMemberGuarantorsFromOutside();
      if (fromCoop < needCoop) {
        return "At least " + needCoop + " guarantor" + (needCoop == 1 ? " has" : "s have")
            + " to be a member of this co-operative — you've added " + fromCoop + ".";
      }
      if (fromOutside < needOutside) {
        return "At least " + needOutside + " guarantor" + (needOutside == 1 ? " has" : "s have")
            + " to be from outside the co-operative — you've added " + fromOutside + ".";
      }
    }
    return null;
  }

  private static String requiredMessage(Cooperative coop) {
    int min = Math.max(1, coop.getMinGuarantors());
    return "This co-operative needs at least " + min + " guarantor" + (min == 1 ? "" : "s") + " for every member.";
  }

  private static String trim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
