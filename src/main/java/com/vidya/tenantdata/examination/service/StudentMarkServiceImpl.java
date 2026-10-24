package com.vidya.tenantdata.examination.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.examination.dto.BulkStudentMarksRequest;
import com.vidya.tenantdata.examination.dto.BulkStudentMarksResponse;
import com.vidya.tenantdata.examination.dto.StudentMarkResponse;
import com.vidya.tenantdata.examination.dto.SubmitStudentMarkRequest;
import com.vidya.tenantdata.examination.entity.ExamSubjectConfig;
import com.vidya.tenantdata.examination.entity.GradingScaleTier;
import com.vidya.tenantdata.examination.entity.StudentMarkEntry;
import com.vidya.tenantdata.examination.enums.MarkEntryStatus;
import com.vidya.tenantdata.examination.mapper.StudentMarkMapper;
import com.vidya.tenantdata.examination.repository.ExamSubjectConfigRepository;
import com.vidya.tenantdata.examination.repository.GradingScaleTierRepository;
import com.vidya.tenantdata.examination.repository.StudentMarkEntryRepository;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentMarkServiceImpl implements StudentMarkService {

    private final StudentMarkEntryRepository studentMarkEntryRepository;
    private final ExamSubjectConfigRepository examSubjectConfigRepository;
    private final StudentRepository studentRepository;
    private final StaffRepository staffRepository;
    private final GradingScaleTierRepository gradingScaleTierRepository;
    private final StudentMarkMapper studentMarkMapper;

    @Override
    @Transactional("tenantTransactionManager")
    public StudentMarkResponse submitStudentMark(UUID configId, SubmitStudentMarkRequest request, UUID evaluatorStaffId) {
        log.info("Submitting marks for student [{}] in subject config [{}]", request.getStudentId(), configId);

        ExamSubjectConfig config = examSubjectConfigRepository.findActiveById(configId)
                .orElseThrow(() -> new ResourceNotFoundException("ExamSubjectConfig", configId));

        Student student = studentRepository.findByIdAndNotDeleted(request.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student", request.getStudentId()));

        Staff evaluator = null;
        if (evaluatorStaffId != null) {
            evaluator = staffRepository.findByIdAndNotDeleted(evaluatorStaffId).orElse(null);
        }

        StudentMarkEntry entry = studentMarkEntryRepository.findByConfigAndStudent(config.getId(), student.getId())
                .orElseGet(() -> new StudentMarkEntry(CodeGenerator.studentMarkEntryCode(), config, student));

        if (entry.getStatus() == MarkEntryStatus.LOCKED) {
            throw new BusinessException("Cannot modify locked student marks");
        }

        applyMarksData(entry, request, config, evaluator);

        StudentMarkEntry saved = studentMarkEntryRepository.save(entry);
        return studentMarkMapper.toResponse(saved);
    }

    @Override
    @Transactional("tenantTransactionManager")
    public BulkStudentMarksResponse submitBulkMarks(BulkStudentMarksRequest request, UUID evaluatorStaffId) {
        log.info("Processing bulk marks for config [{}], {} entries",
                request.getExamSubjectConfigId(), request.getEntries().size());

        ExamSubjectConfig config = examSubjectConfigRepository.findActiveById(request.getExamSubjectConfigId())
                .orElseThrow(() -> new ResourceNotFoundException("ExamSubjectConfig", request.getExamSubjectConfigId()));

        Staff evaluator = null;
        if (evaluatorStaffId != null) {
            evaluator = staffRepository.findByIdAndNotDeleted(evaluatorStaffId).orElse(null);
        }

        int createdCount = 0;
        int updatedCount = 0;

        for (SubmitStudentMarkRequest item : request.getEntries()) {
            Student student = studentRepository.findByIdAndNotDeleted(item.getStudentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Student", item.getStudentId()));

            Optional<StudentMarkEntry> existingOpt = studentMarkEntryRepository.findByConfigAndStudent(config.getId(), student.getId());
            StudentMarkEntry entry;

            if (existingOpt.isPresent()) {
                entry = existingOpt.get();
                if (entry.getStatus() == MarkEntryStatus.LOCKED) {
                    throw new BusinessException("Cannot modify locked mark entry for student: " + student.getId());
                }
                updatedCount++;
            } else {
                entry = new StudentMarkEntry(CodeGenerator.studentMarkEntryCode(), config, student);
                createdCount++;
            }

            applyMarksData(entry, item, config, evaluator);
            studentMarkEntryRepository.save(entry);
        }

        return BulkStudentMarksResponse.builder()
                .examSubjectConfigId(config.getId())
                .totalProcessed(request.getEntries().size())
                .createdCount(createdCount)
                .updatedCount(updatedCount)
                .build();
    }

    @Override
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public StudentMarkResponse getMarkById(UUID id) {
        StudentMarkEntry entry = studentMarkEntryRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StudentMarkEntry", id));
        return studentMarkMapper.toResponse(entry);
    }

    @Override
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public Page<StudentMarkResponse> searchMarks(UUID configId, MarkEntryStatus status, Pageable pageable) {
        return studentMarkEntryRepository.searchMarks(configId, status, pageable)
                .map(studentMarkMapper::toResponse);
    }

    @Override
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<StudentMarkResponse> getMarksByStudent(UUID studentId) {
        return studentMarkEntryRepository.findByStudentId(studentId)
                .stream()
                .map(studentMarkMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional("tenantTransactionManager")
    public void lockMarks(UUID configId) {
        log.info("Locking marks for subject config [{}]", configId);

        List<StudentMarkEntry> entries = studentMarkEntryRepository.findByConfigId(configId);
        for (StudentMarkEntry entry : entries) {
            entry.setStatus(MarkEntryStatus.LOCKED);
            studentMarkEntryRepository.save(entry);
        }
    }

    private void applyMarksData(StudentMarkEntry entry, SubmitStudentMarkRequest request,
                                ExamSubjectConfig config, Staff evaluator) {
        entry.setAbsent(request.isAbsent());
        entry.setExempt(request.isExempt());
        entry.setRemarks(request.getRemarks());
        entry.setEvaluatedByStaff(evaluator);
        entry.setStatus(MarkEntryStatus.SUBMITTED);

        if (request.isAbsent()) {
            entry.setMarksObtained(BigDecimal.ZERO);
            entry.setTheoryMarksObtained(BigDecimal.ZERO);
            entry.setPracticalMarksObtained(BigDecimal.ZERO);
            entry.setGrade("AB");
            entry.setGradePoint(BigDecimal.ZERO);
            return;
        }

        if (request.isExempt()) {
            entry.setMarksObtained(null);
            entry.setTheoryMarksObtained(null);
            entry.setPracticalMarksObtained(null);
            entry.setGrade("EX");
            entry.setGradePoint(BigDecimal.ZERO);
            return;
        }

        BigDecimal marks = request.getMarksObtained();
        if (marks != null && marks.compareTo(config.getMaxMarks()) > 0) {
            throw new BusinessException("Marks obtained (" + marks + ") cannot exceed maximum marks (" + config.getMaxMarks() + ")");
        }

        entry.setMarksObtained(marks);
        entry.setTheoryMarksObtained(request.getTheoryMarksObtained());
        entry.setPracticalMarksObtained(request.getPracticalMarksObtained());

        // Grade calculation
        if (marks != null && config.getMaxMarks() != null && config.getMaxMarks().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal percentage = marks.multiply(BigDecimal.valueOf(100))
                    .divide(config.getMaxMarks(), 2, RoundingMode.HALF_UP);

            if (config.getExamSchedule() != null && config.getExamSchedule().getGradingScale() != null) {
                UUID scaleId = config.getExamSchedule().getGradingScale().getId();
                Optional<GradingScaleTier> tierOpt = gradingScaleTierRepository.findMatchingTier(scaleId, percentage);
                if (tierOpt.isPresent()) {
                    entry.setGrade(tierOpt.get().getGrade());
                    entry.setGradePoint(tierOpt.get().getGradePoint());
                } else {
                    entry.setGrade(null);
                    entry.setGradePoint(null);
                }
            }
        }
    }
}
