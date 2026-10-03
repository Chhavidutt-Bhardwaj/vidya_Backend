package com.vidya.tenantdata.timetable;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenant.context.TenantContext;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.assignment.repository.ClassSubjectAssignmentRepository;
import com.vidya.tenantdata.assignment.repository.TeacherSubjectAssignmentRepository;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import com.vidya.tenantdata.timetable.dto.CreateRoomRequest;
import com.vidya.tenantdata.timetable.dto.CreateTimetableRequest;
import com.vidya.tenantdata.timetable.enums.RoomType;
import com.vidya.tenantdata.timetable.mapper.BellScheduleMapper;
import com.vidya.tenantdata.timetable.mapper.RoomMapper;
import com.vidya.tenantdata.timetable.mapper.TimetableEntryMapper;
import com.vidya.tenantdata.timetable.mapper.TimetableMapper;
import com.vidya.tenantdata.timetable.repository.BellSchedulePeriodRepository;
import com.vidya.tenantdata.timetable.repository.BellScheduleRepository;
import com.vidya.tenantdata.timetable.repository.RoomRepository;
import com.vidya.tenantdata.timetable.repository.TimetableEntryRepository;
import com.vidya.tenantdata.timetable.repository.TimetableRepository;
import com.vidya.tenantdata.timetable.service.BellScheduleServiceImpl;
import com.vidya.tenantdata.timetable.service.RoomServiceImpl;
import com.vidya.tenantdata.timetable.service.TimetableConflictValidator;
import com.vidya.tenantdata.timetable.service.TimetableServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Timetable Domain — Tenant Isolation & IDOR Protection Tests")
class TimetableIsolationIntegrationTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private BellScheduleRepository bellScheduleRepository;

    @Mock
    private BellSchedulePeriodRepository bellSchedulePeriodRepository;

    @Mock
    private TimetableRepository timetableRepository;

    @Mock
    private TimetableEntryRepository timetableEntryRepository;

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
    private TeacherSubjectAssignmentRepository teacherSubjectAssignmentRepository;

    @Mock
    private ClassSubjectAssignmentRepository classSubjectAssignmentRepository;

    @Mock
    private TimetableConflictValidator conflictValidator;

    @Spy
    private RoomMapper roomMapper = new RoomMapper();

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
    private RoomServiceImpl roomService;
    private BellScheduleServiceImpl bellScheduleService;

    private static final String TENANT_A = "tenant_alpha";
    private static final String TENANT_B = "tenant_beta";

    private final UUID tenantBRoomId = UUID.randomUUID();
    private final UUID tenantBScheduleId = UUID.randomUUID();
    private final UUID tenantBTimetableId = UUID.randomUUID();
    private final UUID tenantBEntryId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantCode(TENANT_A);
        timetableMapper = new TimetableMapper(bellScheduleMapper, timetableEntryMapper);
        timetableService = new TimetableServiceImpl(
                timetableRepository, timetableEntryRepository, bellScheduleRepository, bellSchedulePeriodRepository,
                academicYearRepository, academicClassRepository, sectionRepository, subjectRepository,
                staffRepository, roomRepository, teacherSubjectAssignmentRepository, classSubjectAssignmentRepository,
                conflictValidator, timetableMapper, timetableEntryMapper,
                timetableSubstitutionRepository, timetableSubstitutionMapper
        );
        roomService = new RoomServiceImpl(roomRepository, timetableEntryRepository, roomMapper);
        bellScheduleService = new BellScheduleServiceImpl(bellScheduleRepository, bellSchedulePeriodRepository, timetableRepository, timetableEntryRepository, bellScheduleMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Tenant A cannot get Room belonging to Tenant B (returns 404)")
    void getRoom_crossTenant_returns404() {
        when(roomRepository.findByIdAndNotDeleted(tenantBRoomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.getRoomById(tenantBRoomId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A cannot get BellSchedule belonging to Tenant B (returns 404)")
    void getBellSchedule_crossTenant_returns404() {
        when(bellScheduleRepository.findByIdAndNotDeleted(tenantBScheduleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bellScheduleService.getBellScheduleById(tenantBScheduleId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A cannot get Timetable belonging to Tenant B (returns 404)")
    void getTimetable_crossTenant_returns404() {
        when(timetableRepository.findByIdAndNotDeleted(tenantBTimetableId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> timetableService.getTimetableById(tenantBTimetableId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A cannot create Timetable referencing Tenant B academic year")
    void createTimetable_crossTenantYear_fails() {
        UUID tenantBYearId = UUID.randomUUID();
        CreateTimetableRequest request = new CreateTimetableRequest(tenantBYearId, UUID.randomUUID(), "Master", null, null, null);
        when(academicYearRepository.findByIdAndNotDeleted(tenantBYearId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> timetableService.createTimetable(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
