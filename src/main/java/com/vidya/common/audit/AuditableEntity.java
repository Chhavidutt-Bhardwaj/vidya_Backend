package com.vidya.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Base entity providing standard audit fields for Master DB entities.
 *
 * <p>All master-tier entities that require audit history should extend this class.
 * The {@code @EntityListeners(AuditingEntityListener.class)} annotation enables
 * automatic population of created_at, updated_at, created_by, updated_by fields.
 *
 * <p>Spring Data JPA auditing must be enabled via {@code @EnableJpaAuditing}
 * in the DataSource configuration class, with an {@code AuditorAware<UUID>} bean
 * that provides the current authenticated user's UUID.
 *
 * <p>Timestamps are stored as UTC {@code Instant}. Do not use {@code LocalDateTime}
 * for persisted timestamps — timezone ambiguity causes data integrity issues
 * across environments.
 *
 * <p>Optimistic locking via {@code @Version} prevents lost-update anomalies
 * in concurrent write scenarios.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
