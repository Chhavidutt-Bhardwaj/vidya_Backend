package com.vidya.tenantdata.assessment.moderation;

import com.vidya.tenantdata.assessment.enums.AdjustmentStatus;
import com.vidya.tenantdata.assessment.enums.AdjustmentType;
import com.vidya.tenantdata.assessment.enums.ModerationMethod;
import com.vidya.tenantdata.assessment.enums.ModerationStatus;
import com.vidya.tenantdata.assessment.enums.ReevaluationStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ModerationDtos {

    private ModerationDtos() {
    }

    // ── single adjustments (grace, correction) ──

    public record ProposeAdjustmentRequest(UUID examSubjectConfigId, UUID assessmentId, @NotNull UUID studentId, @NotNull AdjustmentType type,
                                           @NotNull @DecimalMin("-1000") @DecimalMax("1000") BigDecimal delta,
                                           @NotBlank @Size(max = 500) String reason) {
    }

    public record DecisionRequest(@Size(max = 500) String note) {
    }

    public record AdjustmentResponse(UUID id, UUID examSubjectConfigId, UUID assessmentId, UUID studentId, String studentName,
                                     AdjustmentType type, BigDecimal delta, String reason, AdjustmentStatus status, UUID moderationBatchId,
                                     UUID reevaluationRequestId, UUID proposedBy, UUID decidedBy, Instant decidedAt, Instant createdAt) {
    }

    // ── moderation ──

    public record ModerationRequest(@NotNull UUID examSubjectConfigId, @NotNull ModerationMethod method,
                                    @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") BigDecimal value,
                                    @DecimalMin("0") BigDecimal onlyBelow, @Size(max = 500) String reason) {
    }

    public record ModerationLine(UUID studentId, String studentName, BigDecimal before, BigDecimal delta, BigDecimal after,
                                 boolean passedBefore, boolean passedAfter) {
    }

    public record ModerationPreview(UUID examSubjectConfigId, BigDecimal maxMarks, BigDecimal passMarks, int students, int affected,
                                    BigDecimal averageBefore, BigDecimal averageAfter, int passingBefore, int passingAfter,
                                    List<ModerationLine> lines) {
    }

    public record ModerationBatchResponse(UUID id, String batchCode, UUID examSubjectConfigId, ModerationMethod method, BigDecimal value,
                                          BigDecimal onlyBelow, String reason, ModerationStatus status, int studentsAffected,
                                          UUID appliedBy, Instant appliedAt, UUID revertedBy, Instant revertedAt) {
    }

    // ── re-evaluation ──

    public record ReevaluationCreateRequest(@NotNull UUID examSubjectConfigId, @NotNull UUID studentId,
                                            @NotBlank @Size(max = 1000) String reason) {
    }

    public record AssignReviewerRequest(UUID reviewerStaffId) {
    }

    public record ReevaluationDecision(@NotNull ReevaluationStatus outcome, @DecimalMin("0") BigDecimal revisedMarks,
                                       @Size(max = 1000) String note) {
    }

    public record ReevaluationResponse(UUID id, String requestCode, UUID examSubjectConfigId, String subjectName, String examTermName,
                                       UUID studentId, String studentName, String reason, ReevaluationStatus status, BigDecimal originalMarks,
                                       BigDecimal currentMarks, BigDecimal revisedMarks, BigDecimal maxMarks, UUID reviewerStaffId,
                                       String reviewerNote, Instant decidedAt, Instant createdAt) {
    }
}
