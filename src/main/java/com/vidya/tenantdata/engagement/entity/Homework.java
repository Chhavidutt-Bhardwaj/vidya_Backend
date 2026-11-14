package com.vidya.tenantdata.engagement.entity;

import com.vidya.tenantdata.engagement.enums.HomeworkStatus;
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

/** Work a subject teacher sets for a class (every section) or one section, due on a date. */
@Entity
@Table(name = "homework")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Homework {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "homework_code", nullable = false, updatable = false, length = 50)
    private String homeworkCode;

    @Column(name = "academic_year_id", nullable = false)
    private UUID academicYearId;

    @Column(name = "academic_class_id", nullable = false)
    private UUID academicClassId;

    /** Null = every section of the class. */
    @Column(name = "section_id")
    private UUID sectionId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "assigned_on", nullable = false)
    private LocalDate assignedOn;

    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HomeworkStatus status = HomeworkStatus.PUBLISHED;

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

    public Homework(String homeworkCode) {
        this.homeworkCode = homeworkCode;
    }
}
