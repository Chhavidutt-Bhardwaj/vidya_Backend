package com.vidya.tenantdata.assessment.formative;

import com.vidya.tenantdata.assessment.enums.AssessmentMode;
import com.vidya.tenantdata.assessment.enums.AssessmentStatus;
import com.vidya.tenantdata.assessment.enums.AssessmentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AssessmentDtos {

    private AssessmentDtos() {
    }

    public record CreateAssessmentRequest(@NotNull UUID componentId, @NotNull UUID examTermId, @NotNull UUID academicClassId, UUID sectionId,
                                          @NotNull UUID subjectId, @NotBlank @Size(max = 200) String title, @NotNull LocalDate assessmentDate,
                                          @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000") BigDecimal maxMarks,
                                          AssessmentMode mode) {
    }

    public record UpdateAssessmentRequest(@Size(max = 200) String title, LocalDate assessmentDate,
                                          @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000") BigDecimal maxMarks,
                                          AssessmentMode mode) {
    }

    public record ScoreItem(@NotNull UUID studentId, @DecimalMin("0") BigDecimal marks, boolean absent, boolean exempt,
                            @Size(max = 255) String remarks) {
    }

    public record ScoresRequest(@NotEmpty @Size(max = 500) @Valid List<ScoreItem> scores) {
    }

    public record ScoreResponse(UUID studentId, String studentName, String rollNumber, BigDecimal marks, BigDecimal adjustedMarks,
                                boolean absent, boolean exempt) {
    }

    public record AssessmentResponse(UUID id, String assessmentCode, UUID componentId, String componentCode, String componentName,
                                     AssessmentType assessmentType, UUID examTermId, String examTermName, UUID academicYearId,
                                     UUID academicClassId, String className, UUID sectionId, String sectionName, UUID subjectId,
                                     String subjectName, String title, LocalDate assessmentDate, BigDecimal maxMarks, AssessmentMode mode,
                                     AssessmentStatus status, int scored, int enrolled, BigDecimal averagePercent,
                                     List<ScoreResponse> scores) {
    }
}
