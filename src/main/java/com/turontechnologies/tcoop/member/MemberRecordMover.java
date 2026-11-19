package com.turontechnologies.tcoop.member;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * When a person's membership ID changes (an admin handover), everything that is personally
 * theirs moves to the new ID with them: savings, loans, repayments, withdrawal requests,
 * guarantees, meeting attendance, support tickets, and the phone that gets their notifications.
 * What they did *in a role* stays with the role: the activity log, approval decisions, notices
 * they posted, committee or approver seats.
 */
@Component
public class MemberRecordMover {

  /** table → column holding a member's own ID. */
  private static final List<String[]> PERSONAL =
      List.of(
          new String[] {"savings_records", "member_id"},
          new String[] {"savings_requests", "member_id"},
          new String[] {"savings_payment_intents", "member_id"},
          new String[] {"loan_records", "member_id"},
          new String[] {"loan_records", "guarantor_id"},
          new String[] {"loan_guarantors", "member_id"},
          new String[] {"loan_repayments", "member_id"},
          new String[] {"loan_repayment_claims", "member_id"},
          new String[] {"loan_payment_intents", "member_id"},
          new String[] {"loan_application_fees", "member_id"},
          new String[] {"member_guarantors", "member_id"},
          new String[] {"member_guarantors", "guarantor_member_id"},
          new String[] {"meeting_attendance", "member_id"},
          new String[] {"platform_earnings", "member_id"},
          new String[] {"support_tickets", "raised_by_id"},
          new String[] {"push_tokens", "member_id"});

  private final EntityManager entityManager;

  public MemberRecordMover(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  /** Must run inside the caller's transaction. */
  public void move(String fromMemberId, String toMemberId) {
    entityManager.flush();
    for (String[] target : PERSONAL) {
      entityManager
          .createNativeQuery("UPDATE " + target[0] + " SET " + target[1] + " = ?1 WHERE " + target[1] + " = ?2")
          .setParameter(1, toMemberId)
          .setParameter(2, fromMemberId)
          .executeUpdate();
    }
  }
}
