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

/** A topic inside a syllabus unit; coverage is tracked per topic and section. */
@Entity
@Table(name = "syllabus_topic")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SyllabusTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "syllabus_id", nullable = false, updatable = false)
    private UUID syllabusId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "planned_periods")
    private Integer plannedPeriods;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version = 0L;

    public SyllabusTopic(UUID syllabusId, UUID unitId) {
        this.syllabusId = syllabusId;
        this.unitId = unitId;
    }
}
