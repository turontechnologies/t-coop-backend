package com.turontechnologies.tcoop.cooperative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.turontechnologies.tcoop.cooperative.MemberGuarantorRules.Row;
import java.util.List;
import org.junit.jupiter.api.Test;

class MemberGuarantorRulesTest {

  private static final Row MEMBER_A = new Row("MB-002", "Ada Obi", "080", null);
  private static final Row MEMBER_B = new Row("MB-003", "Bayo Ade", "081", null);
  private static final Row OUTSIDER = new Row(null, "John Doe", "08012345678", null);

  private static Cooperative coop(int min, String source, int fromCoop, int fromOutside) {
    Cooperative coop = new Cooperative("coop-1", "Test Co-op", "Admin", "a@b.co", "080", "x", "NG", "Lagos", "Ikeja", "NGN");
    coop.setRequireMemberGuarantors(true);
    coop.setMinGuarantors(min);
    coop.setMemberGuarantorRules(source, fromCoop, fromOutside);
    return coop;
  }

  @Test
  void anyoneOnlyChecksTheCount() {
    assertEquals("Add at least 2 guarantors — you've added 1.", MemberGuarantorRules.ruleProblem(coop(2, "Any", 0, 0), List.of(OUTSIDER)));
    assertNull(MemberGuarantorRules.ruleProblem(coop(2, "Any", 0, 0), List.of(OUTSIDER, MEMBER_A)));
  }

  @Test
  void coopOnlyRejectsOutsiders() {
    assertEquals(
        "Guarantors must be members of this co-operative. Replace John Doe with a member.",
        MemberGuarantorRules.ruleProblem(coop(2, "CoopOnly", 0, 0), List.of(MEMBER_A, OUTSIDER)));
    assertNull(MemberGuarantorRules.ruleProblem(coop(2, "CoopOnly", 0, 0), List.of(MEMBER_A, MEMBER_B)));
  }

  @Test
  void mixNeedsBothSides() {
    Cooperative mix = coop(3, "Mix", 1, 1);
    assertEquals(
        "At least 1 guarantor has to be from outside the co-operative — you've added 0.",
        MemberGuarantorRules.ruleProblem(mix, List.of(MEMBER_A, MEMBER_B, new Row("MB-004", "C", "0", null))));
    assertEquals(
        "At least 2 guarantors have to be a member of this co-operative — you've added 1.",
        MemberGuarantorRules.ruleProblem(coop(3, "Mix", 2, 1), List.of(MEMBER_A, OUTSIDER, new Row(null, "Jane", "0809", null))));
    assertNull(MemberGuarantorRules.ruleProblem(mix, List.of(MEMBER_A, MEMBER_B, OUTSIDER)));
  }
}
