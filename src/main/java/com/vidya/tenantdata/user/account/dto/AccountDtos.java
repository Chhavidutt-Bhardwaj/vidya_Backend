package com.vidya.tenantdata.user.account.dto;

import com.vidya.security.access.dto.UserRoleResponse;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.UserStatus;
import com.vidya.tenantdata.user.entity.TenantUserType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** One login model for every person of the school (staff, teacher, student, parent). */
public final class AccountDtos {

    private AccountDtos() {
    }

    /**
     * @param email        default: the person's email
     * @param role         account type; default from the person's kind (designation role for staff, TEACHER, STUDENT, PARENT…)
     * @param extraRoleIds additional roles (global or this school's custom roles), e.g. Exam Coordinator
     */
    public record CreateLoginRequest(@Email String email, PlatformRole role, List<UUID> extraRoleIds) {
    }

    /** @param temporaryPassword only for a newly created login; shown once, share it securely */
    public record LoginInfo(UUID userId, String userCode, String email, PlatformRole role, UserStatus status, boolean created,
                            String temporaryPassword, List<String> extraRoles) {
    }

    /**
     * @param primaryRole  account type (null = unchanged)
     * @param extraRoleIds exact set of additional roles (null = unchanged; [] removes all school-assignable extra roles)
     */
    public record SetRolesRequest(PlatformRole primaryRole, List<UUID> extraRoleIds) {
    }

    public record LoginStatusRequest(@NotNull Boolean active) {
    }

    /** Login and roles of one person. {@code staffId} is set when the person is a staff member. */
    public record AccountResponse(UUID tenantUserId, String userCode, String name, TenantUserType userType, UUID staffId, boolean hasLogin,
                                  LoginInfo login, List<UserRoleResponse> roles) {
    }
}
