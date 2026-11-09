package com.vidya.tenantdata.academic.setup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Year-end promotion from the year in the path to {@code targetAcademicYearId}.
 * By default every active student moves to the next class (by class sort order) and into the
 * section with the same code; students of the last class graduate.
 */
@Schema(description = "Promote students of an academic year into the next one")
public record PromotionRequest(
        @NotNull UUID targetAcademicYearId,
        @Schema(description = "Date the old enrollments end (default: last day of the old year)") LocalDate promotionDate,
        @Schema(description = "Keep roll numbers when free in the new section (default false)") Boolean keepRollNumbers,
        @Schema(description = "Enroll students in the compulsory subjects of their new class (default true)") Boolean autoAssignSubjects,
        @Schema(description = "Make the target year current after promotion (default false)") Boolean makeTargetCurrent,
        @Schema(description = "Close the source year after promotion (default false)") Boolean closeSourceYear,
        @Size(max = 200) List<@Valid ClassMapping> classMappings,
        @Size(max = 5000) List<@Valid StudentOverride> overrides
) {
    /** Override the default next class. {@code toAcademicClassId = null} means students of that class graduate. */
    public record ClassMapping(@NotNull UUID fromAcademicClassId, UUID toAcademicClassId) {
    }

    /** Per-student decision, e.g. detained (repeats the class), left the school, or a specific section. */
    public record StudentOverride(
            @NotNull UUID studentId,
            @NotNull PromotionAction action,
            UUID targetAcademicClassId,
            UUID targetSectionId,
            @Size(max = 255) String remarks
    ) {
    }

    public enum PromotionAction {
        /** Next class (default). */
        PROMOTE,
        /** Same class again in the new year. */
        DETAIN,
        /** Finished school: old enrollment COMPLETED, student GRADUATED, no new enrollment. */
        GRADUATE,
        /** Leaving the school: old enrollment WITHDRAWN, student WITHDRAWN. */
        LEAVE,
        /** Do nothing for this student now. */
        SKIP
    }
}
