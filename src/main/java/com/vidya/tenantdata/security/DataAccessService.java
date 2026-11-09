package com.vidya.tenantdata.security;

import com.vidya.security.access.DataDomain;
import com.vidya.security.access.DataScope;
import com.vidya.security.access.service.EffectiveAccess;
import com.vidya.security.access.service.EffectiveAccessService;
import com.vidya.security.repository.PlatformUserRepository;
import com.vidya.security.user.PlatformUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Data-level (row) access checks, used from {@code @PreAuthorize} as {@code @dataAccess.*}.
 * Always combine with the operation's permission, e.g.
 * {@code hasAuthority('STUDENT_READ') and @dataAccess.canAccessStudent(authentication, #id, 'STUDENT')}.
 *
 * <ul>
 *   <li>Scope ALL ({@code <DOMAIN>_SCOPE_ALL}, or a platform/community account): any record of the school.
 *       Which school is already enforced by tenant resolution.</li>
 *   <li>Scope OWN: only {@link OwnRecords} — self, linked children, or students of the sections taught.</li>
 * </ul>
 * Own records are resolved once per request.
 */
@Slf4j
@Service("dataAccess")
@RequiredArgsConstructor
public class DataAccessService {

    private static final String REQUEST_CACHE_KEY = DataAccessService.class.getName() + ".ownRecords";

    private final OwnRecordsResolver resolver;
    private final EffectiveAccessService effectiveAccessService;
    private final PlatformUserRepository userRepository;

    /** True when the user works on every record of the domain (list/search endpoints need this). */
    public boolean hasFullScope(Authentication authentication, String domain) {
        EffectiveAccess access = access(authentication);
        return access != null && access.dataScope(DataDomain.valueOf(domain)) == DataScope.ALL;
    }

    public boolean canAccessStudent(Authentication authentication, UUID studentId, String domain) {
        EffectiveAccess access = access(authentication);
        if (access == null || studentId == null) {
            return false;
        }
        if (access.dataScope(DataDomain.valueOf(domain)) == DataScope.ALL) {
            return true;
        }
        boolean ok = own(access).includesStudent(studentId);
        if (!ok) {
            log.warn("Data access denied: user [{}] scope OWN on {} has no link to student [{}]", access.userId(), domain, studentId);
        }
        return ok;
    }

    public boolean canAccessStudents(Authentication authentication, Collection<UUID> studentIds, String domain) {
        if (studentIds == null) {
            return false;
        }
        for (UUID id : studentIds) {
            if (!canAccessStudent(authentication, id, domain)) {
                return false;
            }
        }
        return true;
    }

    public boolean canAccessStudentCode(Authentication authentication, String studentCode, String domain) {
        if (hasFullScope(authentication, domain)) {
            return true;
        }
        return resolver.studentIdByCode(studentCode).map(id -> canAccessStudent(authentication, id, domain)).orElse(false);
    }

    /**
     * Whole-section views (class lists, daily section attendance). OWN users only for sections they
     * teach; parents and students never see a whole section.
     */
    public boolean canAccessSection(Authentication authentication, UUID sectionId, String domain) {
        EffectiveAccess access = access(authentication);
        if (access == null || sectionId == null) {
            return false;
        }
        if (access.dataScope(DataDomain.valueOf(domain)) == DataScope.ALL) {
            return true;
        }
        OwnRecords own = own(access);
        if (own.sectionIds().contains(sectionId)) {
            return true;
        }
        boolean ok = !own.wholeClassIds().isEmpty()
                && resolver.classOfSection(sectionId).map(own.wholeClassIds()::contains).orElse(false);
        if (!ok) {
            log.warn("Data access denied: user [{}] scope OWN on {} does not teach section [{}]", access.userId(), domain, sectionId);
        }
        return ok;
    }

    public boolean canAccessAttendanceRecord(Authentication authentication, UUID attendanceId) {
        if (hasFullScope(authentication, DataDomain.ATTENDANCE.name())) {
            return true;
        }
        return resolver.studentIdOfAttendance(attendanceId)
                .map(id -> canAccessStudent(authentication, id, DataDomain.ATTENDANCE.name())).orElse(false);
    }

    public boolean canAccessMarkEntry(Authentication authentication, UUID markEntryId) {
        if (hasFullScope(authentication, DataDomain.MARKS.name())) {
            return true;
        }
        return resolver.studentIdOfMarkEntry(markEntryId)
                .map(id -> canAccessStudent(authentication, id, DataDomain.MARKS.name())).orElse(false);
    }

    /** Marks of one exam paper: OWN users only if they teach that subject in that class. */
    public boolean canAccessExamSubjectConfig(Authentication authentication, UUID configId) {
        EffectiveAccess access = access(authentication);
        if (access == null || configId == null) {
            return false;
        }
        if (access.dataScope(DataDomain.MARKS) == DataScope.ALL) {
            return true;
        }
        Optional<String> pair = resolver.subjectClassOfExamConfig(configId);
        return pair.isPresent() && own(access).subjectClassPairs().contains(pair.get());
    }

    /** Students visible to the current user under OWN scope (self, children, taught students). */
    public Set<UUID> ownStudentIds(Authentication authentication) {
        EffectiveAccess access = access(authentication);
        return access == null ? Set.of() : own(access).allStudentIds();
    }

    // ------------------------------------------------------------------------------------------

    private OwnRecords own(EffectiveAccess access) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        String key = REQUEST_CACHE_KEY + "." + access.userId();
        if (attrs != null && attrs.getAttribute(key, RequestAttributes.SCOPE_REQUEST) instanceof OwnRecords cached) {
            return cached;
        }
        OwnRecords own = resolver.resolve(access.userId());
        if (attrs != null) {
            attrs.setAttribute(key, own, RequestAttributes.SCOPE_REQUEST);
        }
        return own;
    }

    private EffectiveAccess access(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof PlatformUserDetails p)) {
            return null;
        }
        if (p.getEffectiveAccess() != null) {
            return p.getEffectiveAccess();
        }
        return userRepository.findById(p.getUserId()).map(effectiveAccessService::getAccess).orElse(null);
    }
}
