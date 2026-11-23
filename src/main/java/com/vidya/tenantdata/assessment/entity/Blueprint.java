package com.vidya.tenantdata.assessment.entity;

import com.vidya.tenantdata.assessment.enums.BlueprintStatus;
import com.vidya.tenantdata.assessment.enums.Board;
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

/** Design of a question paper: sections with question type, marks, count, internal choice and difficulty mix. */
@Entity
@Table(name = "blueprint")
@Getter
@Setter
@NoArgsConstructor
public class Blueprint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "blueprint_code", nullable = false, updatable = false)
    private String blueprintCode;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "academic_class_id", nullable = false)
    private UUID academicClassId;

    @Enumerated(EnumType.STRING)
    @Column(name = "board")
    private Board board;

    @Column(name = "total_marks", nullable = false)
    private BigDecimal totalMarks;

    @Column(name = "duration_minutes")
    private int durationMinutes;

    @Column(name = "instructions", columnDefinition = "text")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BlueprintStatus status = BlueprintStatus.ACTIVE;

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
