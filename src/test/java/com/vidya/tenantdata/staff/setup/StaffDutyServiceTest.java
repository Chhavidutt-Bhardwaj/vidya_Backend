package com.vidya.tenantdata.staff.setup;

import com.vidya.common.exception.BusinessException;
import com.vidya.security.access.account.SchoolAccountService;
import com.vidya.security.access.account.SchoolAccountService.Account;
import com.vidya.security.access.dto.UserRoleResponse;
import com.vidya.security.access.service.AccessControlService;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.UserStatus;
import com.vidya.tenantdata.academic.enums.ClassStage;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.assignment.repository.ClassTeacherAssignmentRepository;
import com.vidya.tenantdata.assignment.repository.TeacherSubjectAssignmentRepository;
import com.vidya.tenantdata.staff.entity.Department;
import com.vidya.tenantdata.staff.entity.DutyType;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.entity.StaffDuty;
import com.vidya.tenantdata.staff.enums.DepartmentCategory;
import com.vidya.tenantdata.staff.enums.DutyCategory;
import com.vidya.tenantdata.staff.enums.DutyScope;
import com.vidya.tenantdata.staff.enums.DutyStatus;
import com.vidya.tenantdata.staff.enums.StaffType;
import com.vidya.tenantdata.staff.repository.DepartmentRepository;
import com.vidya.tenantdata.staff.repository.DutyTypeRepository;
import com.vidya.tenantdata.staff.repository.StaffDutyRepository;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.staff.setup.dto.DutyDtos.DutyRequest;
import com.vidya.tenantdata.staff.setup.dto.DutyDtos.EndDutyRequest;
import com.vidya.tenantdata.staff.setup.service.StaffDutyService;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import com.vidya.tenantdata.user.account.AccountLinkedEvent;
import com.vidya.tenantdata.user.account.SchoolContext;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserProfile;
import com.vidya.tenantdata.user.entity.TenantUserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Duties — a teacher can be HOD, coordinator and exam in-charge at once")
class StaffDutyServiceTest {

    @Mock private DutyTypeRepository dutyTypeRepository;
    @Mock private StaffDutyRepository dutyRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private AcademicClassRepository classRepository;
    @Mock private SectionRepository sectionRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private AcademicYearRepository yearRepository;
    @Mock private ClassTeacherAssignmentRepository classTeacherRepository;
    @Mock private TeacherSubjectAssignmentRepository subjectTeacherRepository;
    @Mock private AccessControlService accessControlService;
    @Mock private SchoolAccountService accounts;
    @Mock private SchoolContext context;

    private StaffDutyService service;
    private final UUID school = UUID.randomUUID();
    private final List<StaffDuty> saved = new ArrayList<>();
    private DutyType hod;
    private DutyType exam;
    private DutyType stage;
    private Department science;

    @BeforeEach
    void setUp() {
        service = new StaffDutyService(dutyTypeRepository, dutyRepository, staffRepository, departmentRepository, classRepository, sectionRepository,
                subjectRepository, yearRepository, classTeacherRepository, subjectTeacherRepository, accessControlService, accounts, context);
        when(context.schoolId()).thenReturn(school);
        hod = type("HOD", DutyScope.DEPARTMENT, true, "COORDINATOR");
        exam = type("EXAM_COORDINATOR", DutyScope.SCHOOL, false, "EXAM_COORDINATOR");
        stage = type("STAGE_COORDINATOR", DutyScope.STAGE, true, "COORDINATOR");
        when(dutyTypeRepository.findAll()).thenReturn(List.of(hod, exam, stage));
        when(dutyTypeRepository.findAllNotDeleted()).thenReturn(List.of(hod, exam, stage));
        science = new Department("SCIENCE", "Science", DepartmentCategory.ACADEMIC, null, 1);
        science.setId(UUID.randomUUID());
        when(departmentRepository.findByIdAndNotDeleted(science.getId())).thenReturn(Optional.of(science));
        when(departmentRepository.findById(science.getId())).thenReturn(Optional.of(science));
        when(dutyRepository.save(any())).thenAnswer(i -> {
            StaffDuty d = i.getArgument(0);
            if (d.getId() == null) {
                d.setId(UUID.randomUUID());
            }
            saved.add(d);
            return d;
        });
        when(dutyRepository.findByDutyTypeIdAndStatus(any(), eq(DutyStatus.ACTIVE))).thenAnswer(i ->
                saved.stream().filter(d -> d.getDutyTypeId().equals(i.getArgument(0)) && d.getStatus() == DutyStatus.ACTIVE).toList());
        when(dutyRepository.findByStaffIdAndStatus(any(), eq(DutyStatus.ACTIVE))).thenAnswer(i ->
                saved.stream().filter(d -> d.getStaffId().equals(i.getArgument(0)) && d.getStatus() == DutyStatus.ACTIVE).toList());
        when(dutyRepository.findById(any())).thenAnswer(i -> saved.stream().filter(d -> d.getId().equals(i.getArgument(0))).findFirst());
        when(dutyRepository.findByStatus(DutyStatus.ACTIVE)).thenAnswer(i -> saved.stream().filter(d -> d.getStatus() == DutyStatus.ACTIVE).toList());
        when(accessControlService.ensureSchoolRole(eq(school), anyString(), any(), any(), any())).thenReturn(UUID.randomUUID());
        when(accessControlService.findRoleId(anyString(), eq(school))).thenReturn(Optional.of(UUID.randomUUID()));
        when(accessControlService.assignRole(any(), any(), eq(school))).thenAnswer(i ->
                new UserRoleResponse(UUID.randomUUID(), UUID.randomUUID(), "R", "R", null, null, false, true, true, null, null, null, null, null));
    }

