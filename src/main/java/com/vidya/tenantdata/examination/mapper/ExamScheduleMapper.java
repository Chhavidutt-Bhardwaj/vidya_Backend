package com.vidya.tenantdata.examination.mapper;

import com.vidya.tenantdata.examination.dto.ExamScheduleResponse;
import com.vidya.tenantdata.examination.dto.ExamSubjectConfigResponse;
import com.vidya.tenantdata.examination.entity.ExamSchedule;
import com.vidya.tenantdata.examination.entity.ExamSubjectConfig;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ExamScheduleMapper {

    public ExamScheduleResponse toResponse(ExamSchedule schedule) {
        if (schedule == null) return null;

        List<ExamSubjectConfigResponse> configs = schedule.getSubjectConfigs() != null
                ? schedule.getSubjectConfigs().stream().map(this::toConfigResponse).toList()
                : Collections.emptyList();

        return ExamScheduleResponse.builder()
                .id(schedule.getId())
                .scheduleCode(schedule.getScheduleCode())
                .examTermId(schedule.getExamTerm() != null ? schedule.getExamTerm().getId() : null)
                .examTermName(schedule.getExamTerm() != null ? schedule.getExamTerm().getName() : null)
                .academicClassId(schedule.getAcademicClass() != null ? schedule.getAcademicClass().getId() : null)
                .academicClassName(schedule.getAcademicClass() != null ? schedule.getAcademicClass().getName() : null)
                .gradingScaleId(schedule.getGradingScale() != null ? schedule.getGradingScale().getId() : null)
                .gradingScaleName(schedule.getGradingScale() != null ? schedule.getGradingScale().getName() : null)
                .name(schedule.getName())
                .status(schedule.getStatus())
                .subjectConfigs(configs)
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt())
                .build();
    }

    public ExamSubjectConfigResponse toConfigResponse(ExamSubjectConfig config) {
        if (config == null) return null;

        return ExamSubjectConfigResponse.builder()
                .id(config.getId())
                .configCode(config.getConfigCode())
                .examScheduleId(config.getExamSchedule() != null ? config.getExamSchedule().getId() : null)
                .subjectId(config.getSubject() != null ? config.getSubject().getId() : null)
                .subjectCode(config.getSubject() != null ? config.getSubject().getSubjectCode() : null)
                .subjectName(config.getSubject() != null ? config.getSubject().getName() : null)
                .examDate(config.getExamDate())
                .startTime(config.getStartTime())
                .endTime(config.getEndTime())
                .maxMarks(config.getMaxMarks())
                .passMarks(config.getPassMarks())
                .theoryMaxMarks(config.getTheoryMaxMarks())
                .practicalMaxMarks(config.getPracticalMaxMarks())
                .roomId(config.getRoom() != null ? config.getRoom().getId() : null)
                .roomNumber(config.getRoom() != null ? config.getRoom().getName() : null)
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
