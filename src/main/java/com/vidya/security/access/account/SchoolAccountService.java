package com.vidya.security.access.account;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.security.access.service.AccessChangedEvent;
import com.vidya.security.access.service.AccessControlService;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.entity.UserStatus;
import com.vidya.security.repository.PlatformUserRepository;
import com.vidya.security.session.entity.SessionStatus;
import com.vidya.security.session.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Login accounts (master {@code platform_user}) for the people of a school: staff, teachers, students and parents.
 * There is one account model and one sign-in ({@code POST /api/v1/auth/login}) for all of them; what differs is the
 * set of account types a person may have ({@link Kind}).
 *
 * <p>Rules:
 * <ul>
 *   <li>Only the roles of the person's kind can be given; SCHOOL_OWNER and platform / community roles never.</li>
 *   <li>The signed-in user must hold every permission of the role they give (no privilege escalation).</li>
 *   <li>An email that already has an account is only reused when that account belongs to this school, is of the
 *       right kind and is not linked to anyone yet; accounts of other schools are never moved.</li>
 * </ul>
 * Every method runs in its own master transaction so the tenant side can compensate on failure.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchoolAccountService {

    /** What kind of person the account is for; decides the allowed account types. */
    public enum Kind {
        STAFF(EnumSet.of(PlatformRole.SCHOOL_ADMIN, PlatformRole.PRINCIPAL, PlatformRole.COORDINATOR, PlatformRole.TEACHER,
                PlatformRole.SUBJECT_TEACHER, PlatformRole.ACCOUNTS, PlatformRole.SCHOOL_STAFF), PlatformRole.SCHOOL_STAFF),
        STUDENT(EnumSet.of(PlatformRole.STUDENT), PlatformRole.STUDENT),
        PARENT(EnumSet.of(PlatformRole.PARENT), PlatformRole.PARENT);

        public final Set<PlatformRole> roles;
        public final PlatformRole defaultRole;

        Kind(Set<PlatformRole> roles, PlatformRole defaultRole) {
            this.roles = roles;
            this.defaultRole = defaultRole;
        }
    }

    public static final Set<PlatformRole> STAFF_ROLES = Kind.STAFF.roles;

    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** @param temporaryPassword only set for a new account; shown once */
    public record Account(UUID userId, String userCode, String email, PlatformRole role, UserStatus status, boolean created,
                          String temporaryPassword) {
    }

    private final PlatformUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessControlService accessControlService;
    private final SessionService sessionService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Creates a login, or adopts an unlinked account of this school with the same email.
     *
     * @param alreadyLinked user ids already linked to people of this school (adoption guard)
     */
    @Transactional(transactionManager = "masterTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public Account createOrAdopt(UUID schoolId, Kind kind, String email, String firstName, String lastName, String phone, PlatformRole role,
                                 Set<UUID> alreadyLinked) {
        requireRole(kind, role);
        if (email == null || email.isBlank()) {
            throw new BusinessException("An email address is required to create a login");
        }
        String mail = email.trim().toLowerCase();
        var existing = userRepository.findByEmailIgnoreCase(mail);
        if (existing.isPresent()) {
            PlatformUser u = existing.get();
            if (!schoolId.equals(u.getSchoolId())) {
                throw new DuplicateResourceException("A login with email " + mail + " already exists for another school or the platform; use another email");
            }
            if (alreadyLinked.contains(u.getId())) {
                throw new DuplicateResourceException("Login " + mail + " already belongs to another person of this school");
            }
            if (u.getPlatformRole() == PlatformRole.SCHOOL_OWNER || !kind.roles.contains(u.getPlatformRole())) {
                throw new BusinessException("Login " + mail + " is a " + u.getPlatformRole() + " account and cannot be linked to a " + kind + " record");
            }
            log.info("Adopting existing login {} ({}) of school {} for a {} record", u.getUserCode(), u.getPlatformRole(), schoolId, kind);
            return new Account(u.getId(), u.getUserCode(), u.getEmail(), u.getPlatformRole(), u.getStatus(), false, null);
        }
        accessControlService.requireActorCanGrantRole(role.name());
        String password = temporaryPassword();
        PlatformUser u = userRepository.save(new PlatformUser(CodeGenerator.userCode(), mail, passwordEncoder.encode(password),
                firstName, lastName != null ? lastName : "", phone, role, schoolId));
        log.info("Created {} login {} ({}) for school {}", kind, u.getUserCode(), role, schoolId);
        return new Account(u.getId(), u.getUserCode(), u.getEmail(), role, u.getStatus(), true, password);
    }

    /** Undo for a login created in a tenant transaction that rolled back. */
    @Transactional(transactionManager = "masterTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void deleteCreated(UUID userId) {
        userRepository.findById(userId).ifPresent(u -> {
            userRepository.delete(u);
            log.warn("Removed login {} created for a person record that was not saved", u.getUserCode());
        });
    }

    @Transactional(readOnly = true, transactionManager = "masterTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public Account get(UUID schoolId, UUID userId) {
        return toAccount(load(schoolId, userId));
    }

    /** Every login of the school. */
    @Transactional(readOnly = true, transactionManager = "masterTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public List<Account> schoolAccounts(UUID schoolId) {
        return userRepository.findBySchoolId(schoolId).stream().map(SchoolAccountService::toAccount).toList();
    }

    /** Changes the account type (primary role). Sessions pick up the new permissions on their next refresh. */
    @Transactional(transactionManager = "masterTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public Account changeRole(UUID schoolId, Kind kind, UUID userId, PlatformRole role) {
        requireRole(kind, role);
        PlatformUser u = load(schoolId, userId);
        if (!kind.roles.contains(u.getPlatformRole())) {
            throw new BusinessException("The " + u.getPlatformRole() + " account type cannot be changed here");
        }
        if (u.getPlatformRole() != role) {
            accessControlService.requireActorCanGrantRole(role.name());
            // Taking a role away needs the same rights as giving it (a teacher cannot demote the admin).
            accessControlService.requireActorCanGrantRole(u.getPlatformRole().name());
            PlatformRole old = u.getPlatformRole();
            u.setPlatformRole(role);
            eventPublisher.publishEvent(AccessChangedEvent.user(userId, "Account type " + old + " → " + role));
            log.info("Login {} role {} → {}", u.getUserCode(), old, role);
        }
        return toAccount(u);
    }

    /** Disables or re-enables a login; disabling signs the person out everywhere. Returns the sessions ended. */
    @Transactional(transactionManager = "masterTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public int setActive(UUID schoolId, UUID userId, boolean active, UUID actorId) {
        PlatformUser u = load(schoolId, userId);
        if (u.getPlatformRole() == PlatformRole.SCHOOL_OWNER) {
            throw new BusinessException("The school owner's login cannot be changed here");
        }
        accessControlService.requireActorCanGrantRole(u.getPlatformRole().name());
        u.setStatus(active ? UserStatus.ACTIVE : UserStatus.INACTIVE);
        int ended = active ? 0 : sessionService.endAllForUser(userId, SessionStatus.REVOKED, SessionService.REASON_ADMIN, actorId, null);
        eventPublisher.publishEvent(AccessChangedEvent.user(userId, active ? "Login enabled" : "Login disabled"));
        log.info("Login {} {} ({} session(s) ended)", u.getUserCode(), active ? "enabled" : "disabled", ended);
        return ended;
    }

    static void requireRole(Kind kind, PlatformRole role) {
        if (role == null || !kind.roles.contains(role)) {
            throw new BusinessException("Role " + role + " cannot be given to a " + kind + ". Allowed: " + kind.roles);
        }
    }

    private PlatformUser load(UUID schoolId, UUID userId) {
        PlatformUser u = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("PlatformUser", userId));
        if (!schoolId.equals(u.getSchoolId())) {
            throw new ResourceNotFoundException("PlatformUser", userId);
        }
        return u;
    }

    private static Account toAccount(PlatformUser u) {
        return new Account(u.getId(), u.getUserCode(), u.getEmail(), u.getPlatformRole(), u.getStatus(), false, null);
    }

    /** 12 characters from an unambiguous alphabet plus a digit and a symbol. */
    static String temporaryPassword() {
        StringBuilder b = new StringBuilder("V");
        for (int i = 0; i < 10; i++) {
            b.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return b.append(RANDOM.nextInt(10)).append('@').toString();
    }
}
