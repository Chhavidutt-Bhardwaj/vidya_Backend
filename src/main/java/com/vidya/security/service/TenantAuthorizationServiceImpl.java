package com.vidya.security.service;

import com.vidya.common.correlation.MdcKeys;
import com.vidya.master.community.entity.CommunityMember;
import com.vidya.master.community.repository.CommunityMemberRepository;
import com.vidya.master.onboarding.entity.SchoolOnboarding;
import com.vidya.master.onboarding.repository.SchoolOnboardingRepository;
import com.vidya.master.school.entity.School;
import com.vidya.master.school.entity.SchoolStatus;
import com.vidya.master.school.repository.SchoolRepository;
import com.vidya.master.tenant.entity.Tenant;
import com.vidya.master.tenant.entity.TenantStatus;
import com.vidya.master.tenant.repository.TenantRepository;
import com.vidya.security.access.AccessScope;
import com.vidya.security.access.service.EffectiveAccessService;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.entity.UserStatus;
import com.vidya.security.repository.PlatformUserRepository;
import com.vidya.security.user.PlatformUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service("tenantAuthorizationService")
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "masterTransactionManager")
public class TenantAuthorizationServiceImpl implements TenantAuthorizationService {

    private final PlatformUserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final SchoolRepository schoolRepository;
    private final SchoolOnboardingRepository onboardingRepository;
    private final CommunityMemberRepository communityMemberRepository;
    private final SecurityAuditService securityAuditService;
    private final EffectiveAccessService effectiveAccessService;

    @Override
    public boolean canAccessTenant(UUID userId, String tenantCode) {
        if (userId == null || tenantCode == null || tenantCode.isBlank()) {
            return false;
        }

        Optional<PlatformUser> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return false;
        }

        PlatformUser user = userOpt.get();
        if (user.getStatus() != UserStatus.ACTIVE || user.isAccountLocked()) {
            return false;
        }

        Optional<Tenant> tenantOpt = tenantRepository.findByTenantCode(tenantCode);
        if (tenantOpt.isEmpty()) {
            return false;
        }

        Tenant tenant = tenantOpt.get();

        // Super Admin & Platform Admin
        if (user.getPlatformRole() == PlatformRole.PLATFORM_SUPER_ADMIN) {
            return true;
        }
        if (user.getPlatformRole() == PlatformRole.PLATFORM_ADMIN) {
            return tenant.getStatus() != TenantStatus.DEACTIVATED && tenant.getStatus() != TenantStatus.ARCHIVED;
        }

