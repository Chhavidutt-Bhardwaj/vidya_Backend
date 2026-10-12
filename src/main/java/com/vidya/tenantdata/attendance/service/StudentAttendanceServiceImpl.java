package com.vidya.tenantdata.attendance.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.common.util.SecurityUtils;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.entity.StudentAcademicAssignment;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.enums.StudentAssignmentStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.academic.repository.StudentAcademicAssignmentRepository;
import com.vidya.tenantdata.assignment.entity.ClassTeacherAssignment;
import com.vidya.tenantdata.assignment.repository.ClassTeacherAssignmentRepository;
import com.vidya.tenantdata.attendance.dto.AttendanceDetailResponse;
import com.vidya.tenantdata.attendance.dto.AttendanceResponse;
import com.vidya.tenantdata.attendance.dto.AttendanceSummaryResponse;
import com.vidya.tenantdata.attendance.dto.BulkAttendanceRequest;
import com.vidya.tenantdata.attendance.dto.BulkAttendanceResponse;
import com.vidya.tenantdata.attendance.dto.DailyAttendanceEntryResponse;
import com.vidya.tenantdata.attendance.dto.DailySectionAttendanceResponse;
import com.vidya.tenantdata.attendance.dto.MarkAttendanceRequest;
import com.vidya.tenantdata.attendance.dto.UpdateAttendanceRequest;
import com.vidya.tenantdata.attendance.dto.UpdateAttendanceStatusRequest;
import com.vidya.tenantdata.attendance.entity.StudentAttendance;
import com.vidya.tenantdata.attendance.enums.AttendanceStatus;
import com.vidya.tenantdata.attendance.mapper.StudentAttendanceMapper;
import com.vidya.tenantdata.attendance.repository.StudentAttendanceRepository;
import com.vidya.tenantdata.calendar.dto.DayStatusResponse;
import com.vidya.tenantdata.calendar.enums.DayStatus;
import com.vidya.tenantdata.calendar.service.CalendarService;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.entity.StudentAcademicEnrollment;
import com.vidya.tenantdata.student.entity.StudentEnrollmentStatus;
import com.vidya.tenantdata.student.repository.StudentAcademicEnrollmentRepository;
import com.vidya.tenantdata.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementation of all student attendance business operations in the tenant database schema.
 *
 * <p><b>Business rules enforced:</b>
 * <ul>
 *   <li>Academic year must be ACTIVE for new attendance records</li>
 *   <li>Student must exist, not be deleted, and have an ACTIVE academic assignment
 *       for the requested year/class/section</li>
 *   <li>Attendance date must be within the academic year's date range</li>
 *   <li>Attendance date must not be in the future</li>
 *   <li>Attendance date must be a working day unless explicitly overridden with proper authority</li>
 *   <li>At most one active attendance record per (student, academicYear, date) — enforced at DB level too</li>
 *   <li>Bulk operations are atomic — one failure rejects the entire batch</li>
 *   <li>Teacher authorization via {@link ClassTeacherAssignment} is checked for TEACHER-type staff</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentAttendanceServiceImpl implements StudentAttendanceService {

    private final StudentAttendanceRepository attendanceRepository;
    private final StudentRepository studentRepository;
    private final StudentAcademicEnrollmentRepository enrollmentRepository;
    private final StudentAcademicAssignmentRepository studentAcademicAssignmentRepository;
    private final AcademicYearRepository academicYearRepository;
    private final AcademicClassRepository academicClassRepository;
    private final SectionRepository sectionRepository;
    private final StaffRepository staffRepository;
    private final ClassTeacherAssignmentRepository classTeacherAssignmentRepository;
    private final CalendarService calendarService;

    private java.time.Clock clock = java.time.Clock.systemDefaultZone();

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setClock(java.time.Clock clock) {
        if (clock != null) {
            this.clock = clock;
        }
    }

    // ── Mark individual attendance ─────────────────────────────────────────────

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public AttendanceDetailResponse markAttendance(MarkAttendanceRequest request, UUID markerStaffId) {
        log.info("Marking attendance: student [{}], year [{}], class [{}], section [{}], date [{}], status [{}]",
                request.getStudentId(), request.getAcademicYearId(), request.getAcademicClassId(),
                request.getSectionId(), request.getAttendanceDate(), request.getStatus());

        // Validate status is not DELETED (only PRESENT/ABSENT/LATE/HALF_DAY/EXCUSED allowed for new records)
        validateIncomingStatus(request.getStatus());

        // Resolve academic context
        AcademicYear academicYear = resolveAndValidateAcademicYearForMarking(request.getAcademicYearId());
        AcademicClass academicClass = resolveAcademicClass(request.getAcademicClassId());
        Section section = resolveAndValidateSection(request.getSectionId(), academicYear, academicClass);

        // Validate date
        validateAttendanceDate(request.getAttendanceDate(), academicYear);

        // Validate calendar working day status vs override
        boolean isOverride = validateCalendarStatusForDate(
                request.getAttendanceDate(), request.getAllowNonWorkingDay(), request.getOverrideReason());
        String effectiveOverrideReason = isOverride ? request.getOverrideReason().trim() : null;

        // Validate student and assignment
        Student student = resolveAndValidateStudent(request.getStudentId());
        validateStudentAcademicAssignment(student, academicYear, academicClass, section);

        // Resolve marking staff (optional — may be null for admin system-level marks)
        Staff markerStaff = resolveMarkerStaff(markerStaffId);

        // Validate teacher authorization if marker is a TEACHER
        if (markerStaff != null) {
            validateTeacherAuthorization(markerStaff, academicYear, section);
        }

        // Duplicate detection (app layer — DB partial unique index is the final guarantee)
        if (attendanceRepository.existsActiveByStudentAndYearAndDate(
                student.getId(), academicYear.getId(), request.getAttendanceDate(), null)) {
            throw new DuplicateResourceException(
                    "StudentAttendance",
                    "studentId, academicYearId, attendanceDate",
                    "Attendance already exists for this student on " + request.getAttendanceDate()
            );
        }

        // Generate unique attendance code
        String code = generateUniqueAttendanceCode();

        // Build and persist
        StudentAttendance attendance = new StudentAttendance(
                code, student, academicYear, academicClass, section,
                request.getAttendanceDate(), request.getStatus(), request.getRemarks(), markerStaff,
                isOverride, effectiveOverrideReason
        );

        StudentAttendance saved = attendanceRepository.save(attendance);
        log.info("Marked attendance [{}] for student [{}] on [{}]: [{}]",
                saved.getAttendanceCode(), student.getStudentCode(), request.getAttendanceDate(), request.getStatus());
        return StudentAttendanceMapper.toDetailResponse(saved);
    }

    // ── Update attendance (correction) ────────────────────────────────────────

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public AttendanceDetailResponse updateAttendance(UUID id, UpdateAttendanceRequest request, UUID markerStaffId) {
        log.info("Updating attendance id [{}] → status [{}]", id, request.getStatus());
        validateIncomingStatus(request.getStatus());

        StudentAttendance attendance = attendanceRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("StudentAttendance", id));

        Staff markerStaff = resolveMarkerStaff(markerStaffId);
        if (markerStaff != null) {
            validateTeacherAuthorization(markerStaff, attendance.getAcademicYear(), attendance.getSection());
        }

        attendance.correct(request.getStatus(), request.getRemarks(), markerStaff);
        StudentAttendance saved = attendanceRepository.save(attendance);
        log.info("Corrected attendance [{}]: status now [{}]", saved.getAttendanceCode(), saved.getStatus());
        return StudentAttendanceMapper.toDetailResponse(saved);
    }

    // ── Status-only update ────────────────────────────────────────────────────

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public AttendanceDetailResponse updateAttendanceStatus(UUID id, UpdateAttendanceStatusRequest request, UUID markerStaffId) {
        log.info("Patching attendance id [{}] → status [{}]", id, request.getStatus());
        validateIncomingStatus(request.getStatus());

        StudentAttendance attendance = attendanceRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("StudentAttendance", id));

        Staff markerStaff = resolveMarkerStaff(markerStaffId);
        if (markerStaff != null) {
            validateTeacherAuthorization(markerStaff, attendance.getAcademicYear(), attendance.getSection());
        }

        attendance.correct(request.getStatus(), null, markerStaff);
        StudentAttendance saved = attendanceRepository.save(attendance);
        return StudentAttendanceMapper.toDetailResponse(saved);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public AttendanceDetailResponse getAttendance(UUID id) {
        StudentAttendance attendance = attendanceRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("StudentAttendance", id));
        return StudentAttendanceMapper.toDetailResponse(attendance);
    }

    // ── Soft delete ───────────────────────────────────────────────────────────

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteAttendance(UUID id) {
        log.info("Soft-deleting attendance id [{}]", id);
        StudentAttendance attendance = attendanceRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("StudentAttendance", id));
        attendance.softDelete();
        attendanceRepository.save(attendance);
    }

    // ── Student attendance history ────────────────────────────────────────────

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Page<AttendanceResponse> getStudentAttendanceHistory(
            UUID studentId, UUID academicYearId, LocalDate fromDate, LocalDate toDate,
            AttendanceStatus status, Pageable pageable) {
        // Verify student exists
        studentRepository.findByIdAndNotDeleted(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));
        return attendanceRepository.findStudentAttendanceHistory(
                studentId, academicYearId, fromDate, toDate, status, pageable)
                .map(StudentAttendanceMapper::toResponse);
    }

    // ── Daily section attendance sheet ────────────────────────────────────────

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public DailySectionAttendanceResponse getDailySectionAttendance(
            UUID academicYearId, UUID sectionId, LocalDate attendanceDate) {

        if (attendanceDate == null) {
            throw new BusinessException("attendanceDate must not be null");
        }

        AcademicYear academicYear = academicYearRepository.findByIdAndNotDeleted(academicYearId)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", academicYearId));

        Section section = sectionRepository.findByIdAndNotDeleted(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Section", sectionId));

        if (!section.getAcademicYear().getId().equals(academicYearId)) {
            throw new BusinessException("Section does not belong to the specified AcademicYear");
        }

        // Fetch all active enrollments for this section
        List<StudentAcademicEnrollment> enrollments = enrollmentRepository.searchEnrollments(
                null, academicYearId, null, sectionId, StudentEnrollmentStatus.ACTIVE, null, Pageable.unpaged()
        ).getContent();

        // Fetch all attendance records for the section on this date
        List<StudentAttendance> records = attendanceRepository.findByYearAndSectionAndDate(
                academicYearId, sectionId, attendanceDate);

        // Map attendance by student ID for O(1) lookup
        Map<UUID, StudentAttendance> attendanceByStudent = records.stream()
                .collect(Collectors.toMap(sa -> sa.getStudent().getId(), Function.identity()));

        // Build daily entries — include students with no attendance record yet (null status)
        List<DailyAttendanceEntryResponse> entries = enrollments.stream()
                .map(enrollment -> {
                    UUID studentId = enrollment.getStudent().getId();
                    StudentAttendance record = attendanceByStudent.get(studentId);
                    return StudentAttendanceMapper.toDailyEntry(record, enrollment);
                })
                .collect(Collectors.toList());

        return DailySectionAttendanceResponse.builder()
                .academicYearId(academicYear.getId())
                .academicYearCode(academicYear.getCode())
                .academicClassId(section.getAcademicClass().getId())
                .academicClassName(section.getAcademicClass().getName())
                .sectionId(section.getId())
                .sectionName(section.getName())
                .attendanceDate(attendanceDate)
                .totalStudents(enrollments.size())
                .markedCount(records.size())
                .entries(entries)
                .build();
    }

    // ── Section attendance history ─────────────────────────────────────────────

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Page<AttendanceResponse> getSectionAttendanceHistory(
            UUID sectionId, LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        sectionRepository.findByIdAndNotDeleted(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Section", sectionId));
        if (fromDate == null || toDate == null) {
            throw new BusinessException("fromDate and toDate are required for section attendance history");
        }
        if (fromDate.isAfter(toDate)) {
            throw new BusinessException("fromDate must not be after toDate");
        }
        return attendanceRepository.findBySectionAndDateRange(sectionId, fromDate, toDate, pageable)
                .map(StudentAttendanceMapper::toResponse);
    }

    // ── Attendance summary ────────────────────────────────────────────────────

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public AttendanceSummaryResponse getAttendanceSummary(UUID studentId, UUID academicYearId) {
        Student student = studentRepository.findByIdAndNotDeleted(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));
        AcademicYear academicYear = academicYearRepository.findByIdAndNotDeleted(academicYearId)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", academicYearId));

        long total    = attendanceRepository.countTotalByStudentAndYear(studentId, academicYearId);
        long present  = attendanceRepository.countByStudentAndYearAndStatus(studentId, academicYearId, AttendanceStatus.PRESENT);
        long absent   = attendanceRepository.countByStudentAndYearAndStatus(studentId, academicYearId, AttendanceStatus.ABSENT);
        long late     = attendanceRepository.countByStudentAndYearAndStatus(studentId, academicYearId, AttendanceStatus.LATE);
        long halfDay  = attendanceRepository.countByStudentAndYearAndStatus(studentId, academicYearId, AttendanceStatus.HALF_DAY);
        long excused  = attendanceRepository.countByStudentAndYearAndStatus(studentId, academicYearId, AttendanceStatus.EXCUSED);

        // Attendance % = (present + 0.5*halfDay + excused) / total * 100  — standard school formula
        BigDecimal percentage = BigDecimal.ZERO;
        if (total > 0) {
            BigDecimal effectivePresent = BigDecimal.valueOf(present)
                    .add(BigDecimal.valueOf(halfDay).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP))
                    .add(BigDecimal.valueOf(excused));
            percentage = effectivePresent
                    .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // Calendar-aware metrics
        LocalDate today = LocalDate.now(clock);
        LocalDate endDate = academicYear.getEndDate();
        LocalDate effectiveEndDate = (endDate != null && endDate.isBefore(today)) ? endDate : today;

        LocalDate startDate = academicYear.getStartDate();
        Optional<StudentAcademicAssignment> assignmentOpt = studentAcademicAssignmentRepository
                .findActiveAssignment(studentId, academicYearId);

        LocalDate studentStart = null;
        if (assignmentOpt.isPresent() && assignmentOpt.get().getStartDate() != null) {
            studentStart = assignmentOpt.get().getStartDate();
        } else if (student.getAdmissionDate() != null) {
            studentStart = student.getAdmissionDate();
        }

        LocalDate effectiveStartDate = startDate;
        if (studentStart != null) {
            if (effectiveStartDate == null || studentStart.isAfter(effectiveStartDate)) {
                effectiveStartDate = studentStart;
            }
        }

        long workingDaysInRange = 0;
        long unmarkedWorkingDays = 0;
        long excludedNonWorkingDayRecords = 0;
        BigDecimal percentageOfWorkingDays = BigDecimal.ZERO;

        if (effectiveStartDate != null && !effectiveStartDate.isAfter(effectiveEndDate)) {
            List<DayStatusResponse> dayStatuses = calendarService.getDaysStatus(effectiveStartDate, effectiveEndDate);
            Set<LocalDate> workingDates = dayStatuses.stream()
                    .filter(ds -> ds.status() == DayStatus.WORKING)
                    .map(DayStatusResponse::date)
                    .collect(Collectors.toSet());

            workingDaysInRange = workingDates.size();

            List<StudentAttendance> allRecords = attendanceRepository.findAllActiveByStudentAndYear(studentId, academicYearId);
            Set<LocalDate> markedWorkingDates = new HashSet<>();
            BigDecimal presentWeightedDays = BigDecimal.ZERO;

            for (StudentAttendance sa : allRecords) {
                boolean isWorking = workingDates.contains(sa.getAttendanceDate());
                boolean isOverride = sa.isNonWorkingDayOverride();

                if (!isWorking && !isOverride) {
                    excludedNonWorkingDayRecords++;
                } else {
                    if (isWorking) {
                        markedWorkingDates.add(sa.getAttendanceDate());
                    }
                    if (sa.getStatus() == AttendanceStatus.PRESENT) {
                        presentWeightedDays = presentWeightedDays.add(BigDecimal.ONE);
                    } else if (sa.getStatus() == AttendanceStatus.HALF_DAY) {
                        presentWeightedDays = presentWeightedDays.add(new BigDecimal("0.5"));
                    } else if (sa.getStatus() == AttendanceStatus.EXCUSED) {
                        presentWeightedDays = presentWeightedDays.add(BigDecimal.ONE);
                    }
                }
            }

            unmarkedWorkingDays = Math.max(0, workingDaysInRange - markedWorkingDates.size());
            if (workingDaysInRange > 0) {
                percentageOfWorkingDays = presentWeightedDays
                        .divide(BigDecimal.valueOf(workingDaysInRange), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
            }
        }

        String studentName = null;
        if (student.getTenantUser() != null && student.getTenantUser().getProfile() != null) {
            studentName = student.getTenantUser().getProfile().getFullName();
        }

        return AttendanceSummaryResponse.builder()
                .studentId(student.getId())
                .studentCode(student.getStudentCode())
                .studentName(studentName)
                .academicYearId(academicYear.getId())
                .academicYearCode(academicYear.getCode())
                .totalDays(total)
                .presentCount(present)
                .absentCount(absent)
                .lateCount(late)
                .halfDayCount(halfDay)
                .excusedCount(excused)
                .attendancePercentage(percentage)
                .workingDaysInRange(workingDaysInRange)
                .unmarkedWorkingDays(unmarkedWorkingDays)
                .percentageOfWorkingDays(percentageOfWorkingDays)
                .excludedNonWorkingDayRecords(excludedNonWorkingDayRecords)
                .build();
    }

    // ── Bulk attendance ───────────────────────────────────────────────────────

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public BulkAttendanceResponse markBulkAttendance(BulkAttendanceRequest request, UUID markerStaffId) {
        log.info("Bulk attendance upsert: year [{}], class [{}], section [{}], date [{}], {} records",
                request.getAcademicYearId(), request.getAcademicClassId(), request.getSectionId(),
                request.getAttendanceDate(), request.getRecords().size());

        // 1. Validate date is non-null
        if (request.getAttendanceDate() == null) {
            throw new BusinessException("attendanceDate must not be null");
        }

        // 2. Validate academic context
        AcademicYear academicYear = resolveAndValidateAcademicYearForMarking(request.getAcademicYearId());
        AcademicClass academicClass = resolveAcademicClass(request.getAcademicClassId());
        Section section = resolveAndValidateSection(request.getSectionId(), academicYear, academicClass);

        // 3. Validate date
        validateAttendanceDate(request.getAttendanceDate(), academicYear);

        // 3b. Validate calendar working day status vs override (looked up once per distinct date)
        boolean isOverride = validateCalendarStatusForDate(
                request.getAttendanceDate(), request.getAllowNonWorkingDay(), request.getOverrideReason());
        String effectiveOverrideReason = isOverride ? request.getOverrideReason().trim() : null;

        // 4. Resolve marking staff and validate teacher authorization once (section-level)
        Staff markerStaff = resolveMarkerStaff(markerStaffId);
        if (markerStaff != null) {
            validateTeacherAuthorization(markerStaff, academicYear, section);
        }

        // 5. Detect duplicate student IDs in the request itself
        List<BulkAttendanceRequest.BulkAttendanceRecordRequest> records = request.getRecords();
        Set<UUID> seen = new HashSet<>();
        for (BulkAttendanceRequest.BulkAttendanceRecordRequest record : records) {
            if (record.getStudentId() == null) {
                throw new BusinessException("Each bulk attendance record must have a non-null studentId");
            }
            if (!seen.add(record.getStudentId())) {
                throw new BusinessException(
                        "Duplicate studentId in bulk request: " + record.getStudentId());
            }
        }

        // 6. Validate all statuses upfront
        for (BulkAttendanceRequest.BulkAttendanceRecordRequest record : records) {
            validateIncomingStatus(record.getStatus());
        }

        // 7. Validation phase: Validate every student and their academic assignment before any mutation
        Map<UUID, Student> studentMap = new java.util.LinkedHashMap<>();
        for (BulkAttendanceRequest.BulkAttendanceRecordRequest record : records) {
            Student student = resolveAndValidateStudent(record.getStudentId());
            validateStudentAcademicAssignment(student, academicYear, academicClass, section);
            studentMap.put(student.getId(), student);
        }

        // 8. Upsert phase: For each student, determine whether to create or update
        int createdCount = 0;
        int updatedCount = 0;
        List<StudentAttendance> toSave = new ArrayList<>(records.size());
        List<BulkAttendanceResponse.BulkAttendanceRecordResult> resultRecords = new ArrayList<>(records.size());

        for (BulkAttendanceRequest.BulkAttendanceRecordRequest record : records) {
            Student student = studentMap.get(record.getStudentId());
            Optional<StudentAttendance> existingOpt = attendanceRepository.findActiveByStudentAndYearAndDate(
                    student.getId(), academicYear.getId(), request.getAttendanceDate());

            if (existingOpt.isPresent()) {
                // Case B: Existing active record — UPDATE
                StudentAttendance existing = existingOpt.get();
                existing.correct(record.getStatus(), record.getRemarks(), markerStaff, isOverride, effectiveOverrideReason);
                toSave.add(existing);
                updatedCount++;

                resultRecords.add(BulkAttendanceResponse.BulkAttendanceRecordResult.builder()
                        .studentId(student.getId())
                        .attendanceId(existing.getId())
                        .attendanceCode(existing.getAttendanceCode())
                        .operation("UPDATED")
                        .status(record.getStatus())
                        .remarks(record.getRemarks())
                        .build());
            } else {
                // Case A: No active record (or only soft-deleted) — CREATE
                String code = generateUniqueAttendanceCode();
                StudentAttendance newAttendance = new StudentAttendance(
                        code, student, academicYear, academicClass, section,
                        request.getAttendanceDate(), record.getStatus(), record.getRemarks(), markerStaff,
                        isOverride, effectiveOverrideReason
                );
                toSave.add(newAttendance);
                createdCount++;

                resultRecords.add(BulkAttendanceResponse.BulkAttendanceRecordResult.builder()
                        .studentId(student.getId())
                        .attendanceId(null)
                        .attendanceCode(code)
                        .operation("CREATED")
                        .status(record.getStatus())
                        .remarks(record.getRemarks())
                        .build());
            }
        }

        // 9. Persist all records atomically in one batch
        List<StudentAttendance> saved = attendanceRepository.saveAll(toSave);
        log.info("Bulk attendance upsert complete: {} created, {} updated, {} total for section [{}] on [{}]",
                createdCount, updatedCount, saved.size(), section.getId(), request.getAttendanceDate());

        // Update attendance IDs for newly created records in the result list
        Map<String, UUID> codeToIdMap = saved.stream()
                .collect(Collectors.toMap(StudentAttendance::getAttendanceCode, StudentAttendance::getId));

        for (BulkAttendanceResponse.BulkAttendanceRecordResult res : resultRecords) {
            if (res.getAttendanceId() == null && res.getAttendanceCode() != null) {
                res.setAttendanceId(codeToIdMap.get(res.getAttendanceCode()));
            }
        }

        return BulkAttendanceResponse.builder()
                .created(createdCount)
                .updated(updatedCount)
                .total(records.size())
                .records(resultRecords)
                .build();
    }

    // ── Private validation helpers ────────────────────────────────────────────

    /**
     * Attendance status must not be DELETED for new marks / corrections via API.
     * Soft-delete is only allowed via the DELETE endpoint.
     */
    private void validateIncomingStatus(AttendanceStatus status) {
        if (status == null) {
            throw new BusinessException("Attendance status must not be null");
        }
        if (status == AttendanceStatus.DELETED) {
            throw new BusinessException(
                    "Cannot mark attendance with status DELETED. Use the DELETE endpoint to remove a record.");
        }
    }

    private AcademicYear resolveAndValidateAcademicYearForMarking(UUID academicYearId) {
        AcademicYear academicYear = academicYearRepository.findByIdAndNotDeleted(academicYearId)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", academicYearId));

        AcademicYearStatus status = academicYear.getStatus();
        if (status != AcademicYearStatus.ACTIVE) {
            throw new BusinessException(
                    "Cannot mark attendance against an academic year with status: " + status
                            + ". Only ACTIVE academic years allow new attendance records.");
        }
        return academicYear;
    }

    private AcademicClass resolveAcademicClass(UUID academicClassId) {
        return academicClassRepository.findByIdAndNotDeleted(academicClassId)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicClass", academicClassId));
    }

    private Section resolveAndValidateSection(UUID sectionId, AcademicYear academicYear, AcademicClass academicClass) {
        Section section = sectionRepository.findByIdAndNotDeleted(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Section", sectionId));

        if (!section.getAcademicYear().getId().equals(academicYear.getId())) {
            throw new BusinessException("Section does not belong to the specified AcademicYear");
        }
        if (!section.getAcademicClass().getId().equals(academicClass.getId())) {
            throw new BusinessException("Section does not belong to the specified AcademicClass");
        }
        return section;
    }

    private Student resolveAndValidateStudent(UUID studentId) {
        return studentRepository.findByIdAndNotDeleted(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));
    }

    /**
     * Validates that the student has an ACTIVE academic assignment for the given year/class/section.
     * This enforces the full academic hierarchy constraint.
     */
    private void validateStudentAcademicAssignment(Student student, AcademicYear academicYear,
                                                   AcademicClass academicClass, Section section) {
        // Find the active assignment for this student in this academic year
        Optional<StudentAcademicAssignment> assignmentOpt = studentAcademicAssignmentRepository
                .findActiveAssignment(student.getId(), academicYear.getId());

        if (assignmentOpt.isEmpty()) {
            throw new BusinessException(
                    "Student [" + student.getStudentCode() + "] does not have an active academic assignment "
                            + "for academic year [" + academicYear.getCode() + "]");
        }

        StudentAcademicAssignment assignment = assignmentOpt.get();

        if (assignment.getStatus() != StudentAssignmentStatus.ACTIVE) {
            throw new BusinessException(
                    "Student [" + student.getStudentCode() + "] academic assignment status is "
                            + assignment.getStatus() + " (must be ACTIVE)");
        }

        if (!assignment.getAcademicClass().getId().equals(academicClass.getId())) {
            throw new BusinessException(
                    "Student [" + student.getStudentCode() + "] is assigned to a different AcademicClass "
                            + "(" + assignment.getAcademicClass().getName() + "). "
                            + "Cannot mark attendance for AcademicClass [" + academicClass.getName() + "]");
        }

        if (!assignment.getSection().getId().equals(section.getId())) {
            throw new BusinessException(
                    "Student [" + student.getStudentCode() + "] is assigned to Section ["
                            + assignment.getSection().getName() + "], "
                            + "not Section [" + section.getName() + "]");
        }
    }

    /**
     * Validates the attendance date:
     * <ul>
     *   <li>Must not be null</li>
     *   <li>Must be within the academic year's start/end date range</li>
     *   <li>Must not be in the future</li>
     * </ul>
     */
    private void validateAttendanceDate(LocalDate attendanceDate, AcademicYear academicYear) {
        if (attendanceDate == null) {
            throw new BusinessException("attendanceDate must not be null");
        }

        LocalDate today = LocalDate.now(clock);
        if (attendanceDate.isAfter(today)) {
            throw new BusinessException(
                    "Cannot mark attendance for a future date: " + attendanceDate
                            + ". Today is " + today);
        }

        if (academicYear.getStartDate() != null && attendanceDate.isBefore(academicYear.getStartDate())) {
            throw new BusinessException(
                    "Attendance date [" + attendanceDate + "] is before the academic year start date ["
                            + academicYear.getStartDate() + "]");
        }

        if (academicYear.getEndDate() != null && attendanceDate.isAfter(academicYear.getEndDate())) {
            throw new BusinessException(
                    "Attendance date [" + attendanceDate + "] is after the academic year end date ["
                            + academicYear.getEndDate() + "]");
        }
    }

    /**
     * Resolves the marking staff, if markerStaffId is provided.
     *
     * @param markerStaffId UUID of the authenticated staff member (may be null for system-level or admin flows)
     * @return resolved Staff, or null if markerStaffId is null
     */
    private Staff resolveMarkerStaff(UUID markerStaffId) {
        if (markerStaffId == null) {
            return null;
        }
        return staffRepository.findByIdAndNotDeleted(markerStaffId).orElse(null);
    }

    /**
     * Validates teacher authorization for attendance marking.
     *
     * <p>If the staff member is of type TEACHER, they must have an ACTIVE ClassTeacherAssignment
     * for the target section in the target academic year. Administrative roles bypass this check
     * (they are assumed to have ATTENDANCE_CREATE permission at the controller level).
     *
     * <p>Non-TEACHER staff (ADMINISTRATIVE, SUPPORT, etc.) are NOT restricted here —
     * the RBAC permission system handles their access at the controller layer.
     */
    private void validateTeacherAuthorization(Staff staff, AcademicYear academicYear, Section section) {
        if (staff.getStaffType() != com.vidya.tenantdata.staff.enums.StaffType.TEACHER) {
            // Non-teacher staff — authorization handled by RBAC permissions
            return;
        }

        // Employment status check
        EmploymentStatus empStatus = staff.getEmploymentStatus();
        if (empStatus == EmploymentStatus.TERMINATED
                || empStatus == EmploymentStatus.RETIRED
                || empStatus == EmploymentStatus.DELETED
                || empStatus == EmploymentStatus.SUSPENDED
                || empStatus == EmploymentStatus.RESIGNED
                || empStatus == EmploymentStatus.INACTIVE) {
            throw new BusinessException(
                    "Staff member with employment status [" + empStatus + "] is not authorized to mark attendance");
        }

        // Teacher must have an ACTIVE class teacher assignment for this section + year
        Optional<ClassTeacherAssignment> ctaOpt = classTeacherAssignmentRepository
                .findActiveBySectionAndAcademicYear(academicYear.getId(), section.getId());

        if (ctaOpt.isEmpty() || !ctaOpt.get().getStaff().getId().equals(staff.getId())) {
            throw new BusinessException(
                    "Teacher [" + staff.getStaffCode() + "] is not the authorized class teacher "
                            + "for section [" + section.getName() + "] in academic year ["
                            + academicYear.getCode() + "]. Only the designated class teacher "
                            + "may mark attendance for this section.");
        }
    }

    private String generateUniqueAttendanceCode() {
        String code = CodeGenerator.attendanceCode();
        int attempts = 0;
        while (attendanceRepository.existsByAttendanceCode(code) && attempts < 5) {
            code = CodeGenerator.attendanceCode();
            attempts++;
        }
        if (attendanceRepository.existsByAttendanceCode(code)) {
            throw new DuplicateResourceException("StudentAttendance", "attendanceCode", code);
        }
        return code;
    }

    // ── Gaps report ───────────────────────────────────────────────────────────

    @Override
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<LocalDate> getMissingAttendanceDates(UUID sectionId, UUID academicYearId, LocalDate fromDate, LocalDate toDate) {
        if (sectionId == null) {
            throw new BusinessException("sectionId must not be null");
        }
        if (academicYearId == null) {
            throw new BusinessException("academicYearId must not be null");
        }
        if (fromDate == null || toDate == null) {
            throw new BusinessException("fromDate and toDate must not be null");
        }
        if (fromDate.isAfter(toDate)) {
            throw new BusinessException("fromDate must not be after toDate");
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        if (days > 92) {
            throw new BusinessException("Date range must not exceed 92 days. Requested: " + days + " days.");
        }

        // Verify section and year exist
        sectionRepository.findByIdAndNotDeleted(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Section", sectionId));
        academicYearRepository.findByIdAndNotDeleted(academicYearId)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", academicYearId));

        List<DayStatusResponse> dayStatuses = calendarService.getDaysStatus(fromDate, toDate);
        List<LocalDate> workingDates = dayStatuses.stream()
                .filter(ds -> ds.status() == DayStatus.WORKING)
                .map(DayStatusResponse::date)
                .sorted()
                .toList();

        Set<LocalDate> markedDates = attendanceRepository.findDistinctAttendanceDatesBySectionAndYearAndRange(
                sectionId, academicYearId, fromDate, toDate
        );

        return workingDates.stream()
                .filter(d -> !markedDates.contains(d))
                .toList();
    }

    // ── Calendar validation helpers ───────────────────────────────────────────

    private boolean validateCalendarStatusForDate(LocalDate date, Boolean allowNonWorkingDay, String overrideReason) {
        DayStatusResponse dayStatus = calendarService.getDayStatus(date);
        boolean isNonWorking = dayStatus.status() != DayStatus.WORKING;
        boolean allowOverride = Boolean.TRUE.equals(allowNonWorkingDay);

        if (isNonWorking) {
            if (!allowOverride) {
                if (dayStatus.status() == DayStatus.HOLIDAY) {
                    throw new BusinessException("Cannot mark attendance on holiday: " + dayStatus.holidayName()
                            + " (" + date + ")");
                } else {
                    throw new BusinessException("Cannot mark attendance on weekly off: " + date
                            + " (" + date.getDayOfWeek() + ")");
                }
            }
            validateOverrideAuthority();
            validateOverrideReason(overrideReason);
            return true;
        }
        return false;
    }

    private void validateOverrideAuthority() {
        boolean authorized = SecurityUtils.hasAuthority("ATTENDANCE_OVERRIDE_NON_WORKING")
                || SecurityUtils.hasAuthority("ROLE_SCHOOL_ADMIN")
                || SecurityUtils.hasAuthority("ROLE_SCHOOL_OWNER")
                || SecurityUtils.hasAuthority("ROLE_PLATFORM_ADMIN")
                || SecurityUtils.hasAuthority("ROLE_PLATFORM_SUPER_ADMIN");
        if (!authorized) {
            throw new AccessDeniedException(
                    "Caller does not have authority to override non-working day attendance. Required authority: ATTENDANCE_OVERRIDE_NON_WORKING");
        }
    }

    private void validateOverrideReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException("overrideReason is required when allowNonWorkingDay is true");
        }
        if (reason.trim().length() > 255) {
            throw new BusinessException("overrideReason must not exceed 255 characters");
        }
    }
}
