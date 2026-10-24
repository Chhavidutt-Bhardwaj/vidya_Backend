package com.vidya.tenantdata.examination.dto;

import com.vidya.tenantdata.examination.enums.ExamScheduleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Exam schedule response")
public class ExamScheduleResponse {

    private UUID id;
    private String scheduleCode;
    private UUID examTermId;
    private String examTermName;
    private UUID academicClassId;
    private String academicClassName;
    private UUID gradingScaleId;
    private String gradingScaleName;
    private String name;
    private ExamScheduleStatus status;
    private List<ExamSubjectConfigResponse> subjectConfigs;
    private Instant createdAt;
    private Instant updatedAt;
}
