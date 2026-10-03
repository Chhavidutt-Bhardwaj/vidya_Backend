package com.vidya.tenantdata.student.dto;

import com.vidya.tenantdata.student.entity.StudentSubjectEnrollmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update subject enrollment status or effective dates")
public class UpdateStudentSubjectEnrollmentRequest {

    @Schema(description = "Subject enrollment status (DROPPED, EXEMPTED, COMPLETED, ACTIVE)", example = "DROPPED")
    private StudentSubjectEnrollmentStatus status;

    @Schema(description = "Effective end date", example = "2026-10-15")
    private LocalDate effectiveTo;

    @Size(max = 1000, message = "Remarks cannot exceed 1000 characters")
    @Schema(description = "Remarks")
    private String remarks;
}
