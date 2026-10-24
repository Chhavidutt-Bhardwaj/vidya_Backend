package com.vidya.tenantdata.reportcard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Subject-level performance in report card")
public class ReportCardSubjectResultResponse {

    private UUID id;
    private UUID subjectId;
    private String subjectName;
    private String subjectCode;
    private BigDecimal maxMarks;
    private BigDecimal passMarks;
    private BigDecimal theoryMarks;
    private BigDecimal practicalMarks;
    private BigDecimal marksObtained;
    private BigDecimal percentage;
    private String grade;
    private BigDecimal gradePoint;
    private boolean isPassing;
    private boolean isAbsent;
    private boolean isExempt;
}
