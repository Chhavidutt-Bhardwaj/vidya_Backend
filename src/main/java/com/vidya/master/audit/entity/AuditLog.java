package com.vidya.master.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an immutable platform-wide audit log entry.
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "action", nullable = false, length = 30)
    private String action;

    @Column(name = "old_value", columnDefinition = "jsonb")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "jsonb")
    private String newValue;

    @Column(name = "performed_by")
    private UUID performedBy;

    @Column(name = "performed_by_type", nullable = false, length = 30)
    private String performedByType = "PLATFORM_USER";

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AuditLog(String entityType,
                    UUID entityId,
                    String action,
                    String oldValue,
                    String newValue,
                    UUID performedBy,
                    String performedByType,
                    String correlationId,
                    String ipAddress) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.performedBy = performedBy;
        this.performedByType = performedByType != null ? performedByType : "PLATFORM_USER";
        this.correlationId = correlationId;
        this.ipAddress = ipAddress;
        this.createdAt = Instant.now();
    }
}
