package com.vidya.tenantdata.examination.dto;

import com.vidya.tenantdata.examination.enums.ExamTermStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Exam term response")
public class ExamTermResponse {

    private UUID id;
    private String termCode;
    private UUID academicYearId;
    private String academicYearName;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal weightagePercentage;
    private ExamTermStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
