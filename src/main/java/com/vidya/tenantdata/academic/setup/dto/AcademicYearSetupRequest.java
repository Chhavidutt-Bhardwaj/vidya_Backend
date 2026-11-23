package com.vidya.tenantdata.academic.setup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One-call academic year setup: the year, its terms, working days, and (optionally) the structure
 * copied from an earlier year.
 */
@Schema(description = "Create an academic year with terms, working days and structure copied from a previous year")
public record AcademicYearSetupRequest(
        @NotBlank @Size(max = 50) @Schema(example = "AY2027-28") String code,
        @NotBlank @Size(max = 100) @Schema(example = "Academic Year 2027-2028") String name,
        @NotNull @Schema(example = "2027-04-01") LocalDate startDate,
        @NotNull @Schema(example = "2028-03-31") LocalDate endDate,
        @Schema(description = "Make it the current year right away (default false; usually done after promotion)") Boolean makeCurrent,
        @Size(max = 12) List<@Valid Term> terms,
        @Valid WorkingDays workingDays,
        @Valid CopyFrom copyFrom
) {
    public record Term(
            @NotBlank @Size(max = 100) @Schema(example = "Term 1") String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @DecimalMin("0.00") @DecimalMax("100.00") @Schema(example = "50") BigDecimal weightagePercentage
    ) {
    }

    public record WorkingDays(
            Boolean monday, Boolean tuesday, Boolean wednesday, Boolean thursday, Boolean friday,
            Boolean saturday, Boolean sunday,
            @Pattern(regexp = "ALL|FIRST_THIRD_FIFTH|SECOND_FOURTH") @Schema(example = "SECOND_FOURTH",
                    description = "Which Saturdays are working days when saturday=true") String saturdayPattern
    ) {
    }

    /** What to copy from {@code academicYearId}. Each flag defaults as documented. */
    public record CopyFrom(
            @NotNull UUID academicYearId,
            @Schema(description = "Sections of every class (default true)") Boolean sections,
            @Schema(description = "Curricula and their class subjects (default true)") Boolean curricula,
            @Schema(description = "Subjects per class / section (default true)") Boolean classSubjects,
            @Schema(description = "Term names and dates, shifted to the new year (default true, ignored when terms are given)") Boolean terms,
            @Schema(description = "Working-day settings (default true, ignored when workingDays is given)") Boolean workingDays,
            @Schema(description = "Same class teacher per section (default false)") Boolean classTeachers,
            @Schema(description = "Same subject teachers (default false)") Boolean subjectTeachers
    ) {
    }
}
