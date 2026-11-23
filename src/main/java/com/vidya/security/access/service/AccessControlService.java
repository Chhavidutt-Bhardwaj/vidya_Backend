package com.vidya.security.access.service;

import com.vidya.common.api.ApiResponseCode;
import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.exception.UnauthorizedException;
import com.vidya.master.subscription.repository.SubscriptionFeatureRepository;
import com.vidya.security.access.AccessScope;
import com.vidya.security.access.dto.AssignRoleRequest;
import com.vidya.security.access.dto.EffectiveAccessResponse;
import com.vidya.security.access.dto.PermissionOverrideRequest;
import com.vidya.security.access.dto.PermissionOverrideResponse;
import com.vidya.security.access.dto.PermissionRequest;
import com.vidya.security.access.dto.PermissionResponse;
import com.vidya.security.access.dto.RoleRequest;
import com.vidya.security.access.dto.RoleResponse;
import com.vidya.security.access.dto.SchoolUserAccessResponse;
import com.vidya.security.access.dto.UserRoleResponse;
import com.vidya.security.access.entity.AccessPermission;
import com.vidya.security.access.entity.AccessRole;
import com.vidya.security.access.entity.MatrixCell;
import com.vidya.security.access.entity.UserPermissionOverride;
import com.vidya.security.access.entity.UserRoleAssignment;
import com.vidya.security.access.repository.AccessPermissionRepository;
import com.vidya.security.access.repository.AccessRoleRepository;
import com.vidya.security.access.repository.UserPermissionOverrideRepository;
import com.vidya.security.access.repository.UserRoleAssignmentRepository;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.repository.PlatformUserRepository;
import com.vidya.security.user.PlatformUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages roles, permissions, role assignments and per-user overrides.
 *
 * <p>Every method takes a {@code schoolScope}:
 * <ul>
 *   <li>{@code null}: platform administration (any global role, any school's roles, any user).</li>
 *   <li>a school id: school self-service. Only that school's custom roles are editable, global
 *       roles of school scope are read-only/assignable, and target users must belong to the school.</li>
 * </ul>
 *
 * <p><b>Anti-escalation:</b> a non-super-user actor can only grant what they hold themselves.
 * Adding a permission to a role, assigning a role, or creating a GRANT override all require the
 * actor to currently hold every permission involved. Roles with {@code grants_all_permissions}
 * can only be assigned or edited by a super user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "masterTransactionManager")
public class AccessControlService {

    private static final Set<String> RESERVED_ROLE_CODES = EnumSet.allOf(PlatformRole.class).stream()
            .map(Enum::name).collect(Collectors.toUnmodifiableSet());

    private final AccessPermissionRepository permissionRepository;
    private final AccessRoleRepository roleRepository;
    private final UserRoleAssignmentRepository assignmentRepository;
    private final UserPermissionOverrideRepository overrideRepository;
    private final PlatformUserRepository userRepository;
    private final SubscriptionFeatureRepository featureRepository;
    private final EffectiveAccessService effectiveAccessService;
    private final ApplicationEventPublisher eventPublisher;

    // ================================ PERMISSIONS ================================

    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public List<PermissionResponse> listPermissions() {
        return permissionRepository.findAllByOrderByModuleCodeAscCodeAsc().stream().map(PermissionResponse::from).toList();
    }

    /** Permissions a school may put into its custom roles: active, SCHOOL scope. */
    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public List<PermissionResponse> listSchoolAssignablePermissions() {
        return permissionRepository.findAllByOrderByModuleCodeAscCodeAsc().stream()
                .filter(p -> p.isActive() && p.getScope() == AccessPermission.PermissionScope.SCHOOL)
                .map(PermissionResponse::from).toList();
    }

    public PermissionResponse createPermission(PermissionRequest req) {
        if (req.code() == null || req.code().isBlank()) {
            throw new BusinessException(ApiResponseCode.VALIDATION_ERROR, "Permission code is required");
        }
        String code = req.code().trim();
        if (permissionRepository.existsByCode(code)) {
            throw new DuplicateResourceException("Permission", "code", code);
        }
        AccessPermission p = new AccessPermission(code, req.name().trim(), req.description(), normalizeModule(req.moduleCode()),
                req.scope(), validateFeature(req.featureCode()));
        if (req.active() != null) {
            p.setActive(req.active());
        }
        applyMatrixCells(p, req);
        AccessPermission saved = permissionRepository.save(p);
        publishAll("Permission created: " + code);
        log.info("Permission [{}] created by [{}]", code, actorId());
        return PermissionResponse.from(saved);
    }

