package com.vidya.tenantdata.staff.setup;

import com.vidya.common.exception.BusinessException;
import com.vidya.security.access.account.SchoolAccountService;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.UserStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.staff.entity.Department;
import com.vidya.tenantdata.staff.entity.Designation;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.enums.DepartmentCategory;
import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.enums.StaffType;
import com.vidya.tenantdata.staff.repository.DepartmentRepository;
import com.vidya.tenantdata.staff.repository.DesignationRepository;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.staff.repository.StaffSubjectRepository;
import com.vidya.tenantdata.staff.setup.dto.DutyDtos.DutyRequest;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.BulkOnboardRequest;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.LoginRequest;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.OffboardRequest;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.OnboardStaffRequest;
import com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.TemplateRequest;
import com.vidya.tenantdata.staff.setup.service.StaffDutyService;
import com.vidya.tenantdata.staff.setup.service.StaffOnboardingService;
import com.vidya.tenantdata.staff.setup.service.StaffOrgService;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import com.vidya.tenantdata.user.account.SchoolContext;
import com.vidya.tenantdata.user.account.TenantUserAccountService;
import com.vidya.tenantdata.user.account.dto.AccountDtos.LoginInfo;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserProfile;
import com.vidya.tenantdata.user.entity.TenantUserType;
import com.vidya.tenantdata.user.repository.TenantUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Staff setup — onboarding, offboarding, templates")
class StaffSetupServiceTest {

    @Mock private StaffRepository staffRepository;
    @Mock private StaffSubjectRepository staffSubjectRepository;
    @Mock private TenantUserRepository tenantUserRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private DesignationRepository designationRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private AcademicClassRepository classRepository;
    @Mock private TenantUserAccountService accounts;
    @Mock private SchoolAccountService schoolAccounts;
    @Mock private StaffDutyService duties;
    @Mock private SchoolContext context;
    @Mock private EntityManager em;

    private StaffOnboardingService onboarding;
    private StaffOrgService org;
    private Designation pgt;
    private Designation driver;
    private Department science;
    private final UUID school = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        onboarding = new StaffOnboardingService(staffRepository, staffSubjectRepository, tenantUserRepository, departmentRepository,
                designationRepository, subjectRepository, classRepository, accounts, duties, context);
        org = new StaffOrgService(departmentRepository, designationRepository, staffRepository, schoolAccounts, duties, context);
        ReflectionTestUtils.setField(onboarding, "em", em);
        ReflectionTestUtils.setField(org, "em", em);
        Query q = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(q);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        when(q.getSingleResult()).thenReturn(2L);
        when(q.getResultList()).thenReturn(List.of());
        when(q.executeUpdate()).thenReturn(1);

