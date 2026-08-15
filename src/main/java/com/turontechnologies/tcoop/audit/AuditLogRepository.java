package com.turontechnologies.tcoop.audit;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

  List<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
