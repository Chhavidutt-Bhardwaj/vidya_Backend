package com.vidya.tenantdata.academic.config.entity;

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

/** A unit (chapter) of a syllabus, optionally tied to a term and a planned period. */
@Entity
@Table(name = "syllabus_unit")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SyllabusUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "syllabus_id", nullable = false, updatable = false)
    private UUID syllabusId;

    @Column(name = "unit_no", nullable = false)
    private int unitNo;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "learning_outcomes", columnDefinition = "text")
    private String learningOutcomes;

    @Column(name = "exam_term_id")
    private UUID examTermId;

    @Column(name = "planned_periods")
    private Integer plannedPeriods;

    @Column(name = "planned_start")
    private LocalDate plannedStart;

    @Column(name = "planned_end")
    private LocalDate plannedEnd;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version = 0L;

    public SyllabusUnit(UUID syllabusId) {
        this.syllabusId = syllabusId;
    }
}
