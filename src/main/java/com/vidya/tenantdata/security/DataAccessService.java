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

    // ---- enrollments (STUDENT) ---------------------------------------------------------------

    public boolean canAccessEnrollment(Authentication authentication, UUID enrollmentId, String domain) {
        if (hasFullScope(authentication, domain)) {
            return true;
        }
        return enrollmentId != null && resolver.studentIdOfEnrollment(enrollmentId)
                .map(id -> canAccessStudent(authentication, id, domain)).orElse(false);
    }

    public boolean canAccessEnrollmentCode(Authentication authentication, String code, String domain) {
        if (hasFullScope(authentication, domain)) {
            return true;
        }
        return code != null && resolver.studentIdOfEnrollmentCode(code)
                .map(id -> canAccessStudent(authentication, id, domain)).orElse(false);
    }

    /**
     * Filtered list endpoints: ALL users may list anything; OWN users must narrow the query to a
     * record they may see (a linked student, or a section they teach).
     */
    public boolean canQueryStudents(Authentication authentication, UUID studentId, UUID sectionId, String domain) {
        if (hasFullScope(authentication, domain)) {
            return true;
        }
        return (studentId != null && canAccessStudent(authentication, studentId, domain))
                || (sectionId != null && canAccessSection(authentication, sectionId, domain));
    }

    // ---- staff, assignments (STAFF) ----------------------------------------------------------

    /** A staff record: ALL, or the user's own staff row. */
    public boolean canAccessStaff(Authentication authentication, UUID staffId, String domain) {
        EffectiveAccess access = access(authentication);
        if (access == null || staffId == null) {
            return false;
        }
        if (access.dataScope(DataDomain.valueOf(domain)) == DataScope.ALL) {
            return true;
        }
        boolean ok = own(access).includesStaff(staffId);
        if (!ok) {
            log.warn("Data access denied: user [{}] scope OWN on {} is not staff [{}]", access.userId(), domain, staffId);
        }
        return ok;
    }

    public boolean canAccessStaffCode(Authentication authentication, String staffCode, String domain) {
        if (hasFullScope(authentication, domain)) {
            return true;
        }
        return staffCode != null && resolver.staffIdByCode(staffCode)
                .map(id -> canAccessStaff(authentication, id, domain)).orElse(false);
    }

    /** A class-teacher assignment (by id or code): ALL, or it is the user's own. */
    public boolean canAccessClassTeacherAssignment(Authentication authentication, UUID id, String code) {
        if (hasFullScope(authentication, DataDomain.STAFF.name())) {
            return true;
        }
        return (id != null || code != null) && resolver.classTeacherAssignment(id, code)
                .map(r -> canAccessStaff(authentication, r[0], DataDomain.STAFF.name())).orElse(false);
    }

    /** A teacher-subject assignment (by id or code): ALL, or it is the user's own. */
    public boolean canAccessTeacherSubjectAssignment(Authentication authentication, UUID id, String code) {
        if (hasFullScope(authentication, DataDomain.STAFF.name())) {
            return true;
        }
        return (id != null || code != null) && resolver.teacherSubjectAssignment(id, code)
                .map(r -> canAccessStaff(authentication, r[0], DataDomain.STAFF.name())).orElse(false);
    }

    /** Assignment lists: ALL, or narrowed to the user's own staff id or a section/class they teach. */
    public boolean canQueryAssignments(Authentication authentication, UUID staffId, UUID sectionId, UUID classId) {
        if (hasFullScope(authentication, DataDomain.STAFF.name())) {
            return true;
        }
        return (staffId != null && canAccessStaff(authentication, staffId, DataDomain.STAFF.name()))
                || (sectionId != null && canAccessSection(authentication, sectionId, DataDomain.STAFF.name()))
                || (sectionId == null && classId != null && canAccessClass(authentication, classId, DataDomain.STAFF.name()));
    }

    // ---- timetable (TIMETABLE) ---------------------------------------------------------------

    /**
     * Read a section's timetable: ALL, sections the user teaches, or the section the user (student)
     * or their child (parent) is enrolled in.
     */
    public boolean canViewSectionTimetable(Authentication authentication, UUID sectionId) {
        EffectiveAccess access = access(authentication);
        if (access == null || sectionId == null) {
            return false;
        }
        if (access.dataScope(DataDomain.TIMETABLE) == DataScope.ALL) {
            return true;
        }
        if (own(access).studentSectionIds().contains(sectionId)) {
            return true;
        }
        return canAccessSection(authentication, sectionId, DataDomain.TIMETABLE.name());
    }

    /** A whole class (timetable or assignment views): ALL, a class the user teaches, or their own / child's class. */
    public boolean canAccessClass(Authentication authentication, UUID classId, String domain) {
        EffectiveAccess access = access(authentication);
        if (access == null || classId == null) {
            return false;
        }
        if (access.dataScope(DataDomain.valueOf(domain)) == DataScope.ALL) {
            return true;
        }
        OwnRecords own = own(access);
        if (own.teachingClassIds().contains(classId)) {
            return true;
        }
        if (DataDomain.valueOf(domain) == DataDomain.TIMETABLE) {
            for (UUID section : own.studentSectionIds()) {
                if (resolver.classOfSection(section).map(classId::equals).orElse(false)) {
                    return true;
                }
            }
        }
        log.warn("Data access denied: user [{}] scope OWN on {} has no link to class [{}]", access.userId(), domain, classId);
        return false;
    }

    /** Class timetable, optionally narrowed to one section. */
    public boolean canViewClassTimetable(Authentication authentication, UUID classId, UUID sectionId) {
        if (sectionId != null) {
            return canViewSectionTimetable(authentication, sectionId)
                    && (hasFullScope(authentication, DataDomain.TIMETABLE.name())
                    || resolver.classOfSection(sectionId).map(c -> c.equals(classId)).orElse(false));
        }
        return canAccessClass(authentication, classId, DataDomain.TIMETABLE.name());
    }

    /** A teacher's grid / day / load: ALL, or the user's own. */
    public boolean canViewTeacherTimetable(Authentication authentication, UUID teacherId) {
        return canAccessStaff(authentication, teacherId, DataDomain.TIMETABLE.name());
    }

    /** Timetable entry search: ALL, or narrowed to the user's own teacher id or a section they may view. */
    public boolean canQueryTimetableEntries(Authentication authentication, UUID sectionId, UUID teacherId, UUID classId) {
        if (hasFullScope(authentication, DataDomain.TIMETABLE.name())) {
            return true;
        }
        return (teacherId != null && canViewTeacherTimetable(authentication, teacherId))
                || (sectionId != null && canViewSectionTimetable(authentication, sectionId))
                || (sectionId == null && teacherId == null && classId != null
                    && canAccessClass(authentication, classId, DataDomain.TIMETABLE.name()));
    }

    public boolean canAccessTimetableEntry(Authentication authentication, UUID entryId) {
        if (hasFullScope(authentication, DataDomain.TIMETABLE.name())) {
            return true;
        }
        return entryId != null && resolver.timetableEntry(entryId)
                .map(r -> (r[0] != null && canAccessStaffQuietly(authentication, r[0]))
                        || (r[1] != null && canViewSectionTimetable(authentication, r[1])))
                .orElse(false);
    }

    /** A substitution: ALL, the user is the original or substitute teacher, or it is in a section they may view. */
    public boolean canAccessSubstitution(Authentication authentication, UUID substitutionId) {
        if (hasFullScope(authentication, DataDomain.TIMETABLE.name())) {
            return true;
        }
        return substitutionId != null && resolver.substitution(substitutionId)
                .map(r -> (r[0] != null && canAccessStaffQuietly(authentication, r[0]))
                        || (r[1] != null && canAccessStaffQuietly(authentication, r[1]))
                        || (r[2] != null && canViewSectionTimetable(authentication, r[2])))
                .orElse(false);
    }

    public boolean canQuerySubstitutions(Authentication authentication, UUID sectionId, UUID teacherId) {
        if (hasFullScope(authentication, DataDomain.TIMETABLE.name())) {
            return true;
        }
        return (teacherId != null && canViewTeacherTimetable(authentication, teacherId))
                || (sectionId != null && canViewSectionTimetable(authentication, sectionId));
    }

    // ---- school user accounts (USER) ---------------------------------------------------------

    /** A tenant_user row: ALL, or the user's own account. */
    public boolean canAccessTenantUser(Authentication authentication, UUID tenantUserId, String domain) {
        EffectiveAccess access = access(authentication);
        if (access == null || tenantUserId == null) {
            return false;
        }
        if (access.dataScope(DataDomain.valueOf(domain)) == DataScope.ALL) {
            return true;
        }
        boolean ok = own(access).tenantUserIds().contains(tenantUserId);
        if (!ok) {
            log.warn("Data access denied: user [{}] scope OWN on {} is not tenant user [{}]", access.userId(), domain, tenantUserId);
        }
        return ok;
    }

    private boolean canAccessStaffQuietly(Authentication authentication, UUID staffId) {
        EffectiveAccess access = access(authentication);
        return access != null && own(access).includesStaff(staffId);
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
