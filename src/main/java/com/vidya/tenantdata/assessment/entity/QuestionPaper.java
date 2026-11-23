package com.vidya.tenantdata.assessment.entity;

import com.vidya.tenantdata.assessment.enums.PaperStatus;
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
import java.util.UUID;

/** A question paper, generated from a blueprint or assembled by hand. Sets B, C ... point to set A. */
@Entity
@Table(name = "question_paper")
@Getter
@Setter
@NoArgsConstructor
public class QuestionPaper {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "paper_code", nullable = false, updatable = false)
    private String paperCode;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "academic_class_id", nullable = false)
    private UUID academicClassId;

    @Column(name = "blueprint_id")
    private UUID blueprintId;

    @Column(name = "exam_subject_config_id")
    private UUID examSubjectConfigId;

    @Column(name = "assessment_id")
    private UUID assessmentId;

    @Column(name = "set_label", nullable = false)
    private String setLabel = "A";

    @Column(name = "parent_paper_id")
    private UUID parentPaperId;

    @Column(name = "total_marks", nullable = false)
    private BigDecimal totalMarks = BigDecimal.ZERO;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "instructions", columnDefinition = "text")
    private String instructions;

    @Column(name = "generation_seed")
    private Long generationSeed;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaperStatus status = PaperStatus.DRAFT;

    @Column(name = "finalised_at")
    private Instant finalisedAt;

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
