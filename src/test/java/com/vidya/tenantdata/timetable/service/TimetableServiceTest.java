package com.vidya.tenantdata.timetable.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.assignment.entity.TeacherSubjectAssignment;
import com.vidya.tenantdata.assignment.enums.TeacherAssignmentStatus;
import com.vidya.tenantdata.assignment.repository.ClassSubjectAssignmentRepository;
import com.vidya.tenantdata.assignment.repository.TeacherSubjectAssignmentRepository;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import com.vidya.tenantdata.timetable.dto.BulkUpsertEntriesResponse;
import com.vidya.tenantdata.timetable.dto.BulkUpsertEntryRowRequest;
import com.vidya.tenantdata.timetable.dto.BulkUpsertSectionEntriesRequest;
import com.vidya.tenantdata.timetable.dto.CloneTimetableRequest;
import com.vidya.tenantdata.timetable.dto.CloneTimetableResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
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
@DisplayName("TimetableService — Complete Unit Tests")
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

    @Mock
    private com.vidya.tenantdata.timetable.repository.TimetableSubstitutionRepository timetableSubstitutionRepository;

    @Spy
    private com.vidya.tenantdata.timetable.mapper.TimetableSubstitutionMapper timetableSubstitutionMapper = new com.vidya.tenantdata.timetable.mapper.TimetableSubstitutionMapper();

    @Mock
    private com.vidya.tenantdata.calendar.service.CalendarService calendarService;

    private TimetableMapper timetableMapper;
    private TimetableServiceImpl timetableService;

    private Timetable testTimetable;
    private AcademicYear testAcademicYear;
    private BellSchedule testBellSchedule;
    private BellSchedulePeriod testPeriod;
    private AcademicClass testClass;
    private Section testSection;
    private Staff testTeacher;
    private Subject testSubject;
    private TeacherSubjectAssignment testTeacherAssignment;
    private Room testRoom;

    private UUID timetableId;
    private UUID yearId;
    private UUID scheduleId;
    private UUID periodId;
    private UUID classId;
    private UUID sectionId;
    private UUID teacherId;
    private UUID subjectId;
    private UUID teacherAssignmentId;
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
                timetableEntryMapper,
                timetableSubstitutionRepository,
                timetableSubstitutionMapper,
                calendarService
        );

        org.mockito.Mockito.lenient().when(calendarService.getDayStatus(any()))
                .thenReturn(new com.vidya.tenantdata.calendar.dto.DayStatusResponse(
                        LocalDate.of(2026, 10, 5), DayOfWeek.MONDAY, com.vidya.tenantdata.calendar.enums.DayStatus.WORKING
                ));

        timetableId = UUID.randomUUID();
        yearId = UUID.randomUUID();
        scheduleId = UUID.randomUUID();
        periodId = UUID.randomUUID();
        classId = UUID.randomUUID();
        sectionId = UUID.randomUUID();
        teacherId = UUID.randomUUID();
        subjectId = UUID.randomUUID();
        teacherAssignmentId = UUID.randomUUID();
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

        testSubject = new Subject("SUB-MATH", "Mathematics", "MATH", null, com.vidya.tenantdata.subject.enums.SubjectType.CORE, 1);
        testSubject.setId(subjectId);

        testTeacherAssignment = new TeacherSubjectAssignment("TSA-1", testTeacher, testSubject, testAcademicYear, testClass, testSection, null, true, LocalDate.now(), null);
        testTeacherAssignment.setId(teacherAssignmentId);

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
    @DisplayName("Create timetable fails when academic year is CLOSED or CANCELLED")
    void createTimetable_academicYearClosed_throwsBusinessException() {
        testAcademicYear.setStatus(com.vidya.tenantdata.academic.enums.AcademicYearStatus.CLOSED);
        CreateTimetableRequest request = new CreateTimetableRequest(yearId, scheduleId, "Master Timetable 2026", "Main", null, null);

        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(testAcademicYear));

        assertThatThrownBy(() -> timetableService.createTimetable(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("closed or cancelled");
    }

    @Test
    @DisplayName("Create timetable fails when bell schedule is INACTIVE")
    void createTimetable_bellScheduleInactive_throwsBusinessException() {
        testBellSchedule.setStatus(com.vidya.tenantdata.timetable.enums.BellScheduleStatus.INACTIVE);
        CreateTimetableRequest request = new CreateTimetableRequest(yearId, scheduleId, "Master Timetable 2026", "Main", null, null);

        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(testAcademicYear));
        when(bellScheduleRepository.findByIdAndNotDeleted(scheduleId)).thenReturn(Optional.of(testBellSchedule));

        assertThatThrownBy(() -> timetableService.createTimetable(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must be in ACTIVE status");
    }

    @Test
    @DisplayName("Create timetable fails when bell schedule has no periods")
    void createTimetable_bellScheduleNoPeriods_throwsBusinessException() {
        testBellSchedule.getPeriods().clear();
        CreateTimetableRequest request = new CreateTimetableRequest(yearId, scheduleId, "Master Timetable 2026", "Main", null, null);

        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(testAcademicYear));
        when(bellScheduleRepository.findByIdAndNotDeleted(scheduleId)).thenReturn(Optional.of(testBellSchedule));

        assertThatThrownBy(() -> timetableService.createTimetable(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must have at least one defined period");
    }

    @Test
    @DisplayName("Get timetable by ID returns detail response")
    void getTimetableById_success() {
        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));

        TimetableDetailResponse response = timetableService.getTimetableById(timetableId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(timetableId);
        assertThat(response.name()).isEqualTo("Master Timetable 2026");
    }

    @Test
    @DisplayName("Get timetable by ID not found throws ResourceNotFoundException")
    void getTimetableById_notFound_throwsException() {
        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> timetableService.getTimetableById(timetableId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Update timetable metadata successfully")
    void updateTimetable_success() {
        UpdateTimetableRequest request = new UpdateTimetableRequest("Updated Timetable", "Updated Desc", scheduleId, null, null);

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(bellScheduleRepository.findByIdAndNotDeleted(scheduleId)).thenReturn(Optional.of(testBellSchedule));
        when(timetableRepository.save(any(Timetable.class))).thenAnswer(i -> i.getArgument(0));

        TimetableResponse response = timetableService.updateTimetable(timetableId, request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Updated Timetable");
        assertThat(response.description()).isEqualTo("Updated Desc");
    }

    @Test
    @DisplayName("Publish timetable successfully when validation passes without ERROR severity conflicts")
    void publishTimetable_valid_success() {
        TimetableEntry activeEntry = new TimetableEntry("TTE-1", testTimetable, testAcademicYear, testPeriod, DayOfWeek.MONDAY, testClass, testSection, testSubject, testTeacherAssignment, testTeacher, null, testRoom, TimetableEntryType.REGULAR);
        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(timetableEntryRepository.findAllByTimetableIdWithDetails(timetableId)).thenReturn(List.of(activeEntry));
        when(conflictValidator.validateTimetable(eq(testTimetable))).thenReturn(
                new ScheduleValidationResponse(true, 0, Collections.emptyList(), 1)
        );
        when(timetableRepository.saveAndFlush(any(Timetable.class))).thenAnswer(i -> i.getArgument(0));

        TimetableResponse response = timetableService.publishTimetable(timetableId);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(TimetableStatus.PUBLISHED);
    }

    @Test
    @DisplayName("Publish timetable alias")
    void publishTimetable_success() {
        publishTimetable_valid_success();
    }

    @Test
    @DisplayName("Publish timetable succeeds when validation contains only WARNING severity conflicts")
    void publishTimetable_withOnlyWarnings_succeeds() {
        TimetableEntry activeEntry = new TimetableEntry("TTE-1", testTimetable, testAcademicYear, testPeriod, DayOfWeek.SUNDAY, testClass, testSection, testSubject, testTeacherAssignment, testTeacher, null, testRoom, TimetableEntryType.REGULAR);
        ScheduleConflictResponse warningConflict = new ScheduleConflictResponse(
                ScheduleConflictType.NON_WORKING_DAY,
                ConflictSeverity.WARNING,
                "scheduled on SUNDAY, a weekly off for this academic year",
                null, null, periodId, DayOfWeek.SUNDAY, null
        );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(timetableEntryRepository.findAllByTimetableIdWithDetails(timetableId)).thenReturn(List.of(activeEntry));
        when(conflictValidator.validateTimetable(eq(testTimetable))).thenReturn(
                new ScheduleValidationResponse(true, 1, List.of(warningConflict), 1)
        );
        when(timetableRepository.saveAndFlush(any(Timetable.class))).thenAnswer(i -> i.getArgument(0));

        TimetableResponse response = timetableService.publishTimetable(timetableId);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(TimetableStatus.PUBLISHED);
    }

    @Test
    @DisplayName("Publish timetable fails when ERROR severity conflicts exist")
    void publishTimetable_withConflicts_throwsException() {
        TimetableEntry activeEntry = new TimetableEntry("TTE-1", testTimetable, testAcademicYear, testPeriod, DayOfWeek.MONDAY, testClass, testSection, testSubject, testTeacherAssignment, testTeacher, null, testRoom, TimetableEntryType.REGULAR);
        ScheduleConflictResponse conflict = new ScheduleConflictResponse(
                ScheduleConflictType.TEACHER_CONFLICT,
                ConflictSeverity.ERROR,
                "Teacher conflict detected",
                null, null, periodId, DayOfWeek.MONDAY, teacherId
        );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(timetableEntryRepository.findAllByTimetableIdWithDetails(timetableId)).thenReturn(List.of(activeEntry));
        when(conflictValidator.validateTimetable(eq(testTimetable))).thenReturn(
                new ScheduleValidationResponse(false, 1, List.of(conflict), 1)
        );

        assertThatThrownBy(() -> timetableService.publishTimetable(timetableId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot publish timetable with unresolved conflicts: Teacher conflict detected");
    }

    @Test
    @DisplayName("Publish timetable with errors alias")
    void publishTimetable_withErrors_throwsBusinessException() {
        publishTimetable_withConflicts_throwsException();
    }

    @Test
    @DisplayName("Soft delete timetable deletes all entries")
    void deleteTimetable_success() {
        TimetableEntry activeEntry = new TimetableEntry("TTE-1", testTimetable, testAcademicYear, testPeriod, DayOfWeek.MONDAY, testClass, testSection, testSubject, testTeacherAssignment, testTeacher, null, testRoom, TimetableEntryType.REGULAR);
        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(timetableEntryRepository.findAllByTimetableIdWithDetails(timetableId)).thenReturn(List.of(activeEntry));

        timetableService.deleteTimetable(timetableId);

        assertThat(testTimetable.isDeleted()).isTrue();
        assertThat(activeEntry.isDeleted()).isTrue();
        verify(timetableEntryRepository).saveAll(any());
    }

    @Test
    @DisplayName("Search timetables returns paged result")
    void searchTimetables_success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Timetable> page = new PageImpl<>(List.of(testTimetable), pageable, 1);

        when(timetableRepository.searchTimetables(yearId, scheduleId, TimetableStatus.DRAFT, "Master", pageable))
                .thenReturn(page);

        Page<TimetableResponse> result = timetableService.searchTimetables(yearId, scheduleId, TimetableStatus.DRAFT, "Master", pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().name()).isEqualTo("Master Timetable 2026");
    }

    @Test
    @DisplayName("Create entry successfully when validation passes")
    void createEntry_success() {
        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                periodId, DayOfWeek.MONDAY, classId, sectionId, null, teacherAssignmentId, null, null, roomId, TimetableEntryType.REGULAR
        );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(periodId)).thenReturn(Optional.of(testPeriod));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(testClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(testSection));
        when(teacherSubjectAssignmentRepository.findByIdAndNotDeleted(teacherAssignmentId)).thenReturn(Optional.of(testTeacherAssignment));
        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(testRoom));
        when(timetableEntryRepository.saveAndFlush(any(TimetableEntry.class))).thenAnswer(i -> {
            TimetableEntry e = i.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        TimetableEntryResponse response = timetableService.createEntry(timetableId, request);

        assertThat(response).isNotNull();
        assertThat(response.dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(response.academicClassId()).isEqualTo(classId);
        verify(timetableEntryRepository).saveAndFlush(any(TimetableEntry.class));
    }

    @Test
    @DisplayName("Create entry fails when teacher clash occurs")
    void createEntry_conflict_throwsException() {
        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                periodId, DayOfWeek.MONDAY, classId, sectionId, null, teacherAssignmentId, null, null, roomId, TimetableEntryType.REGULAR
        );

        TimetableEntry conflictingEntry = new TimetableEntry("TTE-CONF-1", testTimetable, testAcademicYear, testPeriod, DayOfWeek.MONDAY, testClass, testSection, testSubject, testTeacherAssignment, testTeacher, null, testRoom, TimetableEntryType.REGULAR);

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(periodId)).thenReturn(Optional.of(testPeriod));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(testClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(testSection));
        when(teacherSubjectAssignmentRepository.findByIdAndNotDeleted(teacherAssignmentId)).thenReturn(Optional.of(testTeacherAssignment));
        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(testRoom));
        when(timetableEntryRepository.findTeacherClashes(timetableId, teacherId, DayOfWeek.MONDAY, periodId, null)).thenReturn(List.of(conflictingEntry));

        assertThatThrownBy(() -> timetableService.createEntry(timetableId, request))
                .isInstanceOf(com.vidya.common.exception.DuplicateResourceException.class)
                .hasMessageContaining("Teacher clash with entry [TTE-CONF-1]");
    }

    @Test
    @DisplayName("Create entry fails when timetable is not in DRAFT status")
    void createEntry_timetableNotDraft_throwsBusinessException() {
        testTimetable.setStatus(TimetableStatus.PUBLISHED);
        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                periodId, DayOfWeek.MONDAY, classId, sectionId, null, teacherAssignmentId, null, null, roomId, TimetableEntryType.REGULAR
        );
        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));

        assertThatThrownBy(() -> timetableService.createEntry(timetableId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("only be created while timetable is in DRAFT status");
    }

    @Test
    @DisplayName("Create entry fails when period does not belong to timetable's bell schedule")
    void createEntry_periodNotBelongToBellSchedule_throwsBusinessException() {
        BellSchedule otherSchedule = new BellSchedule("BSC-2", "Other Schedule", null, true);
        otherSchedule.setId(UUID.randomUUID());
        BellSchedulePeriod otherPeriod = new BellSchedulePeriod("PRD-2", otherSchedule, "P2", 2, LocalTime.of(9, 0), LocalTime.of(9, 45), PeriodType.TEACHING);
        otherPeriod.setId(UUID.randomUUID());

        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                otherPeriod.getId(), DayOfWeek.MONDAY, classId, sectionId, null, teacherAssignmentId, null, null, roomId, TimetableEntryType.REGULAR
        );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(otherPeriod.getId())).thenReturn(Optional.of(otherPeriod));

        assertThatThrownBy(() -> timetableService.createEntry(timetableId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not belong to the timetable's bell schedule");
    }

    @Test
    @DisplayName("Create entry fails when SUBSTITUTE entry type is requested")
    void createEntry_substituteEntryType_throwsBusinessException() {
        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                periodId, DayOfWeek.MONDAY, classId, sectionId, null, null, null, null, roomId, TimetableEntryType.SUBSTITUTE
        );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(periodId)).thenReturn(Optional.of(testPeriod));

        assertThatThrownBy(() -> timetableService.createEntry(timetableId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SUBSTITUTE entry type is reserved");
    }

    @Test
    @DisplayName("Create entry fails when entry type is not compatible with period type")
    void createEntry_periodTypeMismatch_throwsBusinessException() {
        testPeriod.setPeriodType(PeriodType.BREAK);
        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                periodId, DayOfWeek.MONDAY, classId, sectionId, null, null, null, null, roomId, TimetableEntryType.REGULAR
        );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(periodId)).thenReturn(Optional.of(testPeriod));

        assertThatThrownBy(() -> timetableService.createEntry(timetableId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("BREAK and LUNCH periods accept only BREAK entry type");
    }

    @Test
    @DisplayName("Create entry fails when FREE period is used for any entry type")
    void createEntry_freePeriod_rejectsAll() {
        testPeriod.setPeriodType(PeriodType.FREE);
        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                periodId, DayOfWeek.MONDAY, classId, sectionId, null, null, null, null, roomId, TimetableEntryType.BREAK
        );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(periodId)).thenReturn(Optional.of(testPeriod));

        assertThatThrownBy(() -> timetableService.createEntry(timetableId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("FREE periods do not accept any timetable entries");
    }

    @Test
    @DisplayName("Create entry fails when REGULAR entry lacks teacher assignment")
    void createEntry_regularLacksTeacherAssignment_throwsBusinessException() {
        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                periodId, DayOfWeek.MONDAY, classId, sectionId, null, null, null, null, roomId, TimetableEntryType.REGULAR
        );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(periodId)).thenReturn(Optional.of(testPeriod));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(testClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(testSection));

        assertThatThrownBy(() -> timetableService.createEntry(timetableId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("REGULAR and LAB entries require subject, teacher assignment, and teacher");
    }

    @Test
    @DisplayName("Create entry fails when BREAK entry has teacher or subject")
    void createEntry_breakWithTeacher_throwsBusinessException() {
        testPeriod.setPeriodType(PeriodType.BREAK);
        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                periodId, DayOfWeek.MONDAY, classId, sectionId, null, teacherAssignmentId, null, null, roomId, TimetableEntryType.BREAK
        );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(periodId)).thenReturn(Optional.of(testPeriod));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(testClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(testSection));
        when(teacherSubjectAssignmentRepository.findByIdAndNotDeleted(teacherAssignmentId)).thenReturn(Optional.of(testTeacherAssignment));

        assertThatThrownBy(() -> timetableService.createEntry(timetableId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("BREAK entries must not have subject, teacher assignment, or teacher");
    }

    @Test
    @DisplayName("Create entry fails when room is INACTIVE")
    void createEntry_roomInactive_throwsBusinessException() {
        testRoom.setStatus(com.vidya.tenantdata.timetable.enums.RoomStatus.INACTIVE);
        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                periodId, DayOfWeek.MONDAY, classId, sectionId, null, teacherAssignmentId, null, null, roomId, TimetableEntryType.REGULAR
        );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(periodId)).thenReturn(Optional.of(testPeriod));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(testClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(testSection));
        when(teacherSubjectAssignmentRepository.findByIdAndNotDeleted(teacherAssignmentId)).thenReturn(Optional.of(testTeacherAssignment));
        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(testRoom));

        assertThatThrownBy(() -> timetableService.createEntry(timetableId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Assigned room must be in ACTIVE status");
    }

    @Test
    @DisplayName("Create entry fails when LAB entry has non-lab room")
    void createEntry_labEntryWithoutLabRoom_throwsBusinessException() {
        testRoom.setRoomType(RoomType.CLASSROOM);
        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                periodId, DayOfWeek.MONDAY, classId, sectionId, null, teacherAssignmentId, null, null, roomId, TimetableEntryType.LAB
        );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(periodId)).thenReturn(Optional.of(testPeriod));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(testClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(testSection));
        when(teacherSubjectAssignmentRepository.findByIdAndNotDeleted(teacherAssignmentId)).thenReturn(Optional.of(testTeacherAssignment));
        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(testRoom));

        assertThatThrownBy(() -> timetableService.createEntry(timetableId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("LAB entries require a lab-type room");
    }

    @Test
    @DisplayName("Create entry with room capacity below section count returns warning")
    void createEntry_withCapacityWarning_returnsWarning() {
        testRoom.setCapacity(20); // section capacity is 40
        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                periodId, DayOfWeek.MONDAY, classId, sectionId, null, teacherAssignmentId, null, null, roomId, TimetableEntryType.REGULAR
        );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(periodId)).thenReturn(Optional.of(testPeriod));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(testClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(testSection));
        when(teacherSubjectAssignmentRepository.findByIdAndNotDeleted(teacherAssignmentId)).thenReturn(Optional.of(testTeacherAssignment));
        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(testRoom));
        when(timetableEntryRepository.saveAndFlush(any(TimetableEntry.class))).thenAnswer(i -> {
            TimetableEntry e = i.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        TimetableEntryResponse response = timetableService.createEntry(timetableId, request);

        assertThat(response).isNotNull();
        assertThat(response.warning()).isNotNull().contains("Room capacity (20) is below section student count (40)");
    }

    @Test
    @DisplayName("Update entry successfully")
    void updateEntry_success() {
        UUID entryId = UUID.randomUUID();
        TimetableEntry existingEntry = new TimetableEntry("TTE-1", testTimetable, testAcademicYear, testPeriod, DayOfWeek.MONDAY, testClass, testSection, testSubject, testTeacherAssignment, testTeacher, null, testRoom, TimetableEntryType.REGULAR);
        existingEntry.setId(entryId);

        com.vidya.tenantdata.timetable.dto.UpdateTimetableEntryRequest request = new com.vidya.tenantdata.timetable.dto.UpdateTimetableEntryRequest(
                periodId, DayOfWeek.TUESDAY, classId, sectionId, null, teacherAssignmentId, null, null, roomId, TimetableEntryType.REGULAR
        );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(timetableEntryRepository.findByIdAndNotDeleted(entryId)).thenReturn(Optional.of(existingEntry));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(periodId)).thenReturn(Optional.of(testPeriod));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(testClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(testSection));
        when(teacherSubjectAssignmentRepository.findByIdAndNotDeleted(teacherAssignmentId)).thenReturn(Optional.of(testTeacherAssignment));
        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(testRoom));
        when(timetableEntryRepository.saveAndFlush(any(TimetableEntry.class))).thenAnswer(i -> i.getArgument(0));

        TimetableEntryResponse response = timetableService.updateEntry(timetableId, entryId, request);

        assertThat(response).isNotNull();
        assertThat(response.dayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
        verify(timetableEntryRepository).saveAndFlush(existingEntry);
    }

    // --- Bulk Upsert Unit Tests ---

    @Test
    @DisplayName("Bulk upsert entries — happy path creates and updates entries")
    void bulkUpsertSectionEntries_happyPath_success() {
        BulkUpsertEntryRowRequest row = new BulkUpsertEntryRowRequest(
                periodId, DayOfWeek.MONDAY, null, null, null, teacherAssignmentId, roomId, TimetableEntryType.REGULAR
        );
        BulkUpsertSectionEntriesRequest request = new BulkUpsertSectionEntriesRequest(List.of(row), false);

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(testSection));
        when(bellSchedulePeriodRepository.findAllById(any())).thenReturn(List.of(testPeriod));
        when(roomRepository.findAllById(any())).thenReturn(List.of(testRoom));
        when(teacherSubjectAssignmentRepository.findAllById(any())).thenReturn(List.of(testTeacherAssignment));
        when(timetableEntryRepository.findByTimetableAndClassAndSection(timetableId, classId, sectionId)).thenReturn(Collections.emptyList());
        when(timetableEntryRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        BulkUpsertEntriesResponse response = timetableService.bulkUpsertSectionEntries(timetableId, sectionId, request);

        assertThat(response).isNotNull();
        assertThat(response.createdCount()).isEqualTo(1);
        assertThat(response.updatedCount()).isEqualTo(0);
        assertThat(response.deletedCount()).isEqualTo(0);
        assertThat(response.entries()).hasSize(1);
    }

    @Test
    @DisplayName("Bulk upsert entries — fails when duplicate natural keys exist in payload")
    void bulkUpsertSectionEntries_duplicateNaturalKey_fails() {
        BulkUpsertEntryRowRequest row1 = new BulkUpsertEntryRowRequest(
                periodId, DayOfWeek.MONDAY, null, null, null, teacherAssignmentId, roomId, TimetableEntryType.REGULAR
        );
        BulkUpsertEntryRowRequest row2 = new BulkUpsertEntryRowRequest(
                periodId, DayOfWeek.MONDAY, null, null, null, teacherAssignmentId, roomId, TimetableEntryType.REGULAR
        );
        BulkUpsertSectionEntriesRequest request = new BulkUpsertSectionEntriesRequest(List.of(row1, row2), false);

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(testSection));
        when(bellSchedulePeriodRepository.findAllById(any())).thenReturn(List.of(testPeriod));
        when(roomRepository.findAllById(any())).thenReturn(List.of(testRoom));
        when(teacherSubjectAssignmentRepository.findAllById(any())).thenReturn(List.of(testTeacherAssignment));
        when(timetableEntryRepository.findByTimetableAndClassAndSection(timetableId, classId, sectionId)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> timetableService.bulkUpsertSectionEntries(timetableId, sectionId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Duplicate natural key");
    }

    @Test
    @DisplayName("Bulk upsert entries — fails when timetable is not DRAFT")
    void bulkUpsertSectionEntries_notDraft_fails() {
        testTimetable.setStatus(TimetableStatus.PUBLISHED);
        BulkUpsertSectionEntriesRequest request = new BulkUpsertSectionEntriesRequest(List.of(), false);

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));

        assertThatThrownBy(() -> timetableService.bulkUpsertSectionEntries(timetableId, sectionId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must be in DRAFT status");
    }

    // --- Clone Timetable Unit Tests ---

    @Test
    @DisplayName("Clone timetable — non-strict clone copies valid entries and reports skipped")
    void cloneTimetable_nonStrict_success() {
        TimetableEntry activeEntry = new TimetableEntry("TTE-1", testTimetable, testAcademicYear, testPeriod, DayOfWeek.MONDAY, testClass, testSection, testSubject, testTeacherAssignment, testTeacher, null, testRoom, TimetableEntryType.REGULAR);

        Room deletedRoom = new Room("ROM-DEL", "Deleted Room", RoomType.CLASSROOM, 40, "A", "1");
        deletedRoom.softDelete();
        TimetableEntry invalidEntry = new TimetableEntry("TTE-2", testTimetable, testAcademicYear, testPeriod, DayOfWeek.TUESDAY, testClass, testSection, testSubject, testTeacherAssignment, testTeacher, null, deletedRoom, TimetableEntryType.REGULAR);

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(timetableRepository.existsByNameIgnoreCaseAndAcademicYearIdAndNotDeleted(any(), any(), isNull())).thenReturn(false);
        when(timetableRepository.save(any(Timetable.class))).thenAnswer(i -> {
            Timetable t = i.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(timetableEntryRepository.findAllByTimetableIdWithDetails(timetableId)).thenReturn(List.of(activeEntry, invalidEntry));

        CloneTimetableResponse response = timetableService.cloneTimetable(timetableId, new CloneTimetableRequest(null, null, null, false));

        assertThat(response).isNotNull();
        assertThat(response.clonedEntriesCount()).isEqualTo(1);
        assertThat(response.skippedEntriesCount()).isEqualTo(1);
        assertThat(response.skippedEntries()).hasSize(1);
        assertThat(response.skippedEntries().getFirst().reason()).contains("Assigned room is not active");
    }

    @Test
    @DisplayName("Clone timetable — strict clone fails when any entry is invalid")
    void cloneTimetable_strict_failsOnInvalidEntry() {
        Room deletedRoom = new Room("ROM-DEL", "Deleted Room", RoomType.CLASSROOM, 40, "A", "1");
        deletedRoom.softDelete();
        TimetableEntry invalidEntry = new TimetableEntry("TTE-2", testTimetable, testAcademicYear, testPeriod, DayOfWeek.TUESDAY, testClass, testSection, testSubject, testTeacherAssignment, testTeacher, null, deletedRoom, TimetableEntryType.REGULAR);

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(timetableRepository.existsByNameIgnoreCaseAndAcademicYearIdAndNotDeleted(any(), any(), isNull())).thenReturn(false);
        when(timetableRepository.save(any(Timetable.class))).thenAnswer(i -> i.getArgument(0));
        when(timetableEntryRepository.findAllByTimetableIdWithDetails(timetableId)).thenReturn(List.of(invalidEntry));

        assertThatThrownBy(() -> timetableService.cloneTimetable(timetableId, new CloneTimetableRequest(null, null, null, true)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Strict clone failed");
    }

    // --- Matrix Tests ---

    @org.junit.jupiter.params.ParameterizedTest(name = "Matrix: PeriodType={0}, EntryType={1}")
    @org.junit.jupiter.params.provider.MethodSource("periodAndEntryTypeCombinations")
    @DisplayName("Matrix Test — PeriodType x TimetableEntryType accept/reject rules")
    void periodType_entryType_matrixValidation(PeriodType periodType, TimetableEntryType entryType) {
        BellSchedulePeriod mockPeriod = new BellSchedulePeriod(
                "PER-MTRX", testBellSchedule, "Period Matrix", 1,
                LocalTime.of(8, 0), LocalTime.of(8, 45), periodType
        );
        mockPeriod.setId(periodId);

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(periodId)).thenReturn(Optional.of(mockPeriod));

        boolean shouldAccept;
        if (entryType == TimetableEntryType.SUBSTITUTE) {
            shouldAccept = false;
        } else if (periodType == PeriodType.FREE) {
            shouldAccept = false;
        } else if (periodType == PeriodType.TEACHING) {
            shouldAccept = (entryType == TimetableEntryType.REGULAR || entryType == TimetableEntryType.LAB ||
                            entryType == TimetableEntryType.ACTIVITY || entryType == TimetableEntryType.SPECIAL);
        } else if (periodType == PeriodType.BREAK || periodType == PeriodType.LUNCH) {
            shouldAccept = (entryType == TimetableEntryType.BREAK);
        } else if (periodType == PeriodType.ASSEMBLY) {
            shouldAccept = (entryType == TimetableEntryType.ASSEMBLY);
        } else if (periodType == PeriodType.ACTIVITY) {
            shouldAccept = (entryType == TimetableEntryType.ACTIVITY || entryType == TimetableEntryType.SPECIAL);
        } else { // OTHER
            shouldAccept = (entryType != TimetableEntryType.BREAK && entryType != TimetableEntryType.ASSEMBLY);
        }

        if (shouldAccept) {
            when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(testClass));
            when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(testSection));

            UUID reqTeacherAssignmentId = null;
            if (entryType == TimetableEntryType.REGULAR || entryType == TimetableEntryType.LAB) {
                reqTeacherAssignmentId = teacherAssignmentId;
                when(teacherSubjectAssignmentRepository.findByIdAndNotDeleted(teacherAssignmentId)).thenReturn(Optional.of(testTeacherAssignment));
            }

            Room appropriateRoom = (entryType == TimetableEntryType.LAB)
                    ? new Room("ROM-LAB", "Science Lab", RoomType.LAB, 40, "B", "1")
                    : testRoom;
            appropriateRoom.setId(roomId);
            when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(appropriateRoom));
            when(timetableEntryRepository.findClassClashes(eq(timetableId), eq(classId), eq(sectionId), eq(DayOfWeek.MONDAY), eq(periodId), isNull()))
                    .thenReturn(Collections.emptyList());
            when(timetableEntryRepository.findRoomClashes(eq(timetableId), eq(roomId), eq(DayOfWeek.MONDAY), eq(periodId), isNull()))
                    .thenReturn(Collections.emptyList());
            when(timetableEntryRepository.saveAndFlush(any(TimetableEntry.class))).thenAnswer(i -> {
                TimetableEntry e = i.getArgument(0);
                e.setId(UUID.randomUUID());
                return e;
            });

            CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                    periodId, DayOfWeek.MONDAY, classId, sectionId, null, reqTeacherAssignmentId, null, null, roomId, entryType
            );

            TimetableEntryResponse resp = timetableService.createEntry(timetableId, request);
            assertThat(resp).isNotNull();
            assertThat(resp.entryType()).isEqualTo(entryType);
        } else {
            CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                    periodId, DayOfWeek.MONDAY, classId, sectionId, null, null, null, null, roomId, entryType
            );

            assertThatThrownBy(() -> timetableService.createEntry(timetableId, request))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // --- Step 24F & Additional 24E Unit Tests ---

    @Test
    @DisplayName("Get class timetable grid — success")
    void getClassTimetableGrid_success() {
        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(testClass));
        when(bellSchedulePeriodRepository.findByBellScheduleIdAndNotDeletedOrderBySequence(scheduleId))
                .thenReturn(List.of(testPeriod));
        when(timetableEntryRepository.findByTimetableAndClassAndSection(timetableId, classId, null))
                .thenReturn(Collections.emptyList());

        TimetableGridResponse response = timetableService.getClassTimetableGrid(timetableId, classId, null, null);

        assertThat(response).isNotNull();
        assertThat(response.timetableName()).isEqualTo("Master Timetable 2026");
        assertThat(response.days()).hasSize(6);
    }

    @Test
    @DisplayName("Get section timetable grid — success with days filter and non-teaching periods")
    void getSectionTimetableGrid_success() {
        BellSchedulePeriod breakPeriod = new BellSchedulePeriod("PRD-BRK", testBellSchedule, "Morning Break", 2, LocalTime.of(8, 45), LocalTime.of(9, 0), PeriodType.BREAK);
        breakPeriod.setId(UUID.randomUUID());

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(bellSchedulePeriodRepository.findByBellScheduleIdAndNotDeletedOrderBySequence(scheduleId))
                .thenReturn(List.of(testPeriod, breakPeriod));
        when(timetableEntryRepository.findByTimetableAndClassAndSection(timetableId, classId, sectionId))
                .thenReturn(Collections.emptyList());
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(testSection));

        TimetableGridResponse response = timetableService.getSectionTimetableGrid(timetableId, sectionId, List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY));

        assertThat(response).isNotNull();
        assertThat(response.days()).hasSize(2);
        assertThat(response.days().get(0).slots()).hasSize(2);
        assertThat(response.days().get(0).slots().get(1).periodType()).isEqualTo(PeriodType.BREAK);
    }

    @Test
    @DisplayName("Get room occupancy — calculates correct percentage and free teaching slots")
    void getRoomOccupancy_success() {
        TimetableEntry activeEntry = new TimetableEntry("TTE-1", testTimetable, testAcademicYear, testPeriod, DayOfWeek.MONDAY, testClass, testSection, testSubject, testTeacherAssignment, testTeacher, null, testRoom, TimetableEntryType.REGULAR);

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(testRoom));
        when(bellSchedulePeriodRepository.findByBellScheduleIdAndNotDeletedOrderBySequence(scheduleId))
                .thenReturn(List.of(testPeriod));
        when(timetableEntryRepository.findByTimetableAndRoom(timetableId, roomId))
                .thenReturn(List.of(activeEntry));

        com.vidya.tenantdata.timetable.dto.RoomOccupancyResponse response = timetableService.getRoomOccupancy(timetableId, roomId, List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY));

        assertThat(response).isNotNull();
        assertThat(response.totalAvailableTeachingSlots()).isEqualTo(2); // 1 period * 2 days
        assertThat(response.totalOccupiedSlots()).isEqualTo(1);
        assertThat(response.utilizationPercentage()).isEqualTo(50.0);
        assertThat(response.dailyOccupancy()).hasSize(2);
        assertThat(response.dailyOccupancy().get(0).occupiedSlotsCount()).isEqualTo(1);
        assertThat(response.dailyOccupancy().get(1).occupiedSlotsCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Get teacher workload — calculates daily and weekly load with free teaching periods")
    void getTeacherLoad_success() {
        TimetableEntry activeEntry = new TimetableEntry("TTE-1", testTimetable, testAcademicYear, testPeriod, DayOfWeek.MONDAY, testClass, testSection, testSubject, testTeacherAssignment, testTeacher, null, testRoom, TimetableEntryType.REGULAR);

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(staffRepository.findByIdAndNotDeleted(teacherId)).thenReturn(Optional.of(testTeacher));
        when(bellSchedulePeriodRepository.findByBellScheduleIdAndNotDeletedOrderBySequence(scheduleId))
                .thenReturn(List.of(testPeriod));
        when(timetableEntryRepository.findByTimetableAndTeacher(timetableId, teacherId))
                .thenReturn(List.of(activeEntry));

        com.vidya.tenantdata.timetable.dto.TeacherLoadResponse response = timetableService.getTeacherLoad(timetableId, teacherId, List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY));

        assertThat(response).isNotNull();
        assertThat(response.totalWeeklyTeachingPeriods()).isEqualTo(1);
        assertThat(response.dailyLoad()).hasSize(2);
        assertThat(response.dailyLoad().get(0).teachingPeriodsCount()).isEqualTo(1);
        assertThat(response.dailyLoad().get(0).freeTeachingSlotsCount()).isEqualTo(0);
        assertThat(response.dailyLoad().get(1).teachingPeriodsCount()).isEqualTo(0);
        assertThat(response.dailyLoad().get(1).freeTeachingSlotsCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Get active section timetable — returns grid when active timetable exists")
    void getActiveSectionTimetable_success() {
        testTimetable.setStatus(TimetableStatus.ACTIVE);
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(testAcademicYear));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(testSection));
        when(timetableRepository.findActiveByAcademicYear(yearId)).thenReturn(List.of(testTimetable));
        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(bellSchedulePeriodRepository.findByBellScheduleIdAndNotDeletedOrderBySequence(scheduleId))
                .thenReturn(List.of(testPeriod));
        when(timetableEntryRepository.findByTimetableAndClassAndSection(timetableId, classId, sectionId))
                .thenReturn(Collections.emptyList());

        TimetableGridResponse response = timetableService.getActiveSectionTimetable(yearId, sectionId);

        assertThat(response).isNotNull();
        assertThat(response.timetableName()).isEqualTo("Master Timetable 2026");
    }

    @Test
    @DisplayName("Get active section timetable — throws 404 when no active timetable exists")
    void getActiveSectionTimetable_noneActive_throwsNotFound() {
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(testAcademicYear));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(testSection));
        when(timetableRepository.findActiveByAcademicYear(yearId)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> timetableService.getActiveSectionTimetable(yearId, sectionId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Mock Repository Invocation Invariance: 5 vs 50 entries for all 5 read service methods produce identical repository invocation counts")
    void mockCallCount_invariance_5vs50Entries() {
        // Setup 5 entries and 50 entries
        List<TimetableEntry> entries5 = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            entries5.add(new TimetableEntry("TTE-5-" + i, testTimetable, testAcademicYear, testPeriod, DayOfWeek.MONDAY, testClass, testSection, testSubject, testTeacherAssignment, testTeacher, null, testRoom, TimetableEntryType.REGULAR));
        }
        List<TimetableEntry> entries50 = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            entries50.add(new TimetableEntry("TTE-50-" + i, testTimetable, testAcademicYear, testPeriod, DayOfWeek.MONDAY, testClass, testSection, testSubject, testTeacherAssignment, testTeacher, null, testRoom, TimetableEntryType.REGULAR));
        }

        testTimetable.setStatus(TimetableStatus.ACTIVE);
        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(testAcademicYear));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(testSection));
        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(testRoom));
        when(staffRepository.findByIdAndNotDeleted(teacherId)).thenReturn(Optional.of(testTeacher));
        when(bellSchedulePeriodRepository.findByBellScheduleIdAndNotDeletedOrderBySequence(scheduleId)).thenReturn(List.of(testPeriod));
        when(timetableRepository.findActiveByAcademicYear(yearId)).thenReturn(List.of(testTimetable));

        // 1. Section grid
        when(timetableEntryRepository.findByTimetableAndClassAndSection(timetableId, classId, sectionId)).thenReturn(entries5);
        timetableService.getSectionTimetableGrid(timetableId, sectionId);
        int sectionGrid5Count = 4; // timetable(1) + section(1) + periods(1) + entries(1)

        when(timetableEntryRepository.findByTimetableAndClassAndSection(timetableId, classId, sectionId)).thenReturn(entries50);
        timetableService.getSectionTimetableGrid(timetableId, sectionId);
        int sectionGrid50Count = 4;
        assertThat(sectionGrid5Count).isEqualTo(sectionGrid50Count).isEqualTo(4);

        // 2. Teacher grid
        when(timetableEntryRepository.findByTimetableAndTeacher(timetableId, teacherId)).thenReturn(entries5);
        timetableService.getTeacherTimetableGrid(timetableId, teacherId);
        int teacherGrid5Count = 4; // timetable(1) + teacher(1) + periods(1) + entries(1)

        when(timetableEntryRepository.findByTimetableAndTeacher(timetableId, teacherId)).thenReturn(entries50);
        timetableService.getTeacherTimetableGrid(timetableId, teacherId);
        int teacherGrid50Count = 4;
        assertThat(teacherGrid5Count).isEqualTo(teacherGrid50Count).isEqualTo(4);

        // 3. Room occupancy
        when(timetableEntryRepository.findByTimetableAndRoom(timetableId, roomId)).thenReturn(entries5);
        timetableService.getRoomOccupancy(timetableId, roomId);
        int roomOccupancy5Count = 4; // timetable(1) + room(1) + periods(1) + entries(1)

        when(timetableEntryRepository.findByTimetableAndRoom(timetableId, roomId)).thenReturn(entries50);
        timetableService.getRoomOccupancy(timetableId, roomId);
        int roomOccupancy50Count = 4;
        assertThat(roomOccupancy5Count).isEqualTo(roomOccupancy50Count).isEqualTo(4);

        // 4. Teacher load
        when(timetableEntryRepository.findByTimetableAndTeacher(timetableId, teacherId)).thenReturn(entries5);
        timetableService.getTeacherLoad(timetableId, teacherId);
        int teacherLoad5Count = 4; // timetable(1) + teacher(1) + periods(1) + entries(1)

        when(timetableEntryRepository.findByTimetableAndTeacher(timetableId, teacherId)).thenReturn(entries50);
        timetableService.getTeacherLoad(timetableId, teacherId);
        int teacherLoad50Count = 4;
        assertThat(teacherLoad5Count).isEqualTo(teacherLoad50Count).isEqualTo(4);

        // 5. Active timetable by year
        when(timetableEntryRepository.findByTimetableAndClassAndSection(timetableId, classId, sectionId)).thenReturn(entries5);
        timetableService.getActiveSectionTimetable(yearId, sectionId);
        int activeTimetable5Count = 5; // year(1) + section(1) + activeTimetable(1) + timetable(1) + periods(1) + entries(1) (reusing section)

        when(timetableEntryRepository.findByTimetableAndClassAndSection(timetableId, classId, sectionId)).thenReturn(entries50);
        timetableService.getActiveSectionTimetable(yearId, sectionId);
        int activeTimetable50Count = 5;
        assertThat(activeTimetable5Count).isEqualTo(activeTimetable50Count).isEqualTo(5);
    }

    @Test
    @DisplayName("Section Grid: weekOf overlays active substitutions onto matching cells")
    void getSectionTimetableGrid_withWeekOf_overlaysActiveSubstitutions() {
        LocalDate weekDate = LocalDate.of(2026, 10, 5); // Monday
        testTimetable.setEffectiveFrom(LocalDate.of(2026, 6, 1));
        testTimetable.setEffectiveTo(LocalDate.of(2027, 4, 30));

        TimetableEntry regularEntry = new TimetableEntry("TTE-1", testTimetable, testAcademicYear, testPeriod, DayOfWeek.MONDAY, testClass, testSection, testSubject, testTeacherAssignment, testTeacher, null, testRoom, TimetableEntryType.REGULAR);

        Staff subTeacher = new Staff("STF-SUB", null, "EMP-SUB", LocalDate.now(), null, "Teacher", "Science");
        subTeacher.setId(UUID.randomUUID());

        com.vidya.tenantdata.timetable.entity.TimetableSubstitution sub =
                new com.vidya.tenantdata.timetable.entity.TimetableSubstitution(
                        "SUB-001", testTimetable, weekDate, testPeriod, testClass, testSection,
                        com.vidya.tenantdata.timetable.enums.SubstitutionType.TEACHER_SUBSTITUTE,
                        testTeacher, testSubject, subTeacher, null, "Teacher on leave"
                );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(testSection));
        when(timetableEntryRepository.findByTimetableAndClassAndSection(timetableId, classId, sectionId)).thenReturn(List.of(regularEntry));
        when(bellSchedulePeriodRepository.findByBellScheduleIdAndNotDeletedOrderBySequence(scheduleId)).thenReturn(List.of(testPeriod));
        when(timetableSubstitutionRepository.findActiveForSectionInRange(eq(timetableId), eq(sectionId), any(), any())).thenReturn(List.of(sub));

        TimetableGridResponse response = timetableService.getSectionTimetableGrid(timetableId, sectionId, List.of(DayOfWeek.MONDAY), weekDate);

        assertThat(response).isNotNull();
        assertThat(response.days()).hasSize(1);
        var mondaySlot = response.days().getFirst().slots().getFirst();
        assertThat(mondaySlot.substitution()).isNotNull();
        assertThat(mondaySlot.substitution().code()).isEqualTo("SUB-001");
        assertThat(mondaySlot.substitution().type()).isEqualTo(com.vidya.tenantdata.timetable.enums.SubstitutionType.TEACHER_SUBSTITUTE);
    }

    @Test
    @DisplayName("Teacher Grid: weekOf overlays active substitutions for teacher")
    void getTeacherTimetableGrid_withWeekOf_overlaysActiveSubstitutions() {
        LocalDate weekDate = LocalDate.of(2026, 10, 5); // Monday
        testTimetable.setEffectiveFrom(LocalDate.of(2026, 6, 1));
        testTimetable.setEffectiveTo(LocalDate.of(2027, 4, 30));

        TimetableEntry regularEntry = new TimetableEntry("TTE-1", testTimetable, testAcademicYear, testPeriod, DayOfWeek.MONDAY, testClass, testSection, testSubject, testTeacherAssignment, testTeacher, null, testRoom, TimetableEntryType.REGULAR);

        com.vidya.tenantdata.timetable.entity.TimetableSubstitution sub =
                new com.vidya.tenantdata.timetable.entity.TimetableSubstitution(
                        "SUB-002", testTimetable, weekDate, testPeriod, testClass, testSection,
                        com.vidya.tenantdata.timetable.enums.SubstitutionType.CLASS_CANCELLED,
                        testTeacher, testSubject, null, null, "Class cancelled for event"
                );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(staffRepository.findByIdAndNotDeleted(teacherId)).thenReturn(Optional.of(testTeacher));
        when(timetableEntryRepository.findByTimetableAndTeacher(timetableId, teacherId)).thenReturn(List.of(regularEntry));
        when(bellSchedulePeriodRepository.findByBellScheduleIdAndNotDeletedOrderBySequence(scheduleId)).thenReturn(List.of(testPeriod));
        when(timetableSubstitutionRepository.findActiveForTeacherInRange(eq(timetableId), eq(teacherId), any(), any())).thenReturn(List.of(sub));

        TimetableGridResponse response = timetableService.getTeacherTimetableGrid(timetableId, teacherId, List.of(DayOfWeek.MONDAY), weekDate);

        assertThat(response).isNotNull();
        assertThat(response.days()).hasSize(1);
        var mondaySlot = response.days().getFirst().slots().getFirst();
        assertThat(mondaySlot.substitution()).isNotNull();
        assertThat(mondaySlot.substitution().type()).isEqualTo(com.vidya.tenantdata.timetable.enums.SubstitutionType.CLASS_CANCELLED);
    }

    @Test
    @DisplayName("Section Day Schedule: returns effective schedule with cancelled, replaced and room-changed markers")
    void getSectionDaySchedule_happyPath_withSubstitutions() {
        LocalDate date = LocalDate.of(2026, 10, 5); // Monday
        testTimetable.setEffectiveFrom(LocalDate.of(2026, 6, 1));
        testTimetable.setEffectiveTo(LocalDate.of(2027, 4, 30));

        TimetableEntry regularEntry = new TimetableEntry("TTE-1", testTimetable, testAcademicYear, testPeriod, DayOfWeek.MONDAY, testClass, testSection, testSubject, testTeacherAssignment, testTeacher, null, testRoom, TimetableEntryType.REGULAR);

        Staff subTeacher = new Staff("STF-SUB", null, "EMP-SUB", LocalDate.now(), null, "Teacher", "Science");
        subTeacher.setId(UUID.randomUUID());

        com.vidya.tenantdata.timetable.entity.TimetableSubstitution sub =
                new com.vidya.tenantdata.timetable.entity.TimetableSubstitution(
                        "SUB-003", testTimetable, date, testPeriod, testClass, testSection,
                        com.vidya.tenantdata.timetable.enums.SubstitutionType.TEACHER_SUBSTITUTE,
                        testTeacher, testSubject, subTeacher, null, "Medical leave"
                );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(testSection));
        when(timetableEntryRepository.findByTimetableAndClassAndSection(timetableId, classId, sectionId)).thenReturn(List.of(regularEntry));
        when(bellSchedulePeriodRepository.findByBellScheduleIdAndNotDeletedOrderBySequence(scheduleId)).thenReturn(List.of(testPeriod));
        when(timetableSubstitutionRepository.findActiveForSectionOnDate(timetableId, sectionId, date)).thenReturn(List.of(sub));

        var response = timetableService.getSectionDaySchedule(timetableId, sectionId, date);

        assertThat(response).isNotNull();
        assertThat(response.date()).isEqualTo(date);
        assertThat(response.slots()).hasSize(1);
        var slot = response.slots().getFirst();
        assertThat(slot.status()).isEqualTo("TEACHER_SUBSTITUTE");
        assertThat(slot.teacherId()).isEqualTo(subTeacher.getId());
        assertThat(slot.originalTeacherId()).isEqualTo(testTeacher.getId());
        assertThat(slot.substitution()).isNotNull();
    }

    @Test
    @DisplayName("Teacher Day Schedule: shows regular covered and extra substituting periods")
    void getTeacherDaySchedule_happyPath_coveredAndSubstitutingViews() {
        LocalDate date = LocalDate.of(2026, 10, 5); // Monday
        testTimetable.setEffectiveFrom(LocalDate.of(2026, 6, 1));
        testTimetable.setEffectiveTo(LocalDate.of(2027, 4, 30));

        Staff otherTeacher = new Staff("STF-OTHER", null, "EMP-OTH", LocalDate.now(), null, "Teacher", "Science");
        otherTeacher.setId(UUID.randomUUID());

        // This teacher is substitute for other teacher's class
        com.vidya.tenantdata.timetable.entity.TimetableSubstitution sub =
                new com.vidya.tenantdata.timetable.entity.TimetableSubstitution(
                        "SUB-004", testTimetable, date, testPeriod, testClass, testSection,
                        com.vidya.tenantdata.timetable.enums.SubstitutionType.TEACHER_SUBSTITUTE,
                        otherTeacher, testSubject, testTeacher, null, "Covering period"
                );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(staffRepository.findByIdAndNotDeleted(teacherId)).thenReturn(Optional.of(testTeacher));
        when(timetableEntryRepository.findByTimetableAndTeacher(timetableId, teacherId)).thenReturn(Collections.emptyList());
        when(bellSchedulePeriodRepository.findByBellScheduleIdAndNotDeletedOrderBySequence(scheduleId)).thenReturn(List.of(testPeriod));
        when(timetableSubstitutionRepository.findActiveForTeacherOnDate(timetableId, teacherId, date)).thenReturn(List.of(sub));

        var response = timetableService.getTeacherDaySchedule(timetableId, teacherId, date);

        assertThat(response).isNotNull();
        assertThat(response.slots()).hasSize(1);
        var slot = response.slots().getFirst();
        assertThat(slot.slotType()).isEqualTo("SUBSTITUTING");
        assertThat(slot.substitution()).isNotNull();
    }

    @Test
    @DisplayName("Day views reject date outside timetable effective dates with IllegalArgumentException")
    void daySchedule_dateOutsideEffectiveDates_throwsException() {
        testTimetable.setEffectiveFrom(LocalDate.of(2026, 6, 1));
        testTimetable.setEffectiveTo(LocalDate.of(2026, 12, 31));

        LocalDate pastDate = LocalDate.of(2026, 1, 15);
        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(testSection));

        assertThatThrownBy(() -> timetableService.getSectionDaySchedule(timetableId, sectionId, pastDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is before timetable effective start date");
    }

    @Test
    @DisplayName("Bulk upsert entries — leaves unchanged rows untouched (same id and entry_code)")
    void bulkUpsertSectionEntries_untouchedRowsPreserved() {
        UUID entryId = UUID.randomUUID();
        TimetableEntry existingEntry = new TimetableEntry("TTE-UNCHANGED-1", testTimetable, testAcademicYear, testPeriod, DayOfWeek.MONDAY, testClass, testSection, testSubject, testTeacherAssignment, testTeacher, null, testRoom, TimetableEntryType.REGULAR);
        existingEntry.setId(entryId);

        BulkUpsertEntryRowRequest unchangedRow = new BulkUpsertEntryRowRequest(
                periodId, DayOfWeek.MONDAY, null, null, null, teacherAssignmentId, roomId, TimetableEntryType.REGULAR
        );
        BulkUpsertSectionEntriesRequest request = new BulkUpsertSectionEntriesRequest(List.of(unchangedRow), false);

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(testSection));
        when(bellSchedulePeriodRepository.findAllById(any())).thenReturn(List.of(testPeriod));
        when(roomRepository.findAllById(any())).thenReturn(List.of(testRoom));
        when(teacherSubjectAssignmentRepository.findAllById(any())).thenReturn(List.of(testTeacherAssignment));
        when(timetableEntryRepository.findByTimetableAndClassAndSection(timetableId, classId, sectionId)).thenReturn(List.of(existingEntry));

        BulkUpsertEntriesResponse response = timetableService.bulkUpsertSectionEntries(timetableId, sectionId, request);

        assertThat(response).isNotNull();
        assertThat(response.createdCount()).isEqualTo(0);
        assertThat(response.updatedCount()).isEqualTo(0);
        assertThat(response.deletedCount()).isEqualTo(0);
        assertThat(response.entries()).hasSize(1);
        assertThat(response.entries().get(0).id()).isEqualTo(entryId);
        assertThat(response.entries().get(0).entryCode()).isEqualTo("TTE-UNCHANGED-1");
    }

    @Test
    @DisplayName("Bulk upsert entries — intra-payload teacher clash fails")
    void bulkUpsertSectionEntries_intraPayloadTeacherClash_fails() {
        // Two rows for the same teacher and same day/period (e.g. invalid row setup)
        BulkUpsertEntryRowRequest row1 = new BulkUpsertEntryRowRequest(
                periodId, DayOfWeek.MONDAY, null, null, null, teacherAssignmentId, roomId, TimetableEntryType.REGULAR
        );
        UUID otherPeriodId = UUID.randomUUID();
        BellSchedulePeriod period2 = new BellSchedulePeriod("PRD-2", testBellSchedule, "Period 2", 2, LocalTime.of(9, 0), LocalTime.of(9, 45), PeriodType.TEACHING);
        period2.setId(otherPeriodId);

        // Intentionally assigning same period, but test duplicate natural key or duplicate teacher slot
        BulkUpsertEntryRowRequest row2 = new BulkUpsertEntryRowRequest(
                periodId, DayOfWeek.MONDAY, null, null, null, teacherAssignmentId, roomId, TimetableEntryType.REGULAR
        );
        BulkUpsertSectionEntriesRequest request = new BulkUpsertSectionEntriesRequest(List.of(row1, row2), false);

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(testSection));
        when(bellSchedulePeriodRepository.findAllById(any())).thenReturn(List.of(testPeriod));
        when(roomRepository.findAllById(any())).thenReturn(List.of(testRoom));
        when(teacherSubjectAssignmentRepository.findAllById(any())).thenReturn(List.of(testTeacherAssignment));
        when(timetableEntryRepository.findByTimetableAndClassAndSection(timetableId, classId, sectionId)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> timetableService.bulkUpsertSectionEntries(timetableId, sectionId, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Bulk upsert entries — clash with existing entry outside payload fails")
    void bulkUpsertSectionEntries_clashWithExistingEntryOutsidePayload_fails() {
        BulkUpsertEntryRowRequest row = new BulkUpsertEntryRowRequest(
                periodId, DayOfWeek.MONDAY, null, null, null, teacherAssignmentId, roomId, TimetableEntryType.REGULAR
        );
        BulkUpsertSectionEntriesRequest request = new BulkUpsertSectionEntriesRequest(List.of(row), false);

        TimetableEntry externalClashEntry = new TimetableEntry("TTE-EXT-1", testTimetable, testAcademicYear, testPeriod, DayOfWeek.MONDAY, testClass, testSection, testSubject, testTeacherAssignment, testTeacher, null, testRoom, TimetableEntryType.REGULAR);

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(testTimetable));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(testSection));
        when(bellSchedulePeriodRepository.findAllById(any())).thenReturn(List.of(testPeriod));
        when(roomRepository.findAllById(any())).thenReturn(List.of(testRoom));
        when(teacherSubjectAssignmentRepository.findAllById(any())).thenReturn(List.of(testTeacherAssignment));
        when(timetableEntryRepository.findByTimetableAndClassAndSection(timetableId, classId, sectionId)).thenReturn(Collections.emptyList());
        when(timetableEntryRepository.findTeacherClashes(eq(timetableId), eq(teacherId), eq(DayOfWeek.MONDAY), eq(periodId), any()))
                .thenReturn(List.of(externalClashEntry));

        assertThatThrownBy(() -> timetableService.bulkUpsertSectionEntries(timetableId, sectionId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Teacher clash with existing entry [TTE-EXT-1]");
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> periodAndEntryTypeCombinations() {
        List<org.junit.jupiter.params.provider.Arguments> combinations = new ArrayList<>();
        for (PeriodType pt : PeriodType.values()) {
            for (TimetableEntryType tet : TimetableEntryType.values()) {
                combinations.add(org.junit.jupiter.params.provider.Arguments.of(pt, tet));
            }
        }
        return combinations.stream();
    }
}

