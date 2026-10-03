package com.vidya.tenantdata.timetable;

import com.vidya.common.exception.BusinessException;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.assignment.entity.TeacherSubjectAssignment;
import com.vidya.tenantdata.assignment.repository.ClassSubjectAssignmentRepository;
import com.vidya.tenantdata.assignment.repository.TeacherSubjectAssignmentRepository;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.enums.StaffType;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.enums.SubjectType;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import com.vidya.tenantdata.timetable.dto.CreateTimetableEntryRequest;
import com.vidya.tenantdata.timetable.dto.ScheduleConflictResponse;
import com.vidya.tenantdata.timetable.dto.ScheduleValidationResponse;
import com.vidya.tenantdata.timetable.dto.TimetableEntryResponse;
import com.vidya.tenantdata.timetable.entity.BellSchedule;
import com.vidya.tenantdata.timetable.entity.BellSchedulePeriod;
import com.vidya.tenantdata.timetable.entity.Room;
import com.vidya.tenantdata.timetable.entity.Timetable;
import com.vidya.tenantdata.timetable.entity.TimetableEntry;
import com.vidya.tenantdata.timetable.enums.ConflictSeverity;
import com.vidya.tenantdata.timetable.enums.PeriodType;
import com.vidya.tenantdata.timetable.enums.RoomStatus;
import com.vidya.tenantdata.timetable.enums.RoomType;
import com.vidya.tenantdata.timetable.enums.ScheduleConflictType;
import com.vidya.tenantdata.timetable.enums.TimetableEntryType;
import com.vidya.tenantdata.timetable.enums.TimetableStatus;
import com.vidya.tenantdata.timetable.mapper.BellScheduleMapper;
import com.vidya.tenantdata.timetable.mapper.TimetableEntryMapper;
import com.vidya.tenantdata.timetable.mapper.TimetableMapper;
import com.vidya.tenantdata.timetable.repository.BellSchedulePeriodRepository;
import com.vidya.tenantdata.timetable.repository.BellScheduleRepository;
import com.vidya.tenantdata.timetable.repository.RoomRepository;
import com.vidya.tenantdata.timetable.repository.TimetableEntryRepository;
import com.vidya.tenantdata.timetable.repository.TimetableRepository;
import com.vidya.tenantdata.timetable.service.TimetableConflictValidator;
import com.vidya.tenantdata.timetable.service.TimetableServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Timetable — Concurrency & Conflict Protection Tests")
class TimetableConcurrencyTest {

    @Mock
    private TimetableRepository timetableRepository;

    @Mock
    private TimetableEntryRepository timetableEntryRepository;

    @Mock
    private BellScheduleRepository bellScheduleRepository;

    @Mock
    private BellSchedulePeriodRepository bellSchedulePeriodRepository;

    @Mock
    private AcademicYearRepository academicYearRepository;

    @Mock
    private AcademicClassRepository academicClassRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private TeacherSubjectAssignmentRepository teacherSubjectAssignmentRepository;

    @Mock
    private ClassSubjectAssignmentRepository classSubjectAssignmentRepository;

    @Mock
    private TimetableConflictValidator conflictValidator;

    @Spy
    private BellScheduleMapper bellScheduleMapper = new BellScheduleMapper();

    @Spy
    private TimetableEntryMapper timetableEntryMapper = new TimetableEntryMapper();

    @Mock
    private com.vidya.tenantdata.timetable.repository.TimetableSubstitutionRepository timetableSubstitutionRepository;

    @Spy
    private com.vidya.tenantdata.timetable.mapper.TimetableSubstitutionMapper timetableSubstitutionMapper = new com.vidya.tenantdata.timetable.mapper.TimetableSubstitutionMapper();

    private TimetableMapper timetableMapper;
    private TimetableServiceImpl timetableService;

    private Timetable testTimetable;
    private AcademicYear testAcademicYear;
    private BellSchedule testBellSchedule;
    private BellSchedulePeriod testPeriod;
    private AcademicClass testClass;
    private Section testSection;
    private Staff testTeacher;
    private Room testRoom;

    private UUID timetableId;
    private UUID periodId;
    private UUID classId;
    private UUID sectionId;
    private UUID teacherId;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        timetableMapper = new TimetableMapper(bellScheduleMapper, timetableEntryMapper);
        timetableService = new TimetableServiceImpl(
                timetableRepository, timetableEntryRepository, bellScheduleRepository, bellSchedulePeriodRepository,
                academicYearRepository, academicClassRepository, sectionRepository, subjectRepository,
                staffRepository, roomRepository, teacherSubjectAssignmentRepository, classSubjectAssignmentRepository,
                conflictValidator, timetableMapper, timetableEntryMapper,
                timetableSubstitutionRepository, timetableSubstitutionMapper
        );

