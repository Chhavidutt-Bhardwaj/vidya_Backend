package com.vidya.tenantdata.academic.structure.dto;

import com.vidya.tenantdata.academic.enums.ClassStage;
import com.vidya.tenantdata.academic.enums.StreamStatus;
import com.vidya.tenantdata.examination.grading.GradingConfigService;
import com.vidya.tenantdata.subject.enums.SubjectType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Requests and responses of the academic structure configuration API. */
public final class StructureDtos {

    private StructureDtos() {
    }

    // ── Streams ──────────────────────────────────────────────────────────────

    public record StreamRequest(
            @NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "code: letters, digits, _ and - only") String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 255) String description,
            Integer sortOrder) {
    }

    public record StreamStatusRequest(@NotNull StreamStatus status) {
    }

    public record StreamResponse(UUID id, String code, String name, String description, int sortOrder, StreamStatus status,
                                 long sections, long curriculumSubjects) {
    }

    // ── Class structure ──────────────────────────────────────────────────────

    /**
     * Full replacement of a class's structure settings (null clears a value).
     *
     * @param stage null = derived from gradeLevel
     */
    public record ClassStructureRequest(ClassStage stage, @Min(-3) @Max(12) Integer gradeLevel, Boolean hasStreams, UUID gradingScaleId) {
    }

    public record SectionStreamsRequest(@NotEmpty List<@Valid SectionStream> sections) {
        public record SectionStream(@NotNull UUID sectionId, UUID streamId) {
        }
    }

    // ── Curriculum grid ──────────────────────────────────────────────────────

    /**
     * Subjects of some classes in one curriculum. Each listed class's subject list is replaced by
     * the rows given for it; classes not listed are not touched.
     */
    public record CurriculumGridRequest(@NotEmpty List<UUID> academicClassIds, @NotNull List<@Valid Row> subjects) {
        public record Row(@NotNull UUID academicClassId, @NotNull UUID subjectId, UUID streamId, Boolean compulsory, Integer displayOrder) {
        }
    }

    public record CurriculumGridResult(int added, int updated, int removed, int unchanged) {
    }

    // ── Overview ─────────────────────────────────────────────────────────────

    public record Overview(UUID academicYearId, String academicYearCode, List<StreamResponse> streams, List<ClassNode> classes,
                           DefaultScale defaultGradingScale, List<Issue> issues) {
    }

    public record DefaultScale(UUID id, String name) {
    }

    public record ClassNode(UUID id, String code, String name, ClassStage stage, Integer gradeLevel, boolean hasStreams,
                            GradingConfigService.Source gradingSource, UUID gradingScaleId, String gradingScaleName,
                            List<SectionNode> sections, List<SubjectNode> subjects) {
    }

    public record SectionNode(UUID id, String code, String name, UUID streamId, String streamCode, Integer capacity, long students) {
    }

    /** @param streamCode null = every section of the class */
    public record SubjectNode(UUID curriculumSubjectId, UUID curriculumId, UUID subjectId, String subjectCode, String name,
                              SubjectType type, boolean compulsory, int displayOrder, UUID streamId, String streamCode) {
    }

    /** @param level ERROR blocks correct results (e.g. no grading scale), WARNING should be fixed */
    public record Issue(String level, String code, UUID academicClassId, String message) {
    }

    // ── Template ─────────────────────────────────────────────────────────────

    /**
     * Standard Indian K–12 structure.
     *
     * @param fromLevel        first class: -3 Pre-Nursery, -2 Nursery, -1 LKG, 0 UKG, 1 Class I (default -2)
     * @param toLevel          last class (default 12)
     * @param streams          stream codes for XI–XII: SCIENCE, COMMERCE, HUMANITIES (default all three; empty = none)
     * @param gradingScales    create presets and give each class the one for its stage (default true)
     * @param subjects         create the common subject catalogue (default true)
     * @param academicYearId   also create a curriculum for this year mapping subjects to classes / streams
     * @param dryRun           report what would be created without saving
     */
    public record TemplateRequest(@Min(-3) @Max(12) Integer fromLevel, @Min(-3) @Max(12) Integer toLevel, List<String> streams,
                                  Boolean gradingScales, Boolean subjects, UUID academicYearId, Boolean dryRun) {
    }

    public record TemplateResult(boolean dryRun, List<String> classesCreated, List<String> classesUpdated, List<String> streamsCreated,
                                 List<String> gradingScalesCreated, List<String> subjectsCreated, String curriculumCreated,
                                 int curriculumSubjects, List<String> notes) {
    }
}