        science = new Department("SCIENCE", "Science", DepartmentCategory.ACADEMIC, null, 1);
        science.setId(UUID.randomUUID());
        pgt = designation("PGT", "Post Graduate Teacher (PGT)", StaffType.TEACHER, "TEACHER");
        pgt.setDefaultDepartmentId(science.getId());
        driver = designation("DRIVER", "Driver", StaffType.SUPPORT, null);
        when(departmentRepository.findByIdAndNotDeleted(science.getId())).thenReturn(Optional.of(science));
        when(context.schoolId()).thenReturn(school);
        when(context.linkedMasterUserIds()).thenReturn(Set.of());
        when(tenantUserRepository.save(any())).thenAnswer(i -> withId(i.getArgument(0)));
        when(staffRepository.save(any())).thenAnswer(i -> {
            Staff s = i.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            return s;
        });
        when(accounts.attach(any(), eq(school), anyString(), any(), any(), any(), any())).thenAnswer(i -> {
            TenantUser u = i.getArgument(0);
            UUID userId = UUID.randomUUID();
            u.setMasterUserId(userId);
            return new LoginInfo(userId, "USR-9", i.getArgument(2), i.getArgument(3), UserStatus.ACTIVE, true, "Vtemp12345@", List.of());
        });
        when(duties.endAllFor(any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("teacher onboarding: type, department and login role come from the designation; duties applied; login linked")
    void onboardTeacher() {
        DutyRequest hod = new DutyRequest(null, "HOD", science.getId(), null, null, null, null, null, null, null, null);
        StaffDutyService.Prepared prepared = new StaffDutyService.Prepared(
                new com.vidya.tenantdata.staff.entity.DutyType("HOD", "Head of Department", com.vidya.tenantdata.staff.enums.DutyCategory.ACADEMIC,
                        com.vidya.tenantdata.staff.enums.DutyScope.DEPARTMENT, true, "COORDINATOR"),
                hod, science.getId(), null, null, null, null, null, LocalDate.now(), "Science");
        when(duties.prepare(eq(hod), any(), any())).thenReturn(prepared);

        var r = onboarding.onboard(req("Asha", "asha@school.in", null, pgt.getId(), List.of(hod), null));

        assertThat(r.staff().getStaffType()).isEqualTo(StaffType.TEACHER);
        assertThat(r.staff().getDepartmentId()).isEqualTo(science.getId());
        assertThat(r.staff().getDesignation()).isEqualTo("Post Graduate Teacher (PGT)");
        assertThat(r.login().role()).isEqualTo(PlatformRole.TEACHER);
        assertThat(r.login().temporaryPassword()).isNotBlank();
        assertThat(r.staff().isHasLogin()).isTrue();
        verify(duties).apply(any(Staff.class), eq(prepared));
        verify(context).requireAuthority(eq("USER_CREATE"), anyString());
        verify(context).requireAuthority(eq("SCHOOL_ROLE_MANAGE"), anyString()); // the HOD duty carries a role
    }

    @Test
    @DisplayName("driver without email: phone is enough and no login is created")
    void onboardDriver() {
        var r = onboarding.onboard(req("Ramesh", null, "9876543210", driver.getId(), null, null));

        assertThat(r.login()).isNull();
        assertThat(r.staff().getStaffType()).isEqualTo(StaffType.SUPPORT);
        verify(accounts, never()).attach(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("validation: contact needed, login needs email, extra roles need a login, type needed, bad duty")
    void validation() {
        assertThatThrownBy(() -> onboarding.onboard(req("A", null, null, pgt.getId(), null, null))).hasMessageContaining("email or phone");
        assertThatThrownBy(() -> onboarding.onboard(req("A", null, "999", pgt.getId(), null, new LoginRequest(true, null, null))))
                .hasMessageContaining("needs an email");
        assertThatThrownBy(() -> onboarding.onboard(req("A", null, "999", driver.getId(), null, new LoginRequest(false, null, List.of(UUID.randomUUID())))))
                .hasMessageContaining("extra roles need a login");
        assertThatThrownBy(() -> onboarding.onboard(req("A", "a@b.in", null, null, null, null))).hasMessageContaining("staffType or designationId");

        DutyRequest bad = new DutyRequest(null, "HOD", null, null, null, null, null, null, null, null, null);
        when(duties.prepare(eq(bad), any(), any())).thenAnswer(i -> {
            ((List<String>) i.getArgument(2)).add("Head of Department needs an active departmentId");
            return null;
        });
        assertThatThrownBy(() -> onboarding.onboard(req("A", "a@b.in", null, pgt.getId(), List.of(bad), null))).hasMessageContaining("departmentId");
        verify(staffRepository, never()).save(any());
    }

    @Test
    @DisplayName("bulk: duplicate email inside the file fails the whole batch and saves nothing")
    void bulkAllOrNothing() {
        var r = onboarding.bulkOnboard(new BulkOnboardRequest(List.of(
                req("A", "same@school.in", null, pgt.getId(), null, null),
                req("B", "same@school.in", null, pgt.getId(), null, null),
                req("C", null, "98", driver.getId(), null, null)), false));

        assertThat(r.valid()).isFalse();
        assertThat(r.created()).isZero();
        assertThat(r.rows().get(0).error()).isNull();
        assertThat(r.rows().get(1).error()).contains("already used");
        verify(staffRepository, never()).save(any());
        verify(accounts, never()).attach(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("offboarding: only exit statuses; login disabled, duties ended and open assignments reported")
    void offboard() {
        Staff s = staff();
        when(accounts.disable(s.getTenantUser())).thenReturn(3);
        when(duties.endAllFor(eq(s.getId()), any())).thenReturn(List.of("Head of Department (Science)"));

        assertThatThrownBy(() -> onboarding.offboard(s.getId(), new OffboardRequest(EmploymentStatus.ACTIVE, null, null, null, null)))
                .isInstanceOf(BusinessException.class);

        var r = onboarding.offboard(s.getId(), new OffboardRequest(EmploymentStatus.RESIGNED, LocalDate.now(), null, false, "Moved city"));

        assertThat(s.getEmploymentStatus()).isEqualTo(EmploymentStatus.RESIGNED);
        assertThat(r.loginDisabled()).isTrue();
        assertThat(r.sessionsEnded()).isEqualTo(3);
        assertThat(r.classTeacherAssignments()).isEqualTo(2);
        assertThat(r.assignmentsEnded()).isFalse();
        assertThat(r.dutiesEnded()).containsExactly("Head of Department (Science)");
        assertThat(r.nextSteps()).anyMatch(n -> n.contains("class teacher"));
        verify(context).requireAuthority(eq("USER_UPDATE"), anyString());
    }

    @Test
    @DisplayName("department head is a duty: creating a department with a head hands it to the duty service")
    void departmentHead() {
        when(departmentRepository.save(any())).thenAnswer(i -> {
            Department d = i.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });
        UUID head = UUID.randomUUID();

        org.createDepartment(new com.vidya.tenantdata.staff.setup.dto.StaffSetupDtos.DepartmentRequest("MATH", "Mathematics", null,
                DepartmentCategory.ACADEMIC, head, 2));

        verify(duties).setDepartmentHead(any(Department.class), eq(head));
    }

    @Test
    @DisplayName("template: dry run lists every standard department and designation")
    void template() {
        var r = org.applyTemplate(new TemplateRequest(null, null, true));

        assertThat(r.departmentsCreated()).hasSize(18).contains("Science", "Transport");
        assertThat(r.designationsCreated()).hasSize(29).contains("Principal", "Trained Graduate Teacher (TGT)", "Driver");
        verify(departmentRepository, never()).save(any());
    }

    private Staff staff() {
        TenantUser u = new TenantUser("USR-1", UUID.randomUUID(), "t@school.in", null, TenantUserType.TEACHER);
        u.setProfile(new TenantUserProfile(u, "Meena", "Iyer"));
        Staff s = new Staff("STF-1", u, null, LocalDate.of(2020, 6, 1), StaffType.TEACHER, null, null);
        s.setId(UUID.randomUUID());
        when(staffRepository.findByIdAndNotDeleted(s.getId())).thenReturn(Optional.of(s));
        return s;
    }

    private Designation designation(String code, String name, StaffType type, String role) {
        Designation d = new Designation(code, name, type, role, 50);
        d.setId(UUID.randomUUID());
        when(designationRepository.findByIdAndNotDeleted(d.getId())).thenReturn(Optional.of(d));
        return d;
    }

    private static TenantUser withId(TenantUser u) {
        if (u.getId() == null) {
            u.setId(UUID.randomUUID());
        }
        return u;
    }

    private static OnboardStaffRequest req(String first, String email, String phone, UUID designationId, List<DutyRequest> duties, LoginRequest login) {
        return new OnboardStaffRequest(first, "K", email, phone, null, null, null, LocalDate.now(), null, designationId, null, null, null,
                null, null, null, null, duties, login);
    }
}
