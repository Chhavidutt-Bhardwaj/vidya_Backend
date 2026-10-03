package com.vidya.tenantdata.academic.dto;

import com.vidya.tenantdata.academic.enums.SectionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Representation of a class section")
public class SectionResponse {

    @Schema(description = "Section UUID")
    private UUID id;

    @Schema(description = "Academic year UUID")
    private UUID academicYearId;

    @Schema(description = "Academic year code", example = "AY2026-27")
    private String academicYearCode;

    @Schema(description = "Academic class UUID")
    private UUID academicClassId;

    @Schema(description = "Academic class code", example = "CLASS_5")
    private String academicClassCode;

    @Schema(description = "Academic class name", example = "Class 5")
    private String academicClassName;

    @Schema(description = "Section code", example = "A")
    private String code;

    @Schema(description = "Section name", example = "Section A")
    private String name;

    @Schema(description = "Student capacity", example = "40")
    private Integer capacity;

    @Schema(description = "Current active student enrollment count", example = "35")
    private long activeStudentCount;

    @Schema(description = "Section status", example = "ACTIVE")
    private SectionStatus status;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;

    @Schema(description = "Optimistic locking version")
    private Long version;
}
