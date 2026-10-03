package com.vidya.tenantdata.assignment.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.assignment.dto.CreateTeacherAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.TeacherAssignmentDetailResponse;
import com.vidya.tenantdata.assignment.dto.TeacherAssignmentResponse;
import com.vidya.tenantdata.assignment.dto.UpdateTeacherAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.UpdateTeacherAssignmentStatusRequest;
import com.vidya.tenantdata.assignment.entity.TeacherSubjectAssignment;
import com.vidya.tenantdata.assignment.enums.TeacherAssignmentStatus;
import com.vidya.tenantdata.assignment.mapper.TeacherSubjectAssignmentMapper;
import com.vidya.tenantdata.assignment.repository.TeacherSubjectAssignmentRepository;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.enums.StaffType;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.subject.entity.Curriculum;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.enums.CurriculumStatus;
import com.vidya.tenantdata.subject.enums.SubjectStatus;
import com.vidya.tenantdata.subject.repository.CurriculumRepository;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of TeacherSubjectAssignment business operations in the tenant database schema.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeacherSubjectAssignmentServiceImpl implements TeacherSubjectAssignmentService {

    private final TeacherSubjectAssignmentRepository teacherAssignmentRepository;
    private final StaffRepository staffRepository;
    private final SubjectRepository subjectRepository;
    private final AcademicYearRepository academicYearRepository;
    private final AcademicClassRepository academicClassRepository;
    private final SectionRepository sectionRepository;
    private final CurriculumRepository curriculumRepository;

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public TeacherAssignmentDetailResponse createAssignment(CreateTeacherAssignmentRequest request) {
        log.info("Creating teacher assignment: teacher [{}], subject [{}], year [{}], class [{}]",
                request.getTeacherId(), request.getSubjectId(), request.getAcademicYearId(), request.getAcademicClassId());

        // 1. Resolve Staff (Teacher) from THIS tenant's DB
        Staff teacher = staffRepository.findByIdAndNotDeleted(request.getTeacherId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff", request.getTeacherId()));

        // Validate staffType is TEACHER
        if (teacher.getStaffType() != StaffType.TEACHER) {
            throw new BusinessException("Only staff members with staffType TEACHER can be assigned as teachers. Actual type: " + teacher.getStaffType());
        }

        // Validate employment status allows new active assignment
        if (teacher.getEmploymentStatus() == EmploymentStatus.TERMINATED ||
            teacher.getEmploymentStatus() == EmploymentStatus.RETIRED ||
            teacher.getEmploymentStatus() == EmploymentStatus.DELETED ||
            teacher.getEmploymentStatus() == EmploymentStatus.SUSPENDED ||
            teacher.getEmploymentStatus() == EmploymentStatus.RESIGNED) {
            throw new BusinessException("Cannot assign a new active teaching assignment to staff with status: " + teacher.getEmploymentStatus());
        }

        // 2. Resolve Subject from THIS tenant's DB
        Subject subject = subjectRepository.findByIdAndNotDeleted(request.getSubjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Subject", request.getSubjectId()));

        if (subject.getStatus() != SubjectStatus.ACTIVE) {
            throw new BusinessException("Cannot assign subject that is not ACTIVE. Current status: " + subject.getStatus());
        }

        // 3. Resolve AcademicYear from THIS tenant's DB
        AcademicYear academicYear = academicYearRepository.findByIdAndNotDeleted(request.getAcademicYearId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", request.getAcademicYearId()));

        // 4. Resolve AcademicClass from THIS tenant's DB
        AcademicClass academicClass = academicClassRepository.findByIdAndNotDeleted(request.getAcademicClassId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicClass", request.getAcademicClassId()));

        // 5. If Section is provided, resolve and validate consistency
        Section section = null;
        if (request.getSectionId() != null) {
            section = sectionRepository.findByIdAndNotDeleted(request.getSectionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Section", request.getSectionId()));

            // Consistency check: Section must belong to the given AcademicYear and AcademicClass
            if (!section.getAcademicYear().getId().equals(academicYear.getId())) {
                throw new BusinessException("Section does not belong to the specified AcademicYear");
            }
            if (!section.getAcademicClass().getId().equals(academicClass.getId())) {
                throw new BusinessException("Section does not belong to the specified AcademicClass");
            }
        }

        // 6. If Curriculum is provided, resolve and validate consistency
        Curriculum curriculum = null;
        if (request.getCurriculumId() != null) {
            curriculum = curriculumRepository.findByIdAndNotDeleted(request.getCurriculumId())
                    .orElseThrow(() -> new ResourceNotFoundException("Curriculum", request.getCurriculumId()));

            if (curriculum.getStatus() == CurriculumStatus.DELETED) {
                throw new BusinessException("Cannot associate a DELETED curriculum");
            }
            if (!curriculum.getAcademicYear().getId().equals(academicYear.getId())) {
                throw new BusinessException("Curriculum does not belong to the specified AcademicYear");
            }
        }

        // 7. Duplicate active assignment check
        if (teacherAssignmentRepository.existsActiveDuplicate(
                request.getTeacherId(),
                request.getSubjectId(),
                request.getAcademicYearId(),
                request.getAcademicClassId(),
                request.getSectionId(),
                null)) {
            throw new DuplicateResourceException(
                    "TeacherSubjectAssignment",
                    "teacherId, subjectId, academicYearId, academicClassId, sectionId",
                    request.getTeacherId() + ", " + request.getSubjectId() + ", " + request.getAcademicYearId() + ", " + request.getAcademicClassId() + ", " + request.getSectionId()
            );
        }

        // 8. Primary teacher check
        boolean isPrimary = request.getIsPrimary() == null || request.getIsPrimary();
        if (isPrimary && teacherAssignmentRepository.existsActivePrimary(
                request.getSubjectId(),
                request.getAcademicYearId(),
                request.getAcademicClassId(),
                request.getSectionId(),
                null)) {
            throw new DuplicateResourceException(
                    "TeacherSubjectAssignment",
                    "isPrimary",
                    "Active primary teacher already exists for subject: " + request.getSubjectId()
            );
        }

        // 9. Generate business code
        String assignmentCode = CodeGenerator.teacherAssignmentCode();
        int attempts = 0;
        while (teacherAssignmentRepository.existsByAssignmentCode(assignmentCode) && attempts < 5) {
            assignmentCode = CodeGenerator.teacherAssignmentCode();
            attempts++;
        }
        if (teacherAssignmentRepository.existsByAssignmentCode(assignmentCode)) {
            throw new DuplicateResourceException("TeacherSubjectAssignment", "assignmentCode", assignmentCode);
        }

        // 10. Build and persist entity
        TeacherSubjectAssignment assignment = new TeacherSubjectAssignment(
                assignmentCode,
                teacher,
                subject,
                academicYear,
                academicClass,
                section,
                curriculum,
                isPrimary,
                request.getStartDate(),
                request.getEndDate()
        );

        TeacherSubjectAssignment saved = teacherAssignmentRepository.save(assignment);
        log.info("Created teacher assignment [{}] with id: [{}]", saved.getAssignmentCode(), saved.getId());
        return TeacherSubjectAssignmentMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public TeacherAssignmentDetailResponse getAssignment(UUID id) {
        TeacherSubjectAssignment assignment = teacherAssignmentRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("TeacherSubjectAssignment", id));
        return TeacherSubjectAssignmentMapper.toDetailResponse(assignment);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public TeacherAssignmentDetailResponse getAssignmentByCode(String assignmentCode) {
        TeacherSubjectAssignment assignment = teacherAssignmentRepository.findByAssignmentCodeAndNotDeleted(assignmentCode)
                .orElseThrow(() -> new ResourceNotFoundException("TeacherSubjectAssignment", assignmentCode));
        return TeacherSubjectAssignmentMapper.toDetailResponse(assignment);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Page<TeacherAssignmentResponse> searchAssignments(
            UUID teacherId,
            UUID subjectId,
            UUID academicYearId,
            UUID academicClassId,
            UUID sectionId,
            TeacherAssignmentStatus status,
            Boolean isPrimary,
            Pageable pageable) {
        return teacherAssignmentRepository.searchAssignments(
                teacherId, subjectId, academicYearId, academicClassId, sectionId, status, isPrimary, pageable
        ).map(TeacherSubjectAssignmentMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<TeacherAssignmentResponse> getActiveAssignmentsByTeacher(UUID teacherId, UUID academicYearId) {
        return teacherAssignmentRepository.findActiveByTeacherId(teacherId, academicYearId)
                .stream()
                .map(TeacherSubjectAssignmentMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<TeacherAssignmentResponse> getActiveAssignmentsByClassAndSection(UUID academicYearId, UUID academicClassId, UUID sectionId) {
        return teacherAssignmentRepository.findActiveByClassAndSection(academicYearId, academicClassId, sectionId)
                .stream()
                .map(TeacherSubjectAssignmentMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public TeacherAssignmentDetailResponse updateAssignment(UUID id, UpdateTeacherAssignmentRequest request) {
        log.info("Updating teacher assignment id: [{}]", id);
        TeacherSubjectAssignment assignment = teacherAssignmentRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("TeacherSubjectAssignment", id));

        if (request.getIsPrimary() != null) {
            if (request.getIsPrimary() && !assignment.isPrimary()) {
                // Check if another active primary teacher already exists
                UUID sectionId = assignment.getSection() != null ? assignment.getSection().getId() : null;
                if (teacherAssignmentRepository.existsActivePrimary(
                        assignment.getSubject().getId(),
                        assignment.getAcademicYear().getId(),
                        assignment.getAcademicClass().getId(),
                        sectionId,
                        assignment.getId())) {
                    throw new DuplicateResourceException(
                            "TeacherSubjectAssignment",
                            "isPrimary",
                            "Active primary teacher already exists for subject: " + assignment.getSubject().getId()
                    );
                }
            }
            assignment.setPrimary(request.getIsPrimary());
        }

        if (request.getStartDate() != null) {
            assignment.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            assignment.setEndDate(request.getEndDate());
        }

        TeacherSubjectAssignment updated = teacherAssignmentRepository.save(assignment);
        return TeacherSubjectAssignmentMapper.toDetailResponse(updated);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public TeacherAssignmentDetailResponse updateStatus(UUID id, UpdateTeacherAssignmentStatusRequest request) {
        log.info("Updating status for teacher assignment id: [{}] to [{}]", id, request.getStatus());
        TeacherSubjectAssignment assignment = teacherAssignmentRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("TeacherSubjectAssignment", id));

        assignment.updateStatus(request.getStatus());
        if (request.getStatus() == TeacherAssignmentStatus.COMPLETED && assignment.getEndDate() == null) {
            assignment.setEndDate(LocalDate.now());
        }
        TeacherSubjectAssignment updated = teacherAssignmentRepository.save(assignment);
        return TeacherSubjectAssignmentMapper.toDetailResponse(updated);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteAssignment(UUID id) {
        log.info("Soft-deleting teacher assignment id: [{}]", id);
        TeacherSubjectAssignment assignment = teacherAssignmentRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("TeacherSubjectAssignment", id));

        assignment.softDelete();
        teacherAssignmentRepository.save(assignment);
    }
}
