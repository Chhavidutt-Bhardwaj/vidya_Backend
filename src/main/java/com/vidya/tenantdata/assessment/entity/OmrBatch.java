package com.vidya.tenantdata.assessment.entity;

import com.vidya.tenantdata.assessment.enums.OmrBatchStatus;
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

import java.time.Instant;
import java.util.UUID;

/** Scanned OMR sheets of one paper, scored against its answer key. */
@Entity
@Table(name = "omr_batch")
@Getter
@Setter
@NoArgsConstructor
public class OmrBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "batch_code", nullable = false, updatable = false)
    private String batchCode;

    @Column(name = "question_paper_id", nullable = false)
    private UUID questionPaperId;

    @Column(name = "exam_subject_config_id")
    private UUID examSubjectConfigId;

    @Column(name = "assessment_id")
    private UUID assessmentId;

    @Column(name = "section_id")
    private UUID sectionId;

    @Column(name = "source")
    private String source;

    @Column(name = "negative_marking")
    private boolean negativeMarking;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OmrBatchStatus status = OmrBatchStatus.EVALUATED;

    @Column(name = "sheets_total")
    private int sheetsTotal;

    @Column(name = "sheets_matched")
    private int sheetsMatched;

    @Column(name = "sheets_error")
    private int sheetsError;

    @Column(name = "posted_at")
    private Instant postedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
