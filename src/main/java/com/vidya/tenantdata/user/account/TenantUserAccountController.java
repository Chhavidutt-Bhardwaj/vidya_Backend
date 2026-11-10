package com.vidya.tenantdata.user.account;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.user.account.dto.AccountDtos.AccountResponse;
import com.vidya.tenantdata.user.account.dto.AccountDtos.CreateLoginRequest;
import com.vidya.tenantdata.user.account.dto.AccountDtos.LoginStatusRequest;
import com.vidya.tenantdata.user.account.dto.AccountDtos.SetRolesRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The login of a person of the school. One API for staff, teachers, students and parents; the person is the
 * tenant user, the login is their master account. Sign-in itself is {@code POST /api/v1/auth/login} for everyone.
 */
@RestController
@RequestMapping("/api/v1/tenant/users/{id}/account")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Logins and roles of the school's people (staff, teachers, students, parents)")
public class TenantUserAccountController {

    private static final String ALL_USERS = "@dataAccess.hasFullScope(authentication, 'USER')";

    private final TenantUserAccountService service;

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE') and " + ALL_USERS)
    @Operation(summary = "Give a person a login",
            description = "Account type defaults from the person (designation role for staff, TEACHER, STUDENT, PARENT). "
                    + "The temporary password is returned once. Extra roles need SCHOOL_ROLE_MANAGE.")
    public ResponseEntity<ApiResponse<AccountResponse>> create(@PathVariable UUID id, @Valid @RequestBody(required = false) CreateLoginRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("ACCOUNT_CREATED", "Login created", service.createLogin(id, request)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ') and @dataAccess.canAccessTenantUser(authentication, #id, 'USER')")
    @Operation(summary = "Login and roles of a person")
    public ResponseEntity<ApiResponse<AccountResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("ACCOUNT", "Account", service.access(id)));
    }

    @PutMapping("/roles")
    @PreAuthorize("hasAuthority('SCHOOL_ROLE_MANAGE') and " + ALL_USERS)
    @Operation(summary = "Set account type and extra roles",
            description = "You can only give or take roles whose permissions you hold yourself. Takes effect on the person's next token refresh.")
    public ResponseEntity<ApiResponse<AccountResponse>> roles(@PathVariable UUID id, @Valid @RequestBody SetRolesRequest request) {
        return ResponseEntity.ok(ApiResponse.success("ACCOUNT_ROLES_UPDATED", "Roles updated", service.setRoles(id, request)));
    }

    @PatchMapping("/status")
    @PreAuthorize("hasAuthority('USER_UPDATE') and " + ALL_USERS)
    @Operation(summary = "Enable or disable a login", description = "Disabling signs the person out on all devices")
    public ResponseEntity<ApiResponse<AccountResponse>> status(@PathVariable UUID id, @Valid @RequestBody LoginStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("ACCOUNT_STATUS", request.active() ? "Login enabled" : "Login disabled",
                service.setActive(id, request.active())));
    }
}
