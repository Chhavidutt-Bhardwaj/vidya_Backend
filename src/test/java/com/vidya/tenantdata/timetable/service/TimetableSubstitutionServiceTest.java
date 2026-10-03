package com.vidya.tenantdata.timetable.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.assignment.repository.TeacherSubjectAssignmentRepository;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.timetable.dto.CreateSubstitutionRequest;
import com.vidya.tenantdata.timetable.dto.SubstitutionResponse;
import com.vidya.tenantdata.timetable.dto.UpdateSubstitutionRequest;
import com.vidya.tenantdata.timetable.entity.BellSchedule;
import com.vidya.tenantdata.timetable.entity.BellSchedulePeriod;
import com.vidya.tenantdata.timetable.entity.Room;
import com.vidya.tenantdata.timetable.entity.Timetable;
import com.vidya.tenantdata.timetable.entity.TimetableEntry;
import com.vidya.tenantdata.timetable.entity.TimetableSubstitution;
import com.vidya.tenantdata.timetable.enums.PeriodType;
import com.vidya.tenantdata.timetable.enums.RoomStatus;
import com.vidya.tenantdata.timetable.enums.SubstitutionStatus;
import com.vidya.tenantdata.timetable.enums.SubstitutionType;
import com.vidya.tenantdata.timetable.enums.TimetableEntryType;
import com.vidya.tenantdata.timetable.enums.TimetableStatus;
import com.vidya.tenantdata.timetable.mapper.TimetableSubstitutionMapper;
import com.vidya.tenantdata.timetable.repository.BellSchedulePeriodRepository;
import com.vidya.tenantdata.timetable.repository.RoomRepository;
import com.vidya.tenantdata.timetable.repository.TimetableEntryRepository;
import com.vidya.tenantdata.timetable.repository.TimetableRepository;
import com.vidya.tenantdata.timetable.repository.TimetableSubstitutionRepository;
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

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TimetableSubstitutionService — Unit Tests")
class TimetableSubstitutionServiceTest {

    @Mock
    private TimetableSubstitutionRepository substitutionRepository;

    @Mock
    private TimetableRepository timetableRepository;

    @Mock
    private TimetableEntryRepository timetableEntryRepository;

    @Mock
    private BellSchedulePeriodRepository bellSchedulePeriodRepository;

    @Mock
    private AcademicClassRepository academicClassRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private TeacherSubjectAssignmentRepository teacherSubjectAssignmentRepository;

    @Spy
    private TimetableSubstitutionMapper substitutionMapper = new TimetableSubstitutionMapper();

    @InjectMocks
    private TimetableSubstitutionServiceImpl substitutionService;

    private final LocalDate fixedToday = LocalDate.of(2026, 10, 5); // A Monday
    private Clock fixedClock;