    @Test
    @DisplayName("HOD needs a department, sets the department head and puts COORDINATOR on the login")
    void hod() {
        Staff meena = staff(true, PlatformRole.TEACHER);

        assertThatThrownBy(() -> service.assign(meena.getId(), req(hod, null, null))).hasMessageContaining("departmentId");

        var r = service.assign(meena.getId(), req(hod, science.getId(), null));

        assertThat(r.scopeLabel()).isEqualTo("Science");
        assertThat(r.roleGranted()).isTrue();
        assertThat(science.getHeadStaffId()).isEqualTo(meena.getId());
        verify(context).requireAuthority(eq("SCHOOL_ROLE_MANAGE"), anyString());
        verify(accessControlService).assignRole(eq(meena.getTenantUser().getMasterUserId()), any(), eq(school));
    }

    @Test
    @DisplayName("one HOD per department: refused unless replace=true, which hands over and clears the old holder's role")
    void singleHolder() {
        Staff meena = staff(true, PlatformRole.TEACHER);
        Staff ravi = staff(true, PlatformRole.TEACHER);
        service.assign(meena.getId(), req(hod, science.getId(), null));

        assertThatThrownBy(() -> service.assign(ravi.getId(), req(hod, science.getId(), null))).hasMessageContaining("replace=true");
        assertThatThrownBy(() -> service.assign(meena.getId(), req(hod, science.getId(), null))).hasMessageContaining("already");

        var r = service.assign(ravi.getId(), new DutyRequest(hod.getId(), null, science.getId(), null, null, null, null, null, null, null, true));

        assertThat(science.getHeadStaffId()).isEqualTo(ravi.getId());
        assertThat(saved.stream().filter(d -> d.getStaffId().equals(meena.getId())).findFirst().orElseThrow().getStatus()).isEqualTo(DutyStatus.ENDED);
        verify(accessControlService).removeAssignment(eq(meena.getTenantUser().getMasterUserId()), any(), eq(school));
        assertThat(r.staffId()).isEqualTo(ravi.getId());
    }

    @Test
    @DisplayName("many duties at once; a shared role stays until the last duty needing it ends")
    void manyDuties() {
        Staff meena = staff(true, PlatformRole.TEACHER);
        var h = service.assign(meena.getId(), req(hod, science.getId(), null));
        var s = service.assign(meena.getId(), new DutyRequest(stage.getId(), null, null, ClassStage.MIDDLE, null, null, null, null, null, null, null));
        var e = service.assign(meena.getId(), req(exam, null, null));

        assertThat(service.dutiesOf(meena.getId(), false).duties()).hasSize(3);
        assertThat(s.scopeLabel()).contains("Middle");
        verify(accessControlService).ensureSchoolRole(eq(school), eq("EXAM_COORDINATOR"), any(), any(), any());

        service.end(h.id(), new EndDutyRequest(LocalDate.now()));
        verify(accessControlService, never()).removeAssignment(any(), any(), any()); // stage coordinator still needs COORDINATOR

        service.end(s.id(), null);
        verify(accessControlService).removeAssignment(eq(meena.getTenantUser().getMasterUserId()), any(), eq(school));
        assertThat(service.dutiesOf(meena.getId(), false).duties()).extracting(d -> d.dutyCode()).containsExactly("EXAM_COORDINATOR");
        assertThat(service.dutiesOf(meena.getId(), true).duties()).hasSize(3);
    }

