package com.vidya.tenantdata.assignment.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.assignment.dto.ClassTeacherAssignmentDetailResponse;
import com.vidya.tenantdata.assignment.dto.ClassTeacherAssignmentResponse;
import com.vidya.tenantdata.assignment.dto.CreateClassTeacherAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.ReplaceClassTeacherRequest;
import com.vidya.tenantdata.assignment.dto.UpdateClassTeacherAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.UpdateClassTeacherAssignmentStatusRequest;
import com.vidya.tenantdata.assignment.entity.ClassTeacherAssignment;
import com.vidya.tenantdata.assignment.enums.ClassTeacherAssignmentStatus;
import com.vidya.tenantdata.assignment.mapper.ClassTeacherAssignmentMapper;
import com.vidya.tenantdata.assignment.repository.ClassTeacherAssignmentRepository;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.enums.StaffType;
import com.vidya.tenantdata.staff.repository.StaffRepository;
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
 * Implementation of ClassTeacherAssignment business operations in the tenant database schema.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassTeacherAssignmentServiceImpl implements ClassTeacherAssignmentService {

    private final ClassTeacherAssignmentRepository classTeacherAssignmentRepository;
    private final StaffRepository staffRepository;
    private final AcademicYearRepository academicYearRepository;
    private final AcademicClassRepository academicClassRepository;
    private final SectionRepository sectionRepository;

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public ClassTeacherAssignmentDetailResponse createAssignment(CreateClassTeacherAssignmentRequest request) {
        log.info("Creating class teacher assignment: staff [{}], year [{}], class [{}], section [{}]",
                request.getStaffId(), request.getAcademicYearId(), request.getAcademicClassId(), request.getSectionId());

        // 1. Resolve and validate Staff
        Staff staff = resolveAndValidateTeacher(request.getStaffId());

        // 2. Resolve and validate AcademicYear
        AcademicYear academicYear = resolveAndValidateAcademicYear(request.getAcademicYearId());

        // 3. Resolve AcademicClass
        AcademicClass academicClass = academicClassRepository.findByIdAndNotDeleted(request.getAcademicClassId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicClass", request.getAcademicClassId()));

        // 4. Resolve and validate Section (must belong to the given year AND class)
        Section section = resolveAndValidateSection(request.getSectionId(), academicYear, academicClass);

        // 5. Check for existing ACTIVE class teacher for this (year, section) combination
        if (classTeacherAssignmentRepository.existsActiveBySectionAndAcademicYear(
                academicYear.getId(), section.getId(), null)) {
            throw new DuplicateResourceException(
                    "ClassTeacherAssignment",
                    "academicYearId, sectionId",
                    "An active class teacher is already assigned to this section for the academic year."
            );
        }

        // 6. Generate unique business code
        String assignmentCode = generateUniqueAssignmentCode();

        // 7. Build and persist entity
        ClassTeacherAssignment assignment = new ClassTeacherAssignment(
                assignmentCode,
                staff,
                academicYear,
                academicClass,
                section,
                request.getStartDate(),
                request.getNotes()
        );

        ClassTeacherAssignment saved = classTeacherAssignmentRepository.save(assignment);
        log.info("Created class teacher assignment [{}] with id: [{}]", saved.getAssignmentCode(), saved.getId());
        return ClassTeacherAssignmentMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public ClassTeacherAssignmentDetailResponse getAssignment(UUID id) {
        ClassTeacherAssignment assignment = classTeacherAssignmentRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("ClassTeacherAssignment", id));
        return ClassTeacherAssignmentMapper.toDetailResponse(assignment);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public ClassTeacherAssignmentDetailResponse getAssignmentByCode(String assignmentCode) {
        ClassTeacherAssignment assignment = classTeacherAssignmentRepository.findByAssignmentCodeAndNotDeleted(assignmentCode)
                .orElseThrow(() -> new ResourceNotFoundException("ClassTeacherAssignment", assignmentCode));
        return ClassTeacherAssignmentMapper.toDetailResponse(assignment);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public ClassTeacherAssignmentDetailResponse getCurrentClassTeacher(UUID sectionId, UUID academicYearId) {
        ClassTeacherAssignment assignment = classTeacherAssignmentRepository
                .findActiveBySectionAndAcademicYear(academicYearId, sectionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ClassTeacherAssignment",
                        "No active class teacher found for sectionId=" + sectionId + " and academicYearId=" + academicYearId));
        return ClassTeacherAssignmentMapper.toDetailResponse(assignment);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Page<ClassTeacherAssignmentResponse> searchAssignments(
            UUID staffId,
            UUID academicYearId,
            UUID academicClassId,
            UUID sectionId,
            ClassTeacherAssignmentStatus status,
            Pageable pageable) {
        return classTeacherAssignmentRepository.searchAssignments(
                staffId, academicYearId, academicClassId, sectionId, status, pageable
        ).map(ClassTeacherAssignmentMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<ClassTeacherAssignmentResponse> getActiveAssignmentsByStaff(UUID staffId, UUID academicYearId) {
        return classTeacherAssignmentRepository.findActiveByStaffId(staffId, academicYearId)
                .stream()
                .map(ClassTeacherAssignmentMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<ClassTeacherAssignmentResponse> getAssignmentsBySection(UUID sectionId, UUID academicYearId) {
        return classTeacherAssignmentRepository.findAllBySectionAndAcademicYear(academicYearId, sectionId)
                .stream()
                .map(ClassTeacherAssignmentMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<ClassTeacherAssignmentResponse> getAssignmentsByAcademicYear(UUID academicYearId) {
        return classTeacherAssignmentRepository.findAllByAcademicYear(academicYearId)
                .stream()
                .map(ClassTeacherAssignmentMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public ClassTeacherAssignmentDetailResponse updateAssignment(UUID id, UpdateClassTeacherAssignmentRequest request) {
        log.info("Updating class teacher assignment id: [{}]", id);
        ClassTeacherAssignment assignment = classTeacherAssignmentRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("ClassTeacherAssignment", id));

        if (request.getStartDate() != null) {
            assignment.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            assignment.setEndDate(request.getEndDate());
        }
        if (request.getNotes() != null) {
            assignment.setNotes(request.getNotes());
        }

        ClassTeacherAssignment updated = classTeacherAssignmentRepository.save(assignment);
        return ClassTeacherAssignmentMapper.toDetailResponse(updated);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public ClassTeacherAssignmentDetailResponse updateStatus(UUID id, UpdateClassTeacherAssignmentStatusRequest request) {
        log.info("Updating status for class teacher assignment id: [{}] to [{}]", id, request.getStatus());
        ClassTeacherAssignment assignment = classTeacherAssignmentRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("ClassTeacherAssignment", id));

        assignment.updateStatus(request.getStatus());
        if ((request.getStatus() == ClassTeacherAssignmentStatus.COMPLETED
                || request.getStatus() == ClassTeacherAssignmentStatus.CANCELLED)
                && assignment.getEndDate() == null) {
            assignment.setEndDate(LocalDate.now());
        }

        ClassTeacherAssignment updated = classTeacherAssignmentRepository.save(assignment);
        return ClassTeacherAssignmentMapper.toDetailResponse(updated);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public ClassTeacherAssignmentDetailResponse replaceClassTeacher(UUID existingAssignmentId, ReplaceClassTeacherRequest request) {
        log.info("Replacing class teacher for assignment id: [{}] with new staff [{}]",
                existingAssignmentId, request.getNewStaffId());

        // 1. Resolve and validate existing assignment
        ClassTeacherAssignment existing = classTeacherAssignmentRepository.findByIdAndNotDeleted(existingAssignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("ClassTeacherAssignment", existingAssignmentId));

        if (existing.getStatus() != ClassTeacherAssignmentStatus.ACTIVE) {
            throw new BusinessException(
                    "Cannot replace a class teacher assignment that is not ACTIVE. Current status: " + existing.getStatus());
        }

        // 2. Resolve and validate new staff
        Staff newStaff = resolveAndValidateTeacher(request.getNewStaffId());

        if (newStaff.getId().equals(existing.getStaff().getId())) {
            throw new BusinessException("Cannot replace class teacher with the same staff member");
        }

        LocalDate effectiveDate = request.getEffectiveDate() != null ? request.getEffectiveDate() : LocalDate.now();

        // 3. Complete the existing assignment (preserves history)
        existing.complete(effectiveDate);
        classTeacherAssignmentRepository.save(existing);
        log.info("Completed existing class teacher assignment [{}]", existing.getAssignmentCode());

        // 4. Generate new assignment code
        String newAssignmentCode = generateUniqueAssignmentCode();

        // 5. Create the new assignment (same section, year, class; new staff)
        ClassTeacherAssignment newAssignment = new ClassTeacherAssignment(
                newAssignmentCode,
                newStaff,
                existing.getAcademicYear(),
                existing.getAcademicClass(),
                existing.getSection(),
                effectiveDate,
                request.getNotes()
        );

        ClassTeacherAssignment saved = classTeacherAssignmentRepository.save(newAssignment);
        log.info("Created replacement class teacher assignment [{}] with id: [{}]",
                saved.getAssignmentCode(), saved.getId());
        return ClassTeacherAssignmentMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteAssignment(UUID id) {
        log.info("Soft-deleting class teacher assignment id: [{}]", id);
        ClassTeacherAssignment assignment = classTeacherAssignmentRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("ClassTeacherAssignment", id));

        assignment.softDelete();
        classTeacherAssignmentRepository.save(assignment);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Staff resolveAndValidateTeacher(UUID staffId) {
        Staff staff = staffRepository.findByIdAndNotDeleted(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff", staffId));

        if (staff.getStaffType() != StaffType.TEACHER) {
            throw new BusinessException(
                    "Only staff members with staffType TEACHER can be assigned as class teachers. Actual type: "
                            + staff.getStaffType());
        }

        EmploymentStatus empStatus = staff.getEmploymentStatus();
        if (empStatus == EmploymentStatus.TERMINATED
                || empStatus == EmploymentStatus.RETIRED
                || empStatus == EmploymentStatus.DELETED
                || empStatus == EmploymentStatus.SUSPENDED
                || empStatus == EmploymentStatus.RESIGNED
                || empStatus == EmploymentStatus.INACTIVE) {
            throw new BusinessException(
                    "Cannot assign a new class teacher assignment to staff with employment status: " + empStatus);
        }

        return staff;
    }

    private AcademicYear resolveAndValidateAcademicYear(UUID academicYearId) {
        AcademicYear academicYear = academicYearRepository.findByIdAndNotDeleted(academicYearId)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", academicYearId));

        AcademicYearStatus status = academicYear.getStatus();
        if (status == AcademicYearStatus.CLOSED
                || status == AcademicYearStatus.CANCELLED
                || status == AcademicYearStatus.DELETED) {
            throw new BusinessException(
                    "Cannot create an active class teacher assignment against an academic year with status: " + status);
        }

        return academicYear;
    }

    private Section resolveAndValidateSection(UUID sectionId, AcademicYear academicYear, AcademicClass academicClass) {
        Section section = sectionRepository.findByIdAndNotDeleted(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Section", sectionId));

        if (!section.getAcademicYear().getId().equals(academicYear.getId())) {
            throw new BusinessException(
                    "Section does not belong to the specified AcademicYear");
        }

        if (!section.getAcademicClass().getId().equals(academicClass.getId())) {
            throw new BusinessException(
                    "Section does not belong to the specified AcademicClass");
        }

        return section;
    }

    private String generateUniqueAssignmentCode() {
        String code = CodeGenerator.classTeacherAssignmentCode();
        int attempts = 0;
        while (classTeacherAssignmentRepository.existsByAssignmentCode(code) && attempts < 5) {
            code = CodeGenerator.classTeacherAssignmentCode();
            attempts++;
        }
        if (classTeacherAssignmentRepository.existsByAssignmentCode(code)) {
            throw new DuplicateResourceException("ClassTeacherAssignment", "assignmentCode", code);
        }
        return code;
    }
}
