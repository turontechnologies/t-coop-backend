package com.turontechnologies.tcoop.loan;

import com.turontechnologies.tcoop.member.Member;
import com.turontechnologies.tcoop.member.MemberRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * A co-op's loan committee as an ordered list of approval steps. When the admin hasn't set one up
 * (or everyone on it has left), the co-op admin is the single approver, so a loan never gets stuck.
 */
@Service
public class LoanCommittee {

  /** One approval step: who approves, and what they're called. */
  public record Step(int position, String title, Member member) {}

  private final LoanCommitteeMemberRepository committeeRepository;
  private final MemberRepository memberRepository;

  public LoanCommittee(LoanCommitteeMemberRepository committeeRepository, MemberRepository memberRepository) {
    this.committeeRepository = committeeRepository;
    this.memberRepository = memberRepository;
  }

  public List<Step> stepsOf(String cooperativeId) {
    List<LoanCommitteeMember> seats = committeeRepository.findAllByCooperativeIdOrderByPositionAsc(cooperativeId);
    Map<String, Member> members =
        memberRepository.findAllById(seats.stream().map(LoanCommitteeMember::getMemberId).toList()).stream()
            .filter(m -> "Active".equals(m.getStatus()))
            .collect(Collectors.toMap(Member::getId, Function.identity()));
    List<Step> steps = new ArrayList<>();
    for (LoanCommitteeMember seat : seats) {
      Member member = members.get(seat.getMemberId());
      if (member != null) steps.add(new Step(steps.size() + 1, seat.getTitle(), member));
    }
    if (steps.isEmpty()) {
      memberRepository.findById(cooperativeId).ifPresent(admin -> steps.add(new Step(1, "Admin", admin)));
    }
    return steps;
  }

  /** The step a loan is waiting on (clamped, in case the committee shrank mid-way). */
  public Step currentStep(List<Step> steps, LoanRecord record) {
    if (steps.isEmpty() || !"Awaiting Approval".equals(record.getStatus())) return null;
    int position = record.getApprovalStep() == null ? 1 : record.getApprovalStep();
    return steps.get(Math.max(0, Math.min(position, steps.size()) - 1));
  }

  /** "Awaiting {title}" for a loan in approval, for list views. */
  public String stepTitle(List<Step> steps, LoanRecord record) {
    Step step = currentStep(steps, record);
    return step == null ? null : step.title();
  }
}
