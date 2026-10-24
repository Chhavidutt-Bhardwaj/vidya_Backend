package com.vidya.tenantdata.examination.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "grading_scale_tier")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GradingScaleTier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grading_scale_id", nullable = false)
    private GradingScale gradingScale;

    @Column(name = "grade", nullable = false, length = 10)
    private String grade;

    @Column(name = "min_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal minPercentage;

    @Column(name = "max_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxPercentage;

    @Column(name = "grade_point", nullable = false, precision = 4, scale = 2)
    private BigDecimal gradePoint = BigDecimal.ZERO;

    @Column(name = "description", length = 50)
    private String description;

    @Column(name = "is_passing", nullable = false)
    private boolean isPassing = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    public GradingScaleTier(GradingScale gradingScale, String grade, BigDecimal minPercentage,
                            BigDecimal maxPercentage, BigDecimal gradePoint, String description,
                            boolean isPassing, int sortOrder) {
        this.gradingScale = gradingScale;
        this.grade = grade;
        this.minPercentage = minPercentage;
        this.maxPercentage = maxPercentage;
        this.gradePoint = gradePoint != null ? gradePoint : BigDecimal.ZERO;
        this.description = description;
        this.isPassing = isPassing;
        this.sortOrder = sortOrder;
    }
}
