package com.vidya.tenantdata.assessment.entity;

import com.vidya.tenantdata.assessment.enums.OnlineExamStatus;
import com.vidya.tenantdata.assessment.enums.ResultVisibility;
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

/** A finalised paper taken online by a class or section in a time window. */
@Entity
@Table(name = "online_exam")
@Getter
@Setter
@NoArgsConstructor
public class OnlineExam {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "exam_code", nullable = false, updatable = false)
    private String examCode;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "question_paper_id", nullable = false)
    private UUID questionPaperId;

    @Column(name = "exam_subject_config_id")
    private UUID examSubjectConfigId;

    @Column(name = "assessment_id")
    private UUID assessmentId;

    @Column(name = "academic_class_id", nullable = false)
    private UUID academicClassId;

    @Column(name = "section_id")
    private UUID sectionId;

    @Column(name = "opens_at", nullable = false)
    private Instant opensAt;

    @Column(name = "closes_at", nullable = false)
    private Instant closesAt;

    @Column(name = "duration_minutes")
    private int durationMinutes;

    @Column(name = "shuffle_questions")
    private boolean shuffleQuestions = true;

    @Column(name = "shuffle_options")
    private boolean shuffleOptions = true;

    @Column(name = "negative_marking")
    private boolean negativeMarking;

    @Column(name = "max_attempts")
    private int maxAttempts = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_visibility", nullable = false)
    private ResultVisibility resultVisibility = ResultVisibility.AFTER_PUBLISH;

    @Column(name = "instructions", columnDefinition = "text")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OnlineExamStatus status = OnlineExamStatus.DRAFT;

    @Column(name = "results_published_at")
    private Instant resultsPublishedAt;

    @Column(name = "marks_posted_at")
    private Instant marksPostedAt;

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
