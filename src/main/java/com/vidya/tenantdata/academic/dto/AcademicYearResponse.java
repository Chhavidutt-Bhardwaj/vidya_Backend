package com.vidya.tenantdata.academic.dto;

import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Representation of an academic year")
public class AcademicYearResponse {

    @Schema(description = "Academic year UUID")
    private UUID id;

    @Schema(description = "Academic year code", example = "AY2026-27")
    private String code;

    @Schema(description = "Academic year name", example = "Academic Year 2026-2027")
    private String name;

    @Schema(description = "Start date", example = "2026-04-01")
    private LocalDate startDate;

    @Schema(description = "End date", example = "2027-03-31")
    private LocalDate endDate;

    @Schema(description = "Whether this is the current active operational year")
    private boolean isCurrent;

    @Schema(description = "Status of the academic year", example = "ACTIVE")
    private AcademicYearStatus status;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;

    @Schema(description = "Optimistic locking version")
    private Long version;
}
