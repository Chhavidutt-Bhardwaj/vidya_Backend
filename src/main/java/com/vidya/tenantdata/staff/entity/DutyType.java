package com.vidya.tenantdata.staff.entity;

import com.vidya.tenantdata.staff.enums.DutyCategory;
import com.vidya.tenantdata.staff.enums.DutyScope;
import com.vidya.tenantdata.staff.enums.OrgUnitStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** A kind of responsibility a staff member can hold (HOD, Exam Coordinator, Transport In-charge …). */
@Entity
@Table(name = "duty_type")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DutyType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DutyCategory category = DutyCategory.ADMINISTRATION;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_kind", nullable = false, length = 20)
    private DutyScope scopeKind = DutyScope.SCHOOL;

    @Column(name = "single_holder", nullable = false)
    private boolean singleHolder = true;

    @Column(name = "access_role_code", length = 50)
    private String accessRoleCode;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrgUnitStatus status = OrgUnitStatus.ACTIVE;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version = 0L;

    public DutyType(String code, String name, DutyCategory category, DutyScope scopeKind, boolean singleHolder, String accessRoleCode) {
        this.code = code;
        this.name = name;
        this.category = category;
        this.scopeKind = scopeKind;
        this.singleHolder = singleHolder;
        this.accessRoleCode = accessRoleCode;
    }

    public boolean isActive() {
        return status == OrgUnitStatus.ACTIVE && deletedAt == null;
    }
}
