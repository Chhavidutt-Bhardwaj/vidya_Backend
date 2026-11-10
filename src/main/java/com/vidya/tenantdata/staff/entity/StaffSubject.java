package com.vidya.tenantdata.staff.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A subject a teacher can teach, optionally limited to grade levels (e.g. Mathematics for IX–XII). */
@Entity
@Table(name = "staff_subject")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "from_grade_level")
    private Integer fromGradeLevel;

    @Column(name = "to_grade_level")
    private Integer toGradeLevel;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public StaffSubject(UUID staffId, UUID subjectId, Integer fromGradeLevel, Integer toGradeLevel, boolean primary) {
        this.staffId = staffId;
        this.subjectId = subjectId;
        this.fromGradeLevel = fromGradeLevel;
        this.toGradeLevel = toGradeLevel;
        this.primary = primary;
    }

    /** Covers this grade level? Unknown level or open range = yes. */
    public boolean covers(Integer gradeLevel) {
        return gradeLevel == null || ((fromGradeLevel == null || gradeLevel >= fromGradeLevel) && (toGradeLevel == null || gradeLevel <= toGradeLevel));
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
