package com.vidya.tenantdata.assessment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** A student's answer to one question of an attempt and its marks. */
@Entity
@Table(name = "online_exam_response")
@Getter
@Setter
@NoArgsConstructor
public class OnlineExamResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "paper_item_id", nullable = false)
    private UUID paperItemId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer", columnDefinition = "jsonb")
    private Map<String, Object> answer;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "marks_awarded")
    private BigDecimal marksAwarded;

    @Column(name = "needs_manual")
    private boolean needsManual;

    @Column(name = "graded_by_staff_id")
    private UUID gradedByStaffId;

    @Column(name = "graded_at")
    private Instant gradedAt;

    @Column(name = "feedback")
    private String feedback;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