    public PermissionResponse updatePermission(UUID id, PermissionRequest req) {
        AccessPermission p = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", id));
        p.setName(req.name().trim());
        p.setDescription(req.description());
        if (req.moduleCode() != null) {
            p.setModuleCode(normalizeModule(req.moduleCode()));
        }
        if (req.scope() != null) {
            p.setScope(req.scope());
        }
        p.setFeatureCode(validateFeature(req.featureCode()));
        if (req.active() != null) {
            p.setActive(req.active());
        }
        applyMatrixCells(p, req);
        publishAll("Permission updated: " + p.getCode());
        return PermissionResponse.from(permissionRepository.save(p));
    }

    // ================================ ROLES ================================

    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public List<RoleResponse> listRoles(UUID schoolScope, boolean includePermissions) {
        List<AccessRole> roles = schoolScope == null ? roleRepository.findAll() : roleRepository.findAvailableForSchool(schoolScope);
        return roles.stream()
                .sorted(Comparator.comparing((AccessRole r) -> r.getSchoolId() != null).thenComparing(AccessRole::getCode))
                .map(r -> RoleResponse.from(r, includePermissions)).toList();
    }

    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public RoleResponse getRole(UUID roleId, UUID schoolScope) {
        return RoleResponse.from(loadVisibleRole(roleId, schoolScope), true);
    }

    public RoleResponse createRole(RoleRequest req, UUID schoolScope) {
        if (req.code() == null || req.code().isBlank()) {
            throw new BusinessException(ApiResponseCode.VALIDATION_ERROR, "Role code is required");
        }
        String code = req.code().trim();
        AccessScope scope = req.scope() != null ? req.scope() : (schoolScope != null ? AccessScope.SCHOOL : null);
        if (scope == null) {
            throw new BusinessException(ApiResponseCode.VALIDATION_ERROR, "Role scope is required");
        }
        if (schoolScope != null) {
            if (!scope.isSchoolBound()) {
                throw new BusinessException(ApiResponseCode.VALIDATION_ERROR, "School roles must have scope SCHOOL or SCHOOL_PORTAL");
            }
            if (RESERVED_ROLE_CODES.contains(code) || roleRepository.findGlobalByCode(code).isPresent()) {
                throw new BusinessException(ApiResponseCode.VALIDATION_ERROR,
                        "Role code '" + code + "' is reserved by a platform role; choose another code");
            }
            if (roleRepository.findByCodeAndSchoolId(code, schoolScope).isPresent()) {
                throw new DuplicateResourceException("Role", "code", code);
            }
        } else if (roleRepository.findGlobalByCode(code).isPresent()) {
            throw new DuplicateResourceException("Role", "code", code);
        }

        AccessRole role = new AccessRole(code, req.name().trim(), req.description(), scope, schoolScope, actorId());
        if (req.active() != null) {
            role.setActive(req.active());
        }
        if (req.permissionCodes() != null && !req.permissionCodes().isEmpty()) {
            Set<AccessPermission> perms = loadPermissions(req.permissionCodes());
            perms.forEach(p -> requirePermissionFitsRole(role, p));
            requireActorHolds(perms);
            role.getPermissions().addAll(perms);
        }
        AccessRole saved = roleRepository.save(role);
        log.info("Role [{}] (school {}) created by [{}]", code, schoolScope, actorId());
        return RoleResponse.from(saved, true);
    }

    public RoleResponse updateRole(UUID roleId, RoleRequest req, UUID schoolScope) {
        AccessRole role = loadEditableRole(roleId, schoolScope);
        role.setName(req.name().trim());
        role.setDescription(req.description());
        if (req.active() != null && req.active() != role.isActive()) {
            if (!req.active() && role.isGrantsAllPermissions()) {
                throw new BusinessException(ApiResponseCode.VALIDATION_ERROR, "The super-user role cannot be deactivated");
            }
            role.setActive(req.active());
        }
        role.setUpdatedBy(actorId());
        if (req.permissionCodes() != null) {
            applyPermissionSet(role, req.permissionCodes());
        }
        publishAll("Role updated: " + role.getCode());
        return RoleResponse.from(roleRepository.save(role), true);
    }

