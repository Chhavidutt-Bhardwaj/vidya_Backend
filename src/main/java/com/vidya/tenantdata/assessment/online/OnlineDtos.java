package com.vidya.tenantdata.assessment.online;

import com.vidya.tenantdata.assessment.bank.ScorePosting.PostResult;
import com.vidya.tenantdata.assessment.enums.AttemptStatus;
import com.vidya.tenantdata.assessment.enums.OnlineExamStatus;
import com.vidya.tenantdata.assessment.enums.QuestionType;
import com.vidya.tenantdata.assessment.enums.ResultVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OnlineDtos {

    private OnlineDtos() {
    }

    public record ExamRequest(@NotBlank @Size(max = 200) String title, @NotNull UUID questionPaperId, UUID sectionId,
                              UUID examSubjectConfigId, UUID assessmentId, @NotNull Instant opensAt, @NotNull Instant closesAt,
                              @Min(1) @Max(600) Integer durationMinutes, Boolean shuffleQuestions, Boolean shuffleOptions, boolean negativeMarking,
                              @Min(1) @Max(10) Integer maxAttempts, ResultVisibility resultVisibility, @Size(max = 5000) String instructions) {
    }

    public record ExamView(UUID id, String examCode, String title, UUID questionPaperId, String paperCode, UUID subjectId, String subjectName,
                           UUID academicClassId, UUID sectionId, UUID examSubjectConfigId, UUID assessmentId, Instant opensAt, Instant closesAt,
                           int durationMinutes, boolean shuffleQuestions, boolean shuffleOptions, boolean negativeMarking, int maxAttempts,
                           ResultVisibility resultVisibility, String instructions, OnlineExamStatus status, BigDecimal totalMarks, int questions,
                           Instant resultsPublishedAt, Instant marksPostedAt, Integer myAttempts) {
    }

    public record AnswerEntry(@NotNull UUID itemId, Map<String, Object> answer) {
    }

    public record SaveRequest(@NotEmpty @Size(max = 300) @Valid List<AnswerEntry> answers) {
    }

    public record AttemptItem(UUID itemId, int number, int section, String sectionTitle, Integer sectionAttempt, String passage,
                              QuestionType questionType, BigDecimal marks, BigDecimal negativeMarks, String stem, Map<String, Object> options,
                              Map<String, Object> response) {
    }

    public record ItemResult(UUID itemId, BigDecimal marksAwarded, Boolean correct, boolean awaitingTeacher, Map<String, Object> answerKey,
                             String explanation, String feedback) {
    }

    public record AttemptResult(BigDecimal score, BigDecimal maxScore, int pendingManual, List<ItemResult> items) {
    }

    public record AttemptView(UUID attemptId, UUID examId, String title, int attemptNo, AttemptStatus status, Instant startedAt, Instant deadlineAt,
                              long secondsLeft, String instructions, List<AttemptItem> items, AttemptResult result) {
    }

    public record AttemptSummary(UUID attemptId, UUID studentId, String studentName, int attemptNo, AttemptStatus status, Instant startedAt,
                                 Instant submittedAt, BigDecimal autoScore, BigDecimal manualScore, BigDecimal totalScore, BigDecimal maxScore,
                                 int pendingManual) {
    }

    public record ManualItem(UUID responseId, UUID attemptId, UUID studentId, String studentName, UUID itemId, int position, String stem,
                             Map<String, Object> modelAnswer, BigDecimal maxMarks, Map<String, Object> answer, BigDecimal marksAwarded, String feedback) {
    }

    public record GradeEntry(@NotNull UUID responseId, @NotNull @DecimalMin("0") BigDecimal marks, @Size(max = 1000) String feedback) {
    }

    public record GradeRequest(@NotEmpty @Size(max = 500) @Valid List<GradeEntry> grades) {
    }

    public record Posted(UUID examId, PostResult result, List<String> notes) {
    }
}
