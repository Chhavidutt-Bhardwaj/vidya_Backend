package com.vidya.tenantdata.student.entity;

import com.vidya.tenantdata.user.entity.TenantUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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
 * Student operational domain entity in the tenant database.
 *
 * <p>Represents student-specific admission and lifecycle details, referencing
 * the operational {@link TenantUser} identity without polluting it.
 */
@Entity
@Table(name = "student")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "student_code", nullable = false, unique = true, length = 50, updatable = false)
    private String studentCode;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_user_id", nullable = false, unique = true)
    private TenantUser tenantUser;

    @Column(name = "admission_number", nullable = false, unique = true, length = 100)
    private String admissionNumber;

    @Column(name = "admission_date", nullable = false)
    private LocalDate admissionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StudentStatus status = StudentStatus.ACTIVE;

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

    public Student(String studentCode, TenantUser tenantUser, String admissionNumber, LocalDate admissionDate) {
        this.studentCode = studentCode;
        this.tenantUser = tenantUser;
        this.admissionNumber = admissionNumber != null ? admissionNumber.trim() : null;
        this.admissionDate = admissionDate;
        this.status = StudentStatus.ACTIVE;
    }

    public void updateStatus(StudentStatus newStatus) {
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
        if (newStatus == StudentStatus.DELETED) {
            this.deletedAt = Instant.now();
        }
    }

    public boolean isDeleted() {
        return deletedAt != null || status == StudentStatus.DELETED;
    }

    public void softDelete() {
        updateStatus(StudentStatus.DELETED);
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
