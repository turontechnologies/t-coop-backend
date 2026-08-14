package com.turontechnologies.tcoop.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/** Mirrors the frontend's existing audit-log feature (logActivity) server-side. */
@Service
public class AuditLogService {

  private final AuditLogRepository auditLogRepository;

  public AuditLogService(AuditLogRepository auditLogRepository) {
    this.auditLogRepository = auditLogRepository;
  }

  public void log(
      String actorId,
      String actorRole,
      String module,
      String action,
      String resource,
      String status,
      HttpServletRequest request) {
    String ip = request == null ? null : clientIp(request);
    auditLogRepository.save(
        new AuditLog(actorId, actorRole, module, action, resource, status, ip));
  }

  private String clientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      // First entry is the original client when behind a proxy/tunnel.
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
