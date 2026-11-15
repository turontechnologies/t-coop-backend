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

  /** Set on a request once it has been logged, so {@link AuditTrailInterceptor} doesn't log it twice. */
  public static final String LOGGED_ATTRIBUTE = AuditLogService.class.getName() + ".logged";

  public void log(
      String actorId,
      String actorRole,
      String module,
      String action,
      String resource,
      String status,
      HttpServletRequest request) {
    HttpServletRequest current = request != null ? request : currentRequest();
    String ip = current == null ? null : clientIp(current);
    auditLogRepository.save(
        new AuditLog(actorId, actorRole, module, action, truncate(resource, 500), status, ip));
    if (current != null) current.setAttribute(LOGGED_ATTRIBUTE, Boolean.TRUE);
  }

  private static HttpServletRequest currentRequest() {
    return org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
            instanceof org.springframework.web.context.request.ServletRequestAttributes attributes
        ? attributes.getRequest()
        : null;
  }

  private static String truncate(String value, int max) {
    return value == null || value.length() <= max ? value : value.substring(0, max);
  }

  String clientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      // First entry is the original client when behind a proxy/tunnel.
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
