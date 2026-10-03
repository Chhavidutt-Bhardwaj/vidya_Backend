package com.vidya.master.audit.repository;

import com.vidya.master.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, UUID entityId, Pageable pageable);

    List<AuditLog> findByPerformedByOrderByCreatedAtDesc(UUID performedBy);

    List<AuditLog> findByCorrelationId(String correlationId);
}