        timetableId = UUID.randomUUID();
        periodId = UUID.randomUUID();
        classId = UUID.randomUUID();
        sectionId = UUID.randomUUID();
        teacherId = UUID.randomUUID();
        roomId = UUID.randomUUID();

        testAcademicYear = new AcademicYear("AY-2026", "2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 5, 31), true, com.vidya.tenantdata.academic.enums.AcademicYearStatus.ACTIVE);
        testAcademicYear.setId(UUID.randomUUID());

        testBellSchedule = new BellSchedule("BSC-1", "Standard Schedule", null, true);
        testBellSchedule.setId(UUID.randomUUID());

        testPeriod = new BellSchedulePeriod("PRD-1", testBellSchedule, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45), PeriodType.TEACHING);
        testPeriod.setId(periodId);
        testBellSchedule.addPeriod(testPeriod);

        testClass = new AcademicClass("CLS-8", "Class 8", "Class 8", 8);
        testClass.setId(classId);

        testSection = new Section(testAcademicYear, testClass, "SEC-A", "Section A", 40);
        testSection.setId(sectionId);

        testTeacher = new Staff("STF-1", null, "EMP-1", LocalDate.now(), StaffType.TEACHER, "Teacher", "Math");
        testTeacher.setId(teacherId);
        testTeacher.setEmploymentStatus(EmploymentStatus.ACTIVE);

        testRoom = new Room("ROM-101", "Room 101", RoomType.CLASSROOM, 40, "Main", "1st");
        testRoom.setId(roomId);
        testRoom.setStatus(RoomStatus.ACTIVE);

        testTimetable = new Timetable("TTB-1", testAcademicYear, testBellSchedule, "Master Timetable 2026", null, null, null);
        testTimetable.setId(timetableId);
        testTimetable.setStatus(TimetableStatus.DRAFT);
    }

    @Test
    @DisplayName("Concurrent creation for same teacher slot allows only 1 entry to succeed")
    void concurrentTeacherBooking_onlyOneSucceeds() throws Exception {
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threads);

        AtomicBoolean firstCallerPassed = new AtomicBoolean(false);

        UUID teacherAssignmentId = UUID.randomUUID();
        Subject testSubject = new Subject("SUB-1", "Mathematics", "Math", "Math desc", SubjectType.CORE, 1);
        testSubject.setId(UUID.randomUUID());
        TeacherSubjectAssignment testTeacherAssignment = new TeacherSubjectAssignment("TSA-1", testTeacher, testSubject, testAcademicYear, testClass, testSection, null, true, LocalDate.now(), null);
        testTeacherAssignment.setId(teacherAssignmentId);

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(periodId)).thenReturn(Optional.of(testPeriod));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(testClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(testSection));
        when(teacherSubjectAssignmentRepository.findByIdAndNotDeleted(teacherAssignmentId)).thenReturn(Optional.of(testTeacherAssignment));
        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(testRoom));

        when(timetableEntryRepository.findClassClashes(eq(timetableId), eq(classId), eq(sectionId), eq(DayOfWeek.MONDAY), eq(periodId), isNull()))
                .thenReturn(Collections.emptyList());
        when(timetableEntryRepository.findRoomClashes(eq(timetableId), eq(roomId), eq(DayOfWeek.MONDAY), eq(periodId), isNull()))
                .thenReturn(Collections.emptyList());

        when(timetableEntryRepository.findTeacherClashes(eq(timetableId), eq(teacherId), eq(DayOfWeek.MONDAY), eq(periodId), isNull()))
                .thenAnswer(invocation -> {
                    if (firstCallerPassed.compareAndSet(false, true)) {
                        return Collections.emptyList();
                    } else {
                        return List.of(new TimetableEntry("TTE-CLASH", testTimetable, testAcademicYear, testPeriod, DayOfWeek.MONDAY, testClass, testSection, null, testTeacherAssignment, testTeacher, null, testRoom, TimetableEntryType.REGULAR));
                    }
                });

        when(timetableEntryRepository.saveAndFlush(any(TimetableEntry.class))).thenAnswer(i -> {
            TimetableEntry e = i.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                periodId, DayOfWeek.MONDAY, classId, sectionId, null, teacherAssignmentId, null, null, roomId, TimetableEntryType.REGULAR
        );

        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(executor.submit(() -> {
                startLatch.await();
                try {
                    TimetableEntryResponse resp = timetableService.createEntry(timetableId, request);
                    return resp != null;
                } catch (Exception e) {
                    return false;
                } finally {
                    finishLatch.countDown();
                }
            }));
        }

        startLatch.countDown();
        finishLatch.await();

        int successCount = 0;
        int failureCount = 0;
        for (Future<Boolean> f : results) {
            if (f.get()) {
                successCount++;
            } else {
                failureCount++;
            }
        }

        assertThat(successCount).isEqualTo(1);
        assertThat(failureCount).isEqualTo(1);
        executor.shutdown();
    }
}
