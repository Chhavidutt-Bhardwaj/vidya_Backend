package com.vidya.tenantdata.subject.dto;

import com.vidya.tenantdata.subject.enums.SubjectType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Representation of a Subject mapping within a Curriculum.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Representation of a subject assigned to a curriculum")
public class CurriculumSubjectResponse {

    @Schema(description = "CurriculumSubject assignment UUID")
    private UUID id;

    @Schema(description = "Curriculum UUID")
    private UUID curriculumId;

    @Schema(description = "Curriculum business code", example = "CUR-A1B2C3D4")
    private String curriculumCode;

    @Schema(description = "Subject UUID")
    private UUID subjectId;

    @Schema(description = "Subject business code", example = "SUB-A1B2C3D4")
    private String subjectCode;

    @Schema(description = "Subject name", example = "Mathematics")
    private String subjectName;

    @Schema(description = "Subject short abbreviation", example = "MATH")
    private String subjectShortName;

    @Schema(description = "Subject classification", example = "CORE")
    private SubjectType subjectType;

    @Schema(description = "Academic Class UUID")
    private UUID academicClassId;

    @Schema(description = "Academic Class business code", example = "CLS-10")
    private String academicClassCode;

    @Schema(description = "Academic Class name", example = "Class 10")
    private String academicClassName;

    @Schema(description = "Whether subject is compulsory or elective for this class level", example = "true")
    private boolean isCompulsory;

    @Schema(description = "Display order for reporting/timetable", example = "1")
    private Integer displayOrder;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;
}
