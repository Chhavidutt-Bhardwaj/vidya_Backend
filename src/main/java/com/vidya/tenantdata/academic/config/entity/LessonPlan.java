package com.vidya.tenantdata.academic.config.entity;

import com.vidya.tenantdata.academic.config.enums.LessonPlanStatus;
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

/** A teacher's plan for a lesson of one subject in a class (all the teacher's sections) or one section. */
@Entity
@Table(name = "lesson_plan")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LessonPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "plan_code", nullable = false, updatable = false, length = 50)
    private String planCode;

    @Column(name = "academic_year_id", nullable = false)
    private UUID academicYearId;

    @Column(name = "academic_class_id", nullable = false)
    private UUID academicClassId;

    @Column(name = "section_id")
    private UUID sectionId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "syllabus_unit_id")
    private UUID syllabusUnitId;

    @Column(name = "syllabus_topic_id")
    private UUID syllabusTopicId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    @Column(nullable = false)
    private int periods = 1;

    @Column(columnDefinition = "text")
    private String objectives;

    @Column(name = "teaching_method", columnDefinition = "text")
    private String teachingMethod;

    @Column(columnDefinition = "text")
    private String resources;

    @Column(columnDefinition = "text")
    private String homework;

    @Column(columnDefinition = "text")
    private String assessment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LessonPlanStatus status = LessonPlanStatus.DRAFT;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_remarks", length = 500)
    private String reviewRemarks;

    @Column(name = "delivered_on")
    private LocalDate deliveredOn;

    @Column(columnDefinition = "text")
    private String reflection;

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

    public LessonPlan(String planCode) {
        this.planCode = planCode;
    }
}
