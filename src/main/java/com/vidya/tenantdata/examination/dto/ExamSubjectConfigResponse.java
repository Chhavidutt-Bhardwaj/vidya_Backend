package com.vidya.tenantdata.examination.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Exam subject configuration response")
public class ExamSubjectConfigResponse {

    private UUID id;
    private String configCode;
    private UUID examScheduleId;
    private UUID subjectId;
    private String subjectCode;
    private String subjectName;
    private LocalDate examDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal maxMarks;
    private BigDecimal passMarks;
    private BigDecimal theoryMaxMarks;
    private BigDecimal practicalMaxMarks;
    private UUID roomId;
    private String roomNumber;
    private Instant createdAt;
    private Instant updatedAt;
}
