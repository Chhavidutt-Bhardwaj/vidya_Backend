package com.vidya.tenantdata.timetable.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.assignment.repository.ClassSubjectAssignmentRepository;
import com.vidya.tenantdata.assignment.repository.TeacherSubjectAssignmentRepository;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import com.vidya.tenantdata.timetable.dto.CreateTimetableEntryRequest;
import com.vidya.tenantdata.timetable.dto.CreateTimetableRequest;
import com.vidya.tenantdata.timetable.dto.ScheduleConflictResponse;
import com.vidya.tenantdata.timetable.dto.ScheduleValidationResponse;
import com.vidya.tenantdata.timetable.dto.TimetableDetailResponse;
import com.vidya.tenantdata.timetable.dto.TimetableEntryResponse;
import com.vidya.tenantdata.timetable.dto.TimetableGridResponse;
import com.vidya.tenantdata.timetable.dto.TimetableResponse;
import com.vidya.tenantdata.timetable.dto.UpdateTimetableRequest;
import com.vidya.tenantdata.timetable.entity.BellSchedule;
import com.vidya.tenantdata.timetable.entity.BellSchedulePeriod;
import com.vidya.tenantdata.timetable.entity.Room;
import com.vidya.tenantdata.timetable.entity.Timetable;
import com.vidya.tenantdata.timetable.entity.TimetableEntry;
import com.vidya.tenantdata.timetable.enums.ConflictSeverity;
import com.vidya.tenantdata.timetable.enums.PeriodType;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TimetableService — Unit Tests")
class TimetableServiceTest {

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
    private UUID yearId;
    private UUID scheduleId;
    private UUID periodId;
    private UUID classId;
    private UUID sectionId;
    private UUID teacherId;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        timetableMapper = new TimetableMapper(bellScheduleMapper, timetableEntryMapper);
        timetableService = new TimetableServiceImpl(
                timetableRepository,
                timetableEntryRepository,
                bellScheduleRepository,
                bellSchedulePeriodRepository,
                academicYearRepository,
                academicClassRepository,
                sectionRepository,
                subjectRepository,
                staffRepository,
                roomRepository,
                teacherSubjectAssignmentRepository,
                classSubjectAssignmentRepository,
                conflictValidator,
                timetableMapper,
                timetableEntryMapper
        );

        timetableId = UUID.randomUUID();
        yearId = UUID.randomUUID();
        scheduleId = UUID.randomUUID();
        periodId = UUID.randomUUID();
        classId = UUID.randomUUID();
        sectionId = UUID.randomUUID();
        teacherId = UUID.randomUUID();
        roomId = UUID.randomUUID();

        testAcademicYear = new AcademicYear("AY-2026", "2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 5, 31), true, com.vidya.tenantdata.academic.enums.AcademicYearStatus.ACTIVE);
        testAcademicYear.setId(yearId);

        testBellSchedule = new BellSchedule("BSC-1", "Standard Schedule", null, true);
        testBellSchedule.setId(scheduleId);

        testPeriod = new BellSchedulePeriod("PRD-1", testBellSchedule, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45), PeriodType.TEACHING);
        testPeriod.setId(periodId);
        testBellSchedule.addPeriod(testPeriod);

        testClass = new AcademicClass("CLS-8", "Class 8", "Class 8", 8);
        testClass.setId(classId);

        testSection = new Section(testAcademicYear, testClass, "SEC-A", "Section A", 40);
        testSection.setId(sectionId);

        testTeacher = new Staff("STF-1", null, "EMP-1", LocalDate.now(), null, "Teacher", "Math");
        testTeacher.setId(teacherId);

        testRoom = new Room("ROM-101", "Room 101", RoomType.CLASSROOM, 40, "Main", "1st");
        testRoom.setId(roomId);

