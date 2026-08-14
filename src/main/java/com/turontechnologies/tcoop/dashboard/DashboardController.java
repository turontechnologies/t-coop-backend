package com.turontechnologies.tcoop.dashboard;

import com.turontechnologies.tcoop.member.MemberRepository;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

  private final MemberRepository memberRepository;
  private final DashboardService dashboardService;

  public DashboardController(MemberRepository memberRepository, DashboardService dashboardService) {
    this.memberRepository = memberRepository;
    this.dashboardService = dashboardService;
  }

  @GetMapping("/api/v1/dashboard/summary")
  public ResponseEntity<?> summary(Authentication authentication) {
    String memberId = (String) authentication.getPrincipal();
    var member = memberRepository.findById(memberId).orElse(null);

    if (member == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Member no longer exists"));
    }

    return ResponseEntity.ok(dashboardService.getSummary(member));
  }
}
