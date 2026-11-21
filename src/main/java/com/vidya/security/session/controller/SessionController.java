package com.vidya.security.session.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.ApiResponseCode;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.common.correlation.MdcKeys;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.exception.UnauthorizedException;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.repository.PlatformUserRepository;
import com.vidya.security.session.dto.LoginHistoryResponse;
import com.vidya.security.session.dto.SessionResponse;
import com.vidya.security.session.entity.LoginEventType;
import com.vidya.security.session.entity.SessionStatus;
import com.vidya.security.session.service.ClientInfo;
import com.vidya.security.session.service.LoginHistoryService;
import com.vidya.security.session.service.SessionService;
import com.vidya.security.user.PlatformUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sessions and login history for:
 * <ul>
 *   <li>the current user ({@code /api/v1/auth/...}): see devices, sign one out, sign out others;</li>
 *   <li>platform admins ({@code /api/v1/admin/users/{userId}/...}): any user;</li>
 *   <li>school admins ({@code /api/v1/schools/{schoolId}/...}): users of their own school.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Sessions & Login History", description = "Active devices, remote sign-out and login audit trail")
@SecurityRequirement(name = "bearerAuth")
public class SessionController {

    private final SessionService sessionService;
    private final LoginHistoryService loginHistoryService;
    private final PlatformUserRepository userRepository;

    // ======================= current user =======================

    @GetMapping("/auth/sessions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "My sessions (devices). activeOnly=false includes ended sessions")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> mySessions(
            @AuthenticationPrincipal PlatformUserDetails me,
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        requireUser(me);
        return ok("Sessions retrieved", sessionService.list(me.getUserId(), activeOnly, me.getSessionId()));
    }