    @Test
    @DisplayName("no login yet: duty is recorded, role follows when the login is created")
    void roleFollowsLogin() {
        Staff noLogin = staff(false, null);
        var r = service.assign(noLogin.getId(), req(exam, null, null));
        assertThat(r.roleGranted()).isFalse();
        verify(accessControlService, never()).assignRole(any(), any(), any());

        UUID userId = UUID.randomUUID();
        noLogin.getTenantUser().setMasterUserId(userId);
        when(accounts.get(school, userId)).thenReturn(new Account(userId, "U", "e", PlatformRole.TEACHER, UserStatus.ACTIVE, false, null));
        when(staffRepository.findByTenantUserIdAndNotDeleted(noLogin.getTenantUser().getId())).thenReturn(Optional.of(noLogin));

        service.onAccountLinked(new AccountLinkedEvent(noLogin.getTenantUser().getId(), userId, school));

        verify(accessControlService).assignRole(eq(userId), any(), eq(school));
        assertThat(saved.get(0).getRoleAssignmentId()).isNotNull();
    }

    @Test
    @DisplayName("offboarding ends every duty and clears the department head")
    void endAll() {
        Staff meena = staff(true, PlatformRole.TEACHER);
        service.assign(meena.getId(), req(hod, science.getId(), null));
        service.assign(meena.getId(), req(exam, null, null));

        List<String> ended = service.endAllFor(meena.getId(), LocalDate.now());

        assertThat(ended).containsExactlyInAnyOrder("Head of Department (Science)", "Examination Coordinator");
        assertThat(science.getHeadStaffId()).isNull();
        assertThat(saved).allMatch(d -> d.getStatus() == DutyStatus.ENDED);
    }

    @Test
    @DisplayName("standard duty types cannot be deleted or re-scoped; inactive staff cannot hold duties")
    void guards() {
        assertThatThrownBy(() -> service.deleteDutyType(hod.getId())).isInstanceOf(BusinessException.class).hasMessageContaining("Standard");
        Staff gone = staff(true, PlatformRole.TEACHER);
        gone.updateStatus(com.vidya.tenantdata.staff.enums.EmploymentStatus.RESIGNED);
        assertThatThrownBy(() -> service.assign(gone.getId(), req(exam, null, null))).hasMessageContaining("RESIGNED");
    }

    private DutyRequest req(DutyType t, UUID departmentId, ClassStage s) {
        return new DutyRequest(t.getId(), null, departmentId, s, null, null, null, null, null, null, null);
    }

    private DutyType type(String code, DutyScope scope, boolean single, String role) {
        DutyType t = new DutyType(code, switch (code) {
            case "HOD" -> "Head of Department";
            case "EXAM_COORDINATOR" -> "Examination Coordinator";
            default -> "Academic Coordinator";
        }, DutyCategory.ACADEMIC, scope, single, role);
        t.setId(UUID.randomUUID());
        t.setSystem(true);
        when(dutyTypeRepository.findByIdAndNotDeleted(t.getId())).thenReturn(Optional.of(t));
        when(dutyTypeRepository.findById(t.getId())).thenReturn(Optional.of(t));
        when(dutyTypeRepository.findByCode(code)).thenReturn(Optional.of(t));
        return t;
    }

    private Staff staff(boolean login, PlatformRole role) {
        TenantUser u = new TenantUser("USR-" + saved.size(), login ? UUID.randomUUID() : null, "t@school.in", null, TenantUserType.TEACHER);
        u.setId(UUID.randomUUID());
        u.setProfile(new TenantUserProfile(u, "Meena", "Iyer"));
        Staff s = new Staff("STF-" + UUID.randomUUID().toString().substring(0, 4), u, null, LocalDate.of(2020, 6, 1), StaffType.TEACHER, null, null);
        s.setId(UUID.randomUUID());
        when(staffRepository.findByIdAndNotDeleted(s.getId())).thenReturn(Optional.of(s));
        when(staffRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(staffRepository.findAllById(any())).thenAnswer(i -> List.of(s));
        if (login) {
            when(accounts.get(school, u.getMasterUserId())).thenReturn(new Account(u.getMasterUserId(), "U", "e", role, UserStatus.ACTIVE, false, null));
        }
        when(dutyRepository.findByStaffIdOrderByStartDateDesc(s.getId())).thenAnswer(i -> saved.stream().filter(d -> d.getStaffId().equals(s.getId())).toList());
        return s;
    }
}
