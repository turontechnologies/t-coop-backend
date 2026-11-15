package com.turontechnologies.tcoop.audit;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

  List<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

  String FILTER =
      " from AuditLog a left join Member m on m.id = a.actorId"
          + " where a.createdAt >= :from and a.createdAt < :to"
          + " and (:module = '' or a.module = :module)"
          + " and (:status = '' or a.status = :status)"
          + " and (:role = '' or a.actorRole = :role)"
          + " and (:coopId = '' or m.cooperativeId = :coopId)"
          + " and (:q = ''"
          + "   or lower(a.module) like :q or lower(a.action) like :q"
          + "   or lower(coalesce(a.resource, '')) like :q or lower(coalesce(a.ipAddress, '')) like :q"
          + "   or lower(coalesce(a.actorId, '')) like :q"
          + "   or lower(concat(coalesce(m.firstName, ''), ' ', coalesce(m.lastName, ''))) like :q)";

  /** Every log entry matching the filters, newest first. {@code q} is a lowercase "%term%". */
  @Query(value = "select a" + FILTER + " order by a.createdAt desc, a.id desc", countQuery = "select count(a)" + FILTER)
  Page<AuditLog> search(
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to,
      @Param("module") String module,
      @Param("status") String status,
      @Param("role") String role,
      @Param("coopId") String coopId,
      @Param("q") String q,
      Pageable pageable);

  long countByCreatedAtGreaterThanEqual(LocalDateTime from);

  long countByCreatedAtGreaterThanEqualAndStatus(LocalDateTime from, String status);

  @Query("select distinct a.module from AuditLog a order by a.module")
  List<String> distinctModules();
}
