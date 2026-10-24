package com.vidya.tenantdata.examination.dto;

import com.vidya.tenantdata.examination.enums.MarkEntryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Student mark entry response")
public class StudentMarkResponse {

    private UUID id;
    private String entryCode;
    private UUID examSubjectConfigId;
    private String subjectName;
    private String subjectCode;
    private UUID studentId;
    private String studentName;
    private String admissionNumber;
    private BigDecimal marksObtained;
    private BigDecimal maxMarks;
    private BigDecimal theoryMarksObtained;
    private BigDecimal practicalMarksObtained;
    private BigDecimal percentage;
    private String grade;
    private BigDecimal gradePoint;
    private boolean isAbsent;
    private boolean isExempt;
    private boolean isPassing;
    private String remarks;
    private UUID evaluatedByStaffId;
    private String evaluatedByStaffName;
    private MarkEntryStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