    public RoleResponse replaceRolePermissions(UUID roleId, Set<String> codes, UUID schoolScope) {
        AccessRole role = loadEditableRole(roleId, schoolScope);
        applyPermissionSet(role, codes);
        role.setUpdatedBy(actorId());
        publishAll("Role permissions replaced: " + role.getCode());
        return RoleResponse.from(roleRepository.save(role), true);
    }

    public RoleResponse addRolePermissions(UUID roleId, Set<String> codes, UUID schoolScope) {
        AccessRole role = loadEditableRole(roleId, schoolScope);
        Set<AccessPermission> perms = loadPermissions(codes);
        perms.forEach(p -> requirePermissionFitsRole(role, p));
        requireActorHolds(perms);
        role.getPermissions().addAll(perms);
        role.setUpdatedBy(actorId());
        publishAll("Role permissions added: " + role.getCode());
        return RoleResponse.from(roleRepository.save(role), true);
    }

    public RoleResponse removeRolePermission(UUID roleId, String code, UUID schoolScope) {
        AccessRole role = loadEditableRole(roleId, schoolScope);
        role.getPermissions().removeIf(p -> p.getCode().equals(code));
        role.setUpdatedBy(actorId());
        publishAll("Role permission removed: " + role.getCode() + " - " + code);
        return RoleResponse.from(roleRepository.save(role), true);
    }

    public void deleteRole(UUID roleId, UUID schoolScope) {
        AccessRole role = loadEditableRole(roleId, schoolScope);
        if (role.isSystem()) {
            throw new BusinessException(ApiResponseCode.VALIDATION_ERROR,
                    "System role '" + role.getCode() + "' cannot be deleted; deactivate it or edit its permissions instead");
        }
        List<UUID> holders = assignmentRepository.findUserIdsByRoleId(role.getId());
        roleRepository.delete(role);
        holders.forEach(u -> eventPublisher.publishEvent(AccessChangedEvent.user(u, "Role deleted")));
        log.info("Role [{}] (school {}) deleted by [{}]; {} assignment(s) removed", role.getCode(), role.getSchoolId(), actorId(), holders.size());
    }

    // ================================ ASSIGNMENTS ================================

    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public List<UserRoleResponse> listUserRoles(UUID userId, UUID schoolScope) {
        PlatformUser user = loadTargetUser(userId, schoolScope);
        return userRoles(user, Instant.now());
    }

    public UserRoleResponse assignRole(UUID userId, AssignRoleRequest req, UUID schoolScope) {
        PlatformUser user = loadTargetUser(userId, schoolScope);
        AccessRole role = loadVisibleRole(req.roleId(), schoolScope);
        AccessScope userScope = user.getPlatformRole().getScope();

        if (!role.isActive()) {
            throw new BusinessException(ApiResponseCode.VALIDATION_ERROR, "Role '" + role.getCode() + "' is inactive");
        }
        if (!EffectiveAccessService.isScopeCompatible(userScope, role.getScope())) {
            throw new BusinessException(ApiResponseCode.VALIDATION_ERROR,
                    "A " + userScope + " account cannot hold the " + role.getScope() + " role '" + role.getCode() + "'");
        }
        if (!role.isGlobal() && !role.getSchoolId().equals(user.getSchoolId())) {
            throw new BusinessException(ApiResponseCode.VALIDATION_ERROR, "Role belongs to another school");
        }
        if (role.getCode().equals(user.getPlatformRole().name()) && role.isGlobal()) {
            throw new BusinessException(ApiResponseCode.VALIDATION_ERROR, "User already holds this role as their account type");
        }
        requireActorCanAssign(role);
        if (req.validFrom() != null && req.validUntil() != null && !req.validUntil().isAfter(req.validFrom())) {
            throw new BusinessException(ApiResponseCode.VALIDATION_ERROR, "validUntil must be after validFrom");
        }

        UserRoleAssignment assignment = assignmentRepository.findByUserIdAndRoleId(userId, role.getId())
                .orElseGet(() -> new UserRoleAssignment(userId, role, user.getSchoolId(), null, null, null, null));
        assignment.setActive(true);
        assignment.setValidFrom(req.validFrom());
        assignment.setValidUntil(req.validUntil());
        assignment.setNotes(req.notes());
        assignment.setAssignedBy(actorId());
        UserRoleAssignment saved = assignmentRepository.save(assignment);
        eventPublisher.publishEvent(AccessChangedEvent.user(userId, "Role assigned: " + role.getCode()));
        log.info("Role [{}] assigned to user [{}] by [{}]", role.getCode(), userId, actorId());
        return UserRoleResponse.from(saved, Instant.now());
    }

