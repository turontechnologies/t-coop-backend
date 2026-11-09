package com.turontechnologies.tcoop.coopstaff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.turontechnologies.tcoop.member.Member;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Whatever role someone holds, their own savings and loans stay theirs; everything else is
 * exactly what the role grants, read or write. */
class CoopPermissionsBaselineTest {

  private static final String COOP = "COOP-0001";

  @Test
  void roleWithoutSavingsOrLoansStillKeepsOwnSavingsAndLoans() {
    UUID roleId = UUID.randomUUID();
    CoopRole role = new CoopRole(COOP, "Notice editor", List.of("Notice Board::write", "Reports::read"));
    CoopRoleRepository roles = mock(CoopRoleRepository.class);
    when(roles.findById(roleId)).thenReturn(Optional.of(role));
    Member member = mock(Member.class);
    when(member.getCooperativeId()).thenReturn(COOP);
    when(member.getRole()).thenReturn("member");
    when(member.getId()).thenReturn("MB-0100");
    when(member.getCoopRoleId()).thenReturn(roleId);

    CoopPermissions permissions = new CoopPermissions(roles);

    // Baseline: own savings and loans, read and write.
    assertThat(permissions.allows(member, COOP, "Savings & Contributions", "My Savings", true)).isTrue();
    assertThat(permissions.allows(member, COOP, "Loans", "My Loans", true)).isTrue();
    // Granted modules follow their access level.
    assertThat(permissions.allows(member, COOP, "Notice Board", null, true)).isTrue();
    assertThat(permissions.allows(member, COOP, "Reports", null, false)).isTrue();
    assertThat(permissions.allows(member, COOP, "Reports", null, true)).isFalse();
    // Nothing else.
    assertThat(permissions.allows(member, COOP, "Loans", "Requests", false)).isFalse();
    assertThat(permissions.allows(member, COOP, "Members Directory", null, false)).isFalse();
    assertThat(permissions.allows(member, COOP, "Savings & Contributions", "Members Savings", false)).isFalse();
    // And it's what login sends.
    assertThat(CoopPermissions.withBaseline(role.getPermissions()))
        .contains("Savings & Contributions::My Savings::write", "Loans::My Loans::write", "Notice Board::write");
  }
}
