package com.vidya.tenantdata.subject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for updating a CurriculumSubject's properties.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to update curriculum subject properties")
public class UpdateCurriculumSubjectRequest {

    @Schema(description = "Whether subject is compulsory (true) or elective (false)", example = "false")
    private Boolean isCompulsory;

    @Min(value = 0, message = "displayOrder cannot be negative")
    @Schema(description = "Display order for UI/reporting/timetable", example = "3")
    private Integer displayOrder;
}
