package com.vidya.tenantdata.assessment.bank;

import com.vidya.tenantdata.assessment.enums.BloomLevel;
import com.vidya.tenantdata.assessment.enums.BlueprintStatus;
import com.vidya.tenantdata.assessment.enums.Board;
import com.vidya.tenantdata.assessment.enums.Difficulty;
import com.vidya.tenantdata.assessment.enums.PaperStatus;
import com.vidya.tenantdata.assessment.enums.QuestionStatus;
import com.vidya.tenantdata.assessment.enums.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
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

public final class BankDtos {

    private BankDtos() {
    }

    // ── questions ──

    /** {@code children}: the questions of a CASE_BASED passage (one level only). See {@link QuestionFormats} for options / answer. */
    public record QuestionRequest(@NotNull UUID subjectId, UUID academicClassId, UUID syllabusUnitId, UUID syllabusTopicId,
                                  @NotNull QuestionType questionType, Difficulty difficulty, BloomLevel bloomLevel,
                                  @DecimalMin("0") @DecimalMax("100") BigDecimal marks, @DecimalMin("0") @DecimalMax("100") BigDecimal negativeMarks,
                                  @NotBlank @Size(max = 20000) String stem, Map<String, Object> options, Map<String, Object> answer,
                                  @Size(max = 20000) String explanation, @Min(5) @Max(36000) Integer expectedSeconds,
                                  @Size(max = 10) String language, Board board, @Size(max = 200) String source, List<UUID> outcomeIds,
                                  @Size(max = 20) @Valid List<QuestionRequest> children) {
    }

    public record QuestionsRequest(@NotEmpty @Size(max = 200) @Valid List<QuestionRequest> questions) {
    }

    public record ReviewRequest(boolean approve, @Size(max = 500) String note) {
    }

    public record QuestionResponse(UUID id, String questionCode, UUID subjectId, String subjectName, UUID academicClassId, String className,
                                   UUID syllabusUnitId, UUID syllabusTopicId, UUID parentId, QuestionType questionType, Difficulty difficulty,
                                   BloomLevel bloomLevel, BigDecimal marks, BigDecimal negativeMarks, String stem, Map<String, Object> options,
                                   Map<String, Object> answer, String explanation, Integer expectedSeconds, String language, Board board,
                                   String source, QuestionStatus status, UUID reviewedBy, Instant reviewedAt, String reviewNote, int timesUsed,
                                   Instant lastUsedAt, BigDecimal facilityIndex, BigDecimal discriminationIndex, int responsesAnalysed,
                                   List<UUID> outcomeIds, List<QuestionResponse> children, UUID createdBy, Instant updatedAt) {
    }

    public record PoolCount(QuestionType questionType, Difficulty difficulty, long approved) {
    }

    // ── blueprints ──

    public record BlueprintSectionRequest(@NotBlank @Size(max = 100) String title, @Size(max = 500) String instructions, QuestionType questionType,
                                          @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") BigDecimal marksPerQuestion,
                                          @Min(1) @Max(200) int questionCount, @Min(1) @Max(200) Integer attemptCount,
                                          @Min(0) int easyCount, @Min(0) int mediumCount, @Min(0) int hardCount,
                                          List<BloomLevel> bloomLevels, List<UUID> unitIds, List<UUID> outcomeIds) {
    }

    public record BlueprintRequest(@NotBlank @Size(max = 200) String name, @NotNull UUID subjectId, @NotNull UUID academicClassId, Board board,
                                   @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000") BigDecimal totalMarks,
                                   @Min(5) @Max(600) int durationMinutes, @Size(max = 5000) String instructions,
                                   @NotEmpty @Size(max = 20) @Valid List<BlueprintSectionRequest> sections) {
    }

    public record BlueprintSectionResponse(UUID id, int position, String title, String instructions, QuestionType questionType,
                                           BigDecimal marksPerQuestion, int questionCount, Integer attemptCount, int easyCount, int mediumCount,
                                           int hardCount, List<BloomLevel> bloomLevels, List<UUID> unitIds, List<UUID> outcomeIds,
                                           BigDecimal sectionMarks) {
    }

    public record BlueprintResponse(UUID id, String blueprintCode, String name, UUID subjectId, String subjectName, UUID academicClassId,
                                    String className, Board board, BigDecimal totalMarks, int durationMinutes, String instructions,
                                    BlueprintStatus status, List<BlueprintSectionResponse> sections, Instant updatedAt) {
    }

    public record Shortfall(int sectionPosition, String sectionTitle, String difficulty, int wanted, int available, String message) {
    }

    public record BlueprintCheck(UUID blueprintId, boolean ready, int poolSize, List<Shortfall> shortfalls) {
    }

    // ── papers ──

    public record GenerateRequest(@Size(max = 200) String title, @Min(1) @Max(6) Integer sets, Long seed,
                                  @Min(0) @Max(1095) Integer avoidUsedWithinDays, UUID examSubjectConfigId, UUID assessmentId) {
    }

    public record ManualSection(@NotBlank @Size(max = 100) String title, @Min(1) Integer attemptCount,
                                @DecimalMin(value = "0", inclusive = false) BigDecimal marksPerQuestion, @NotEmpty List<UUID> questionIds) {
    }

    public record ManualPaperRequest(@NotBlank @Size(max = 200) String title, @NotNull UUID subjectId, @NotNull UUID academicClassId,
                                     @Min(5) @Max(600) Integer durationMinutes, @Size(max = 5000) String instructions,
                                     UUID examSubjectConfigId, UUID assessmentId, @NotEmpty @Size(max = 20) @Valid List<ManualSection> sections) {
    }

    public record ReplaceItemRequest(UUID questionId) {
    }

    public record PaperItemView(UUID itemId, int position, UUID parentItemId, UUID questionId, String questionCode, QuestionType questionType,
                                Difficulty difficulty, BloomLevel bloomLevel, BigDecimal marks, BigDecimal negativeMarks, String stem,
                                Map<String, Object> options, Map<String, Object> answer, String explanation, Integer omrNumber) {
    }

    public record PaperSectionView(int position, String title, Integer attemptCount, BigDecimal marks, List<PaperItemView> items) {
    }

    public record PaperResponse(UUID id, String paperCode, String title, UUID subjectId, String subjectName, UUID academicClassId, String className,
                                UUID blueprintId, UUID examSubjectConfigId, UUID assessmentId, String setLabel, UUID parentPaperId,
                                BigDecimal totalMarks, Integer durationMinutes, String instructions, Long generationSeed, PaperStatus status,
                                Instant finalisedAt, List<PaperSectionView> sections, List<SetRef> otherSets, List<String> notes) {
    }

    public record SetRef(UUID id, String setLabel, String paperCode) {
    }

    public record KeyEntry(int omrNumber, int position, QuestionType questionType, List<String> choiceKeys, Map<String, Object> answer,
                           BigDecimal marks) {
    }

    public record AnswerKey(UUID paperId, String setLabel, List<KeyEntry> entries) {
    }
}
