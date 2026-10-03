package com.vidya.tenantdata.student.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Guardian or parent record associated with a student.
 */
@Entity
@Table(name = "student_guardian")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentGuardian {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "guardian_code", nullable = false, unique = true, length = 50, updatable = false)
    private String guardianCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "guardian_name", nullable = false, length = 150)
    private String guardianName;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false, length = 50)
    private GuardianRelationshipType relationshipType;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "occupation", length = 100)
    private String occupation;

    @Column(name = "annual_income", precision = 15, scale = 2)
    private BigDecimal annualIncome;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary = false;

    @Column(name = "is_emergency_contact", nullable = false)
    private boolean isEmergencyContact = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private GuardianStatus status = GuardianStatus.ACTIVE;

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

    public StudentGuardian(String guardianCode,
                           Student student,
                           String guardianName,
                           GuardianRelationshipType relationshipType,
                           String phone,
                           String email,
                           String occupation,
                           BigDecimal annualIncome,
                           String addressLine1,
                           String addressLine2,
                           String city,
                           String state,
                           String postalCode,
                           boolean isPrimary,
                           boolean isEmergencyContact) {
        this.guardianCode = guardianCode;
        this.student = student;
        this.guardianName = guardianName != null ? guardianName.trim() : null;
        this.relationshipType = relationshipType;
        this.phone = phone != null ? phone.trim() : null;
        this.email = email != null ? email.trim() : null;
        this.occupation = occupation != null ? occupation.trim() : null;
        this.annualIncome = annualIncome;
        this.addressLine1 = addressLine1 != null ? addressLine1.trim() : null;
        this.addressLine2 = addressLine2 != null ? addressLine2.trim() : null;
        this.city = city != null ? city.trim() : null;
        this.state = state != null ? state.trim() : null;
        this.postalCode = postalCode != null ? postalCode.trim() : null;
        this.isPrimary = isPrimary;
        this.isEmergencyContact = isEmergencyContact;
        this.status = GuardianStatus.ACTIVE;
    }

    public void updateStatus(GuardianStatus newStatus) {
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
        if (newStatus == GuardianStatus.DELETED) {
            this.deletedAt = Instant.now();
        }
    }

    public boolean isDeleted() {
        return deletedAt != null || status == GuardianStatus.DELETED;
    }

    public void softDelete() {
        updateStatus(GuardianStatus.DELETED);
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
