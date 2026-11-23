package com.vidya.tenantdata.assessment.entity;

import com.vidya.tenantdata.assessment.enums.Board;
import com.vidya.tenantdata.assessment.enums.MarkRounding;
import com.vidya.tenantdata.assessment.enums.SchemeStatus;
import com.vidya.tenantdata.assessment.enums.TermAggregation;
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

/** How a group of classes is assessed in a year: board, components, pass / grace / compartment rules and grade scales. */
@Entity
@Table(name = "assessment_scheme")
@Getter
@Setter
@NoArgsConstructor
public class AssessmentScheme {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "scheme_code", nullable = false)
    private String schemeCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "board", nullable = false)
    private Board board;

    @Column(name = "academic_year_id", nullable = false)
    private UUID academicYearId;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "preset_key")
    private String presetKey;

    @Column(name = "scholastic_grading_scale_id")
    private UUID scholasticGradingScaleId;

    @Column(name = "co_scholastic_grades", nullable = false)
    private String coScholasticGrades = "A,B,C";

    @Column(name = "competency_levels", nullable = false)
    private String competencyLevels = "BEGINNING,DEVELOPING,PROFICIENT,ADVANCED";

    @Column(name = "subject_pass_percent", nullable = false)
    private BigDecimal subjectPassPercent = new BigDecimal("33");

    @Column(name = "overall_pass_percent")
    private BigDecimal overallPassPercent;

    @Column(name = "separate_component_pass")
    private boolean separateComponentPass;

    @Enumerated(EnumType.STRING)
    @Column(name = "rounding_mode", nullable = false)
    private MarkRounding roundingMode = MarkRounding.HALF_UP;

    @Column(name = "rounding_scale")
    private int roundingScale;

    @Column(name = "best_of_subjects")
    private Integer bestOfSubjects;

    @Enumerated(EnumType.STRING)
    @Column(name = "term_aggregation", nullable = false)
    private TermAggregation termAggregation = TermAggregation.WEIGHTED;

    @Column(name = "grace_max_per_subject", nullable = false)
    private BigDecimal graceMaxPerSubject = BigDecimal.ZERO;

    @Column(name = "grace_max_total", nullable = false)
    private BigDecimal graceMaxTotal = BigDecimal.ZERO;

    @Column(name = "grace_max_subjects")
    private int graceMaxSubjects;

    @Column(name = "compartment_max_subjects")
    private int compartmentMaxSubjects;

    @Column(name = "reevaluation_window_days")
    private int reevaluationWindowDays = 7;

    @Column(name = "rank_enabled")
    private boolean rankEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SchemeStatus status = SchemeStatus.DRAFT;

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
