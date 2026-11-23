package com.vidya.tenantdata.academic.config.entity;

import com.vidya.tenantdata.academic.config.enums.SyllabusStatus;
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

/** What one class studies in one subject in one academic year. Units and topics hang off it. */
@Entity
@Table(name = "syllabus")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Syllabus {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "syllabus_code", nullable = false, updatable = false, length = 50)
    private String syllabusCode;

    @Column(name = "academic_year_id", nullable = false, updatable = false)
    private UUID academicYearId;

    @Column(name = "academic_class_id", nullable = false, updatable = false)
    private UUID academicClassId;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "reference_books", length = 1000)
    private String referenceBooks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SyllabusStatus status = SyllabusStatus.DRAFT;

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
    private Long version = 0L;

    public Syllabus(String syllabusCode, UUID academicYearId, UUID academicClassId, UUID subjectId, String title) {
        this.syllabusCode = syllabusCode;
        this.academicYearId = academicYearId;
        this.academicClassId = academicClassId;
        this.subjectId = subjectId;
        this.title = title;
    }
}
