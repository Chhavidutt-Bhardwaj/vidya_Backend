package com.vidya.tenantdata.reportcard.dto;

import com.vidya.tenantdata.reportcard.enums.ReportCardStatus;
import com.vidya.tenantdata.reportcard.enums.ResultStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Full student report card document response")
public class StudentReportCardResponse {

    private UUID id;
    private String reportCardCode;
    private UUID examTermId;
    private String examTermName;
    private UUID academicYearId;
    private String academicYearName;
    private UUID academicClassId;
    private String academicClassName;
    private UUID sectionId;
    private String sectionName;
    private UUID studentId;
    private String studentName;
    private String admissionNumber;
    private Integer rollNumber;
    private BigDecimal totalMaxMarks;
    private BigDecimal totalMarksObtained;
    private BigDecimal percentage;
    private String overallGrade;
    private BigDecimal cgpa;
    private Integer sectionRank;
    private Integer classRank;
    private int attendanceWorkingDays;
    private int attendancePresentDays;
    private BigDecimal attendancePercentage;
    private ResultStatus resultStatus;
    private ReportCardStatus status;
    private Instant publishedAt;
    private List<ReportCardSubjectResultResponse> subjectResults;
    private List<ReportCardRemarkResponse> remarks;
    private Instant createdAt;
    private Instant updatedAt;
}
