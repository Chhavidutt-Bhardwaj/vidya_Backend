package com.vidya.tenantdata.assessment.competency;

import com.vidya.tenantdata.assessment.enums.AreaGroup;
import com.vidya.tenantdata.assessment.enums.BloomLevel;
import com.vidya.tenantdata.assessment.enums.Board;
import com.vidya.tenantdata.assessment.enums.CompetencyCategory;
import com.vidya.tenantdata.assessment.enums.RecordStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public final class CompetencyDtos {

    private CompetencyDtos() {
    }

    private static final String CODE = "^[A-Za-z0-9_.\\-]+$";

    public record CompetencyRequest(@NotBlank @Size(max = 50) @Pattern(regexp = CODE) String code, @NotBlank @Size(max = 200) String name,
                                    @Size(max = 4000) String description, CompetencyCategory category, UUID subjectId, Board board,
                                    UUID parentId, RecordStatus status) {
    }

    public record CompetencyResponse(UUID id, String code, String name, String description, CompetencyCategory category, UUID subjectId,
                                     String subjectName, Board board, UUID parentId, RecordStatus status, long outcomes) {
    }

    public record OutcomeRequest(@NotBlank @Size(max = 50) @Pattern(regexp = CODE) String code, UUID competencyId, @NotNull UUID subjectId,
                                 UUID academicClassId, @NotBlank @Size(max = 4000) String statement, BloomLevel bloomLevel, Board board,
                                 int sortOrder, RecordStatus status) {
    }

    public record OutcomesRequest(@NotEmpty @Size(max = 500) @Valid List<OutcomeRequest> outcomes) {
    }

    public record OutcomeResponse(UUID id, String code, UUID competencyId, String competencyName, UUID subjectId, String subjectName,
                                  UUID academicClassId, String className, String statement, BloomLevel bloomLevel, Board board, int sortOrder,
                                  RecordStatus status, List<UUID> syllabusTopicIds, long questions) {
    }

    public record TopicsRequest(@NotNull List<UUID> syllabusTopicIds) {
    }

    // ── ratings ──

    public record RatingItem(@NotNull UUID studentId, UUID competencyId, UUID learningOutcomeId, @NotBlank @Size(max = 30) String level,
                             @Size(max = 1000) String evidence) {
    }

    public record RatingsRequest(@NotNull UUID examTermId, @NotEmpty @Size(max = 2000) @Valid List<RatingItem> ratings) {
    }

    public record RatingResponse(UUID id, UUID studentId, String studentName, UUID examTermId, UUID competencyId, String competencyName,
                                 UUID learningOutcomeId, String outcomeCode, String outcomeStatement, String level, String evidence,
                                 UUID ratedByStaffId) {
    }

    // ── co-scholastic ──

    public record CoScholasticItem(@NotNull UUID studentId, @NotNull UUID areaId, @NotBlank @Size(max = 10) String grade,
                                   @Size(max = 500) String remarks) {
    }

    public record CoScholasticRequest(@NotNull UUID examTermId, @NotEmpty @Size(max = 3000) @Valid List<CoScholasticItem> grades) {
    }

    public record CoScholasticResponse(UUID id, UUID studentId, String studentName, UUID examTermId, UUID areaId, String areaCode,
                                       String areaName, AreaGroup areaGroup, String grade, String remarks) {
    }

    public record SaveResult(int saved) {
    }
}
