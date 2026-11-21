package com.vidya.tenantdata.user.account;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.security.access.account.SchoolAccountService;
import com.vidya.security.access.account.SchoolAccountService.Account;
import com.vidya.security.access.account.SchoolAccountService.Kind;
import com.vidya.security.access.dto.AssignRoleRequest;
import com.vidya.security.access.dto.UserRoleResponse;
import com.vidya.security.access.service.AccessControlService;
import com.vidya.security.entity.PlatformRole;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.repository.DesignationRepository;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.user.account.dto.AccountDtos.AccountResponse;
import com.vidya.tenantdata.user.account.dto.AccountDtos.CreateLoginRequest;
import com.vidya.tenantdata.user.account.dto.AccountDtos.LoginInfo;
import com.vidya.tenantdata.user.account.dto.AccountDtos.SetRolesRequest;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserProfile;
import com.vidya.tenantdata.user.entity.TenantUserStatus;
import com.vidya.tenantdata.user.entity.TenantUserType;
import com.vidya.tenantdata.user.repository.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Logins for the people of the school. One service for staff, teachers, students and parents: it creates the
 * master account, links it to the person ({@code tenant_user.master_user_id}), and manages roles and status.
 *
 * <p>Accounts live in the master database and are created in their own transaction. If the surrounding
 * tenant transaction rolls back, every login created in it is deleted again, so a failed save never leaves
 * a stray account behind.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantUserAccountService {

    private final TenantUserRepository tenantUserRepository;
    private final StaffRepository staffRepository;
    private final DesignationRepository designationRepository;
    private final SchoolAccountService accounts;
    private final AccessControlService accessControlService;
    private final SchoolContext context;
    private final ApplicationEventPublisher events;

    public static Kind kindOf(TenantUserType type) {
        return switch (type) {
            case STUDENT -> Kind.STUDENT;
            case PARENT -> Kind.PARENT;
            default -> Kind.STAFF;
        };
    }

    // ── API used by controllers ──────────────────────────────────────────────

    @Transactional(transactionManager = "tenantTransactionManager")
    public AccountResponse createLogin(UUID tenantUserId, CreateLoginRequest r) {
        TenantUser user = user(tenantUserId);
        if (user.getMasterUserId() != null) {
            throw new BusinessException(name(user) + " already has a login");
        }
        if (user.getStatus() != TenantUserStatus.ACTIVE) {
            throw new BusinessException(name(user) + " is " + user.getStatus() + "; only active people get a login");
        }
        Optional<Staff> staff = staffOf(user);
        staff.filter(s -> s.getEmploymentStatus() != EmploymentStatus.ACTIVE).ifPresent(s -> {
            throw new BusinessException(name(user) + " is " + s.getEmploymentStatus() + " staff; only active staff get a login");
        });
        if (r != null && r.extraRoleIds() != null && !r.extraRoleIds().isEmpty()) {
            context.requireAuthority("SCHOOL_ROLE_MANAGE", "assign roles");
        }
        String email = r != null && r.email() != null && !r.email().isBlank() ? r.email().trim().toLowerCase(Locale.ROOT) : user.getEmail();
        if (email == null) {
            throw new BusinessException("A login needs an email address; send one in the request");
        }
        if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(email)) {
            if (tenantUserRepository.existsByEmailIgnoreCase(email)) {
                throw new BusinessException("Email " + email + " is already used by another person of this school");
            }
            user.setEmail(email);
        }
        PlatformRole role = r != null && r.role() != null ? r.role() : defaultRole(user, staff.orElse(null));
        List<String> warnings = new ArrayList<>();
        LoginInfo login = attach(user, context.schoolId(), email, role, r != null ? r.extraRoleIds() : null, context.linkedMasterUserIds(), warnings);
        return response(user, staff.orElse(null), login, warnings);
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public AccountResponse access(UUID tenantUserId) {
        TenantUser user = user(tenantUserId);
        Staff staff = staffOf(user).orElse(null);
        if (user.getMasterUserId() == null) {
            return response(user, staff, null, List.of());
        }
        UUID schoolId = context.schoolId();
        Account a = accounts.get(schoolId, user.getMasterUserId());
        List<UserRoleResponse> roles = accessControlService.listUserRoles(user.getMasterUserId(), schoolId);
        return new AccountResponse(user.getId(), user.getUserCode(), name(user), user.getUserType(), staff != null ? staff.getId() : null, true,
                info(a, extraRoleCodes(roles)), roles);
    }

    /**
     * Sets the account type and/or the exact set of extra roles. Each change is checked against the signed-in
     * user's own permissions and takes effect on the person's next token refresh.
     */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public AccountResponse setRoles(UUID tenantUserId, SetRolesRequest r) {
        TenantUser user = user(tenantUserId);
        UUID userId = requireLogin(user);
        UUID schoolId = context.schoolId();
        if (r.primaryRole() != null) {
            accounts.changeRole(schoolId, kindOf(user.getUserType()), userId, r.primaryRole());
        }
        if (r.extraRoleIds() != null) {
            Set<UUID> wanted = new LinkedHashSet<>(r.extraRoleIds());
            List<UserRoleResponse> current = accessControlService.listUserRoles(userId, schoolId).stream().filter(x -> !x.primary()).toList();
            for (UserRoleResponse cur : current) {
                if (!wanted.remove(cur.roleId())) {
                    accessControlService.removeAssignment(userId, cur.assignmentId(), schoolId);
                }
            }
            for (UUID roleId : wanted) {
                accessControlService.assignRole(userId, new AssignRoleRequest(roleId, null, null, null), schoolId);
            }
        }
        return access(tenantUserId);
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public AccountResponse setActive(UUID tenantUserId, boolean active) {
        TenantUser user = user(tenantUserId);
        UUID userId = requireLogin(user);
        if (active) {
            if (user.getStatus() != TenantUserStatus.ACTIVE) {
                throw new BusinessException(name(user) + " is " + user.getStatus() + "; reactivate the person first");
            }
            staffOf(user).filter(s -> s.getEmploymentStatus() != EmploymentStatus.ACTIVE).ifPresent(s -> {
                throw new BusinessException(name(user) + " is " + s.getEmploymentStatus() + " staff; reactivate the staff record first");
            });
        }
        accounts.setActive(context.schoolId(), userId, active, context.actorId());
        return access(tenantUserId);
    }

    /** Disables the login of a person (offboarding). Returns the sessions ended, 0 when there is no login. */
    public int disable(TenantUser user) {
        if (user.getMasterUserId() == null) {
            return 0;
        }
        return accounts.setActive(context.schoolId(), user.getMasterUserId(), false, context.actorId());
    }

    // ── API used by onboarding flows (inside their tenant transaction) ───────

    /**
     * Creates or adopts the login, links it to the person and assigns the extra roles. Must be called inside
     * a tenant transaction; a new login is removed again if that transaction does not commit.
     */
    public LoginInfo attach(TenantUser user, UUID schoolId, String email, PlatformRole role, List<UUID> extraRoleIds, Set<UUID> linked,
                            List<String> warnings) {
        TenantUserProfile p = user.getProfile();
        String first = p != null && p.getFirstName() != null ? p.getFirstName() : user.getUserCode();
        String last = p != null ? p.getLastName() : null;
        Account a = accounts.createOrAdopt(schoolId, kindOf(user.getUserType()), email, first, last, user.getPhone(), role, linked);
        if (a.created()) {
            compensateOnRollback(a.userId());
        } else if (a.role() != role) {
            warnings.add("Existing login " + a.email() + " was linked with its current role " + a.role()
                    + "; change it with PUT /api/v1/tenant/users/{id}/account/roles if needed");
        }
        user.setMasterUserId(a.userId());
        tenantUserRepository.save(user);
        List<String> extra = new ArrayList<>();
        if (extraRoleIds != null) {
            for (UUID roleId : new LinkedHashSet<>(extraRoleIds)) {
                extra.add(accessControlService.assignRole(a.userId(), new AssignRoleRequest(roleId, null, null, null), schoolId).roleCode());
            }
        }
        events.publishEvent(new AccountLinkedEvent(user.getId(), a.userId(), schoolId));
        return new LoginInfo(a.userId(), a.userCode(), a.email(), a.role(), a.status(), a.created(), a.temporaryPassword(), extra);
    }

    /** Account type a person gets by default: designation role for staff, else by kind. */
    public PlatformRole defaultRole(TenantUser user, Staff staff) {
        if (user.getUserType() == TenantUserType.ADMIN) {
            return PlatformRole.SCHOOL_ADMIN;
        }
        if (staff != null && staff.getDesignationId() != null) {
            var d = designationRepository.findByIdAndNotDeleted(staff.getDesignationId()).orElse(null);
            if (d != null && d.getDefaultRole() != null) {
                return PlatformRole.valueOf(d.getDefaultRole());
            }
        }
        if (user.getUserType() == TenantUserType.TEACHER) {
            return PlatformRole.TEACHER;
        }
        return kindOf(user.getUserType()).defaultRole;
    }

    /** Deletes a login created in this tenant transaction if the transaction does not commit. */
    private void compensateOnRollback(UUID userId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        accounts.deleteCreated(userId);
                    } catch (RuntimeException e) {
                        log.error("Could not remove login {} after a failed save; remove it manually", userId, e);
                    }
                }
            }
        });
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private TenantUser user(UUID id) {
        return tenantUserRepository.findByIdAndNotDeleted(id).orElseThrow(() -> new ResourceNotFoundException("TenantUser", id));
    }

    private Optional<Staff> staffOf(TenantUser user) {
        return kindOf(user.getUserType()) == Kind.STAFF ? staffRepository.findByTenantUserIdAndNotDeleted(user.getId()) : Optional.empty();
    }

    private static UUID requireLogin(TenantUser user) {
        if (user.getMasterUserId() == null) {
            throw new BusinessException(name(user) + " has no login; create one first");
        }
        return user.getMasterUserId();
    }

    private AccountResponse response(TenantUser user, Staff staff, LoginInfo login, List<String> warnings) {
        List<UserRoleResponse> roles = login != null ? accessControlService.listUserRoles(login.userId(), context.schoolId()) : List.of();
        return new AccountResponse(user.getId(), user.getUserCode(), name(user), user.getUserType(), staff != null ? staff.getId() : null,
                login != null, login, roles);
    }

    private static List<String> extraRoleCodes(List<UserRoleResponse> roles) {
        return roles.stream().filter(r -> !r.primary()).map(UserRoleResponse::roleCode).filter(Objects::nonNull).toList();
    }

    private static LoginInfo info(Account a, List<String> extra) {
        return new LoginInfo(a.userId(), a.userCode(), a.email(), a.role(), a.status(), false, null, extra);
    }

    public static String name(TenantUser u) {
        TenantUserProfile p = u.getProfile();
        if (p != null) {
            String n = ((p.getFirstName() != null ? p.getFirstName() : "") + " " + (p.getLastName() != null ? p.getLastName() : "")).trim();
            if (!n.isEmpty()) {
                return n;
            }
        }
        return u.getUserCode();
    }
}
