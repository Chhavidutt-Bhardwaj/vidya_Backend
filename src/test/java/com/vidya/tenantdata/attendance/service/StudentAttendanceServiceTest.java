package com.vidya.tenantdata.attendance.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
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
import com.vidya.tenantdata.assignment.repository.ClassTeacherAssignmentRepository;
import com.vidya.tenantdata.attendance.dto.AttendanceDetailResponse;
import com.vidya.tenantdata.attendance.dto.AttendanceSummaryResponse;
import com.vidya.tenantdata.attendance.dto.BulkAttendanceRequest;
import com.vidya.tenantdata.attendance.dto.BulkAttendanceResponse;
import com.vidya.tenantdata.attendance.dto.MarkAttendanceRequest;
import com.vidya.tenantdata.attendance.dto.UpdateAttendanceRequest;
import com.vidya.tenantdata.attendance.dto.UpdateAttendanceStatusRequest;
import com.vidya.tenantdata.attendance.entity.StudentAttendance;
import com.vidya.tenantdata.attendance.enums.AttendanceStatus;
import com.vidya.tenantdata.attendance.repository.StudentAttendanceRepository;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.entity.StudentStatus;
import com.vidya.tenantdata.student.repository.StudentAcademicEnrollmentRepository;
import com.vidya.tenantdata.student.repository.StudentRepository;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserType;
import com.vidya.tenantdata.calendar.dto.DayStatusResponse;
import com.vidya.tenantdata.calendar.enums.DayStatus;
import com.vidya.tenantdata.calendar.service.CalendarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StudentAttendanceService — Unit Tests")
class StudentAttendanceServiceTest {

    @Mock private StudentAttendanceRepository attendanceRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private StudentAcademicEnrollmentRepository enrollmentRepository;
    @Mock private StudentAcademicAssignmentRepository studentAcademicAssignmentRepository;
    @Mock private AcademicYearRepository academicYearRepository;
    @Mock private AcademicClassRepository academicClassRepository;
    @Mock private SectionRepository sectionRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private ClassTeacherAssignmentRepository classTeacherAssignmentRepository;
    @Mock private CalendarService calendarService;

    @InjectMocks
    private StudentAttendanceServiceImpl service;

    private AcademicYear activeYear;
    private AcademicClass academicClass;
    private Section section;
    private Student student;
    private StudentAcademicAssignment assignment;

    private final UUID yearId    = UUID.randomUUID();
    private final UUID classId   = UUID.randomUUID();
    private final UUID sectionId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID attendanceId = UUID.randomUUID();

    private final LocalDate TODAY = LocalDate.now();
    private final LocalDate YESTERDAY = TODAY.minusDays(1);
    private final LocalDate TOMORROW = TODAY.plusDays(1);
    private final LocalDate YEAR_START = TODAY.minusMonths(6);
    private final LocalDate YEAR_END = TODAY.plusMonths(6);

    @BeforeEach
    void setUp() {
        activeYear = new AcademicYear("AY2026-27", "2026-2027", YEAR_START, YEAR_END, true, AcademicYearStatus.ACTIVE);
        activeYear.setId(yearId);

        academicClass = new AcademicClass("CLS-5", "Class 5", "Class 5", 5);
        academicClass.setId(classId);

        section = new Section(activeYear, academicClass, "SEC-5A", "Section A", 40);
        section.setId(sectionId);

        TenantUser tenantUser = new TenantUser("USR-001", "student@test.com", "9876543210", TenantUserType.STUDENT);
        tenantUser.setId(UUID.randomUUID());

        student = new Student("STU-A1B2C3D4", tenantUser, "ADM-001", YEAR_START);
        student.setId(studentId);

        assignment = new StudentAcademicAssignment(student, activeYear, academicClass, section, "001", YEAR_START);
        assignment.setId(UUID.randomUUID());

        lenient().when(calendarService.getDayStatus(any()))
                .thenReturn(new DayStatusResponse(TODAY, TODAY.getDayOfWeek(), DayStatus.WORKING, null, null));
    }

