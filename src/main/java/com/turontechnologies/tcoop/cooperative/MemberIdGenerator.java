package com.turontechnologies.tcoop.cooperative;

import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Hands out the next Membership ID in a co-op's own format (Settings -> Co-operative -> Member ID
 * Format), e.g. MB-0007. Used when an admin adds a member and when an application is approved.
 */
@Component
public class MemberIdGenerator {

  private static final String NUMERIC_DIGITS = "0123456789";
  private static final String ALPHA_DIGITS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final String ALPHANUMERIC_DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

  private final MemberRepository memberRepository;

  public MemberIdGenerator(MemberRepository memberRepository) {
    this.memberRepository = memberRepository;
  }

  /** The next free ID for this co-op. IDs are unique across the whole platform, so one already
   * taken by another co-op on the same prefix is skipped. */
  public String nextFor(Cooperative coop) {
    List<String> existingIds =
        new ArrayList<>(memberRepository.findAllByCooperativeId(coop.getId()).stream().map(Member::getId).toList());
    String nextId = next(coop.getMemberIdPrefix(), coop.getMemberIdType(), coop.getMemberIdPadding(), existingIds);
    while (memberRepository.existsById(nextId)) {
      existingIds.add(nextId);
      nextId = next(coop.getMemberIdPrefix(), coop.getMemberIdType(), coop.getMemberIdPadding(), existingIds);
    }
    return nextId;
  }

  /**
   * Parses every existing id matching {@code PREFIX-<suffix>} (any letter case), where the suffix
   * uses the chosen type's characters (NUMERIC = 0-9, ALPHA = A-Z, ALPHANUMERIC = 0-9 then A-Z),
   * reads each as a number, and returns the highest plus one in the same characters, padded to
   * {@code padding} wide. Ids in another format are ignored, so changing the format starts again
   * from 1.
   */
  public static String next(String prefix, String idType, int padding, List<String> existingIds) {
    String digits = alphabetFor(idType);
    int base = digits.length();
    Pattern pattern =
        Pattern.compile("^" + Pattern.quote(prefix) + "-?([" + digits + "]+)$", Pattern.CASE_INSENSITIVE);
    long max =
        existingIds.stream()
            .map(pattern::matcher)
            .filter(Matcher::matches)
            .map(matcher -> decode(matcher.group(1).toUpperCase(), digits, base))
            .max(Comparator.naturalOrder())
            .orElse(0L);
    return prefix + "-" + encode(max + 1, digits, base, padding);
  }

  private static String alphabetFor(String idType) {
    return switch (idType == null ? "NUMERIC" : idType) {
      case "ALPHA" -> ALPHA_DIGITS;
      case "ALPHANUMERIC" -> ALPHANUMERIC_DIGITS;
      default -> NUMERIC_DIGITS;
    };
  }

  private static long decode(String value, String digits, int base) {
    long result = 0;
    for (char c : value.toCharArray()) {
      result = result * base + digits.indexOf(c);
    }
    return result;
  }

  private static String encode(long value, String digits, int base, int padding) {
    StringBuilder encoded = new StringBuilder();
    long remaining = value;
    do {
      encoded.append(digits.charAt((int) (remaining % base)));
      remaining /= base;
    } while (remaining > 0);
    while (encoded.length() < padding) {
      encoded.append(digits.charAt(0));
    }
    return encoded.reverse().toString();
  }
}
