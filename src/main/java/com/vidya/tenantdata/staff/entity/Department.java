package com.vidya.tenantdata.staff.entity;

import com.vidya.tenantdata.staff.enums.DepartmentCategory;
import com.vidya.tenantdata.staff.enums.OrgUnitStatus;
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
import java.util.UUID;

/** Department such as Science, Languages, Administration or Transport. */
@Entity
@Table(name = "department")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private DepartmentCategory category = DepartmentCategory.ACADEMIC;

    /** Head of department (a staff member). */
    @Column(name = "head_staff_id")
    private UUID headStaffId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public Department(String code, String name, DepartmentCategory category, String description, int sortOrder) {
        this.code = code;
        this.name = name;
        this.category = category;
        this.description = description;
        this.sortOrder = sortOrder;
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrgUnitStatus status = OrgUnitStatus.ACTIVE;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isActive() {
        return deletedAt == null && status == OrgUnitStatus.ACTIVE;
    }
}
