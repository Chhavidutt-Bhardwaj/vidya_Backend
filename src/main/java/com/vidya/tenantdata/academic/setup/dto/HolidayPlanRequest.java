package com.vidya.tenantdata.academic.setup.dto;

import com.vidya.tenantdata.calendar.enums.HolidayType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Holidays for a whole year in one call. Every row is checked before anything is saved. */
@Schema(description = "Create many holidays of an academic year at once")
public record HolidayPlanRequest(
        @NotEmpty @Size(max = 200) List<@Valid Row> holidays,
        @Schema(description = "Only validate and return the result; save nothing (default false)") Boolean dryRun
) {
    public record Row(
            @NotBlank @Size(max = 100) @Schema(example = "Independence Day") String name,
            @NotNull HolidayType holidayType,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @Size(max = 500) String description
    ) {
    }
}