    private UUID timetableId;
    private Timetable timetable;
    private AcademicYear academicYear;
    private BellSchedule bellSchedule;
    private BellSchedulePeriod period1;
    private AcademicClass class10;
    private Section sectionA;
    private Subject mathSubject;
    private Staff originalTeacher;
    private Staff substituteTeacher;
    private Room room101;
    private Room substituteRoom;
    private TimetableEntry baseEntry;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(fixedToday.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        substitutionService.setClock(fixedClock);

        timetableId = UUID.randomUUID();
        academicYear = new AcademicYear("AY-2026", "2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 5, 31), true, com.vidya.tenantdata.academic.enums.AcademicYearStatus.ACTIVE);
        academicYear.setId(UUID.randomUUID());

        bellSchedule = new BellSchedule("SCH-01", "Regular Schedule", "Desc", true);
        bellSchedule.setId(UUID.randomUUID());

        timetable = new Timetable("TT-2026-01", academicYear, bellSchedule, "Main Timetable", "Desc",
                LocalDate.of(2026, 6, 1), LocalDate.of(2027, 5, 31));
        timetable.setId(timetableId);
        timetable.setStatus(TimetableStatus.ACTIVE);

        period1 = new BellSchedulePeriod("PRD-1", bellSchedule, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45), PeriodType.TEACHING);
        period1.setId(UUID.randomUUID());

        class10 = new AcademicClass("CLS-10", "Class 10", "Class 10", 10);
        class10.setId(UUID.randomUUID());

        sectionA = new Section(academicYear, class10, "10-A", "Section A", 40);
        sectionA.setId(UUID.randomUUID());

        mathSubject = new Subject("MATH", "Mathematics", "MATH101", null, com.vidya.tenantdata.subject.enums.SubjectType.CORE, 100);
        mathSubject.setId(UUID.randomUUID());

        originalTeacher = new Staff("STF-001", null, "EMP-001", LocalDate.of(2020, 1, 1), com.vidya.tenantdata.staff.enums.StaffType.TEACHER, "Teacher", "Math");
        originalTeacher.setId(UUID.randomUUID());

        substituteTeacher = new Staff("STF-002", null, "EMP-002", LocalDate.of(2021, 1, 1), com.vidya.tenantdata.staff.enums.StaffType.TEACHER, "Teacher", "Math");
        substituteTeacher.setId(UUID.randomUUID());

        room101 = new Room("ROM-101", "Room 101", com.vidya.tenantdata.timetable.enums.RoomType.CLASSROOM, 40, "Main", "1st");
        room101.setId(UUID.randomUUID());

        substituteRoom = new Room("ROM-102", "Room 102", com.vidya.tenantdata.timetable.enums.RoomType.CLASSROOM, 40, "Main", "1st");
        substituteRoom.setId(UUID.randomUUID());

        baseEntry = new TimetableEntry("TTE-001", timetable, academicYear, period1, DayOfWeek.MONDAY,
                class10, sectionA, mathSubject, null, originalTeacher, null, room101, TimetableEntryType.REGULAR);
        baseEntry.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Create Substitution — Happy path TEACHER_SUBSTITUTE with sameSubjectQualified=true")
    void createSubstitution_teacherSubstitute_success() {
        CreateSubstitutionRequest req = new CreateSubstitutionRequest(
                timetableId, fixedToday, period1.getId(), class10.getId(), sectionA.getId(),
                SubstitutionType.TEACHER_SUBSTITUTE, substituteTeacher.getId(), null, "Teacher on medical leave"
        );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(timetable));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(period1.getId())).thenReturn(Optional.of(period1));
        when(academicClassRepository.findByIdAndNotDeleted(class10.getId())).thenReturn(Optional.of(class10));
        when(sectionRepository.findByIdAndNotDeleted(sectionA.getId())).thenReturn(Optional.of(sectionA));
        when(timetableEntryRepository.findActiveSlotEntries(timetableId, DayOfWeek.MONDAY, period1.getId(), class10.getId(), sectionA.getId()))
                .thenReturn(List.of(baseEntry));
        when(substitutionRepository.findActiveSlotSubstitutions(timetableId, fixedToday, period1.getId(), class10.getId(), sectionA.getId()))
                .thenReturn(Collections.emptyList());
        when(staffRepository.findByIdAndNotDeleted(substituteTeacher.getId())).thenReturn(Optional.of(substituteTeacher));
        when(timetableEntryRepository.findTeacherClashes(timetableId, substituteTeacher.getId(), DayOfWeek.MONDAY, period1.getId(), null))
                .thenReturn(Collections.emptyList());
        when(substitutionRepository.findActiveTeacherSubstitutions(fixedToday, period1.getId(), substituteTeacher.getId(), null))
                .thenReturn(Collections.emptyList());
        when(teacherSubjectAssignmentRepository.isTeacherQualifiedForSubject(substituteTeacher.getId(), mathSubject.getId(), academicYear.getId()))
                .thenReturn(true);
        when(substitutionRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        SubstitutionResponse res = substitutionService.createSubstitution(req);

        assertThat(res).isNotNull();
        assertThat(res.substitutionType()).isEqualTo(SubstitutionType.TEACHER_SUBSTITUTE);
        assertThat(res.originalTeacherId()).isEqualTo(originalTeacher.getId());
        assertThat(res.substituteTeacherId()).isEqualTo(substituteTeacher.getId());
        assertThat(res.sameSubjectQualified()).isTrue();
        assertThat(res.status()).isEqualTo(SubstitutionStatus.ACTIVE);
    }

