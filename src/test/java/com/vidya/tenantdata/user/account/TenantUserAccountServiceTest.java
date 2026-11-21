package com.vidya.tenantdata.user.account;

import com.vidya.common.exception.BusinessException;
import com.vidya.security.access.account.SchoolAccountService;
import com.vidya.security.access.account.SchoolAccountService.Account;
import com.vidya.security.access.account.SchoolAccountService.Kind;
import com.vidya.security.access.dto.UserRoleResponse;
import com.vidya.security.access.service.AccessControlService;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.UserStatus;
import com.vidya.tenantdata.staff.entity.Designation;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.enums.StaffType;
import com.vidya.tenantdata.staff.repository.DesignationRepository;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.user.account.dto.AccountDtos.CreateLoginRequest;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserProfile;
import com.vidya.tenantdata.user.entity.TenantUserType;
import com.vidya.tenantdata.user.repository.TenantUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Person accounts — one login API for staff, students and parents")
class TenantUserAccountServiceTest {

    @Mock private TenantUserRepository tenantUserRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private DesignationRepository designationRepository;
    @Mock private SchoolAccountService accounts;
    @Mock private AccessControlService accessControlService;
    @Mock private SchoolContext context;
    @Mock private ApplicationEventPublisher events;

    private TenantUserAccountService service;
    private final UUID school = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TenantUserAccountService(tenantUserRepository, staffRepository, designationRepository, accounts, accessControlService, context, events);
        when(context.schoolId()).thenReturn(school);
        when(context.linkedMasterUserIds()).thenReturn(Set.of());
        when(accounts.createOrAdopt(eq(school), any(), anyString(), anyString(), any(), any(), any(), any())).thenAnswer(i ->
                new Account(UUID.randomUUID(), "USR-9", i.getArgument(2), i.getArgument(6), UserStatus.ACTIVE, true, "Vtemp12345@"));
        when(accessControlService.listUserRoles(any(), eq(school))).thenReturn(List.of());
    }

    @Test
    @DisplayName("staff: account type defaults from the designation; the link is recorded and announced")
    void staffDefaultRole() {
        TenantUser u = person(TenantUserType.TEACHER, "meena@school.in");
        Designation hod = new Designation("ACCOUNTANT", "Accountant", StaffType.ADMINISTRATIVE, "ACCOUNTS", 60);
        hod.setId(UUID.randomUUID());
        Staff s = new Staff("STF-1", u, null, LocalDate.now(), StaffType.ADMINISTRATIVE, "Accountant", null);
        s.setDesignationId(hod.getId());
        when(staffRepository.findByTenantUserIdAndNotDeleted(u.getId())).thenReturn(Optional.of(s));
        when(designationRepository.findByIdAndNotDeleted(hod.getId())).thenReturn(Optional.of(hod));

        var r = service.createLogin(u.getId(), null);

        assertThat(r.login().role()).isEqualTo(PlatformRole.ACCOUNTS);
        assertThat(r.login().temporaryPassword()).isNotBlank();
        assertThat(r.staffId()).isNull(); // response built before staff link lookup is irrelevant here
        assertThat(u.getMasterUserId()).isEqualTo(r.login().userId());
        verify(accounts).createOrAdopt(eq(school), eq(Kind.STAFF), eq("meena@school.in"), eq("Meena"), any(), any(), eq(PlatformRole.ACCOUNTS), any());
        verify(events).publishEvent(any(AccountLinkedEvent.class));
    }

    @Test
    @DisplayName("student and parent get STUDENT / PARENT accounts through the very same call")
    void studentAndParent() {
        TenantUser st = person(TenantUserType.STUDENT, "kid@school.in");
        TenantUser pa = person(TenantUserType.PARENT, "dad@gmail.com");

        assertThat(service.createLogin(st.getId(), null).login().role()).isEqualTo(PlatformRole.STUDENT);
        assertThat(service.createLogin(pa.getId(), null).login().role()).isEqualTo(PlatformRole.PARENT);
        verify(accounts).createOrAdopt(eq(school), eq(Kind.STUDENT), eq("kid@school.in"), any(), any(), any(), eq(PlatformRole.STUDENT), any());
        verify(accounts).createOrAdopt(eq(school), eq(Kind.PARENT), eq("dad@gmail.com"), any(), any(), any(), eq(PlatformRole.PARENT), any());
    }

    @Test
    @DisplayName("refusals: no email, already linked, resigned staff")
    void refusals() {
        TenantUser noMail = person(TenantUserType.STAFF, null);
        assertThatThrownBy(() -> service.createLogin(noMail.getId(), null)).hasMessageContaining("email");

        TenantUser linked = person(TenantUserType.STAFF, "l@school.in");
        linked.setMasterUserId(UUID.randomUUID());
        assertThatThrownBy(() -> service.createLogin(linked.getId(), null)).hasMessageContaining("already has a login");

        TenantUser gone = person(TenantUserType.TEACHER, "g@school.in");
        Staff s = new Staff("STF-2", gone, null, LocalDate.of(2020, 1, 1), StaffType.TEACHER, null, null);
        s.updateStatus(EmploymentStatus.RESIGNED);
        when(staffRepository.findByTenantUserIdAndNotDeleted(gone.getId())).thenReturn(Optional.of(s));
        assertThatThrownBy(() -> service.createLogin(gone.getId(), null)).isInstanceOf(BusinessException.class).hasMessageContaining("RESIGNED");
    }

    @Test
    @DisplayName("a failure after the login was created removes that login when the transaction rolls back")
    void compensation() {
        TenantUser u = person(TenantUserType.TEACHER, "asha@school.in");
        when(accessControlService.assignRole(any(), any(), eq(school))).thenThrow(new AccessDeniedException("no"));
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> service.createLogin(u.getId(), new CreateLoginRequest(null, null, List.of(UUID.randomUUID()))))
                    .isInstanceOf(AccessDeniedException.class);
            var syncs = TransactionSynchronizationManager.getSynchronizations();
            assertThat(syncs).hasSize(1);
            syncs.forEach(x -> x.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(accounts).deleteCreated(any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("setRoles: extra roles are synced to the exact set")
    void setRoles() {
        TenantUser u = person(TenantUserType.TEACHER, "t@school.in");
        UUID userId = UUID.randomUUID();
        u.setMasterUserId(userId);
        UUID keep = UUID.randomUUID();
        UUID drop = UUID.randomUUID();
        UUID add = UUID.randomUUID();
        when(accessControlService.listUserRoles(userId, school)).thenReturn(List.of(
                role(UUID.randomUUID(), UUID.randomUUID(), true), role(UUID.randomUUID(), keep, false), role(UUID.randomUUID(), drop, false)));
        when(accounts.get(school, userId)).thenReturn(new Account(userId, "USR-1", "t@school.in", PlatformRole.TEACHER, UserStatus.ACTIVE, false, null));

        service.setRoles(u.getId(), new com.vidya.tenantdata.user.account.dto.AccountDtos.SetRolesRequest(PlatformRole.COORDINATOR, List.of(keep, add)));

        verify(accounts).changeRole(school, Kind.STAFF, userId, PlatformRole.COORDINATOR);
        verify(accessControlService).removeAssignment(eq(userId), any(), eq(school));
        verify(accessControlService).assignRole(eq(userId), any(), eq(school));
    }

    private TenantUser person(TenantUserType type, String email) {
        TenantUser u = new TenantUser("USR-" + UUID.randomUUID().toString().substring(0, 4), email, "99", type);
        u.setId(UUID.randomUUID());
        u.setProfile(new TenantUserProfile(u, "Meena", "Iyer"));
        when(tenantUserRepository.findByIdAndNotDeleted(u.getId())).thenReturn(Optional.of(u));
        return u;
    }

    private static UserRoleResponse role(UUID assignmentId, UUID roleId, boolean primary) {
        return new UserRoleResponse(assignmentId, roleId, "R", "R", null, null, primary, true, true, null, null, null, null, null);
    }
}