    public void removeAssignment(UUID userId, UUID assignmentId, UUID schoolScope) {
        loadTargetUser(userId, schoolScope);
        UserRoleAssignment a = assignmentRepository.findById(assignmentId)
                .filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("UserRoleAssignment", assignmentId));
        if (schoolScope != null && a.getRole().isGlobal() && a.getRole().getScope() == AccessScope.PLATFORM) {
            throw new AccessDeniedException("School administrators cannot remove platform roles");
        }
        assignmentRepository.delete(a);
        eventPublisher.publishEvent(AccessChangedEvent.user(userId, "Role removed: " + a.getRole().getCode()));
        log.info("Role [{}] removed from user [{}] by [{}]", a.getRole().getCode(), userId, actorId());
    }

    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public List<SchoolUserAccessResponse> listSchoolUsers(UUID schoolId) {
        Instant now = Instant.now();
        Map<UUID, List<UserRoleAssignment>> bySchool = assignmentRepository.findBySchoolIdWithRole(schoolId).stream()
                .collect(Collectors.groupingBy(UserRoleAssignment::getUserId));
        List<SchoolUserAccessResponse> result = new ArrayList<>();
        for (PlatformUser u : userRepository.findBySchoolId(schoolId)) {
            List<UserRoleResponse> roles = new ArrayList<>();
            roleRepository.findGlobalByCode(u.getPlatformRole().name()).ifPresent(r -> roles.add(primaryRole(r)));
            bySchool.getOrDefault(u.getId(), List.of()).forEach(a -> roles.add(UserRoleResponse.from(a, now)));
            result.add(new SchoolUserAccessResponse(u.getId(), u.getUserCode(), u.getFullName(), u.getEmail(),
                    u.getStatus(), u.getPlatformRole(), roles));
        }
        result.sort(Comparator.comparing(SchoolUserAccessResponse::fullName, Comparator.nullsLast(String::compareToIgnoreCase)));
        return result;
    }

    // ================================ OVERRIDES (platform) ================================

    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public List<PermissionOverrideResponse> listOverrides(UUID userId) {
        loadTargetUser(userId, null);
        return overrideRepository.findByUserIdWithPermission(userId).stream().map(PermissionOverrideResponse::from).toList();
    }

    public PermissionOverrideResponse upsertOverride(UUID userId, PermissionOverrideRequest req) {
        PlatformUser user = loadTargetUser(userId, null);
        AccessPermission p = permissionRepository.findByCode(req.permissionCode().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Permission", req.permissionCode()));
        if (req.effect() == UserPermissionOverride.Effect.GRANT) {
            if (user.getPlatformRole().getScope().isSchoolBound() && p.getScope() == AccessPermission.PermissionScope.PLATFORM) {
                throw new BusinessException(ApiResponseCode.VALIDATION_ERROR, "PLATFORM permissions cannot be granted to school accounts");
            }
            requireActorHolds(Set.of(p));
        }
        UserPermissionOverride o = overrideRepository.findByUserIdAndPermissionId(userId, p.getId())
                .orElseGet(() -> new UserPermissionOverride(userId, p, req.effect(), null, null, null));
        o.setEffect(req.effect());
        o.setReason(req.reason());
        o.setValidUntil(req.validUntil());
        o.setCreatedBy(actorId());
        UserPermissionOverride saved = overrideRepository.save(o);
        eventPublisher.publishEvent(AccessChangedEvent.user(userId, "Override " + req.effect() + " " + p.getCode()));
        return PermissionOverrideResponse.from(saved);
    }

    public void deleteOverride(UUID userId, UUID overrideId) {
        UserPermissionOverride o = overrideRepository.findById(overrideId)
                .filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("UserPermissionOverride", overrideId));
        overrideRepository.delete(o);
        eventPublisher.publishEvent(AccessChangedEvent.user(userId, "Override removed"));
    }

    // ================================ EFFECTIVE ACCESS ================================

    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public EffectiveAccessResponse effectiveAccess(UUID userId, UUID schoolScope) {
        PlatformUser user = loadTargetUser(userId, schoolScope);
        return EffectiveAccessResponse.from(effectiveAccessService.resolveFresh(user), userRoles(user, Instant.now()));
    }

    public void evictCache() {
        effectiveAccessService.evictAll();
        log.info("Access cache cleared on demand by [{}]", actorId());
    }

    // ================================ helpers ================================

    private void applyPermissionSet(AccessRole role, Set<String> codes) {
        Set<AccessPermission> target = loadPermissions(codes);
        target.forEach(p -> requirePermissionFitsRole(role, p));
        Set<String> current = role.getPermissions().stream().map(AccessPermission::getCode).collect(Collectors.toSet());
        Set<AccessPermission> added = target.stream().filter(p -> !current.contains(p.getCode())).collect(Collectors.toSet());
        requireActorHolds(added);
        role.getPermissions().clear();
        role.getPermissions().addAll(target);
    }

    private static void applyMatrixCells(AccessPermission p, PermissionRequest req) {
        if (req.matrixCells() == null) {
            return;
        }
        Set<MatrixCell> cells = req.matrixCells().stream()
                .map(c -> new MatrixCell(c.sectionCode(), c.actionCode()))
                .collect(Collectors.toSet());
        if (cells.stream().anyMatch(c -> !c.isValid())) {
            throw new BusinessException(ApiResponseCode.VALIDATION_ERROR,
                    "Matrix cells need sectionCode 1..999 and actionCode between -9 and -4");
        }
        p.getMatrixCells().clear();
        p.getMatrixCells().addAll(cells);
    }

    private Set<AccessPermission> loadPermissions(Collection<String> codes) {
        Set<String> wanted = codes.stream().filter(c -> c != null && !c.isBlank()).map(String::trim).collect(Collectors.toSet());
        List<AccessPermission> found = permissionRepository.findByCodeIn(wanted);
        if (found.size() != wanted.size()) {
            Set<String> missing = new HashSet<>(wanted);
            found.forEach(p -> missing.remove(p.getCode()));
            throw new BusinessException(ApiResponseCode.VALIDATION_ERROR, "Unknown permission code(s): " + missing);
        }
        return new HashSet<>(found);
    }

    private static void requirePermissionFitsRole(AccessRole role, AccessPermission p) {
        boolean ok = switch (role.getScope()) {
            case PLATFORM -> true;
            case COMMUNITY -> p.getScope() != AccessPermission.PermissionScope.PLATFORM;
            case SCHOOL, SCHOOL_PORTAL -> p.getScope() == AccessPermission.PermissionScope.SCHOOL;
        };
        if (!ok) {
            throw new BusinessException(ApiResponseCode.VALIDATION_ERROR,
                    "Permission " + p.getCode() + " (" + p.getScope() + ") cannot be granted to a " + role.getScope() + " role");
        }
    }

    /** Role readable in this scope: platform sees all; a school sees its own + global school-scope roles. */
    private AccessRole loadVisibleRole(UUID roleId, UUID schoolScope) {
        AccessRole role = roleRepository.findWithPermissionsById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));
        if (schoolScope != null) {
            boolean visible = schoolScope.equals(role.getSchoolId())
                    || (role.isGlobal() && role.getScope().isSchoolBound());
            if (!visible) {
                throw new ResourceNotFoundException("Role", roleId);
            }
        }
        return role;
    }

    /** Role editable in this scope: platform edits any (super-user role only by super users); a school edits only its own. */
    private AccessRole loadEditableRole(UUID roleId, UUID schoolScope) {
        AccessRole role = loadVisibleRole(roleId, schoolScope);
        if (schoolScope != null && !schoolScope.equals(role.getSchoolId())) {
            throw new AccessDeniedException("Platform roles are read-only for schools; create a custom role instead");
        }
        if (role.isGrantsAllPermissions() && !actorAccess().superUser()) {
            throw new AccessDeniedException("Only a super user can modify the super-user role");
        }
        return role;
    }

    private PlatformUser loadTargetUser(UUID userId, UUID schoolScope) {
        PlatformUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("PlatformUser", userId));
        if (schoolScope != null && !schoolScope.equals(user.getSchoolId())) {
            throw new ResourceNotFoundException("PlatformUser", userId);
        }
        return user;
    }

    private List<UserRoleResponse> userRoles(PlatformUser user, Instant now) {
        List<UserRoleResponse> roles = new ArrayList<>();
        roleRepository.findGlobalByCode(user.getPlatformRole().name()).ifPresent(r -> roles.add(primaryRole(r)));
        assignmentRepository.findByUserIdWithRole(user.getId()).forEach(a -> roles.add(UserRoleResponse.from(a, now)));
        return roles;
    }

    private static UserRoleResponse primaryRole(AccessRole r) {
        return new UserRoleResponse(null, r.getId(), r.getCode(), r.getName(), r.getScope(), r.getSchoolId(),
                true, r.isActive(), r.isActive(), null, null, "Account type (platform_user.platform_role)", null, null);
    }

    /** Id of a role the school may use: its own role with this code, else the global one. */
    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public Optional<UUID> findRoleId(String code, UUID schoolId) {
        return roleRepository.findByCodeAndSchoolId(code, schoolId).or(() -> roleRepository.findGlobalByCode(code))
                .filter(r -> r.isGlobal() ? r.getScope().isSchoolBound() : true).map(AccessRole::getId);
    }

    /**
     * The school's role with this code, created with the given permissions when it does not exist yet
     * (used by duties such as Exam Coordinator that come with a standard permission set).
     */
    public UUID ensureSchoolRole(UUID schoolId, String code, String name, String description, Set<String> permissionCodes) {
        return findRoleId(code, schoolId).orElseGet(() ->
                createRole(new RoleRequest(code, name, description, AccessScope.SCHOOL, true, permissionCodes), schoolId).id());
    }

    /**
     * Throws unless the signed-in user may give someone this global role as their account type,
     * i.e. holds every permission of that role (same rule as {@link #assignRole}).
     */
    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public void requireActorCanGrantRole(String roleCode) {
        AccessRole role = roleRepository.findGlobalByCode(roleCode)
                .orElseThrow(() -> new ResourceNotFoundException("AccessRole", roleCode));
        requireActorCanAssign(role);
    }

    private void requireActorCanAssign(AccessRole role) {
        EffectiveAccess actor = actorAccess();
        if (actor.superUser()) {
            return;
        }
        if (role.isGrantsAllPermissions()) {
            throw new AccessDeniedException("Only a super user can assign the super-user role");
        }
        requireActorHolds(role.getPermissions());
    }

    private void requireActorHolds(Collection<AccessPermission> permissions) {
        EffectiveAccess actor = actorAccess();
        if (actor.superUser() || permissions.isEmpty()) {
            return;
        }
        List<String> missing = permissions.stream().map(AccessPermission::getCode)
                .filter(c -> !actor.hasPermission(c)).sorted().toList();
        if (!missing.isEmpty()) {
            throw new AccessDeniedException("You cannot grant permissions you do not hold: " + missing);
        }
    }

    private EffectiveAccess actorAccess() {
        PlatformUserDetails principal = principal();
        if (principal.getEffectiveAccess() != null) {
            return principal.getEffectiveAccess();
        }
        PlatformUser actor = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Authenticated user no longer exists"));
        return effectiveAccessService.getAccess(actor);
    }

    private UUID actorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof PlatformUserDetails p ? p.getUserId() : null;
    }

    private static PlatformUserDetails principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof PlatformUserDetails p) {
            return p;
        }
        throw new UnauthorizedException("No authenticated user in context");
    }

    private String validateFeature(String featureCode) {
        if (featureCode == null || featureCode.isBlank()) {
            return null;
        }
        String code = featureCode.trim().toUpperCase();
        // Must be a real catalog row (FK). A module code works too when the module has its own row, e.g. TRANSPORT.
        if (featureRepository.findByFeatureCode(code).isEmpty()) {
            throw new BusinessException(ApiResponseCode.VALIDATION_ERROR,
                    "Unknown subscription feature '" + code + "'. Add it to subscription_feature first.");
        }
        return code;
    }

    private static String normalizeModule(String module) {
        return module == null || module.isBlank() ? "GENERAL" : module.trim().toUpperCase();
    }

    private void publishAll(String reason) {
        eventPublisher.publishEvent(AccessChangedEvent.all(reason));
    }
}
