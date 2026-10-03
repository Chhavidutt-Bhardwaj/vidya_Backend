package com.vidya.tenantdata.timetable.service;

import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.assignment.entity.ClassSubjectAssignment;
import com.vidya.tenantdata.assignment.entity.TeacherSubjectAssignment;
import com.vidya.tenantdata.assignment.repository.ClassSubjectAssignmentRepository;
import com.vidya.tenantdata.assignment.repository.TeacherSubjectAssignmentRepository;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import com.vidya.tenantdata.timetable.dto.CreateTimetableEntryRequest;
import com.vidya.tenantdata.timetable.dto.ScheduleValidationResponse;
import com.vidya.tenantdata.timetable.entity.BellSchedule;
import com.vidya.tenantdata.timetable.entity.BellSchedulePeriod;
import com.vidya.tenantdata.timetable.entity.Room;
import com.vidya.tenantdata.timetable.entity.Timetable;
import com.vidya.tenantdata.timetable.entity.TimetableEntry;
import com.vidya.tenantdata.timetable.enums.PeriodType;
import com.vidya.tenantdata.timetable.enums.RoomType;
import com.vidya.tenantdata.timetable.enums.ScheduleConflictType;
import com.vidya.tenantdata.timetable.enums.TimetableEntryType;
import com.vidya.tenantdata.timetable.repository.BellSchedulePeriodRepository;
import com.vidya.tenantdata.timetable.repository.RoomRepository;
import com.vidya.tenantdata.timetable.repository.TimetableEntryRepository;
import com.vidya.tenantdata.timetable.repository.TimetableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TimetableConflictValidator — Unit Tests")
class TimetableConflictValidatorTest {

    @Mock
    private TimetableRepository timetableRepository;

    @Mock
    private TimetableEntryRepository timetableEntryRepository;

    @Mock
    private BellSchedulePeriodRepository bellSchedulePeriodRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private AcademicClassRepository academicClassRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private TeacherSubjectAssignmentRepository teacherSubjectAssignmentRepository;

    @Mock
    private ClassSubjectAssignmentRepository classSubjectAssignmentRepository;

    @InjectMocks
    private TimetableConflictValidatorImpl validator;

    private Timetable timetable;
    private AcademicYear academicYear;
    private BellSchedule bellSchedule;
    private BellSchedulePeriod period1;
    private AcademicClass class8;
    private Section sectionA;
    private Staff teacherA;
    private Room room101;
    private UUID timetableId;
    private UUID periodId;
    private UUID classId;
    private UUID sectionId;
    private UUID teacherId;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        timetableId = UUID.randomUUID();
        periodId = UUID.randomUUID();
        classId = UUID.randomUUID();
        sectionId = UUID.randomUUID();
        teacherId = UUID.randomUUID();
        roomId = UUID.randomUUID();

        academicYear = new AcademicYear("AY-2026", "2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 5, 31), true, com.vidya.tenantdata.academic.enums.AcademicYearStatus.ACTIVE);
        academicYear.setId(UUID.randomUUID());

        bellSchedule = new BellSchedule("BSC-1", "Standard", null, true);
        bellSchedule.setId(UUID.randomUUID());

        period1 = new BellSchedulePeriod("PRD-1", bellSchedule, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45), PeriodType.TEACHING);
        period1.setId(periodId);

        class8 = new AcademicClass("CLS-8", "Class 8", "Class 8", 8);
        class8.setId(classId);

        sectionA = new Section(academicYear, class8, "SEC-A", "Section A", 40);
        sectionA.setId(sectionId);

        teacherA = new Staff("STF-1", null, "EMP-1", LocalDate.now(), null, "Teacher", "Math");
        teacherA.setId(teacherId);

        room101 = new Room("ROM-101", "Room 101", RoomType.CLASSROOM, 40, "Main", "1st");
        room101.setId(roomId);

