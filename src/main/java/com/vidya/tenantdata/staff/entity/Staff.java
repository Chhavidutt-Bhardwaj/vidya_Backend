package com.vidya.tenantdata.staff.entity;

import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.enums.StaffType;
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
 * Staff operational domain entity in the tenant database.
 *
 * <p>Represents school employment/staff-specific data, referencing the operational
 * {@link TenantUser} identity without polluting it with employment details.
 *
 * <p>The {@code staffCode} is the stable Vidya/domain identifier (e.g. {@code STF-8A92K3LM}).
 * The {@code employeeCode} is the school's own employee identifier (e.g. {@code EMP-1024}).
 */
@Entity
@Table(name = "staff")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "staff_code", nullable = false, unique = true, length = 50, updatable = false)
    private String staffCode;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_user_id", nullable = false, unique = true)
    private TenantUser tenantUser;

    /** School's own employee identifier (e.g. EMP-1024). Optional and school-defined. */
    @Column(name = "employee_code", length = 100)
    private String employeeCode;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_status", nullable = false, length = 30)
    private EmploymentStatus employmentStatus = EmploymentStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "staff_type", nullable = false, length = 30)
    private StaffType staffType;

    @Column(name = "designation", length = 150)
    private String designation;

    @Column(name = "department", length = 150)
    private String department;

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

    public Staff(String staffCode, TenantUser tenantUser, String employeeCode,
                 LocalDate joiningDate, StaffType staffType, String designation, String department) {
        this.staffCode = staffCode;
        this.tenantUser = tenantUser;
        this.employeeCode = employeeCode != null ? employeeCode.trim() : null;
        this.joiningDate = joiningDate;
        this.staffType = staffType;
        this.designation = designation != null ? designation.trim() : null;
        this.department = department != null ? department.trim() : null;
        this.employmentStatus = EmploymentStatus.ACTIVE;
    }

    public void updateStatus(EmploymentStatus newStatus) {
        this.employmentStatus.validateTransitionTo(newStatus);
        this.employmentStatus = newStatus;
        if (newStatus == EmploymentStatus.DELETED) {
            this.deletedAt = Instant.now();
        }
    }

    public boolean isDeleted() {
        return deletedAt != null || employmentStatus == EmploymentStatus.DELETED;
    }

    public void softDelete() {
        updateStatus(EmploymentStatus.DELETED);
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
