package com.vidya.tenantdata.assessment.entity;

import com.vidya.tenantdata.assessment.enums.BloomLevel;
import com.vidya.tenantdata.assessment.enums.Board;
import com.vidya.tenantdata.assessment.enums.Difficulty;
import com.vidya.tenantdata.assessment.enums.QuestionStatus;
import com.vidya.tenantdata.assessment.enums.QuestionType;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** A question bank item. Case-based questions are a passage (parent) with child questions. */
@Entity
@Table(name = "question")
@Getter
@Setter
@NoArgsConstructor
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "question_code", nullable = false, updatable = false)
    private String questionCode;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "academic_class_id")
    private UUID academicClassId;

    @Column(name = "syllabus_unit_id")
    private UUID syllabusUnitId;

    @Column(name = "syllabus_topic_id")
    private UUID syllabusTopicId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false)
    private QuestionType questionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false)
    private Difficulty difficulty = Difficulty.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "bloom_level")
    private BloomLevel bloomLevel;

    @Column(name = "marks", nullable = false)
    private BigDecimal marks;

    @Column(name = "negative_marks", nullable = false)
    private BigDecimal negativeMarks = BigDecimal.ZERO;

    @Column(name = "stem", nullable = false, columnDefinition = "text")
    private String stem;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options", columnDefinition = "jsonb")
    private Map<String, Object> options;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer", columnDefinition = "jsonb")
    private Map<String, Object> answer;

    @Column(name = "explanation", columnDefinition = "text")
    private String explanation;

    @Column(name = "expected_seconds")
    private Integer expectedSeconds;

    @Column(name = "language", nullable = false)
    private String language = "en";

    @Enumerated(EnumType.STRING)
    @Column(name = "board")
    private Board board;

    @Column(name = "source")
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private QuestionStatus status = QuestionStatus.DRAFT;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note")
    private String reviewNote;

    @Column(name = "times_used")
    private int timesUsed;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "facility_index")
    private BigDecimal facilityIndex;

    @Column(name = "discrimination_index")
    private BigDecimal discriminationIndex;

    @Column(name = "responses_analysed")
    private int responsesAnalysed;

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