        testTimetable = new Timetable("TTB-1", testAcademicYear, testBellSchedule, "Master Timetable 2026", null, null, null);
        testTimetable.setId(timetableId);
    }

    @Test
    @DisplayName("Create timetable successfully")
    void createTimetable_success() {
        CreateTimetableRequest request = new CreateTimetableRequest(yearId, scheduleId, "Master Timetable 2026", "Main", null, null);

        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(testAcademicYear));
        when(bellScheduleRepository.findByIdAndNotDeleted(scheduleId)).thenReturn(Optional.of(testBellSchedule));
        when(timetableRepository.save(any(Timetable.class))).thenAnswer(i -> {
            Timetable t = i.getArgument(0);
            t.setId(timetableId);
            return t;
        });

        TimetableResponse response = timetableService.createTimetable(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Master Timetable 2026");
        assertThat(response.status()).isEqualTo(TimetableStatus.DRAFT);
        verify(timetableRepository).save(any(Timetable.class));
    }

    @Test
    @DisplayName("Publish timetable successfully when validation passes")
    void publishTimetable_valid_success() {
        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(conflictValidator.validateTimetable(testTimetable))
                .thenReturn(new ScheduleValidationResponse(true, 0, Collections.emptyList(), 5));
        when(timetableRepository.save(any(Timetable.class))).thenAnswer(i -> i.getArgument(0));

        TimetableResponse response = timetableService.publishTimetable(timetableId);

        assertThat(response.status()).isEqualTo(TimetableStatus.PUBLISHED);
        assertThat(testTimetable.getStatus()).isEqualTo(TimetableStatus.PUBLISHED);
    }

    @Test
    @DisplayName("Publish timetable fails when conflicts exist")
    void publishTimetable_withConflicts_throwsException() {
        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(conflictValidator.validateTimetable(testTimetable))
                .thenReturn(new ScheduleValidationResponse(false, 1, List.of(
                        new ScheduleConflictResponse(ScheduleConflictType.TEACHER_CONFLICT, ConflictSeverity.ERROR, "Teacher clash", null, null, null, null, null)
                ), 5));

        assertThatThrownBy(() -> timetableService.publishTimetable(timetableId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("unresolved conflicts");
    }

    @Test
    @DisplayName("Create entry successfully when validation passes")
    void createEntry_success() {
        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                periodId, DayOfWeek.MONDAY, classId, sectionId, null, null, teacherId, null, roomId, TimetableEntryType.REGULAR
        );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(conflictValidator.validateEntry(eq(timetableId), any(CreateTimetableEntryRequest.class), isNull()))
                .thenReturn(new ScheduleValidationResponse(true, 0, Collections.emptyList(), 1));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(periodId)).thenReturn(Optional.of(testPeriod));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(testClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(testSection));
        when(staffRepository.findByIdAndNotDeleted(teacherId)).thenReturn(Optional.of(testTeacher));
        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(testRoom));
        when(timetableEntryRepository.save(any(TimetableEntry.class))).thenAnswer(i -> {
            TimetableEntry e = i.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        TimetableEntryResponse response = timetableService.createEntry(timetableId, request);

        assertThat(response).isNotNull();
        assertThat(response.dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(response.academicClassId()).isEqualTo(classId);
        verify(timetableEntryRepository).save(any(TimetableEntry.class));
    }

    @Test
    @DisplayName("Create entry fails when conflict validator reports conflicts")
    void createEntry_conflict_throwsException() {
        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                periodId, DayOfWeek.MONDAY, classId, sectionId, null, null, teacherId, null, roomId, TimetableEntryType.REGULAR
        );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(conflictValidator.validateEntry(eq(timetableId), any(CreateTimetableEntryRequest.class), isNull()))
                .thenReturn(new ScheduleValidationResponse(false, 1, List.of(
                        new ScheduleConflictResponse(ScheduleConflictType.TEACHER_CONFLICT, ConflictSeverity.ERROR, "Teacher already booked", null, null, null, null, null)
                ), 1));

        assertThatThrownBy(() -> timetableService.createEntry(timetableId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Scheduling conflict detected");
    }

    @Test
    @DisplayName("Get class timetable grid returns weekly grid")
    void getClassTimetableGrid_success() {
        TimetableEntry entry = new TimetableEntry("TTE-1", testTimetable, testAcademicYear, testPeriod, DayOfWeek.MONDAY, testClass, testSection, null, null, testTeacher, null, testRoom, TimetableEntryType.REGULAR);
        entry.setId(UUID.randomUUID());

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(testClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(testSection));
        when(timetableEntryRepository.findByTimetableAndClassAndSection(timetableId, classId, sectionId)).thenReturn(List.of(entry));
        when(bellSchedulePeriodRepository.findByBellScheduleIdAndNotDeletedOrderBySequence(scheduleId)).thenReturn(List.of(testPeriod));

        TimetableGridResponse response = timetableService.getClassTimetableGrid(timetableId, classId, sectionId);

        assertThat(response).isNotNull();
        assertThat(response.filterType()).isEqualTo("CLASS");
        assertThat(response.days()).hasSize(6); // Mon - Sat
        assertThat(response.days().getFirst().slots()).hasSize(1);
        assertThat(response.days().getFirst().slots().getFirst().entryId()).isEqualTo(entry.getId());
    }
}
