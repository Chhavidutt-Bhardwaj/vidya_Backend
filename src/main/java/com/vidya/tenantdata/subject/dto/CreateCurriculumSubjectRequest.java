package com.vidya.tenantdata.subject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Request payload for adding a Subject to a Curriculum at a specific AcademicClass level.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to assign a subject to a curriculum for a specific class")
public class CreateCurriculumSubjectRequest {

    @NotNull(message = "subjectId is required")
    @Schema(description = "UUID of the academic Subject in this tenant's database", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID subjectId;

    @NotNull(message = "academicClassId is required")
    @Schema(description = "UUID of the AcademicClass in this tenant's database", example = "b1ffcd88-8b1a-3de7-aa5c-5aa8ac270b22")
    private UUID academicClassId;

    @Schema(description = "Whether subject is compulsory (true) or elective (false)", example = "true", defaultValue = "true")
    private Boolean isCompulsory;

    @Min(value = 0, message = "displayOrder cannot be negative")
    @Schema(description = "Display order for UI/reporting/timetable", example = "1")
    private Integer displayOrder;
}