        // Status check for non-platform users: tenant must be ACTIVE
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            log.warn("Access denied: Tenant [{}] is not ACTIVE (current status: {})", tenantCode, tenant.getStatus());
            auditAccessDenied(user, "TENANT:" + tenantCode, "Tenant is " + tenant.getStatus());
            return false;
        }

        // Other platform staff (PLATFORM_STAFF): same reach as platform admins, limited by their permissions
        if (scopeOf(user) == AccessScope.PLATFORM) {
            return true;
        }

        // School operational users (any SCHOOL-scope account: owner, admin, teachers, staff, custom-role users)
        if (scopeOf(user) == AccessScope.SCHOOL) {
            if (user.getSchoolId() == null || tenant.getSchoolId() == null) {
                auditAccessDenied(user, "TENANT:" + tenantCode, "School user or tenant has no assigned schoolId");
                return false;
            }
            boolean belongsToSchool = tenant.getSchoolId().equals(user.getSchoolId());
            if (!belongsToSchool) {
                auditAccessDenied(user, "TENANT:" + tenantCode, "School user school mismatch");
            }
            return belongsToSchool;
        }

        // Community users: only tenants of schools they onboarded
        if (scopeOf(user) == AccessScope.COMMUNITY) {
            if (tenant.getSchoolId() == null) {
                auditAccessDenied(user, "TENANT:" + tenantCode, "Tenant has no assigned schoolId for community access");
                return false;
            }
            boolean hasAccess = onboardingRepository.existsByPlatformUserIdAndSchoolId(user.getId(), tenant.getSchoolId());
            if (hasAccess) {
                return true;
            }
            auditAccessDenied(user, "TENANT:" + tenantCode, "Community user not linked to tenant's school");
            return false;
        }

        // Default deny: SCHOOL_PORTAL accounts (PARENT, STUDENT) have no tenant-wide access yet
        auditAccessDenied(user, "TENANT:" + tenantCode, "Role not authorized");
        return false;
    }

    @Override
    public boolean canAccessSchool(UUID userId, UUID schoolId) {
        if (userId == null || schoolId == null) {
            return false;
        }

        Optional<PlatformUser> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return false;
        }

        PlatformUser user = userOpt.get();
        if (user.getStatus() != UserStatus.ACTIVE || user.isAccountLocked()) {
            return false;
        }

        Optional<School> schoolOpt = schoolRepository.findById(schoolId);
        if (schoolOpt.isEmpty()) {
            return false;
        }

        School school = schoolOpt.get();

        // Platform accounts (admins and platform staff)
        if (scopeOf(user) == AccessScope.PLATFORM) {
            return true;
        }

        // School status validation for non-platform admins
        if (school.getStatus() == SchoolStatus.DEACTIVATED) {
            auditAccessDenied(user, "SCHOOL:" + schoolId, "School is DEACTIVATED");
            return false;
        }

        // School staff (any SCHOOL-scope account). Parents/students (SCHOOL_PORTAL) stay denied.
        if (scopeOf(user) == AccessScope.SCHOOL) {
            if (user.getSchoolId() == null) {
                auditAccessDenied(user, "SCHOOL:" + schoolId, "User has no schoolId");
                return false;
            }
            boolean matches = user.getSchoolId().equals(schoolId);
            if (!matches) {
                auditAccessDenied(user, "SCHOOL:" + schoolId, "Cross-school access attempt");
            }
            return matches;
        }

        // Community users
        if (scopeOf(user) == AccessScope.COMMUNITY) {
            boolean hasAccess = onboardingRepository.existsByPlatformUserIdAndSchoolId(user.getId(), schoolId);
            if (hasAccess) {
                return true;
            }
            auditAccessDenied(user, "SCHOOL:" + schoolId, "Community user not authorized for school");
            return false;
        }

        auditAccessDenied(user, "SCHOOL:" + schoolId, "Unauthorized role");
        return false;
    }

    @Override
    public boolean canAccessOnboarding(UUID userId, UUID onboardingId) {
        if (userId == null || onboardingId == null) {
            return false;
        }

        Optional<PlatformUser> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return false;
        }

        PlatformUser user = userOpt.get();
        if (user.getStatus() != UserStatus.ACTIVE || user.isAccountLocked()) {
            return false;
        }

        Optional<SchoolOnboarding> obOpt = onboardingRepository.findById(onboardingId);
        if (obOpt.isEmpty()) {
            return false;
        }

        SchoolOnboarding onboarding = obOpt.get();

        // Platform accounts
        if (scopeOf(user) == AccessScope.PLATFORM) {
            return true;
        }

        // School staff
        if (scopeOf(user) == AccessScope.SCHOOL) {
            if (user.getSchoolId() != null && onboarding.getSchool() != null) {
                return user.getSchoolId().equals(onboarding.getSchool().getId());
            }
            return false;
        }

        // Community users & Field Agent
        if (scopeOf(user) == AccessScope.COMMUNITY) {

            List<CommunityMember> members = communityMemberRepository.findByPlatformUserId(user.getId());
            for (CommunityMember member : members) {
                if (onboarding.getCommunityMemberId() != null && onboarding.getCommunityMemberId().equals(member.getId())) {
                    return true;
                }
                if (onboarding.getCommunityId() != null && onboarding.getCommunityId().equals(member.getCommunity().getId())) {
                    return true;
                }
            }
            return false;
        }

        return false;
    }

    @Override
    public boolean hasPermission(UUID userId, String permissionCode) {
        if (userId == null || permissionCode == null) {
            return false;
        }
        return userRepository.findById(userId)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE && !u.isAccountLocked())
                .map(u -> {
                    var access = effectiveAccessService.getAccess(u);
                    return access.superUser() || access.hasPermission(permissionCode);
                })
                .orElse(false);
    }

    // SpEL-friendly overloads
    @Override
    public boolean canAccessTenant(Authentication authentication, String tenantCode) {
        PlatformUserDetails principal = extractPrincipal(authentication);
        if (principal == null) {
            return false;
        }
        return canAccessTenant(principal.getUserId(), tenantCode);
    }

    @Override
    public boolean canAccessSchool(Authentication authentication, UUID schoolId) {
        PlatformUserDetails principal = extractPrincipal(authentication);
        if (principal == null) {
            return false;
        }
        return canAccessSchool(principal.getUserId(), schoolId);
    }

    @Override
    public boolean canAccessOnboarding(Authentication authentication, UUID onboardingId) {
        PlatformUserDetails principal = extractPrincipal(authentication);
        if (principal == null) {
            return false;
        }
        return canAccessOnboarding(principal.getUserId(), onboardingId);
    }

    @Override
    public boolean hasPermission(Authentication authentication, String permissionName) {
        PlatformUserDetails principal = extractPrincipal(authentication);
        if (principal == null) {
            return false;
        }
        return principal.hasPermission(permissionName);
    }

    @Override
    public boolean isCommunityMember(Authentication authentication, UUID communityId) {
        PlatformUser user = activeUser(authentication);
        if (user == null || communityId == null) {
            return false;
        }
        if (isPlatformAdminRole(user.getPlatformRole())) {
            return true;
        }
        if (!isCommunityRole(user.getPlatformRole())) {
            return false;
        }
        return communityMemberRepository.existsByCommunityIdAndPlatformUserId(communityId, user.getId());
    }

    @Override
    public boolean canInitiateOnboarding(Authentication authentication, UUID communityId, UUID communityMemberId) {
        PlatformUser user = activeUser(authentication);
        if (user == null) {
            return false;
        }
        if (isPlatformAdminRole(user.getPlatformRole())) {
            return true;
        }
        if (!isCommunityRole(user.getPlatformRole())) {
            return false;
        }
        if (communityId == null && communityMemberId == null) {
            // Community users must attribute the onboarding to their own membership.
            return false;
        }
        List<CommunityMember> memberships = communityMemberRepository.findByPlatformUserId(user.getId());
        boolean memberOk = communityMemberId == null
                || memberships.stream().anyMatch(m -> communityMemberId.equals(m.getId()));
        boolean communityOk = communityId == null
                || memberships.stream().anyMatch(m -> m.getCommunity() != null && communityId.equals(m.getCommunity().getId()));
        boolean consistent = communityId == null || communityMemberId == null
                || memberships.stream().anyMatch(m -> communityMemberId.equals(m.getId())
                        && m.getCommunity() != null && communityId.equals(m.getCommunity().getId()));
        if (!(memberOk && communityOk && consistent)) {
            auditAccessDenied(user, "ONBOARDING_INITIATE:community=" + communityId + ",member=" + communityMemberId,
                    "Community attribution does not match the user's memberships");
            return false;
        }
        return true;
    }

    private PlatformUser activeUser(Authentication authentication) {
        PlatformUserDetails principal = extractPrincipal(authentication);
        if (principal == null || principal.getUserId() == null) {
            return null;
        }
        return userRepository.findById(principal.getUserId())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE && !u.isAccountLocked())
                .orElse(null);
    }

    private static boolean isPlatformAdminRole(PlatformRole role) {
        return role != null && role.isPlatformAdmin();
    }

    private static boolean isCommunityRole(PlatformRole role) {
        return role != null && role.getScope() == AccessScope.COMMUNITY;
    }

    private static AccessScope scopeOf(PlatformUser user) {
        return user.getPlatformRole() != null ? user.getPlatformRole().getScope() : AccessScope.SCHOOL_PORTAL;
    }

    private PlatformUserDetails extractPrincipal(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getPrincipal() instanceof PlatformUserDetails userDetails) {
            return userDetails;
        }
        return null;
    }

    private void auditAccessDenied(PlatformUser user, String resource, String reason) {
        securityAuditService.logAccessDenied(
                user.getId(),
                user.getEmail(),
                resource,
                reason,
                null,
                MDC.get(MdcKeys.CORRELATION_ID)
        );
    }
}
