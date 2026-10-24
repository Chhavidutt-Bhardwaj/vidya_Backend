package com.vidya.tenantdata.examination.service;

import com.vidya.tenantdata.examination.dto.CreateExamScheduleRequest;
import com.vidya.tenantdata.examination.dto.CreateExamSubjectConfigRequest;
import com.vidya.tenantdata.examination.dto.ExamScheduleResponse;
import com.vidya.tenantdata.examination.dto.ExamSubjectConfigResponse;
import com.vidya.tenantdata.examination.enums.ExamScheduleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ExamScheduleService {

    ExamScheduleResponse createExamSchedule(CreateExamScheduleRequest request);

    ExamScheduleResponse getExamScheduleById(UUID id);

    Page<ExamScheduleResponse> searchSchedules(UUID termId, UUID classId, ExamScheduleStatus status, Pageable pageable);

    ExamSubjectConfigResponse addSubjectToSchedule(UUID scheduleId, CreateExamSubjectConfigRequest request);

    List<ExamSubjectConfigResponse> getScheduleSubjects(UUID scheduleId);

    void removeSubjectFromSchedule(UUID scheduleId, UUID subjectConfigId);

    ExamScheduleResponse publishSchedule(UUID id);

    void deleteExamSchedule(UUID id);
}
