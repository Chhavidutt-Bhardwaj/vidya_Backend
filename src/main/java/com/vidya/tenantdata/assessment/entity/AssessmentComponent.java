package com.vidya.tenantdata.assessment.entity;

import com.vidya.tenantdata.assessment.enums.AssessmentType;
import com.vidya.tenantdata.assessment.enums.ComponentAggregation;
import com.vidya.tenantdata.assessment.enums.ComponentSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** Part of a subject's term result with its weight (marks out of the subject total), e.g. Periodic Test 10 or Term Exam 80. */
@Entity
@Table(name = "assessment_component")
@Getter
@Setter
@NoArgsConstructor
public class AssessmentComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "scheme_id", nullable = false)
    private UUID schemeId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_type", nullable = false)
    private AssessmentType assessmentType;

    @Column(name = "exam_term_id")
    private UUID examTermId;

    @Column(name = "weight", nullable = false)
    private BigDecimal weight;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregation", nullable = false)
    private ComponentAggregation aggregation = ComponentAggregation.AVERAGE;

    @Column(name = "best_of")
    private Integer bestOf;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private ComponentSource source = ComponentSource.ASSESSMENTS;

    @Column(name = "pass_required")
    private boolean passRequired;

    @Column(name = "sort_order")
    private int sortOrder;
}
