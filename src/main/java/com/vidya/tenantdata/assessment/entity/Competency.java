package com.vidya.tenantdata.assessment.entity;

import com.vidya.tenantdata.assessment.enums.Board;
import com.vidya.tenantdata.assessment.enums.CompetencyCategory;
import com.vidya.tenantdata.assessment.enums.RecordStatus;
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

/** A subject competency or cross-cutting skill; learning outcomes sit under it. */
@Entity
@Table(name = "competency")
@Getter
@Setter
@NoArgsConstructor
public class Competency {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private CompetencyCategory category = CompetencyCategory.SUBJECT;

    @Column(name = "subject_id")
    private UUID subjectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "board")
    private Board board;

    @Column(name = "parent_id")
    private UUID parentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RecordStatus status = RecordStatus.ACTIVE;

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
