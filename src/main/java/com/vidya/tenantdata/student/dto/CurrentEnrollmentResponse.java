package com.vidya.tenantdata.student.dto;

import com.vidya.tenantdata.student.entity.StudentEnrollmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response for current active academic enrollment of a student")
public class CurrentEnrollmentResponse {

    @Schema(description = "Enrollment UUID")
    private UUID enrollmentId;

    @Schema(description = "Enrollment business code", example = "ENR-A1B2C3D4")
    private String enrollmentCode;

    @Schema(description = "Student summary")
    private StudentResponse student;

    @Schema(description = "Academic year UUID")
    private UUID academicYearId;

    @Schema(description = "Academic year code", example = "AY2026-27")
    private String academicYearCode;

    @Schema(description = "Academic year name", example = "Academic Year 2026-2027")
    private String academicYearName;

    @Schema(description = "Academic class UUID")
    private UUID academicClassId;

    @Schema(description = "Academic class code", example = "CLS-07")
    private String academicClassCode;

    @Schema(description = "Academic class name", example = "Class 7")
    private String academicClassName;

    @Schema(description = "Section UUID")
    private UUID sectionId;

    @Schema(description = "Section code", example = "SEC-A")
    private String sectionCode;

    @Schema(description = "Section name", example = "Section A")
    private String sectionName;

    @Schema(description = "Roll number", example = "15")
    private String rollNumber;

    @Schema(description = "Enrollment status", example = "ACTIVE")
    private StudentEnrollmentStatus enrollmentStatus;

    @Schema(description = "Enrollment date", example = "2026-06-01")
    private LocalDate enrollmentDate;

    @Schema(description = "List of enrolled subjects in current enrollment")
    private List<StudentSubjectEnrollmentResponse> subjects;
}
