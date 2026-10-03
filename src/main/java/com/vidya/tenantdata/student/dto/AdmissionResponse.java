package com.vidya.tenantdata.student.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Transactional admission response containing student identity, academic enrollment, and allocated subjects/guardians")
public class AdmissionResponse {

    @Schema(description = "Student identity details")
    private StudentDetailResponse student;

    @Schema(description = "Initial academic enrollment details")
    private EnrollmentResponse enrollment;

    @Schema(description = "List of assigned subjects")
    private List<StudentSubjectEnrollmentResponse> enrolledSubjects;

    @Schema(description = "List of recorded guardians")
    private List<StudentGuardianResponse> guardians;
}
