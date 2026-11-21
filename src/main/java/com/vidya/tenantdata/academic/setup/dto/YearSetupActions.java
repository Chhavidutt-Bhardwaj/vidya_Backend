package com.vidya.tenantdata.academic.setup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request / response types of the bulk setup actions on an academic year. */
public final class YearSetupActions {

    private YearSetupActions() {
    }

    @Schema(description = "Create the same sections (A, B, …) for many classes")
    public record BulkSectionsRequest(
            @Schema(description = "Classes to add sections to; empty = every active class") List<UUID> academicClassIds,
            @NotEmpty @Size(max = 26) List<@Pattern(regexp = "[A-Za-z0-9-]{1,10}") String> sectionCodes,
            @Min(1) Integer capacity
    ) {
    }

    @Schema(description = "Turn the year's curriculum into class subjects")
    public record FromCurriculumRequest(
            @Schema(description = "One curriculum; empty = every DRAFT/ACTIVE curriculum of the year") UUID curriculumId
    ) {
    }

    @Schema(description = "Assign class teachers to many sections at once")
    public record ClassTeachersRequest(@NotEmpty @Size(max = 500) List<@Valid Row> assignments) {
        public record Row(@NotNull UUID sectionId, @NotNull UUID staffId) {
        }
    }

    @Schema(description = "Start the academic year")
    public record StartYearRequest(
            @Schema(description = "Make it the current year (default true)") Boolean makeCurrent,
            @Schema(description = "Timetable to activate; default: the year's only PUBLISHED timetable") UUID timetableId,
            @Schema(description = "Start even if REQUIRED setup steps are missing (default false)") Boolean force
    ) {
    }

    /** @param created rows created; @param skipped rows that already existed */
    public record BulkResult(int created, int skipped, Map<String, Object> details) {
    }

    public record StartYearResponse(SetupStatusResponse status, UUID activatedTimetableId, List<String> warnings) {
    }
}
