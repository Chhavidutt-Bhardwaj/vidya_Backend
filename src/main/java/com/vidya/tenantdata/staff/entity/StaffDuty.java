package com.vidya.tenantdata.staff.entity;

import com.vidya.tenantdata.academic.enums.ClassStage;
import com.vidya.tenantdata.staff.enums.DutyStatus;
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
import java.time.LocalDate;
import java.util.UUID;

/** One responsibility held by one staff member, for a scope and a period. */
@Entity
@Table(name = "staff_duty")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffDuty {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "duty_type_id", nullable = false)
    private UUID dutyTypeId;

    @Column(name = "academic_year_id")
    private UUID academicYearId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "class_stage", length = 30)
    private ClassStage classStage;

    @Column(name = "academic_class_id")
    private UUID academicClassId;

    @Column(name = "section_id")
    private UUID sectionId;

    @Column(name = "subject_id")
    private UUID subjectId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DutyStatus status = DutyStatus.ACTIVE;

    @Column(length = 255)
    private String notes;

    @Column(name = "role_assignment_id")
    private UUID roleAssignmentId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private Long version = 0L;

    public StaffDuty(UUID staffId, UUID dutyTypeId, LocalDate startDate, String notes) {
        this.staffId = staffId;
        this.dutyTypeId = dutyTypeId;
        this.startDate = startDate;
        this.notes = notes;
    }

    public void end(LocalDate on) {
        this.endDate = on.isBefore(startDate) ? startDate : on;
        this.status = DutyStatus.ENDED;
    }

    /** Same scope as another duty of the same type (for the single-holder rule). */
    public boolean sameScope(StaffDuty o) {
        return java.util.Objects.equals(departmentId, o.departmentId) && classStage == o.classStage
                && java.util.Objects.equals(academicClassId, o.academicClassId) && java.util.Objects.equals(sectionId, o.sectionId)
                && java.util.Objects.equals(subjectId, o.subjectId);
    }
}
