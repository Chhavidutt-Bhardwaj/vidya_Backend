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
@Schema(description = "Comprehensive student profile response combining identity, current academic enrollment, and guardians")
public class StudentProfileResponse {

    @Schema(description = "Student identity & profile details")
    private StudentDetailResponse student;

    @Schema(description = "Current active academic enrollment")
    private CurrentEnrollmentResponse currentEnrollment;

    @Schema(description = "List of guardians/parents")
    private List<StudentGuardianResponse> guardians;
}
