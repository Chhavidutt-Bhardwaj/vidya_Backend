package com.vidya.tenantdata.assignment.dto;

import com.vidya.tenantdata.assignment.enums.ClassTeacherAssignmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Summary DTO for ClassTeacherAssignment listings and queries.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Summary representation of a class teacher assignment")
public class ClassTeacherAssignmentResponse {

    @Schema(description = "Assignment UUID")
    private UUID id;

    @Schema(description = "Stable business assignment code (e.g. CTA-A1B2C3D4)", example = "CTA-A1B2C3D4")
    private String assignmentCode;

    @Schema(description = "Staff (Teacher) UUID")
    private UUID staffId;

    @Schema(description = "Staff Code", example = "STF-A1B2C3D4")
    private String staffCode;

    @Schema(description = "Teacher Full Name", example = "Priya Sharma")
    private String staffName;

    @Schema(description = "Teacher Email", example = "priya.sharma@school.edu.in")
    private String staffEmail;

    @Schema(description = "Academic Year UUID")
    private UUID academicYearId;

    @Schema(description = "Academic Year Code", example = "AY2026-27")
    private String academicYearCode;

    @Schema(description = "Academic Class UUID")
    private UUID academicClassId;

    @Schema(description = "Academic Class Code", example = "CLS-10")
    private String academicClassCode;

    @Schema(description = "Academic Class Name", example = "Class 10")
    private String academicClassName;

    @Schema(description = "Section UUID")
    private UUID sectionId;

    @Schema(description = "Section Code", example = "SEC-10A")
    private String sectionCode;

    @Schema(description = "Section Name", example = "Section A")
    private String sectionName;

    @Schema(description = "Assignment start date", example = "2026-04-01")
    private LocalDate startDate;

    @Schema(description = "Assignment end date", example = "2027-03-31")
    private LocalDate endDate;

    @Schema(description = "Notes about this assignment")
    private String notes;

    @Schema(description = "Assignment lifecycle status", example = "ACTIVE")
    private ClassTeacherAssignmentStatus status;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;
}
