package com.vidya.tenantdata.academic.dto;

import com.vidya.tenantdata.academic.enums.StudentAssignmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Representation of a student academic assignment / placement")
public class StudentAcademicAssignmentResponse {

    @Schema(description = "Assignment UUID")
    private UUID id;

    @Schema(description = "Student UUID")
    private UUID studentId;

    @Schema(description = "Student code", example = "STU-ABC12345")
    private String studentCode;

    @Schema(description = "Student admission number", example = "ADM-2026-0042")
    private String admissionNumber;

    @Schema(description = "Student full name from operational profile", example = "Aarav Sharma")
    private String studentName;

    @Schema(description = "Academic year summary")
    private AcademicYearSummary academicYear;

    @Schema(description = "Academic class summary")
    private AcademicClassSummary academicClass;

    @Schema(description = "Section summary")
    private SectionSummary section;

    @Schema(description = "Roll number in the section", example = "12")
    private String rollNumber;

    @Schema(description = "Assignment status", example = "ACTIVE")
    private StudentAssignmentStatus status;

    @Schema(description = "Placement start date", example = "2026-04-01")
    private LocalDate startDate;

    @Schema(description = "Placement end date (null for active)", example = "null")
    private LocalDate endDate;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;

    @Schema(description = "Optimistic locking version")
    private Long version;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Academic year summary")
    public static class AcademicYearSummary {
        private UUID id;
        private String code;
        private String name;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Academic class summary")
    public static class AcademicClassSummary {
        private UUID id;
        private String code;
        private String name;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Section summary")
    public static class SectionSummary {
        private UUID id;
        private String code;
        private String name;
    }
}
