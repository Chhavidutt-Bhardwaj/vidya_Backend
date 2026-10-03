package com.vidya.tenantdata.assignment.dto;

import com.vidya.tenantdata.assignment.enums.TeacherAssignmentStatus;
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
 * Summary DTO for TeacherSubjectAssignment listings and queries.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Representation of a teacher subject assignment")
public class TeacherAssignmentResponse {

    @Schema(description = "Assignment UUID")
    private UUID id;

    @Schema(description = "Stable business assignment code (e.g. TSA-A1B2C3D4)", example = "TSA-A1B2C3D4")
    private String assignmentCode;

    @Schema(description = "Teacher (Staff) UUID")
    private UUID teacherId;

    @Schema(description = "Teacher Staff Code", example = "STF-A1B2C3D4")
    private String teacherStaffCode;

    @Schema(description = "Teacher Full Name", example = "Priya Sharma")
    private String teacherName;

    @Schema(description = "Teacher Email", example = "priya.sharma@school.edu.in")
    private String teacherEmail;

    @Schema(description = "Subject UUID")
    private UUID subjectId;

    @Schema(description = "Subject Code", example = "SUB-A1B2C3D4")
    private String subjectCode;

    @Schema(description = "Subject Name", example = "Mathematics")
    private String subjectName;

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

    @Schema(description = "Section UUID (optional)")
    private UUID sectionId;

    @Schema(description = "Section Code", example = "SEC-10A")
    private String sectionCode;

    @Schema(description = "Section Name", example = "Section A")
    private String sectionName;

    @Schema(description = "Curriculum UUID (optional)")
    private UUID curriculumId;

    @Schema(description = "Curriculum Code", example = "CUR-A1B2C3D4")
    private String curriculumCode;

    @Schema(description = "Whether this is the primary teacher")
    private boolean isPrimary;

    @Schema(description = "Start date", example = "2026-04-01")
    private LocalDate startDate;

    @Schema(description = "End date", example = "2027-03-31")
    private LocalDate endDate;

    @Schema(description = "Assignment lifecycle status", example = "ACTIVE")
    private TeacherAssignmentStatus status;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;
}
