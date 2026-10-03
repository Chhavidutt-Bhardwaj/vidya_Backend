package com.vidya.tenantdata.academic.entity;

import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Operational academic year in the tenant schema.
 */
@Entity
@Table(name = "academic_year")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademicYear {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 50, updatable = false)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "is_current", nullable = false)
    private boolean isCurrent = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AcademicYearStatus status = AcademicYearStatus.DRAFT;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public AcademicYear(String code, String name, LocalDate startDate, LocalDate endDate, boolean isCurrent, AcademicYearStatus status) {
        this.code = code != null ? code.trim() : null;
        this.name = name != null ? name.trim() : null;
        this.startDate = startDate;
        this.endDate = endDate;
        this.isCurrent = isCurrent;
        this.status = status != null ? status : AcademicYearStatus.DRAFT;
    }

    public void updateStatus(AcademicYearStatus newStatus) {
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
        if (newStatus == AcademicYearStatus.DELETED) {
            this.deletedAt = Instant.now();
            this.isCurrent = false;
        } else if (newStatus == AcademicYearStatus.CLOSED || newStatus == AcademicYearStatus.CANCELLED) {
            this.isCurrent = false;
        }
    }

    public boolean isDeleted() {
        return deletedAt != null || status == AcademicYearStatus.DELETED;
    }

    public void softDelete() {
        updateStatus(AcademicYearStatus.DELETED);
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
