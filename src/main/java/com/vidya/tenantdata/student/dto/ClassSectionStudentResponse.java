package com.vidya.tenantdata.student.dto;

import com.vidya.tenantdata.student.entity.StudentEnrollmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Class/Section student roster item response")
public class ClassSectionStudentResponse {

    @Schema(description = "Enrollment UUID")
    private UUID enrollmentId;

    @Schema(description = "Enrollment code", example = "ENR-A1B2C3D4")
    private String enrollmentCode;

    @Schema(description = "Student UUID")
    private UUID studentId;

    @Schema(description = "Student business code", example = "STU-A1B2C3D4")
    private String studentCode;

    @Schema(description = "Admission number", example = "ADM-2026-00123")
    private String admissionNumber;

    @Schema(description = "Student first name", example = "Rahul")
    private String firstName;

    @Schema(description = "Student last name", example = "Sharma")
    private String lastName;

    @Schema(description = "Student full name", example = "Rahul Sharma")
    private String fullName;

    @Schema(description = "Gender", example = "MALE")
    private String gender;

    @Schema(description = "Date of birth", example = "2013-05-12")
    private LocalDate dateOfBirth;

    @Schema(description = "Contact phone", example = "+919876543210")
    private String contactPhone;

    @Schema(description = "Class UUID")
    private UUID academicClassId;

    @Schema(description = "Class name", example = "Class 7")
    private String academicClassName;

    @Schema(description = "Section UUID")
    private UUID sectionId;

    @Schema(description = "Section name", example = "Section A")
    private String sectionName;

    @Schema(description = "Roll number in section", example = "15")
    private String rollNumber;

    @Schema(description = "Enrollment status", example = "ACTIVE")
    private StudentEnrollmentStatus enrollmentStatus;

    @Schema(description = "Enrollment date", example = "2026-06-01")
    private LocalDate enrollmentDate;
}
