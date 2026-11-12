package com.turontechnologies.tcoop.loan;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.turontechnologies.tcoop.member.Member;
import org.junit.jupiter.api.Test;

/** A loan can only be paid into another account when it's in the borrower's registered name. */
class LoanPayoutNameMatchTest {

  private static Member member(String first, String last) {
    Member member = mock(Member.class);
    when(member.getFirstName()).thenReturn(first);
    when(member.getLastName()).thenReturn(last);
    return member;
  }

  @Test
  void acceptsTheSameNameInAnyOrderCaseAndWithMiddleNames() {
    Member bola = member("Bola", "Adeyemi");
    assertTrue(LoanSelfServiceController.nameMatches("ADEYEMI BOLA", bola));
    assertTrue(LoanSelfServiceController.nameMatches("Bola Oluwaseun Adeyemi", bola));
    assertTrue(LoanSelfServiceController.nameMatches("adeyemi, bola o.", bola));
  }

  @Test
  void rejectsSomeoneElsesAccount() {
    Member bola = member("Bola", "Adeyemi");
    assertFalse(LoanSelfServiceController.nameMatches("John Stranger", bola));
    assertFalse(LoanSelfServiceController.nameMatches("Bola Johnson", bola));
    assertFalse(LoanSelfServiceController.nameMatches("Adeyemi Tunde", bola));
    assertFalse(LoanSelfServiceController.nameMatches("Bolanle Adeyemi", bola));
    assertFalse(LoanSelfServiceController.nameMatches("", bola));
  }
}
