package com.vidya.tenantdata.examination.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.examination.dto.CreateExamScheduleRequest;
import com.vidya.tenantdata.examination.dto.CreateExamSubjectConfigRequest;
import com.vidya.tenantdata.examination.dto.ExamScheduleResponse;
import com.vidya.tenantdata.examination.dto.ExamSubjectConfigResponse;
import com.vidya.tenantdata.examination.entity.ExamSchedule;
import com.vidya.tenantdata.examination.entity.ExamSubjectConfig;
import com.vidya.tenantdata.examination.entity.ExamTerm;
import com.vidya.tenantdata.examination.entity.GradingScale;
import com.vidya.tenantdata.examination.enums.ExamScheduleStatus;
import com.vidya.tenantdata.examination.mapper.ExamScheduleMapper;
import com.vidya.tenantdata.examination.repository.ExamScheduleRepository;
import com.vidya.tenantdata.examination.repository.ExamSubjectConfigRepository;
import com.vidya.tenantdata.examination.repository.ExamTermRepository;
import com.vidya.tenantdata.examination.repository.GradingScaleRepository;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import com.vidya.tenantdata.timetable.entity.Room;
import com.vidya.tenantdata.timetable.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamScheduleServiceImpl implements ExamScheduleService {

    private final ExamScheduleRepository examScheduleRepository;
    private final ExamTermRepository examTermRepository;
    private final AcademicClassRepository academicClassRepository;
    private final GradingScaleRepository gradingScaleRepository;
    private final ExamSubjectConfigRepository examSubjectConfigRepository;
    private final SubjectRepository subjectRepository;
    private final RoomRepository roomRepository;
    private final ExamScheduleMapper examScheduleMapper;

    @Override
    @Transactional("tenantTransactionManager")
    public ExamScheduleResponse createExamSchedule(CreateExamScheduleRequest request) {
        log.info("Creating exam schedule [{}] for term [{}] and class [{}]",
                request.getName(), request.getExamTermId(), request.getAcademicClassId());

        ExamTerm term = examTermRepository.findActiveById(request.getExamTermId())
                .orElseThrow(() -> new ResourceNotFoundException("ExamTerm", request.getExamTermId()));

        AcademicClass academicClass = academicClassRepository.findByIdAndNotDeleted(request.getAcademicClassId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicClass", request.getAcademicClassId()));

        if (examScheduleRepository.existsByTermAndClass(term.getId(), academicClass.getId())) {
            throw new DuplicateResourceException("An exam schedule already exists for this term and class");
        }

        GradingScale scale = null;
        if (request.getGradingScaleId() != null) {
            scale = gradingScaleRepository.findActiveById(request.getGradingScaleId())
                    .orElseThrow(() -> new ResourceNotFoundException("GradingScale", request.getGradingScaleId()));
        } else {
            scale = gradingScaleRepository.findDefaultScale().orElse(null);
        }

        ExamSchedule schedule = new ExamSchedule(
                CodeGenerator.examScheduleCode(),
                term,
                academicClass,
                scale,
                request.getName()
        );

        ExamSchedule saved = examScheduleRepository.save(schedule);
        return examScheduleMapper.toResponse(saved);
    }

    @Override
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public ExamScheduleResponse getExamScheduleById(UUID id) {
        ExamSchedule schedule = examScheduleRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExamSchedule", id));
        return examScheduleMapper.toResponse(schedule);
    }

    @Override
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public Page<ExamScheduleResponse> searchSchedules(UUID termId, UUID classId, ExamScheduleStatus status, Pageable pageable) {
        return examScheduleRepository.searchSchedules(termId, classId, status, pageable)
                .map(examScheduleMapper::toResponse);
    }

    @Override
    @Transactional("tenantTransactionManager")
    public ExamSubjectConfigResponse addSubjectToSchedule(UUID scheduleId, CreateExamSubjectConfigRequest request) {
        log.info("Adding subject [{}] to exam schedule [{}]", request.getSubjectId(), scheduleId);

        ExamSchedule schedule = examScheduleRepository.findActiveById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("ExamSchedule", scheduleId));

        if (schedule.getStatus() == ExamScheduleStatus.LOCKED || schedule.getStatus() == ExamScheduleStatus.CONCLUDED) {
            throw new BusinessException("Cannot modify subjects on a locked or concluded exam schedule");
        }

        Subject subject = subjectRepository.findByIdAndNotDeleted(request.getSubjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Subject", request.getSubjectId()));

        if (examSubjectConfigRepository.existsByScheduleAndSubject(schedule.getId(), subject.getId())) {
            throw new DuplicateResourceException("Subject '" + subject.getName() + "' is already scheduled for this exam");
        }

        if (request.getStartTime().isAfter(request.getEndTime()) || request.getStartTime().equals(request.getEndTime())) {
            throw new BusinessException("Exam start time must be before end time");
        }

        if (request.getPassMarks().compareTo(request.getMaxMarks()) > 0) {
            throw new BusinessException("Passing marks cannot exceed maximum marks");
        }

        Room room = null;
        if (request.getRoomId() != null) {
            room = roomRepository.findByIdAndNotDeleted(request.getRoomId())
                    .orElseThrow(() -> new ResourceNotFoundException("Room", request.getRoomId()));
        }

        ExamSubjectConfig config = new ExamSubjectConfig(
                CodeGenerator.examSubjectConfigCode(),
                schedule,
                subject,
                request.getExamDate(),
                request.getStartTime(),
                request.getEndTime(),
                request.getMaxMarks(),
                request.getPassMarks(),
                request.getTheoryMaxMarks(),
                request.getPracticalMaxMarks(),
                room
        );

        ExamSubjectConfig saved = examSubjectConfigRepository.save(config);
        return examScheduleMapper.toConfigResponse(saved);
    }

    @Override
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<ExamSubjectConfigResponse> getScheduleSubjects(UUID scheduleId) {
        return examSubjectConfigRepository.findByExamScheduleId(scheduleId)
                .stream()
                .map(examScheduleMapper::toConfigResponse)
                .toList();
    }

    @Override
    @Transactional("tenantTransactionManager")
    public void removeSubjectFromSchedule(UUID scheduleId, UUID subjectConfigId) {
        log.info("Removing subject config [{}] from exam schedule [{}]", subjectConfigId, scheduleId);

        ExamSubjectConfig config = examSubjectConfigRepository.findActiveById(subjectConfigId)
                .orElseThrow(() -> new ResourceNotFoundException("ExamSubjectConfig", subjectConfigId));

        if (!config.getExamSchedule().getId().equals(scheduleId)) {
            throw new BusinessException("Subject config does not belong to specified exam schedule");
        }

        config.setDeletedAt(Instant.now());
        examSubjectConfigRepository.save(config);
    }

    @Override
    @Transactional("tenantTransactionManager")
    public ExamScheduleResponse publishSchedule(UUID id) {
        log.info("Publishing exam schedule [{}]", id);

        ExamSchedule schedule = examScheduleRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExamSchedule", id));

        schedule.setStatus(ExamScheduleStatus.PUBLISHED);
        ExamSchedule saved = examScheduleRepository.save(schedule);
        return examScheduleMapper.toResponse(saved);
    }

    @Override
    @Transactional("tenantTransactionManager")
    public void deleteExamSchedule(UUID id) {
        log.info("Soft deleting exam schedule [{}]", id);

        ExamSchedule schedule = examScheduleRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExamSchedule", id));

        schedule.setDeletedAt(Instant.now());
        examScheduleRepository.save(schedule);
    }
}
