package com.vidya.tenantdata.assessment;

import com.vidya.tenantdata.academic.config.service.TeachingScope;
import com.vidya.tenantdata.security.DataAccessService;
import com.vidya.tenantdata.security.OwnRecords;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Row rules of the assessment engine, on the MARKS data domain. MARKS scope ALL (exam cell, principal, admin) works on
 * every class; teachers on the subjects and classes they teach (teacher subject assignments); students and parents
 * only on their own / their children's records.
 */
@Component
@RequiredArgsConstructor
public class AssessmentAccess {

    public static final String DOMAIN = "MARKS";

    private final DataAccessService dataAccess;
    private final TeachingScope teaching;

    public boolean all() {
        return dataAccess.hasFullScope(auth(), DOMAIN);
    }

    public UUID actorId() {
        return teaching.actorId();
    }

    public OwnRecords own() {
        return dataAccess.ownRecords(auth());
    }

    /** The signed-in user's staff record, if any. */
    public UUID staffId() {
        Set<UUID> ids = own().staffIds();
        return ids.isEmpty() ? null : ids.iterator().next();
    }

    public boolean hasAuthority(String authority) {
        Authentication a = auth();
        return a != null && a.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(authority::equals);
    }

    /** Teaches the subject in the class (any class when classId is null). */
    public boolean teaches(UUID subjectId, UUID classId) {
        if (all()) {
            return true;
        }
        Set<String> pairs = own().subjectClassPairs();
        if (classId != null) {
            return pairs.contains(subjectId + ":" + classId);
        }
        String prefix = subjectId + ":";
        return pairs.stream().anyMatch(p -> p.startsWith(prefix));
    }

    public void requireTeaches(UUID subjectId, UUID classId, String forWhat) {
        if (!teaches(subjectId, classId)) {
            throw new AccessDeniedException("You can " + forWhat + " only for the subjects and classes you teach");
        }
    }

    public void requireClass(UUID classId, UUID sectionId, String forWhat) {
        boolean ok = sectionId != null ? dataAccess.canAccessSection(auth(), sectionId, DOMAIN) : dataAccess.canAccessClass(auth(), classId, DOMAIN);
        if (!ok) {
            throw new AccessDeniedException("You can " + forWhat + " only for the classes and sections you teach");
        }
    }

    public boolean canSeeStudent(UUID studentId) {
        return dataAccess.canAccessStudent(auth(), studentId, DOMAIN);
    }

    public void requireStudent(UUID studentId, String forWhat) {
        if (!canSeeStudent(studentId)) {
            throw new AccessDeniedException("You can " + forWhat + " only for your own students");
        }
    }

    public void requireStudents(Collection<UUID> studentIds, String forWhat) {
        if (!studentIds.isEmpty() && !dataAccess.canAccessStudents(auth(), studentIds, DOMAIN)) {
            throw new AccessDeniedException("You can " + forWhat + " only for your own students");
        }
    }

    public void requireExamPaper(UUID examSubjectConfigId, String forWhat) {
        if (!dataAccess.canAccessExamSubjectConfig(auth(), examSubjectConfigId)) {
            throw new AccessDeniedException("You can " + forWhat + " only for exam papers of subjects you teach");
        }
    }

    /** Students the login is (a student account) or is guardian of. */
    public Set<UUID> familyStudentIds() {
        OwnRecords own = own();
        Set<UUID> ids = new LinkedHashSet<>(own.selfStudentIds());
        ids.addAll(own.childStudentIds());
        return ids;
    }

    public UUID selfStudentId() {
        Set<UUID> ids = own().selfStudentIds();
        if (ids.isEmpty()) {
            throw new AccessDeniedException("Only a student account can take an online exam");
        }
        return ids.iterator().next();
    }

    public void requireAuthorOrAll(UUID createdBy, String forWhat) {
        if (!all() && (createdBy == null || !createdBy.equals(actorId()))) {
            throw new AccessDeniedException("You can " + forWhat + " only what you created");
        }
    }

    private static Authentication auth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
