package com.vidya.security.access.account;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.security.access.account.SchoolAccountService.Kind;
import com.vidya.security.access.service.AccessControlService;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.repository.PlatformUserRepository;
import com.vidya.security.session.service.SessionService;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("School logins — one account model for staff, students and parents")
class SchoolAccountServiceTest {

    @Mock private PlatformUserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AccessControlService accessControlService;
    @Mock private SessionService sessionService;
    @Mock private ApplicationEventPublisher events;

    private SchoolAccountService service;
    private final UUID school = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SchoolAccountService(userRepository, passwordEncoder, accessControlService, sessionService, events);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any())).thenAnswer(i -> {
            PlatformUser u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
    }

    @Test
    @DisplayName("new login: role must fit the kind, escalation checked, temporary password returned once")
    void creates() {
        var a = service.createOrAdopt(school, Kind.STAFF, "Asha@School.in", "Asha", "Rao", null, PlatformRole.TEACHER, Set.of());

        assertThat(a.created()).isTrue();
        assertThat(a.email()).isEqualTo("asha@school.in");
        assertThat(a.temporaryPassword()).hasSize(13).matches(".*\\d@$");
        verify(accessControlService).requireActorCanGrantRole("TEACHER");

        var s = service.createOrAdopt(school, Kind.STUDENT, "s@school.in", "S", null, null, PlatformRole.STUDENT, Set.of());
        assertThat(s.role()).isEqualTo(PlatformRole.STUDENT);

        assertThatThrownBy(() -> service.createOrAdopt(school, Kind.STAFF, "x@y.in", "X", null, null, PlatformRole.SCHOOL_OWNER, Set.of()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.createOrAdopt(school, Kind.STAFF, "x@y.in", "X", null, null, PlatformRole.STUDENT, Set.of()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.createOrAdopt(school, Kind.PARENT, "x@y.in", "X", null, null, PlatformRole.TEACHER, Set.of()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("a teacher cannot create a login with more rights than they have")
    void escalation() {
        doThrow(new AccessDeniedException("You cannot grant permissions you do not hold")).when(accessControlService).requireActorCanGrantRole("SCHOOL_ADMIN");

        assertThatThrownBy(() -> service.createOrAdopt(school, Kind.STAFF, "a@b.in", "A", null, null, PlatformRole.SCHOOL_ADMIN, Set.of()))
                .isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("existing email: adopted only if it is an unlinked login of this school and of the right kind")
    void adopt() {
        PlatformUser mine = user(school, PlatformRole.COORDINATOR);
        when(userRepository.findByEmailIgnoreCase("c@school.in")).thenReturn(Optional.of(mine));

        var a = service.createOrAdopt(school, Kind.STAFF, "c@school.in", "C", null, null, PlatformRole.TEACHER, Set.of());
        assertThat(a.created()).isFalse();
        assertThat(a.role()).isEqualTo(PlatformRole.COORDINATOR);

        assertThatThrownBy(() -> service.createOrAdopt(school, Kind.STAFF, "c@school.in", "C", null, null, PlatformRole.TEACHER, Set.of(mine.getId())))
                .isInstanceOf(DuplicateResourceException.class);
        assertThatThrownBy(() -> service.createOrAdopt(school, Kind.STUDENT, "c@school.in", "C", null, null, PlatformRole.STUDENT, Set.of()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("COORDINATOR");

        PlatformUser other = user(UUID.randomUUID(), PlatformRole.TEACHER);
        when(userRepository.findByEmailIgnoreCase("o@other.in")).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.createOrAdopt(school, Kind.STAFF, "o@other.in", "O", null, null, PlatformRole.TEACHER, Set.of()))
                .isInstanceOf(DuplicateResourceException.class).hasMessageContaining("another school");

        PlatformUser owner = user(school, PlatformRole.SCHOOL_OWNER);
        when(userRepository.findByEmailIgnoreCase("owner@school.in")).thenReturn(Optional.of(owner));
        assertThatThrownBy(() -> service.createOrAdopt(school, Kind.STAFF, "owner@school.in", "O", null, null, PlatformRole.TEACHER, Set.of()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("role change needs rights to both the old and the new role; owner untouchable")
    void changeRole() {
        PlatformUser u = user(school, PlatformRole.TEACHER);
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));

        service.changeRole(school, Kind.STAFF, u.getId(), PlatformRole.COORDINATOR);

        assertThat(u.getPlatformRole()).isEqualTo(PlatformRole.COORDINATOR);
        verify(accessControlService).requireActorCanGrantRole("COORDINATOR");
        verify(accessControlService).requireActorCanGrantRole("TEACHER");
        verify(events).publishEvent(any(Object.class));

        assertThatThrownBy(() -> service.changeRole(UUID.randomUUID(), Kind.STAFF, u.getId(), PlatformRole.TEACHER)).hasMessageContaining("PlatformUser");

        PlatformUser owner = user(school, PlatformRole.SCHOOL_OWNER);
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        assertThatThrownBy(() -> service.setActive(school, owner.getId(), false, null)).isInstanceOf(BusinessException.class);
    }

    private static PlatformUser user(UUID school, PlatformRole role) {
        PlatformUser u = new PlatformUser("USR-1", "u@x.in", "h", "U", "", null, role, school);
        u.setId(UUID.randomUUID());
        return u;
    }
}
