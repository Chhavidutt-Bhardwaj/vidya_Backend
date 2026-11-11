package com.vidya.tenantdata.operations.entity;

import com.vidya.tenantdata.operations.enums.FeeAppliesTo;
import com.vidya.tenantdata.operations.enums.FeeStructureStatus;
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

import java.time.Instant;
import java.util.UUID;

/** What one class pays in one academic year (per fee head), for all its students or only new / existing ones. */
@Entity
@Table(name = "fee_structure")
@Getter
@Setter
@NoArgsConstructor
public class FeeStructure {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "structure_code", nullable = false, updatable = false, length = 50)
    private String structureCode;

    @Column(name = "academic_year_id", nullable = false, updatable = false)
    private UUID academicYearId;

    @Column(name = "academic_class_id", nullable = false, updatable = false)
    private UUID academicClassId;

    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to", nullable = false, updatable = false, length = 20)
    private FeeAppliesTo appliesTo = FeeAppliesTo.ALL;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeeStructureStatus status = FeeStructureStatus.DRAFT;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version = 0L;
}
