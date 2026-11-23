package com.vidya.tenantdata.assessment.entity;

import com.vidya.tenantdata.assessment.enums.AssessmentMode;
import com.vidya.tenantdata.assessment.enums.AssessmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A formative or summative assessment (class test, project, quiz, oral ...) recorded under a scheme component. */
@Entity
@Table(name = "assessment")
@Getter
@Setter
@NoArgsConstructor
public class Assessment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "assessment_code", nullable = false, updatable = false)
    private String assessmentCode;

    @Column(name = "component_id", nullable = false)
    private UUID componentId;

    @Column(name = "exam_term_id", nullable = false)
    private UUID examTermId;

    @Column(name = "academic_year_id", nullable = false)
    private UUID academicYearId;

    @Column(name = "academic_class_id", nullable = false)
    private UUID academicClassId;

    @Column(name = "section_id")
    private UUID sectionId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "assessment_date", nullable = false)
    private LocalDate assessmentDate;

    @Column(name = "max_marks", nullable = false)
    private BigDecimal maxMarks;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false)
    private AssessmentMode mode = AssessmentMode.OFFLINE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AssessmentStatus status = AssessmentStatus.OPEN;

    @Column(name = "deleted_at")
    private Instant deletedAt;

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
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
