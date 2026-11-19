package com.turontechnologies.tcoop.membership;

import com.turontechnologies.tcoop.cooperative.Cooperative;
import com.turontechnologies.tcoop.cooperative.CooperativeRepository;
import com.turontechnologies.tcoop.notification.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * A member named as someone's guarantor on an application to join says whether they know them.
 * Only while the application is waiting for the admin.
 */
@RestController
public class GuarantorVouchController {

  private final ApplicationGuarantorRepository guarantorRepository;
  private final MembershipApplicationRepository applicationRepository;
  private final CooperativeRepository cooperativeRepository;
  private final NotificationService notificationService;

  public GuarantorVouchController(
      ApplicationGuarantorRepository guarantorRepository,
      MembershipApplicationRepository applicationRepository,
      CooperativeRepository cooperativeRepository,
      NotificationService notificationService) {
    this.guarantorRepository = guarantorRepository;
    this.applicationRepository = applicationRepository;
    this.cooperativeRepository = cooperativeRepository;
    this.notificationService = notificationService;
  }

  public record AnswerRequest(@NotNull(message = "Say yes or no") Boolean knowsThem) {}

  @GetMapping("/api/v1/membership-vouches")
  public ResponseEntity<?> mine(Authentication authentication) {
    String memberId = (String) authentication.getPrincipal();
    List<ApplicationViews.VouchRequest> requests =
        guarantorRepository.findAllByGuarantorMemberIdAndStatus(memberId, "Pending").stream()
            .map(g -> {
              MembershipApplication app = applicationRepository.findById(g.getApplicationId()).orElse(null);
              if (app == null || !MembershipApplication.SUBMITTED.equals(app.getStatus())) return null;
              String coopName = cooperativeRepository.findById(app.getCooperativeId()).map(Cooperative::getName).orElse("");
              return new ApplicationViews.VouchRequest(
                  g.getId(), app.getReference(), app.getFullName(), app.getPhone(), app.getPhotoUrl(), coopName, app.getSubmittedAt());
            })
            .filter(java.util.Objects::nonNull)
            .toList();
    return ResponseEntity.ok(requests);
  }

  @PostMapping("/api/v1/membership-vouches/{id}")
  public ResponseEntity<?> answer(
      Authentication authentication, @PathVariable String id, @Valid @RequestBody AnswerRequest request) {
    String memberId = (String) authentication.getPrincipal();
    ApplicationGuarantor guarantor;
    try {
      guarantor = guarantorRepository.findById(UUID.fromString(id)).orElse(null);
    } catch (IllegalArgumentException e) {
      guarantor = null;
    }
    if (guarantor == null || !memberId.equals(guarantor.getGuarantorMemberId())) {
      return ResponseEntity.status(404).body(Map.of("error", "We couldn't find that request."));
    }
    MembershipApplication app = applicationRepository.findById(guarantor.getApplicationId()).orElse(null);
    if (app == null || !MembershipApplication.SUBMITTED.equals(app.getStatus())) {
      return ResponseEntity.status(409).body(Map.of("error", "This application has already been decided."));
    }
    if (!"Pending".equals(guarantor.getStatus())) {
      return ResponseEntity.ok(Map.of("status", guarantor.getStatus()));
    }
    guarantor.respond(request.knowsThem());
    guarantorRepository.save(guarantor);
    notificationService.notifyCoopAdmin(
        app.getCooperativeId(),
        "MEMBERSHIP_GUARANTOR_RESPONSE",
        request.knowsThem() ? guarantor.getName() + " stands for " + app.getFullName() : guarantor.getName() + " said no",
        request.knowsThem()
            ? guarantor.getName() + " says they know " + app.getFullName() + " and will be their guarantor (" + app.getReference() + ")."
            : guarantor.getName() + " says they don't know " + app.getFullName() + " or won't be their guarantor (" + app.getReference() + ").",
        "/members/applications/" + app.getId());
    return ResponseEntity.ok(Map.of("status", guarantor.getStatus()));
  }
}