        timetable = new Timetable("TTB-1", academicYear, bellSchedule, "Main Timetable", null, null, null);
        timetable.setId(timetableId);
    }

    @Test
    @DisplayName("Valid entry has zero conflicts")
    void validateEntry_noConflicts_returnsValid() {
        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                periodId, DayOfWeek.MONDAY, classId, sectionId, null, null, teacherId, null, roomId, TimetableEntryType.REGULAR
        );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(timetable));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(periodId)).thenReturn(Optional.of(period1));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(class8));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(sectionA));
        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(room101));
        when(timetableEntryRepository.findTeacherClashes(timetableId, teacherId, DayOfWeek.MONDAY, periodId, null))
                .thenReturn(List.of());
        when(timetableEntryRepository.findClassClashes(timetableId, classId, sectionId, DayOfWeek.MONDAY, periodId, null))
                .thenReturn(List.of());
        when(timetableEntryRepository.findRoomClashes(timetableId, roomId, DayOfWeek.MONDAY, periodId, null))
                .thenReturn(List.of());

        ScheduleValidationResponse response = validator.validateEntry(timetableId, request, null);

        assertThat(response.valid()).isTrue();
        assertThat(response.totalConflicts()).isEqualTo(0);
    }

    @Test
    @DisplayName("Detects teacher clash when teacher is already booked in the same period")
    void validateEntry_teacherClash_detected() {
        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                periodId, DayOfWeek.MONDAY, classId, sectionId, null, null, teacherId, null, null, TimetableEntryType.REGULAR
        );

        TimetableEntry existingEntry = new TimetableEntry(
                "TTE-1", timetable, academicYear, period1, DayOfWeek.MONDAY, class8, sectionA, null, null, teacherA, null, null, TimetableEntryType.REGULAR
        );
        existingEntry.setId(UUID.randomUUID());

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(timetable));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(periodId)).thenReturn(Optional.of(period1));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(class8));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(sectionA));
        when(timetableEntryRepository.findTeacherClashes(timetableId, teacherId, DayOfWeek.MONDAY, periodId, null))
                .thenReturn(List.of(existingEntry));
        when(timetableEntryRepository.findClassClashes(timetableId, classId, sectionId, DayOfWeek.MONDAY, periodId, null))
                .thenReturn(List.of());

        ScheduleValidationResponse response = validator.validateEntry(timetableId, request, null);

        assertThat(response.valid()).isFalse();
        assertThat(response.totalConflicts()).isEqualTo(1);
        assertThat(response.conflicts().getFirst().type()).isEqualTo(ScheduleConflictType.TEACHER_CONFLICT);
    }

    @Test
    @DisplayName("Detects class clash when class/section already has a subject scheduled in the same period")
    void validateEntry_classClash_detected() {
        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                periodId, DayOfWeek.MONDAY, classId, sectionId, null, null, null, null, null, TimetableEntryType.REGULAR
        );

        TimetableEntry existingEntry = new TimetableEntry(
                "TTE-1", timetable, academicYear, period1, DayOfWeek.MONDAY, class8, sectionA, null, null, null, null, null, TimetableEntryType.REGULAR
        );
        existingEntry.setId(UUID.randomUUID());

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(timetable));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(periodId)).thenReturn(Optional.of(period1));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(class8));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(sectionA));
        when(timetableEntryRepository.findClassClashes(timetableId, classId, sectionId, DayOfWeek.MONDAY, periodId, null))
                .thenReturn(List.of(existingEntry));

        ScheduleValidationResponse response = validator.validateEntry(timetableId, request, null);

        assertThat(response.valid()).isFalse();
        assertThat(response.totalConflicts()).isEqualTo(1);
        assertThat(response.conflicts().getFirst().type()).isEqualTo(ScheduleConflictType.CLASS_CONFLICT);
    }

    @Test
    @DisplayName("Detects room clash when room is occupied in the same period")
    void validateEntry_roomClash_detected() {
        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                periodId, DayOfWeek.MONDAY, classId, sectionId, null, null, null, null, roomId, TimetableEntryType.REGULAR
        );

        TimetableEntry existingEntry = new TimetableEntry(
                "TTE-1", timetable, academicYear, period1, DayOfWeek.MONDAY, class8, sectionA, null, null, null, null, room101, TimetableEntryType.REGULAR
        );
        existingEntry.setId(UUID.randomUUID());

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(timetable));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(periodId)).thenReturn(Optional.of(period1));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(class8));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(sectionA));
        when(roomRepository.findByIdAndNotDeleted(roomId)).thenReturn(Optional.of(room101));
        when(timetableEntryRepository.findClassClashes(timetableId, classId, sectionId, DayOfWeek.MONDAY, periodId, null))
                .thenReturn(List.of());
        when(timetableEntryRepository.findRoomClashes(timetableId, roomId, DayOfWeek.MONDAY, periodId, null))
                .thenReturn(List.of(existingEntry));

        ScheduleValidationResponse response = validator.validateEntry(timetableId, request, null);

        assertThat(response.valid()).isFalse();
        assertThat(response.totalConflicts()).isEqualTo(1);
        assertThat(response.conflicts().getFirst().type()).isEqualTo(ScheduleConflictType.ROOM_CONFLICT);
    }

    @Test
    @DisplayName("Detects period mismatch when period belongs to different bell schedule")
    void validateEntry_periodMismatch_detected() {
        BellSchedule anotherSchedule = new BellSchedule("BSC-2", "Winter", null, false);
        anotherSchedule.setId(UUID.randomUUID());
        BellSchedulePeriod invalidPeriod = new BellSchedulePeriod("PRD-99", anotherSchedule, "Winter Period 1", 1, LocalTime.of(9, 0), LocalTime.of(9, 45), PeriodType.TEACHING);
        invalidPeriod.setId(periodId);

        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                periodId, DayOfWeek.MONDAY, classId, sectionId, null, null, null, null, null, TimetableEntryType.REGULAR
        );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(timetable));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(periodId)).thenReturn(Optional.of(invalidPeriod));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(class8));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(sectionA));
        when(timetableEntryRepository.findClassClashes(timetableId, classId, sectionId, DayOfWeek.MONDAY, periodId, null))
                .thenReturn(List.of());

        ScheduleValidationResponse response = validator.validateEntry(timetableId, request, null);

        assertThat(response.valid()).isFalse();
        assertThat(response.conflicts().stream().anyMatch(c -> c.type() == ScheduleConflictType.PERIOD_CONFLICT)).isTrue();
    }
}