    // ── Standard creates ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Mark PRESENT attendance — success")
    void markAttendance_present_success() {
        setupStandardMocks();
        when(attendanceRepository.existsByAttendanceCode(any())).thenReturn(false);
        when(attendanceRepository.save(any())).thenAnswer(inv -> {
            StudentAttendance a = inv.getArgument(0);
            a.setId(attendanceId);
            return a;
        });

        MarkAttendanceRequest request = new MarkAttendanceRequest(
                studentId, yearId, classId, sectionId, TODAY, AttendanceStatus.PRESENT, "On time");

        AttendanceDetailResponse response = service.markAttendance(request, null);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    @DisplayName("Mark ABSENT attendance — success")
    void markAttendance_absent_success() {
        setupStandardMocks();
        when(attendanceRepository.existsByAttendanceCode(any())).thenReturn(false);
        when(attendanceRepository.save(any())).thenAnswer(inv -> { StudentAttendance a = inv.getArgument(0); a.setId(attendanceId); return a; });

        MarkAttendanceRequest request = new MarkAttendanceRequest(
                studentId, yearId, classId, sectionId, TODAY, AttendanceStatus.ABSENT, "Sick");
        AttendanceDetailResponse response = service.markAttendance(request, null);
        assertThat(response.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
    }

    @Test
    @DisplayName("Mark LATE attendance — success")
    void markAttendance_late_success() {
        setupStandardMocks();
        when(attendanceRepository.existsByAttendanceCode(any())).thenReturn(false);
        when(attendanceRepository.save(any())).thenAnswer(inv -> { StudentAttendance a = inv.getArgument(0); a.setId(attendanceId); return a; });

        MarkAttendanceRequest request = new MarkAttendanceRequest(
                studentId, yearId, classId, sectionId, TODAY, AttendanceStatus.LATE, null);
        AttendanceDetailResponse response = service.markAttendance(request, null);
        assertThat(response.getStatus()).isEqualTo(AttendanceStatus.LATE);
    }

    @Test
    @DisplayName("Mark HALF_DAY attendance — success")
    void markAttendance_halfDay_success() {
        setupStandardMocks();
        when(attendanceRepository.existsByAttendanceCode(any())).thenReturn(false);
        when(attendanceRepository.save(any())).thenAnswer(inv -> { StudentAttendance a = inv.getArgument(0); a.setId(attendanceId); return a; });

        MarkAttendanceRequest request = new MarkAttendanceRequest(
                studentId, yearId, classId, sectionId, TODAY, AttendanceStatus.HALF_DAY, null);
        AttendanceDetailResponse response = service.markAttendance(request, null);
        assertThat(response.getStatus()).isEqualTo(AttendanceStatus.HALF_DAY);
    }

    @Test
    @DisplayName("Mark EXCUSED attendance — success")
    void markAttendance_excused_success() {
        setupStandardMocks();
        when(attendanceRepository.existsByAttendanceCode(any())).thenReturn(false);
        when(attendanceRepository.save(any())).thenAnswer(inv -> { StudentAttendance a = inv.getArgument(0); a.setId(attendanceId); return a; });

        MarkAttendanceRequest request = new MarkAttendanceRequest(
                studentId, yearId, classId, sectionId, TODAY, AttendanceStatus.EXCUSED, "Field trip");
        AttendanceDetailResponse response = service.markAttendance(request, null);
        assertThat(response.getStatus()).isEqualTo(AttendanceStatus.EXCUSED);
    }

    // ── Validation failures ───────────────────────────────────────────────────

    @Test
    @DisplayName("Mark DELETED status — rejected")
    void markAttendance_deletedStatus_rejected() {
        MarkAttendanceRequest request = new MarkAttendanceRequest(
                studentId, yearId, classId, sectionId, TODAY, AttendanceStatus.DELETED, null);
        assertThatThrownBy(() -> service.markAttendance(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("DELETED");
    }

    @Test
    @DisplayName("Inactive academic year — rejected")
    void markAttendance_inactiveYear_rejected() {
        AcademicYear closedYear = new AcademicYear("AY-OLD", "2024-2025", YEAR_START.minusYears(2), YEAR_START.minusYears(1), false, AcademicYearStatus.CLOSED);
        closedYear.setId(yearId);
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(closedYear));

        MarkAttendanceRequest request = new MarkAttendanceRequest(
                studentId, yearId, classId, sectionId, TODAY, AttendanceStatus.PRESENT, null);
        assertThatThrownBy(() -> service.markAttendance(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ACTIVE academic year");
    }

    @Test
    @DisplayName("Student not found — throws ResourceNotFoundException")
    void markAttendance_studentNotFound_throwsNotFound() {
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(activeYear));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(academicClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(section));
        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.empty());

        MarkAttendanceRequest request = new MarkAttendanceRequest(
                studentId, yearId, classId, sectionId, TODAY, AttendanceStatus.PRESENT, null);
        assertThatThrownBy(() -> service.markAttendance(request, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("No active academic assignment — rejected")
    void markAttendance_noActiveAssignment_rejected() {
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(activeYear));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(academicClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(section));
        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(student));
        when(studentAcademicAssignmentRepository.findActiveAssignment(studentId, yearId))
                .thenReturn(Optional.empty());

        MarkAttendanceRequest request = new MarkAttendanceRequest(
                studentId, yearId, classId, sectionId, TODAY, AttendanceStatus.PRESENT, null);
        assertThatThrownBy(() -> service.markAttendance(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not have an active academic assignment");
    }

    @Test
    @DisplayName("Student assigned to wrong class — rejected")
    void markAttendance_wrongClass_rejected() {
        AcademicClass wrongClass = new AcademicClass("CLS-10", "Class 10", "Class 10", 10);
        UUID wrongClassId = UUID.randomUUID();
        wrongClass.setId(wrongClassId);

        StudentAcademicAssignment wrongAssignment = new StudentAcademicAssignment(
                student, activeYear, wrongClass, section, "001", YEAR_START);

        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(activeYear));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(academicClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(section));
        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(student));
        when(studentAcademicAssignmentRepository.findActiveAssignment(studentId, yearId))
                .thenReturn(Optional.of(wrongAssignment));

        MarkAttendanceRequest request = new MarkAttendanceRequest(
                studentId, yearId, classId, sectionId, TODAY, AttendanceStatus.PRESENT, null);
        assertThatThrownBy(() -> service.markAttendance(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("different AcademicClass");
    }

    @Test
    @DisplayName("Student assigned to wrong section — rejected")
    void markAttendance_wrongSection_rejected() {
        Section wrongSection = new Section(activeYear, academicClass, "SEC-5B", "Section B", 40);
        UUID wrongSectionId = UUID.randomUUID();
        wrongSection.setId(wrongSectionId);

        StudentAcademicAssignment wrongAssignment = new StudentAcademicAssignment(
                student, activeYear, academicClass, wrongSection, "001", YEAR_START);

        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(activeYear));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(academicClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(section));
        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(student));
        when(studentAcademicAssignmentRepository.findActiveAssignment(studentId, yearId))
                .thenReturn(Optional.of(wrongAssignment));

        MarkAttendanceRequest request = new MarkAttendanceRequest(
                studentId, yearId, classId, sectionId, TODAY, AttendanceStatus.PRESENT, null);
        assertThatThrownBy(() -> service.markAttendance(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Section");
    }

    @Test
    @DisplayName("Future date — rejected")
    void markAttendance_futureDate_rejected() {
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(activeYear));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(academicClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(section));

        MarkAttendanceRequest request = new MarkAttendanceRequest(
                studentId, yearId, classId, sectionId, TOMORROW, AttendanceStatus.PRESENT, null);
        assertThatThrownBy(() -> service.markAttendance(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("future date");
    }

    @Test
    @DisplayName("Date before academic year start — rejected")
    void markAttendance_beforeYearStart_rejected() {
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(activeYear));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(academicClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(section));

        LocalDate beforeStart = YEAR_START.minusDays(1);
        MarkAttendanceRequest request = new MarkAttendanceRequest(
                studentId, yearId, classId, sectionId, beforeStart, AttendanceStatus.PRESENT, null);
        assertThatThrownBy(() -> service.markAttendance(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("before the academic year start date");
    }

    @Test
    @DisplayName("Duplicate attendance — rejected")
    void markAttendance_duplicate_rejected() {
        setupStandardMocks();
        when(attendanceRepository.existsActiveByStudentAndYearAndDate(studentId, yearId, TODAY, null))
                .thenReturn(true);

        MarkAttendanceRequest request = new MarkAttendanceRequest(
                studentId, yearId, classId, sectionId, TODAY, AttendanceStatus.PRESENT, null);
        assertThatThrownBy(() -> service.markAttendance(request, null))
                .isInstanceOf(DuplicateResourceException.class);
    }

    // ── Update / correction ───────────────────────────────────────────────────

    @Test
    @DisplayName("Update attendance status — success")
    void updateAttendance_success() {
        StudentAttendance existing = buildAttendance(AttendanceStatus.ABSENT);
        when(attendanceRepository.findByIdAndNotDeleted(attendanceId)).thenReturn(Optional.of(existing));
        when(attendanceRepository.save(any())).thenReturn(existing);

        UpdateAttendanceRequest req = new UpdateAttendanceRequest(AttendanceStatus.PRESENT, "Correction");
        AttendanceDetailResponse response = service.updateAttendance(attendanceId, req, null);
        assertThat(existing.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    @DisplayName("Status update PATCH — success")
    void updateAttendanceStatus_success() {
        StudentAttendance existing = buildAttendance(AttendanceStatus.ABSENT);
        when(attendanceRepository.findByIdAndNotDeleted(attendanceId)).thenReturn(Optional.of(existing));
        when(attendanceRepository.save(any())).thenReturn(existing);

        UpdateAttendanceStatusRequest req = new UpdateAttendanceStatusRequest(AttendanceStatus.LATE);
        service.updateAttendanceStatus(attendanceId, req, null);
        assertThat(existing.getStatus()).isEqualTo(AttendanceStatus.LATE);
    }

    @Test
    @DisplayName("Update not found — throws ResourceNotFoundException")
    void updateAttendance_notFound_throws() {
        when(attendanceRepository.findByIdAndNotDeleted(attendanceId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateAttendance(attendanceId, new UpdateAttendanceRequest(AttendanceStatus.PRESENT, null), null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Soft delete ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Soft delete — success")
    void deleteAttendance_success() {
        StudentAttendance existing = buildAttendance(AttendanceStatus.PRESENT);
        when(attendanceRepository.findByIdAndNotDeleted(attendanceId)).thenReturn(Optional.of(existing));
        when(attendanceRepository.save(any())).thenReturn(existing);

        service.deleteAttendance(attendanceId);
        assertThat(existing.isDeleted()).isTrue();
    }

    // ── Bulk attendance upsert ─────────────────────────────────────────────────

    @Test
    @DisplayName("Bulk attendance — Case A: Create all 5 submitted students when none exist")
    void markBulkAttendance_create_emptyDatabase_allCreated() {
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(activeYear));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(academicClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(section));
        when(attendanceRepository.existsByAttendanceCode(any())).thenReturn(false);
        when(attendanceRepository.saveAll(any())).thenAnswer(inv -> {
            List<StudentAttendance> list = inv.getArgument(0);
            list.forEach(a -> {
                if (a.getId() == null) a.setId(UUID.randomUUID());
            });
            return list;
        });

        List<BulkAttendanceRequest.BulkAttendanceRecordRequest> recordReqs = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            UUID sId = UUID.randomUUID();
            Student s = createMockStudent(sId, "STU-00" + i);
            StudentAcademicAssignment saa = createMockAssignment(s, section);
            when(studentRepository.findByIdAndNotDeleted(sId)).thenReturn(Optional.of(s));
            when(studentAcademicAssignmentRepository.findActiveAssignment(sId, yearId)).thenReturn(Optional.of(saa));
            when(attendanceRepository.findActiveByStudentAndYearAndDate(sId, yearId, TODAY)).thenReturn(Optional.empty());
            recordReqs.add(new BulkAttendanceRequest.BulkAttendanceRecordRequest(sId, AttendanceStatus.PRESENT, "Note " + i));
        }

        BulkAttendanceRequest req = new BulkAttendanceRequest(yearId, classId, sectionId, TODAY, recordReqs);

        BulkAttendanceResponse response = service.markBulkAttendance(req, null);

        assertThat(response.getCreated()).isEqualTo(5);
        assertThat(response.getUpdated()).isEqualTo(0);
        assertThat(response.getTotal()).isEqualTo(5);
        assertThat(response.getRecords()).hasSize(5);
        assertThat(response.getRecords()).allMatch(r -> "CREATED".equals(r.getOperation()) && r.getAttendanceId() != null);
    }

    @Test
    @DisplayName("Bulk attendance — Case B: Update all 5 submitted students when all exist")
    void markBulkAttendance_update_allExisting_allUpdated() {
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(activeYear));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(academicClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(section));
        when(attendanceRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<BulkAttendanceRequest.BulkAttendanceRecordRequest> recordReqs = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            UUID sId = UUID.randomUUID();
            UUID existingAttId = UUID.randomUUID();
            String existingCode = "ATT-EXISTING-" + i;
            Student s = createMockStudent(sId, "STU-00" + i);
            StudentAcademicAssignment saa = createMockAssignment(s, section);

            StudentAttendance existing = new StudentAttendance(
                    existingCode, s, activeYear, academicClass, section, TODAY, AttendanceStatus.ABSENT, "Old note", null);
            existing.setId(existingAttId);

            when(studentRepository.findByIdAndNotDeleted(sId)).thenReturn(Optional.of(s));
            when(studentAcademicAssignmentRepository.findActiveAssignment(sId, yearId)).thenReturn(Optional.of(saa));
            when(attendanceRepository.findActiveByStudentAndYearAndDate(sId, yearId, TODAY)).thenReturn(Optional.of(existing));

            recordReqs.add(new BulkAttendanceRequest.BulkAttendanceRecordRequest(sId, AttendanceStatus.PRESENT, "Updated note " + i));
        }

        BulkAttendanceRequest req = new BulkAttendanceRequest(yearId, classId, sectionId, TODAY, recordReqs);

        BulkAttendanceResponse response = service.markBulkAttendance(req, null);

        assertThat(response.getCreated()).isEqualTo(0);
        assertThat(response.getUpdated()).isEqualTo(5);
        assertThat(response.getTotal()).isEqualTo(5);
        assertThat(response.getRecords()).allMatch(r -> "UPDATED".equals(r.getOperation()) && r.getStatus() == AttendanceStatus.PRESENT);
    }

    @Test
    @DisplayName("Bulk attendance — Mixed upsert: 3 created, 2 updated, total 5")
    void markBulkAttendance_mixedUpsert() {
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(activeYear));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(academicClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(section));
        when(attendanceRepository.existsByAttendanceCode(any())).thenReturn(false);
        when(attendanceRepository.saveAll(any())).thenAnswer(inv -> {
            List<StudentAttendance> list = inv.getArgument(0);
            list.forEach(a -> {
                if (a.getId() == null) a.setId(UUID.randomUUID());
            });
            return list;
        });

        List<BulkAttendanceRequest.BulkAttendanceRecordRequest> recordReqs = new java.util.ArrayList<>();
        // 2 existing
        for (int i = 1; i <= 2; i++) {
            UUID sId = UUID.randomUUID();
            Student s = createMockStudent(sId, "STU-EXISTING-" + i);
            StudentAcademicAssignment saa = createMockAssignment(s, section);
            StudentAttendance existing = new StudentAttendance(
                    "ATT-OLD-" + i, s, activeYear, academicClass, section, TODAY, AttendanceStatus.ABSENT, null, null);
            existing.setId(UUID.randomUUID());

            when(studentRepository.findByIdAndNotDeleted(sId)).thenReturn(Optional.of(s));
            when(studentAcademicAssignmentRepository.findActiveAssignment(sId, yearId)).thenReturn(Optional.of(saa));
            when(attendanceRepository.findActiveByStudentAndYearAndDate(sId, yearId, TODAY)).thenReturn(Optional.of(existing));
            recordReqs.add(new BulkAttendanceRequest.BulkAttendanceRecordRequest(sId, AttendanceStatus.PRESENT, "Corrected"));
        }

        // 3 new
        for (int i = 3; i <= 5; i++) {
            UUID sId = UUID.randomUUID();
            Student s = createMockStudent(sId, "STU-NEW-" + i);
            StudentAcademicAssignment saa = createMockAssignment(s, section);

            when(studentRepository.findByIdAndNotDeleted(sId)).thenReturn(Optional.of(s));
            when(studentAcademicAssignmentRepository.findActiveAssignment(sId, yearId)).thenReturn(Optional.of(saa));
            when(attendanceRepository.findActiveByStudentAndYearAndDate(sId, yearId, TODAY)).thenReturn(Optional.empty());
            recordReqs.add(new BulkAttendanceRequest.BulkAttendanceRecordRequest(sId, AttendanceStatus.LATE, "New entry"));
        }

        BulkAttendanceRequest req = new BulkAttendanceRequest(yearId, classId, sectionId, TODAY, recordReqs);

        BulkAttendanceResponse response = service.markBulkAttendance(req, null);

        assertThat(response.getCreated()).isEqualTo(3);
        assertThat(response.getUpdated()).isEqualTo(2);
        assertThat(response.getTotal()).isEqualTo(5);
    }

    @Test
    @DisplayName("Bulk attendance — Idempotent repeat: submitting same request twice")
    void markBulkAttendance_idempotentRepeat() {
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(activeYear));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(academicClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(section));
        when(attendanceRepository.existsByAttendanceCode(any())).thenReturn(false);
        when(attendanceRepository.saveAll(any())).thenAnswer(inv -> {
            List<StudentAttendance> list = inv.getArgument(0);
            list.forEach(a -> {
                if (a.getId() == null) a.setId(UUID.randomUUID());
            });
            return list;
        });

        // First pass: 1 new student
        Student s = createMockStudent(studentId, "STU-001");
        StudentAcademicAssignment saa = createMockAssignment(s, section);
        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(s));
        when(studentAcademicAssignmentRepository.findActiveAssignment(studentId, yearId)).thenReturn(Optional.of(saa));
        when(attendanceRepository.findActiveByStudentAndYearAndDate(studentId, yearId, TODAY)).thenReturn(Optional.empty());

        BulkAttendanceRequest req = new BulkAttendanceRequest(
                yearId, classId, sectionId, TODAY,
                List.of(new BulkAttendanceRequest.BulkAttendanceRecordRequest(studentId, AttendanceStatus.PRESENT, "Present today"))
        );

        BulkAttendanceResponse firstPass = service.markBulkAttendance(req, null);
        assertThat(firstPass.getCreated()).isEqualTo(1);
        assertThat(firstPass.getUpdated()).isEqualTo(0);

        // Second pass: same student now has existing attendance record
        StudentAttendance existing = new StudentAttendance(
                firstPass.getRecords().get(0).getAttendanceCode(), s, activeYear, academicClass, section, TODAY, AttendanceStatus.PRESENT, "Present today", null);
        existing.setId(firstPass.getRecords().get(0).getAttendanceId());
        when(attendanceRepository.findActiveByStudentAndYearAndDate(studentId, yearId, TODAY)).thenReturn(Optional.of(existing));

        BulkAttendanceResponse secondPass = service.markBulkAttendance(req, null);
        assertThat(secondPass.getCreated()).isEqualTo(0);
        assertThat(secondPass.getUpdated()).isEqualTo(1);
        assertThat(secondPass.getTotal()).isEqualTo(1);
        assertThat(secondPass.getRecords().get(0).getAttendanceId()).isEqualTo(firstPass.getRecords().get(0).getAttendanceId());
    }

    @Test
    @DisplayName("Bulk attendance — Duplicate studentId in request — rejected atomically")
    void markBulkAttendance_duplicateStudentId_rejected() {
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(activeYear));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(academicClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(section));

        BulkAttendanceRequest req = new BulkAttendanceRequest(
                yearId, classId, sectionId, TODAY,
                List.of(
                        new BulkAttendanceRequest.BulkAttendanceRecordRequest(studentId, AttendanceStatus.PRESENT, null),
                        new BulkAttendanceRequest.BulkAttendanceRecordRequest(studentId, AttendanceStatus.ABSENT, null)
                )
        );

        assertThatThrownBy(() -> service.markBulkAttendance(req, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Duplicate studentId");
    }

    @Test
    @DisplayName("Bulk attendance — Invalid student among batch — entire transaction fails")
    void markBulkAttendance_invalidStudent_failsAll() {
        UUID validId1 = UUID.randomUUID();
        UUID invalidId = UUID.randomUUID();
        UUID validId2 = UUID.randomUUID();

        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(activeYear));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(academicClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(section));

        Student s1 = createMockStudent(validId1, "STU-001");
        StudentAcademicAssignment saa1 = createMockAssignment(s1, section);
        when(studentRepository.findByIdAndNotDeleted(validId1)).thenReturn(Optional.of(s1));
        when(studentAcademicAssignmentRepository.findActiveAssignment(validId1, yearId)).thenReturn(Optional.of(saa1));

        // invalid student is not found
        when(studentRepository.findByIdAndNotDeleted(invalidId)).thenReturn(Optional.empty());

        BulkAttendanceRequest req = new BulkAttendanceRequest(
                yearId, classId, sectionId, TODAY,
                List.of(
                        new BulkAttendanceRequest.BulkAttendanceRecordRequest(validId1, AttendanceStatus.PRESENT, null),
                        new BulkAttendanceRequest.BulkAttendanceRecordRequest(invalidId, AttendanceStatus.ABSENT, null),
                        new BulkAttendanceRequest.BulkAttendanceRecordRequest(validId2, AttendanceStatus.PRESENT, null)
                )
        );

        assertThatThrownBy(() -> service.markBulkAttendance(req, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Bulk attendance — Student assigned to wrong section — entire transaction rejected")
    void markBulkAttendance_wrongSection_rejected() {
        Section otherSection = new Section(activeYear, academicClass, "SEC-5B", "Section B", 40);
        otherSection.setId(UUID.randomUUID());

        Student s = createMockStudent(studentId, "STU-001");
        StudentAcademicAssignment wrongAssignment = createMockAssignment(s, otherSection);

        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(activeYear));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(academicClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(section));
        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(s));
        when(studentAcademicAssignmentRepository.findActiveAssignment(studentId, yearId)).thenReturn(Optional.of(wrongAssignment));

        BulkAttendanceRequest req = new BulkAttendanceRequest(
                yearId, classId, sectionId, TODAY,
                List.of(new BulkAttendanceRequest.BulkAttendanceRecordRequest(studentId, AttendanceStatus.PRESENT, null))
        );

        assertThatThrownBy(() -> service.markBulkAttendance(req, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Section");
    }

    @Test
    @DisplayName("Bulk attendance — Future date — rejected")
    void markBulkAttendance_futureDate_rejected() {
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(activeYear));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(academicClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(section));

        BulkAttendanceRequest req = new BulkAttendanceRequest(
                yearId, classId, sectionId, TOMORROW,
                List.of(new BulkAttendanceRequest.BulkAttendanceRecordRequest(studentId, AttendanceStatus.PRESENT, null))
        );

        assertThatThrownBy(() -> service.markBulkAttendance(req, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("future date");
    }

    @Test
    @DisplayName("Bulk attendance — Soft-deleted record treated as historical data, creates new active record")
    void markBulkAttendance_softDeletedRecord_createsNewActiveRecord() {
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(activeYear));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(academicClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(section));
        when(attendanceRepository.existsByAttendanceCode(any())).thenReturn(false);
        when(attendanceRepository.saveAll(any())).thenAnswer(inv -> {
            List<StudentAttendance> list = inv.getArgument(0);
            list.forEach(a -> {
                if (a.getId() == null) a.setId(UUID.randomUUID());
            });
            return list;
        });

        Student s = createMockStudent(studentId, "STU-001");
        StudentAcademicAssignment saa = createMockAssignment(s, section);
        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(s));
        when(studentAcademicAssignmentRepository.findActiveAssignment(studentId, yearId)).thenReturn(Optional.of(saa));
        // Soft-deleted record in DB means findActiveByStudentAndYearAndDate returns empty
        when(attendanceRepository.findActiveByStudentAndYearAndDate(studentId, yearId, TODAY)).thenReturn(Optional.empty());

        BulkAttendanceRequest req = new BulkAttendanceRequest(
                yearId, classId, sectionId, TODAY,
                List.of(new BulkAttendanceRequest.BulkAttendanceRecordRequest(studentId, AttendanceStatus.PRESENT, "New record after soft delete"))
        );

        BulkAttendanceResponse response = service.markBulkAttendance(req, null);

        assertThat(response.getCreated()).isEqualTo(1);
        assertThat(response.getUpdated()).isEqualTo(0);
        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getRecords().get(0).getOperation()).isEqualTo("CREATED");
    }

    // ── Attendance summary ────────────────────────────────────────────────────

    @Test
    @DisplayName("Attendance summary — correct percentage calculation")
    void getAttendanceSummary_percentageCalculation() {
        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(student));
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(activeYear));
        when(attendanceRepository.countTotalByStudentAndYear(studentId, yearId)).thenReturn(10L);
        when(attendanceRepository.countByStudentAndYearAndStatus(studentId, yearId, AttendanceStatus.PRESENT)).thenReturn(7L);
        when(attendanceRepository.countByStudentAndYearAndStatus(studentId, yearId, AttendanceStatus.ABSENT)).thenReturn(1L);
        when(attendanceRepository.countByStudentAndYearAndStatus(studentId, yearId, AttendanceStatus.LATE)).thenReturn(1L);
        when(attendanceRepository.countByStudentAndYearAndStatus(studentId, yearId, AttendanceStatus.HALF_DAY)).thenReturn(1L);
        when(attendanceRepository.countByStudentAndYearAndStatus(studentId, yearId, AttendanceStatus.EXCUSED)).thenReturn(0L);

        AttendanceSummaryResponse summary = service.getAttendanceSummary(studentId, yearId);

        assertThat(summary.getTotalDays()).isEqualTo(10L);
        assertThat(summary.getPresentCount()).isEqualTo(7L);
        // Percentage = (7 + 0.5 + 0) / 10 * 100 = 75.00
        assertThat(summary.getAttendancePercentage().doubleValue()).isEqualTo(75.00);
    }

    // ── Phase 0B-2 Calendar & Override Unit Tests ─────────────────────────────

    @Test
    @DisplayName("Single mark on WEEKLY_OFF without override — rejected with BusinessException")
    void markAttendance_weeklyOff_noOverride_throwsBusinessException() {
        setupStandardMocks();
        when(calendarService.getDayStatus(TODAY))
                .thenReturn(new DayStatusResponse(TODAY, TODAY.getDayOfWeek(), DayStatus.WEEKLY_OFF, null, null));

        MarkAttendanceRequest request = new MarkAttendanceRequest(
                studentId, yearId, classId, sectionId, TODAY, AttendanceStatus.PRESENT, "Note");

        assertThatThrownBy(() -> service.markAttendance(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("weekly off");
    }

    @Test
    @DisplayName("Single mark on HOLIDAY without override — rejected with BusinessException naming holiday")
    void markAttendance_holiday_noOverride_throwsBusinessExceptionWithHolidayName() {
        setupStandardMocks();
        when(calendarService.getDayStatus(TODAY))
                .thenReturn(new DayStatusResponse(TODAY, TODAY.getDayOfWeek(), DayStatus.HOLIDAY, "Diwali", null));

        MarkAttendanceRequest request = new MarkAttendanceRequest(
                studentId, yearId, classId, sectionId, TODAY, AttendanceStatus.PRESENT, "Note");

        assertThatThrownBy(() -> service.markAttendance(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Diwali");
    }

    @Test
    @DisplayName("Single mark on non-working day with override and valid authority — accepted")
    void markAttendance_nonWorking_withOverrideAndAuthority_success() {
        setupStandardMocks();
        when(calendarService.getDayStatus(TODAY))
                .thenReturn(new DayStatusResponse(TODAY, TODAY.getDayOfWeek(), DayStatus.WEEKLY_OFF, null, null));
        when(attendanceRepository.existsByAttendanceCode(any())).thenReturn(false);
        when(attendanceRepository.save(any())).thenAnswer(inv -> {
            StudentAttendance a = inv.getArgument(0);
            a.setId(attendanceId);
            return a;
        });

        // Setup security context with ATTENDANCE_OVERRIDE_NON_WORKING
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "admin", "pass", List.of(new SimpleGrantedAuthority("ATTENDANCE_OVERRIDE_NON_WORKING")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            MarkAttendanceRequest request = new MarkAttendanceRequest(
                    studentId, yearId, classId, sectionId, TODAY, AttendanceStatus.PRESENT, "Special class",
                    true, "Special Saturday remedial class");

            AttendanceDetailResponse response = service.markAttendance(request, null);

            assertThat(response).isNotNull();
            assertThat(response.isNonWorkingDayOverride()).isTrue();
            assertThat(response.getOverrideReason()).isEqualTo("Special Saturday remedial class");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("Single mark with override but missing reason — rejected with BusinessException")
    void markAttendance_nonWorking_overrideMissingReason_throwsBusinessException() {
        setupStandardMocks();
        when(calendarService.getDayStatus(TODAY))
                .thenReturn(new DayStatusResponse(TODAY, TODAY.getDayOfWeek(), DayStatus.WEEKLY_OFF, null, null));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "admin", "pass", List.of(new SimpleGrantedAuthority("ATTENDANCE_OVERRIDE_NON_WORKING")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            MarkAttendanceRequest request = new MarkAttendanceRequest(
                    studentId, yearId, classId, sectionId, TODAY, AttendanceStatus.PRESENT, "Note",
                    true, "   ");

            assertThatThrownBy(() -> service.markAttendance(request, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("overrideReason is required");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("Single mark with override but caller lacks authority — rejected with AccessDeniedException")
    void markAttendance_nonWorking_overrideWithoutAuthority_throwsAccessDeniedException() {
        setupStandardMocks();
        when(calendarService.getDayStatus(TODAY))
                .thenReturn(new DayStatusResponse(TODAY, TODAY.getDayOfWeek(), DayStatus.WEEKLY_OFF, null, null));

        // Caller has only ATTENDANCE_WRITE, not ATTENDANCE_OVERRIDE_NON_WORKING
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "teacher", "pass", List.of(new SimpleGrantedAuthority("ATTENDANCE_WRITE")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            MarkAttendanceRequest request = new MarkAttendanceRequest(
                    studentId, yearId, classId, sectionId, TODAY, AttendanceStatus.PRESENT, "Note",
                    true, "Special Saturday remedial class");

            assertThatThrownBy(() -> service.markAttendance(request, null))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("ATTENDANCE_OVERRIDE_NON_WORKING");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("Bulk upsert with holiday in payload — rejected in Phase 1 (nothing written)")
    void markBulkAttendance_holiday_noOverride_throwsBusinessException_nothingWritten() {
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(activeYear));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(academicClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(section));
        when(calendarService.getDayStatus(TODAY))
                .thenReturn(new DayStatusResponse(TODAY, TODAY.getDayOfWeek(), DayStatus.HOLIDAY, "Independence Day", null));

        BulkAttendanceRequest req = new BulkAttendanceRequest(
                yearId, classId, sectionId, TODAY,
                List.of(new BulkAttendanceRequest.BulkAttendanceRecordRequest(studentId, AttendanceStatus.PRESENT, null)),
                false, null);

        assertThatThrownBy(() -> service.markBulkAttendance(req, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Independence Day");
    }

    @Test
    @DisplayName("Bulk upsert verifies calendar is looked up once per distinct date")
    void markBulkAttendance_calendarLookedUpOncePerDistinctDate() {
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(activeYear));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(academicClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(section));

        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        UUID s3 = UUID.randomUUID();

        Student student1 = createMockStudent(s1, "STU-001");
        Student student2 = createMockStudent(s2, "STU-002");
        Student student3 = createMockStudent(s3, "STU-003");

        when(studentRepository.findByIdAndNotDeleted(s1)).thenReturn(Optional.of(student1));
        when(studentRepository.findByIdAndNotDeleted(s2)).thenReturn(Optional.of(student2));
        when(studentRepository.findByIdAndNotDeleted(s3)).thenReturn(Optional.of(student3));

        when(studentAcademicAssignmentRepository.findActiveAssignment(s1, yearId))
                .thenReturn(Optional.of(createMockAssignment(student1, section)));
        when(studentAcademicAssignmentRepository.findActiveAssignment(s2, yearId))
                .thenReturn(Optional.of(createMockAssignment(student2, section)));
        when(studentAcademicAssignmentRepository.findActiveAssignment(s3, yearId))
                .thenReturn(Optional.of(createMockAssignment(student3, section)));

        when(attendanceRepository.existsByAttendanceCode(any())).thenReturn(false);
        when(attendanceRepository.saveAll(any())).thenAnswer(inv -> {
            List<StudentAttendance> list = inv.getArgument(0);
            list.forEach(a -> { if (a.getId() == null) a.setId(UUID.randomUUID()); });
            return list;
        });

        when(calendarService.getDayStatus(TODAY))
                .thenReturn(new DayStatusResponse(TODAY, TODAY.getDayOfWeek(), DayStatus.WORKING, null, null));

        BulkAttendanceRequest req = new BulkAttendanceRequest(
                yearId, classId, sectionId, TODAY,
                List.of(
                        new BulkAttendanceRequest.BulkAttendanceRecordRequest(s1, AttendanceStatus.PRESENT, null),
                        new BulkAttendanceRequest.BulkAttendanceRecordRequest(s2, AttendanceStatus.PRESENT, null),
                        new BulkAttendanceRequest.BulkAttendanceRecordRequest(s3, AttendanceStatus.ABSENT, null)
                ),
                false, null
        );

        BulkAttendanceResponse response = service.markBulkAttendance(req, null);

        assertThat(response.getTotal()).isEqualTo(3);
        // Assert calendarService.getDayStatus was called exactly 1 time for the entire batch of 3 students
        verify(calendarService, times(1)).getDayStatus(TODAY);
    }

    @Test
    @DisplayName("Summary calculation computes calendar fields and excludes later-declared holiday records")
    void getAttendanceSummary_calendarMetrics_and_laterDeclaredHolidayExcluded() {
        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(student));
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(activeYear));

        LocalDate d1 = TODAY.minusDays(4); // Working day, PRESENT
        LocalDate d2 = TODAY.minusDays(3); // Working day, HALF_DAY
        LocalDate d3 = TODAY.minusDays(2); // Later declared HOLIDAY, marked PRESENT with NO override -> should be excluded
        LocalDate d4 = TODAY.minusDays(1); // Working day, unmarked
        LocalDate d5 = TODAY;              // Working day, ABSENT

        List<DayStatusResponse> dayStatuses = List.of(
                new DayStatusResponse(d1, d1.getDayOfWeek(), DayStatus.WORKING, null, null),
                new DayStatusResponse(d2, d2.getDayOfWeek(), DayStatus.WORKING, null, null),
                new DayStatusResponse(d3, d3.getDayOfWeek(), DayStatus.HOLIDAY, "Sudden Holiday", null),
                new DayStatusResponse(d4, d4.getDayOfWeek(), DayStatus.WORKING, null, null),
                new DayStatusResponse(d5, d5.getDayOfWeek(), DayStatus.WORKING, null, null)
        );
        when(calendarService.getDaysStatus(YEAR_START, TODAY)).thenReturn(dayStatuses);

        // Active records: d1 (PRESENT, no override), d2 (HALF_DAY, no override), d3 (PRESENT, no override), d5 (ABSENT, no override)
        StudentAttendance rec1 = new StudentAttendance("ATT-01", student, activeYear, academicClass, section, d1, AttendanceStatus.PRESENT, null, null);
        StudentAttendance rec2 = new StudentAttendance("ATT-02", student, activeYear, academicClass, section, d2, AttendanceStatus.HALF_DAY, null, null);
        StudentAttendance rec3 = new StudentAttendance("ATT-03", student, activeYear, academicClass, section, d3, AttendanceStatus.PRESENT, null, null);
        StudentAttendance rec5 = new StudentAttendance("ATT-05", student, activeYear, academicClass, section, d5, AttendanceStatus.ABSENT, null, null);

        when(attendanceRepository.findAllActiveByStudentAndYear(studentId, yearId))
                .thenReturn(List.of(rec1, rec2, rec3, rec5));

        when(attendanceRepository.countTotalByStudentAndYear(studentId, yearId)).thenReturn(4L);
        when(attendanceRepository.countByStudentAndYearAndStatus(studentId, yearId, AttendanceStatus.PRESENT)).thenReturn(2L);
        when(attendanceRepository.countByStudentAndYearAndStatus(studentId, yearId, AttendanceStatus.ABSENT)).thenReturn(1L);
        when(attendanceRepository.countByStudentAndYearAndStatus(studentId, yearId, AttendanceStatus.LATE)).thenReturn(0L);
        when(attendanceRepository.countByStudentAndYearAndStatus(studentId, yearId, AttendanceStatus.HALF_DAY)).thenReturn(1L);
        when(attendanceRepository.countByStudentAndYearAndStatus(studentId, yearId, AttendanceStatus.EXCUSED)).thenReturn(0L);

        AttendanceSummaryResponse summary = service.getAttendanceSummary(studentId, yearId);

        // workingDaysInRange: d1, d2, d4, d5 = 4 working days
        assertThat(summary.getWorkingDaysInRange()).isEqualTo(4L);
        // excludedNonWorkingDayRecords: rec3 on d3 (holiday without override) = 1
        assertThat(summary.getExcludedNonWorkingDayRecords()).isEqualTo(1L);
        // marked working days: d1, d2, d5 = 3 days. Unmarked working days = 4 - 3 = 1 (d4)
        assertThat(summary.getUnmarkedWorkingDays()).isEqualTo(1L);
        // presentWeightedDays for working days: d1 (1.0) + d2 (0.5) + d5 (0.0) = 1.5
        // percentageOfWorkingDays = (1.5 / 4) * 100 = 37.50%
        assertThat(summary.getPercentageOfWorkingDays()).isEqualByComparingTo(new BigDecimal("37.50"));
    }

    @Test
    @DisplayName("Summary calculation clamps start date to student assignment start date for mid-year joiner")
    void getAttendanceSummary_midYearJoiner_clampsStartDateToAssignmentStart() {
        LocalDate midYearStart = TODAY.minusMonths(2); // Joined 2 months ago, while AY started 6 months ago
        StudentAcademicAssignment midYearAssignment = new StudentAcademicAssignment(
                student, activeYear, academicClass, section, "002", midYearStart);
        midYearAssignment.setId(UUID.randomUUID());

        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(student));
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(activeYear));
        when(studentAcademicAssignmentRepository.findActiveAssignment(studentId, yearId))
                .thenReturn(Optional.of(midYearAssignment));

        LocalDate d1 = TODAY.minusDays(2); // Working day, PRESENT
        LocalDate d2 = TODAY.minusDays(1); // Working day, PRESENT
        LocalDate d3 = TODAY;              // Working day, ABSENT

        List<DayStatusResponse> dayStatuses = List.of(
                new DayStatusResponse(d1, d1.getDayOfWeek(), DayStatus.WORKING, null, null),
                new DayStatusResponse(d2, d2.getDayOfWeek(), DayStatus.WORKING, null, null),
                new DayStatusResponse(d3, d3.getDayOfWeek(), DayStatus.WORKING, null, null)
        );
        // Calendar service must be called with midYearStart (clamped), NOT YEAR_START
        when(calendarService.getDaysStatus(midYearStart, TODAY)).thenReturn(dayStatuses);

        StudentAttendance rec1 = new StudentAttendance("ATT-01", student, activeYear, academicClass, section, d1, AttendanceStatus.PRESENT, null, null);
        StudentAttendance rec2 = new StudentAttendance("ATT-02", student, activeYear, academicClass, section, d2, AttendanceStatus.PRESENT, null, null);
        StudentAttendance rec3 = new StudentAttendance("ATT-03", student, activeYear, academicClass, section, d3, AttendanceStatus.ABSENT, null, null);

        when(attendanceRepository.findAllActiveByStudentAndYear(studentId, yearId))
                .thenReturn(List.of(rec1, rec2, rec3));

        when(attendanceRepository.countTotalByStudentAndYear(studentId, yearId)).thenReturn(3L);
        when(attendanceRepository.countByStudentAndYearAndStatus(studentId, yearId, AttendanceStatus.PRESENT)).thenReturn(2L);
        when(attendanceRepository.countByStudentAndYearAndStatus(studentId, yearId, AttendanceStatus.ABSENT)).thenReturn(1L);
        when(attendanceRepository.countByStudentAndYearAndStatus(studentId, yearId, AttendanceStatus.LATE)).thenReturn(0L);
        when(attendanceRepository.countByStudentAndYearAndStatus(studentId, yearId, AttendanceStatus.HALF_DAY)).thenReturn(0L);
        when(attendanceRepository.countByStudentAndYearAndStatus(studentId, yearId, AttendanceStatus.EXCUSED)).thenReturn(0L);

        AttendanceSummaryResponse summary = service.getAttendanceSummary(studentId, yearId);

        assertThat(summary.getWorkingDaysInRange()).isEqualTo(3L);
        assertThat(summary.getUnmarkedWorkingDays()).isEqualTo(0L);
        // (2.0 / 3) * 100 = 66.67%
        assertThat(summary.getPercentageOfWorkingDays()).isEqualByComparingTo(new BigDecimal("66.67"));
        verify(calendarService).getDaysStatus(midYearStart, TODAY);
    }

    @Test
    @DisplayName("Gaps report — returns working dates in range with no recorded attendance")
    void getMissingAttendanceDates_returnsWorkingDaysWithNoRecords() {
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(section));
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(activeYear));

        LocalDate d1 = TODAY.minusDays(4); // Working day, marked
        LocalDate d2 = TODAY.minusDays(3); // Weekly off
        LocalDate d3 = TODAY.minusDays(2); // Working day, missing
        LocalDate d4 = TODAY.minusDays(1); // Holiday
        LocalDate d5 = TODAY;              // Working day, missing

        List<DayStatusResponse> dayStatuses = List.of(
                new DayStatusResponse(d1, d1.getDayOfWeek(), DayStatus.WORKING, null, null),
                new DayStatusResponse(d2, d2.getDayOfWeek(), DayStatus.WEEKLY_OFF, null, null),
                new DayStatusResponse(d3, d3.getDayOfWeek(), DayStatus.WORKING, null, null),
                new DayStatusResponse(d4, d4.getDayOfWeek(), DayStatus.HOLIDAY, "National Holiday", null),
                new DayStatusResponse(d5, d5.getDayOfWeek(), DayStatus.WORKING, null, null)
        );
        when(calendarService.getDaysStatus(d1, d5)).thenReturn(dayStatuses);

        // Only d1 has recorded attendance
        when(attendanceRepository.findDistinctAttendanceDatesBySectionAndYearAndRange(sectionId, yearId, d1, d5))
                .thenReturn(Set.of(d1));

        List<LocalDate> missingDates = service.getMissingAttendanceDates(sectionId, yearId, d1, d5);

        // Missing working dates should be d3 and d5 (d2 is weekly off, d4 is holiday)
        assertThat(missingDates).containsExactly(d3, d5);
    }

    @Test
    @DisplayName("Gaps report — range exceeds 92 days rejected")
    void getMissingAttendanceDates_rangeExceeds92Days_throwsBusinessException() {
        LocalDate from = TODAY.minusDays(100);
        LocalDate to = TODAY;

        assertThatThrownBy(() -> service.getMissingAttendanceDates(sectionId, yearId, from, to))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("92 days");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setupStandardMocks() {
        lenient().when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(activeYear));
        lenient().when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(academicClass));
        lenient().when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(section));
        lenient().when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(student));
        lenient().when(studentAcademicAssignmentRepository.findActiveAssignment(studentId, yearId))
                .thenReturn(Optional.of(assignment));
        lenient().when(attendanceRepository.existsActiveByStudentAndYearAndDate(studentId, yearId, TODAY, null))
                .thenReturn(false);
    }

    private Student createMockStudent(UUID id, String code) {
        TenantUser tu = new TenantUser("USR-" + code, code + "@test.com", "9876543210", TenantUserType.STUDENT);
        tu.setId(UUID.randomUUID());
        Student s = new Student(code, tu, "ADM-" + code, YEAR_START);
        s.setId(id);
        return s;
    }

    private StudentAcademicAssignment createMockAssignment(Student s, Section sec) {
        StudentAcademicAssignment a = new StudentAcademicAssignment(s, activeYear, academicClass, sec, "001", YEAR_START);
        a.setId(UUID.randomUUID());
        return a;
    }

    private StudentAttendance buildAttendance(AttendanceStatus status) {
        StudentAttendance a = new StudentAttendance(
                "ATT-TEST1234", student, activeYear, academicClass, section, TODAY, status, null, null);
        a.setId(attendanceId);
        return a;
    }
}