    @Test
    @DisplayName("Create Substitution — Happy path ROOM_CHANGE")
    void createSubstitution_roomChange_success() {
        CreateSubstitutionRequest req = new CreateSubstitutionRequest(
                timetableId, fixedToday, period1.getId(), class10.getId(), sectionA.getId(),
                SubstitutionType.ROOM_CHANGE, null, substituteRoom.getId(), "Room renovation"
        );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(timetable));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(period1.getId())).thenReturn(Optional.of(period1));
        when(academicClassRepository.findByIdAndNotDeleted(class10.getId())).thenReturn(Optional.of(class10));
        when(sectionRepository.findByIdAndNotDeleted(sectionA.getId())).thenReturn(Optional.of(sectionA));
        when(timetableEntryRepository.findActiveSlotEntries(timetableId, DayOfWeek.MONDAY, period1.getId(), class10.getId(), sectionA.getId()))
                .thenReturn(List.of(baseEntry));
        when(substitutionRepository.findActiveSlotSubstitutions(timetableId, fixedToday, period1.getId(), class10.getId(), sectionA.getId()))
                .thenReturn(Collections.emptyList());
        when(roomRepository.findByIdAndNotDeleted(substituteRoom.getId())).thenReturn(Optional.of(substituteRoom));
        when(timetableEntryRepository.findRoomClashes(timetableId, substituteRoom.getId(), DayOfWeek.MONDAY, period1.getId(), null))
                .thenReturn(Collections.emptyList());
        when(substitutionRepository.findActiveRoomSubstitutions(fixedToday, period1.getId(), substituteRoom.getId(), null))
                .thenReturn(Collections.emptyList());
        when(substitutionRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        SubstitutionResponse res = substitutionService.createSubstitution(req);

        assertThat(res).isNotNull();
        assertThat(res.substitutionType()).isEqualTo(SubstitutionType.ROOM_CHANGE);
        assertThat(res.substituteRoomId()).isEqualTo(substituteRoom.getId());
    }

    @Test
    @DisplayName("Create Substitution — Happy path CLASS_CANCELLED")
    void createSubstitution_classCancelled_success() {
        CreateSubstitutionRequest req = new CreateSubstitutionRequest(
                timetableId, fixedToday, period1.getId(), class10.getId(), sectionA.getId(),
                SubstitutionType.CLASS_CANCELLED, null, null, "Sports day preparation"
        );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(timetable));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(period1.getId())).thenReturn(Optional.of(period1));
        when(academicClassRepository.findByIdAndNotDeleted(class10.getId())).thenReturn(Optional.of(class10));
        when(sectionRepository.findByIdAndNotDeleted(sectionA.getId())).thenReturn(Optional.of(sectionA));
        when(timetableEntryRepository.findActiveSlotEntries(timetableId, DayOfWeek.MONDAY, period1.getId(), class10.getId(), sectionA.getId()))
                .thenReturn(List.of(baseEntry));
        when(substitutionRepository.findActiveSlotSubstitutions(timetableId, fixedToday, period1.getId(), class10.getId(), sectionA.getId()))
                .thenReturn(Collections.emptyList());
        when(substitutionRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        SubstitutionResponse res = substitutionService.createSubstitution(req);

        assertThat(res).isNotNull();
        assertThat(res.substitutionType()).isEqualTo(SubstitutionType.CLASS_CANCELLED);
        assertThat(res.substituteTeacherId()).isNull();
        assertThat(res.substituteRoomId()).isNull();
    }

    @Test
    @DisplayName("Create Substitution — Fails when date is in the past")
    void createSubstitution_pastDate_throwsBusinessException() {
        CreateSubstitutionRequest req = new CreateSubstitutionRequest(
                timetableId, fixedToday.minusDays(1), period1.getId(), class10.getId(), sectionA.getId(),
                SubstitutionType.TEACHER_SUBSTITUTE, substituteTeacher.getId(), null, "Past date"
        );

        assertThatThrownBy(() -> substitutionService.createSubstitution(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be in the past");
    }

    @Test
    @DisplayName("Create Substitution — Fails when timetable is not ACTIVE")
    void createSubstitution_inactiveTimetable_throwsBusinessException() {
        timetable.setStatus(TimetableStatus.DRAFT);
        CreateSubstitutionRequest req = new CreateSubstitutionRequest(
                timetableId, fixedToday, period1.getId(), class10.getId(), sectionA.getId(),
                SubstitutionType.TEACHER_SUBSTITUTE, substituteTeacher.getId(), null, "Draft"
        );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(timetable));

        assertThatThrownBy(() -> substitutionService.createSubstitution(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ACTIVE timetables");
    }

    @Test
    @DisplayName("Create Substitution — Fails when date is outside timetable effective date bounds")
    void createSubstitution_outsideEffectiveDates_throwsBusinessException() {
        timetable.setEffectiveTo(fixedToday.minusDays(1));
        CreateSubstitutionRequest req = new CreateSubstitutionRequest(
                timetableId, fixedToday, period1.getId(), class10.getId(), sectionA.getId(),
                SubstitutionType.TEACHER_SUBSTITUTE, substituteTeacher.getId(), null, "Expired"
        );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(timetable));

        assertThatThrownBy(() -> substitutionService.createSubstitution(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("after timetable effective end date");
    }

    @Test
    @DisplayName("Create Substitution — Fails when no active entry matches slot")
    void createSubstitution_noMatchingEntry_throwsBusinessException() {
        CreateSubstitutionRequest req = new CreateSubstitutionRequest(
                timetableId, fixedToday, period1.getId(), class10.getId(), sectionA.getId(),
                SubstitutionType.TEACHER_SUBSTITUTE, substituteTeacher.getId(), null, "No entry"
        );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(timetable));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(period1.getId())).thenReturn(Optional.of(period1));
        when(academicClassRepository.findByIdAndNotDeleted(class10.getId())).thenReturn(Optional.of(class10));
        when(sectionRepository.findByIdAndNotDeleted(sectionA.getId())).thenReturn(Optional.of(sectionA));
        when(timetableEntryRepository.findActiveSlotEntries(timetableId, DayOfWeek.MONDAY, period1.getId(), class10.getId(), sectionA.getId()))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> substitutionService.createSubstitution(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No active timetable entry exists");
    }

    @Test
    @DisplayName("Create Substitution — Fails when active substitution already exists for slot (409)")
    void createSubstitution_slotAlreadySubstituted_throwsDuplicateResourceException() {
        CreateSubstitutionRequest req = new CreateSubstitutionRequest(
                timetableId, fixedToday, period1.getId(), class10.getId(), sectionA.getId(),
                SubstitutionType.TEACHER_SUBSTITUTE, substituteTeacher.getId(), null, "Duplicate"
        );

        TimetableSubstitution existingSub = new TimetableSubstitution("SUB-001", timetable, fixedToday, period1, class10, sectionA,
                SubstitutionType.CLASS_CANCELLED, originalTeacher, mathSubject, null, null, "Prior sub");

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(timetable));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(period1.getId())).thenReturn(Optional.of(period1));
        when(academicClassRepository.findByIdAndNotDeleted(class10.getId())).thenReturn(Optional.of(class10));
        when(sectionRepository.findByIdAndNotDeleted(sectionA.getId())).thenReturn(Optional.of(sectionA));
        when(timetableEntryRepository.findActiveSlotEntries(timetableId, DayOfWeek.MONDAY, period1.getId(), class10.getId(), sectionA.getId()))
                .thenReturn(List.of(baseEntry));
        when(substitutionRepository.findActiveSlotSubstitutions(timetableId, fixedToday, period1.getId(), class10.getId(), sectionA.getId()))
                .thenReturn(List.of(existingSub));

        assertThatThrownBy(() -> substitutionService.createSubstitution(req))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Active substitution");
    }

    @Test
    @DisplayName("Create Substitution — Fails when substitute teacher is same as original teacher")
    void createSubstitution_sameTeacher_throwsBusinessException() {
        CreateSubstitutionRequest req = new CreateSubstitutionRequest(
                timetableId, fixedToday, period1.getId(), class10.getId(), sectionA.getId(),
                SubstitutionType.TEACHER_SUBSTITUTE, originalTeacher.getId(), null, "Self substitute"
        );

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(timetable));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(period1.getId())).thenReturn(Optional.of(period1));
        when(academicClassRepository.findByIdAndNotDeleted(class10.getId())).thenReturn(Optional.of(class10));
        when(sectionRepository.findByIdAndNotDeleted(sectionA.getId())).thenReturn(Optional.of(sectionA));
        when(timetableEntryRepository.findActiveSlotEntries(timetableId, DayOfWeek.MONDAY, period1.getId(), class10.getId(), sectionA.getId()))
                .thenReturn(List.of(baseEntry));
        when(substitutionRepository.findActiveSlotSubstitutions(timetableId, fixedToday, period1.getId(), class10.getId(), sectionA.getId()))
                .thenReturn(Collections.emptyList());
        when(staffRepository.findByIdAndNotDeleted(originalTeacher.getId())).thenReturn(Optional.of(originalTeacher));

        assertThatThrownBy(() -> substitutionService.createSubstitution(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be the same as the original teacher");
    }

    @Test
    @DisplayName("Create Substitution — Fails when substitute teacher has regular entry clash (409)")
    void createSubstitution_teacherScheduledClash_throwsDuplicateResourceException() {
        CreateSubstitutionRequest req = new CreateSubstitutionRequest(
                timetableId, fixedToday, period1.getId(), class10.getId(), sectionA.getId(),
                SubstitutionType.TEACHER_SUBSTITUTE, substituteTeacher.getId(), null, "Clash"
        );

        TimetableEntry clashEntry = new TimetableEntry("TTE-CLASH", timetable, academicYear, period1, DayOfWeek.MONDAY,
                class10, null, mathSubject, null, substituteTeacher, null, room101, TimetableEntryType.REGULAR);

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(timetable));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(period1.getId())).thenReturn(Optional.of(period1));
        when(academicClassRepository.findByIdAndNotDeleted(class10.getId())).thenReturn(Optional.of(class10));
        when(sectionRepository.findByIdAndNotDeleted(sectionA.getId())).thenReturn(Optional.of(sectionA));
        when(timetableEntryRepository.findActiveSlotEntries(timetableId, DayOfWeek.MONDAY, period1.getId(), class10.getId(), sectionA.getId()))
                .thenReturn(List.of(baseEntry));
        when(substitutionRepository.findActiveSlotSubstitutions(timetableId, fixedToday, period1.getId(), class10.getId(), sectionA.getId()))
                .thenReturn(Collections.emptyList());
        when(staffRepository.findByIdAndNotDeleted(substituteTeacher.getId())).thenReturn(Optional.of(substituteTeacher));
        when(timetableEntryRepository.findTeacherClashes(timetableId, substituteTeacher.getId(), DayOfWeek.MONDAY, period1.getId(), null))
                .thenReturn(List.of(clashEntry));
        when(substitutionRepository.findActiveFreedTeacherEntries(timetableId, fixedToday, period1.getId(), substituteTeacher.getId()))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> substitutionService.createSubstitution(req))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already scheduled to teach");
    }

    @Test
    @DisplayName("Create Substitution — Fails when substitute room is booked by regular entry (409)")
    void createSubstitution_roomBooked_throwsDuplicateResourceException() {
        CreateSubstitutionRequest req = new CreateSubstitutionRequest(
                timetableId, fixedToday, period1.getId(), class10.getId(), sectionA.getId(),
                SubstitutionType.ROOM_CHANGE, null, substituteRoom.getId(), "Room Clash"
        );

        TimetableEntry roomClash = new TimetableEntry("TTE-ROOM-CLASH", timetable, academicYear, period1, DayOfWeek.MONDAY,
                class10, null, mathSubject, null, originalTeacher, null, substituteRoom, TimetableEntryType.REGULAR);

        when(timetableRepository.findByIdAndNotDeleted(timetableId)).thenReturn(Optional.of(timetable));
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(period1.getId())).thenReturn(Optional.of(period1));
        when(academicClassRepository.findByIdAndNotDeleted(class10.getId())).thenReturn(Optional.of(class10));
        when(sectionRepository.findByIdAndNotDeleted(sectionA.getId())).thenReturn(Optional.of(sectionA));
        when(timetableEntryRepository.findActiveSlotEntries(timetableId, DayOfWeek.MONDAY, period1.getId(), class10.getId(), sectionA.getId()))
                .thenReturn(List.of(baseEntry));
        when(substitutionRepository.findActiveSlotSubstitutions(timetableId, fixedToday, period1.getId(), class10.getId(), sectionA.getId()))
                .thenReturn(Collections.emptyList());
        when(roomRepository.findByIdAndNotDeleted(substituteRoom.getId())).thenReturn(Optional.of(substituteRoom));
        when(timetableEntryRepository.findRoomClashes(timetableId, substituteRoom.getId(), DayOfWeek.MONDAY, period1.getId(), null))
                .thenReturn(List.of(roomClash));

        assertThatThrownBy(() -> substitutionService.createSubstitution(req))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already booked");
    }

    @Test
    @DisplayName("Update Substitution — Cancelled substitution cannot be updated")
    void updateSubstitution_cancelledStatus_throwsBusinessException() {
        UUID subId = UUID.randomUUID();
        TimetableSubstitution sub = new TimetableSubstitution("SUB-01", timetable, fixedToday, period1, class10, sectionA,
                SubstitutionType.TEACHER_SUBSTITUTE, originalTeacher, mathSubject, substituteTeacher, null, "Reason");
        sub.cancel();

        when(substitutionRepository.findByIdAndNotDeleted(subId)).thenReturn(Optional.of(sub));

        UpdateSubstitutionRequest req = new UpdateSubstitutionRequest(null, null, "New Reason");

        assertThatThrownBy(() -> substitutionService.updateSubstitution(subId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only ACTIVE substitutions can be updated");
    }

    @Test
    @DisplayName("Cancel Substitution — Successfully transitions ACTIVE to CANCELLED")
    void cancelSubstitution_success() {
        UUID subId = UUID.randomUUID();
        TimetableSubstitution sub = new TimetableSubstitution("SUB-01", timetable, fixedToday, period1, class10, sectionA,
                SubstitutionType.TEACHER_SUBSTITUTE, originalTeacher, mathSubject, substituteTeacher, null, "Reason");

        when(substitutionRepository.findByIdAndNotDeleted(subId)).thenReturn(Optional.of(sub));
        when(substitutionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubstitutionResponse res = substitutionService.cancelSubstitution(subId);

        assertThat(res.status()).isEqualTo(SubstitutionStatus.CANCELLED);
    }

    @Test
    @DisplayName("Delete Substitution — Successfully soft deletes substitution")
    void deleteSubstitution_success() {
        UUID subId = UUID.randomUUID();
        TimetableSubstitution sub = new TimetableSubstitution("SUB-01", timetable, fixedToday, period1, class10, sectionA,
                SubstitutionType.TEACHER_SUBSTITUTE, originalTeacher, mathSubject, substituteTeacher, null, "Reason");

        when(substitutionRepository.findByIdAndNotDeleted(subId)).thenReturn(Optional.of(sub));

        substitutionService.deleteSubstitution(subId);

        assertThat(sub.isDeleted()).isTrue();
        assertThat(sub.getStatus()).isEqualTo(SubstitutionStatus.DELETED);
        verify(substitutionRepository).save(sub);
    }
}
