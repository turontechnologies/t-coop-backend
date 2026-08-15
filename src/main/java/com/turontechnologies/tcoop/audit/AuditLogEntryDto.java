package com.turontechnologies.tcoop.audit;

/** Matches the frontend's AuditLogEntry shape exactly (src/lib/audit-log-data.ts). */
public record AuditLogEntryDto(
    String id,
    String date,
    String activityBy,
    String role,
    String module,
    String action,
    String resource,
    String status,
    String location,
    String ipAddress) {}
