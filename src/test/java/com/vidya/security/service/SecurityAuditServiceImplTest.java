package com.vidya.security.service;

import com.vidya.master.audit.entity.AuditLog;
import com.vidya.master.audit.repository.AuditLogRepository;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.repository.PlatformUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityAuditServiceImpl — Real User ID Audit Verification Tests")
class SecurityAuditServiceImplTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private PlatformUserRepository userRepository;

    private SecurityAuditServiceImpl auditService;

    private final UUID realUserId = UUID.fromString("a0000000-0000-0000-0000-000000000000");
    private final String userEmail = "superuser@vidya.io";

    @BeforeEach
    void setUp() {
        auditService = new SecurityAuditServiceImpl(auditLogRepository, userRepository);
    }

    @Test
    @DisplayName("Should use provided real userId directly on logout")
    void shouldLogLogoutWithExplicitUserId() {
        auditService.logLogout(realUserId, userEmail, "127.0.0.1", "corr-123");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).saveAndFlush(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getEntityId()).isEqualTo(realUserId);
        assertThat(saved.getPerformedBy()).isEqualTo(realUserId);
        assertThat(saved.getEntityType()).isEqualTo("PLATFORM_USER");
        assertThat(saved.getAction()).isEqualTo("LOGOUT");
    }

    @Test
    @DisplayName("Should lookup real userId from database by email if userId is null on logout")
    void shouldLookupRealUserIdFromDatabaseWhenNullOnLogout() {
        PlatformUser user = new PlatformUser(
                "USR-SUPERADMIN01",
                userEmail,
                "hashed",
                "Super",
                "User",
                "+919999999999",
                PlatformRole.PLATFORM_SUPER_ADMIN
        );
        // Force the real UUID
        user.setId(realUserId);

        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(user));

        auditService.logLogout(null, userEmail, "127.0.0.1", "corr-123");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).saveAndFlush(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getEntityId()).isEqualTo(realUserId);
        assertThat(saved.getPerformedBy()).isEqualTo(realUserId);
        assertThat(saved.getEntityType()).isEqualTo("PLATFORM_USER");
    }

    @Test
    @DisplayName("Should record LOGIN_SUCCESS with real userId")
    void shouldLogLoginSuccessWithRealUserId() {
        auditService.logLoginSuccess(realUserId, userEmail, "127.0.0.1", "corr-login-1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).saveAndFlush(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getEntityId()).isEqualTo(realUserId);
        assertThat(saved.getPerformedBy()).isEqualTo(realUserId);
        assertThat(saved.getAction()).isEqualTo("LOGIN");
    }
}