    @DeleteMapping("/auth/sessions/{sessionId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Sign out one of my devices")
    public ResponseEntity<ApiResponse<Void>> endMySession(@AuthenticationPrincipal PlatformUserDetails me,
                                                          @PathVariable UUID sessionId,
                                                          HttpServletRequest request) {
        requireUser(me);
        SessionStatus status = sessionId.equals(me.getSessionId()) ? SessionStatus.LOGGED_OUT : SessionStatus.REVOKED;
        sessionService.endOwned(me.getUserId(), sessionId, status, SessionService.REASON_USER, me.getUserId());
        history(me.getUserId(), LoginEventType.SESSION_REVOKED, "BY_USER", sessionId, request);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, "Session ended"));
    }

    @PostMapping("/auth/sessions/revoke-others")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Sign out every device except this one")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> endOtherSessions(@AuthenticationPrincipal PlatformUserDetails me,
                                                                             HttpServletRequest request) {
        requireUser(me);
        int ended = sessionService.endAllForUser(me.getUserId(), SessionStatus.REVOKED, SessionService.REASON_USER,
                me.getUserId(), me.getSessionId());
        history(me.getUserId(), LoginEventType.SESSION_REVOKED, "OTHER_DEVICES_BY_USER", me.getSessionId(), request);
        return ok("Other sessions ended", Map.of("ended", ended));
    }

    @GetMapping("/auth/login-history")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "My login history")
    public ResponseEntity<PagedApiResponse<LoginHistoryResponse>> myLoginHistory(
            @AuthenticationPrincipal PlatformUserDetails me, @PageableDefault(size = 20) Pageable pageable) {
        requireUser(me);
        return paged("Login history retrieved", loginHistoryService.forUser(me.getUserId(), pageable));
    }

    // ======================= platform admin =======================

    @GetMapping("/admin/users/{userId}/sessions")
    @PreAuthorize("hasAuthority('SESSION_READ')")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> userSessions(@PathVariable UUID userId,
                                                                          @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ok("Sessions retrieved", sessionService.list(userId, activeOnly, null));
    }

    @DeleteMapping("/admin/users/{userId}/sessions/{sessionId}")
    @PreAuthorize("hasAuthority('SESSION_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> revokeUserSession(@AuthenticationPrincipal PlatformUserDetails me,
                                                               @PathVariable UUID userId, @PathVariable UUID sessionId,
                                                               HttpServletRequest request) {
        sessionService.endOwned(userId, sessionId, SessionStatus.REVOKED, SessionService.REASON_ADMIN, actor(me));
        history(userId, LoginEventType.SESSION_REVOKED, SessionService.REASON_ADMIN, sessionId, request);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, "Session revoked"));
    }

    @PostMapping("/admin/users/{userId}/sessions/revoke-all")
    @PreAuthorize("hasAuthority('SESSION_MANAGE')")
    @Operation(summary = "Force sign-out of a user on all devices")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> revokeAllUserSessions(@AuthenticationPrincipal PlatformUserDetails me,
                                                                                  @PathVariable UUID userId,
                                                                                  HttpServletRequest request) {
        int ended = sessionService.endAllForUser(userId, SessionStatus.REVOKED, SessionService.REASON_ADMIN, actor(me), null);
        history(userId, LoginEventType.SESSION_REVOKED, SessionService.REASON_ADMIN + "_ALL", null, request);
        return ok("All sessions revoked", Map.of("ended", ended));
    }

    @GetMapping("/admin/users/{userId}/login-history")
    @PreAuthorize("hasAuthority('SESSION_READ')")
    public ResponseEntity<PagedApiResponse<LoginHistoryResponse>> userLoginHistory(@PathVariable UUID userId,
                                                                                    @PageableDefault(size = 20) Pageable pageable) {
        return paged("Login history retrieved", loginHistoryService.forUser(userId, pageable));
    }

    // ======================= school admin =======================

    @GetMapping("/schools/{schoolId}/users/{userId}/sessions")
    @PreAuthorize("hasAuthority('SCHOOL_SESSION_MANAGE') and @tenantAuthorizationService.canAccessSchool(authentication, #schoolId)")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> schoolUserSessions(@PathVariable UUID schoolId, @PathVariable UUID userId,
                                                                                @RequestParam(defaultValue = "true") boolean activeOnly) {
        requireSchoolUser(schoolId, userId);
        return ok("Sessions retrieved", sessionService.list(userId, activeOnly, null));
    }

    @PostMapping("/schools/{schoolId}/users/{userId}/sessions/revoke-all")
    @PreAuthorize("hasAuthority('SCHOOL_SESSION_MANAGE') and @tenantAuthorizationService.canAccessSchool(authentication, #schoolId)")
    @Operation(summary = "Force sign-out of a school user on all devices (e.g. staff member left)")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> revokeSchoolUserSessions(@AuthenticationPrincipal PlatformUserDetails me,
                                                                                     @PathVariable UUID schoolId, @PathVariable UUID userId,
                                                                                     HttpServletRequest request) {
        requireSchoolUser(schoolId, userId);
        int ended = sessionService.endAllForUser(userId, SessionStatus.REVOKED, SessionService.REASON_ADMIN, actor(me), null);
        history(userId, LoginEventType.SESSION_REVOKED, "REVOKED_BY_SCHOOL_ADMIN", null, request);
        return ok("All sessions revoked", Map.of("ended", ended));
    }

    @GetMapping("/schools/{schoolId}/login-history")
    @PreAuthorize("hasAuthority('SCHOOL_SESSION_MANAGE') and @tenantAuthorizationService.canAccessSchool(authentication, #schoolId)")
    @Operation(summary = "Login history of all users of this school")
    public ResponseEntity<PagedApiResponse<LoginHistoryResponse>> schoolLoginHistory(@PathVariable UUID schoolId,
                                                                                      @PageableDefault(size = 20) Pageable pageable) {
        return paged("Login history retrieved", loginHistoryService.forSchool(schoolId, pageable));
    }

    // ======================= helpers =======================

    private void requireSchoolUser(UUID schoolId, UUID userId) {
        userRepository.findById(userId)
                .filter(u -> schoolId.equals(u.getSchoolId()))
                .orElseThrow(() -> new ResourceNotFoundException("PlatformUser", userId));
    }

    private void history(UUID userId, LoginEventType event, String reason, UUID sessionId, HttpServletRequest request) {
        PlatformUser user = userRepository.findById(userId).orElse(null);
        loginHistoryService.record(user, user != null ? user.getEmail() : null, event, reason, sessionId,
                ClientInfo.from(request), MDC.get(MdcKeys.CORRELATION_ID));
    }

    private static void requireUser(PlatformUserDetails me) {
        if (me == null) {
            throw new UnauthorizedException("No authenticated user in context");
        }
    }

    private static UUID actor(PlatformUserDetails me) {
        return me != null ? me.getUserId() : null;
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, message, data));
    }

    private static <T> ResponseEntity<PagedApiResponse<T>> paged(String message, Page<T> page) {
        return ResponseEntity.ok(PagedApiResponse.of(ApiResponseCode.SUCCESS, message, page));
    }
}
