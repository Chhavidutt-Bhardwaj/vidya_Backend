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
import com.vidya.security.entity.PlatformPermission;
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

        // School Users (SCHOOL_OWNER, SCHOOL_ADMIN)
        if (user.getPlatformRole() == PlatformRole.SCHOOL_OWNER || user.getPlatformRole() == PlatformRole.SCHOOL_ADMIN) {
            if (user.getSchoolId() == null) {
                auditAccessDenied(user, "TENANT:" + tenantCode, "School user has no assigned schoolId");
                return false;
            }
            boolean belongsToSchool = tenant.getSchoolId().equals(user.getSchoolId());
            if (!belongsToSchool) {
                auditAccessDenied(user, "TENANT:" + tenantCode, "School user school mismatch");
            }
            return belongsToSchool;
        }

        // Community Roles
        if (user.getPlatformRole() == PlatformRole.COMMUNITY_ADMIN
                || user.getPlatformRole() == PlatformRole.COMMUNITY_MANAGER
                || user.getPlatformRole() == PlatformRole.FIELD_AGENT) {
            List<CommunityMember> members = communityMemberRepository.findByPlatformUserId(user.getId());
            for (CommunityMember member : members) {
                List<SchoolOnboarding> onboardings = onboardingRepository.findByCommunityId(member.getCommunity().getId());
                for (SchoolOnboarding ob : onboardings) {
                    if (ob.getSchool() != null && ob.getSchool().getId().equals(tenant.getSchoolId())) {
                        return true;
                    }
                }
            }
            auditAccessDenied(user, "TENANT:" + tenantCode, "Community user not linked to tenant's school");
            return false;
        }

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

        // Platform Admins
        if (user.getPlatformRole() == PlatformRole.PLATFORM_SUPER_ADMIN || user.getPlatformRole() == PlatformRole.PLATFORM_ADMIN) {
            return true;
        }

        // School status validation for non-platform admins
        if (school.getStatus() == SchoolStatus.DEACTIVATED) {
            auditAccessDenied(user, "SCHOOL:" + schoolId, "School is DEACTIVATED");
            return false;
        }

        // School Users
        if (user.getPlatformRole() == PlatformRole.SCHOOL_OWNER || user.getPlatformRole() == PlatformRole.SCHOOL_ADMIN) {
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

        // Community Roles
        if (user.getPlatformRole() == PlatformRole.COMMUNITY_ADMIN
                || user.getPlatformRole() == PlatformRole.COMMUNITY_MANAGER
                || user.getPlatformRole() == PlatformRole.FIELD_AGENT) {
            List<CommunityMember> members = communityMemberRepository.findByPlatformUserId(user.getId());
            for (CommunityMember member : members) {
                List<SchoolOnboarding> onboardings = onboardingRepository.findByCommunityId(member.getCommunity().getId());
                for (SchoolOnboarding ob : onboardings) {
                    if (ob.getSchool() != null && ob.getSchool().getId().equals(schoolId)) {
                        return true;
                    }
                }
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

        // Platform Admins
        if (user.getPlatformRole() == PlatformRole.PLATFORM_SUPER_ADMIN || user.getPlatformRole() == PlatformRole.PLATFORM_ADMIN) {
            return true;
        }

        // School users
        if (user.getPlatformRole() == PlatformRole.SCHOOL_OWNER || user.getPlatformRole() == PlatformRole.SCHOOL_ADMIN) {
            if (user.getSchoolId() != null && onboarding.getSchool() != null) {
                return user.getSchoolId().equals(onboarding.getSchool().getId());
            }
            return false;
        }

        // Community users & Field Agent
        if (user.getPlatformRole() == PlatformRole.FIELD_AGENT
                || user.getPlatformRole() == PlatformRole.COMMUNITY_ADMIN
                || user.getPlatformRole() == PlatformRole.COMMUNITY_MANAGER) {

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
    public boolean hasPermission(UUID userId, PlatformPermission permission) {
        if (userId == null || permission == null) {
            return false;
        }
        Optional<PlatformUser> userOpt = userRepository.findById(userId);
        return userOpt.map(u -> u.getStatus() == UserStatus.ACTIVE && !u.isAccountLocked() && u.getPlatformRole().hasPermission(permission))
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
        try {
            PlatformPermission perm = PlatformPermission.valueOf(permissionName);
            return principal.hasPermission(perm);
        } catch (IllegalArgumentException e) {
            return false;
        }
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
