package com.vidya.tenantdata.assessment.entity;

import com.vidya.tenantdata.assessment.enums.AttemptStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One student's sitting of an online exam. */
@Entity
@Table(name = "online_exam_attempt")
@Getter
@Setter
@NoArgsConstructor
public class OnlineExamAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "exam_id", nullable = false)
    private UUID examId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "attempt_no")
    private int attemptNo;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "item_order", nullable = false, columnDefinition = "jsonb")
    private List<String> itemOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "option_order", columnDefinition = "jsonb")
    private Map<String, Object> optionOrder;

    @Column(name = "auto_score")
    private BigDecimal autoScore;

    @Column(name = "manual_score")
    private BigDecimal manualScore;

    @Column(name = "total_score")
    private BigDecimal totalScore;

    @Column(name = "max_score", nullable = false)
    private BigDecimal maxScore;

    @Column(name = "pending_manual")
    private int pendingManual;

    @Column(name = "client_info")
    private String clientInfo;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
