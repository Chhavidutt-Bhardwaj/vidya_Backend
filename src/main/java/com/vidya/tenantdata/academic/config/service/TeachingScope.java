package com.vidya.tenantdata.academic.config.service;

import com.vidya.tenantdata.security.DataAccessService;
import com.vidya.tenantdata.user.account.SchoolContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the signed-in user may touch in syllabus and lesson plans: everything (STAFF data scope ALL — admin,
 * principal, coordinators) or only what they teach. Also resolves display names for responses.
 */
@Component
@RequiredArgsConstructor
public class TeachingScope {

    static final String PERSON_NAME = "COALESCE(NULLIF(trim(concat_ws(' ', p.first_name, p.last_name)), ''), u.user_code)";

    private final DataAccessService dataAccess;
    private final SchoolContext context;

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    /** The user works on the whole school's staff data. */
    public boolean all() {
        return dataAccess.hasFullScope(SecurityContextHolder.getContext().getAuthentication(), "STAFF");
    }

    public UUID actorId() {
        return context.actorId();
    }

    /** Staff records of the signed-in user (normally one, none for an owner or admin without a staff record). */
    public Set<UUID> ownStaffIds() {
        UUID actor = context.actorId();
        if (actor == null) {
            return Set.of();
        }
        return uuids(em.createNativeQuery("SELECT s.id FROM staff s JOIN tenant_user u ON u.id = s.tenant_user_id "
                + "WHERE u.master_user_id = :uid AND s.deleted_at IS NULL AND u.deleted_at IS NULL").setParameter("uid", actor).getResultList());
    }

    /** Does any of these staff teach the subject in the class (in that section, or the whole class) this year? */
    public boolean teaches(Collection<UUID> staffIds, UUID yearId, UUID classId, UUID sectionId, UUID subjectId) {
        if (staffIds == null || staffIds.isEmpty()) {
            return false;
        }
        return ((Number) em.createNativeQuery("SELECT count(*) FROM teacher_subject_assignment WHERE teacher_id IN (:staff) "
                        + "AND academic_year_id = :y AND academic_class_id = :c AND subject_id = :s AND deleted_at IS NULL AND status = 'ACTIVE' "
                        + "AND (section_id IS NULL OR CAST(:sec AS uuid) IS NULL OR section_id = :sec)")
                .setParameter("staff", staffIds).setParameter("y", yearId).setParameter("c", classId).setParameter("s", subjectId)
                .setParameter("sec", sectionId).getSingleResult()).longValue() > 0;
    }

    /** Full scope, or the user teaches the subject there. */
    public void requireTeaches(UUID yearId, UUID classId, UUID sectionId, UUID subjectId, String forWhat) {
        if (!all() && !teaches(ownStaffIds(), yearId, classId, sectionId, subjectId)) {
            throw new AccessDeniedException("You can " + forWhat + " only for the subjects and classes you teach");
        }
    }

    /**
     * May review a lesson plan without full scope: holds an active duty for the subject (subject in-charge),
     * the class, or the class's stage (coordinator).
     */
    public boolean hasDutyOver(Collection<UUID> staffIds, UUID classId, UUID subjectId) {
        if (staffIds == null || staffIds.isEmpty()) {
            return false;
        }
        return ((Number) em.createNativeQuery("SELECT count(*) FROM staff_duty d WHERE d.staff_id IN (:staff) AND d.status = 'ACTIVE' "
                        + "AND (d.subject_id = :s OR d.academic_class_id = :c "
                        + "OR d.class_stage = (SELECT c.stage FROM academic_class c WHERE c.id = :c))")
                .setParameter("staff", staffIds).setParameter("s", subjectId).setParameter("c", classId).getSingleResult()).longValue() > 0;
    }

    /** Active sections of the year: class id → (section id → name), in section order. */
    public Map<UUID, Map<UUID, String>> sectionsOfYear(UUID yearId) {
        Map<UUID, Map<UUID, String>> out = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT academic_class_id, id, name FROM section WHERE academic_year_id = :y "
                + "AND deleted_at IS NULL AND status = 'ACTIVE' ORDER BY code").setParameter("y", yearId).getResultList();
        rows.forEach(r -> out.computeIfAbsent(uuid(r[0]), k -> new java.util.LinkedHashMap<>()).put(uuid(r[1]), (String) r[2]));
        return out;
    }

    /** Terms of the year: id → name. */
    public Map<UUID, String> examTerms(UUID yearId) {
        Map<UUID, String> out = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT id, name FROM exam_term WHERE academic_year_id = :y AND deleted_at IS NULL")
                .setParameter("y", yearId).getResultList();
        rows.forEach(r -> out.put(uuid(r[0]), (String) r[1]));
        return out;
    }

    // ── names ────────────────────────────────────────────────────────────────

    public Map<UUID, String> classNames(Collection<UUID> ids) {
        return names("SELECT id, name FROM academic_class WHERE id IN (:ids)", ids);
    }

    public Map<UUID, String> sectionNames(Collection<UUID> ids) {
        return names("SELECT id, name FROM section WHERE id IN (:ids)", ids);
    }

    public Map<UUID, String> subjectNames(Collection<UUID> ids) {
        return names("SELECT id, name FROM subject WHERE id IN (:ids)", ids);
    }

    public Map<UUID, String> staffNames(Collection<UUID> ids) {
        return names("SELECT s.id, " + PERSON_NAME + " FROM staff s JOIN tenant_user u ON u.id = s.tenant_user_id "
                + "LEFT JOIN tenant_user_profile p ON p.user_id = u.id WHERE s.id IN (:ids)", ids);
    }

    public Map<UUID, String> names(String sql, Collection<UUID> ids) {
        Map<UUID, String> out = new HashMap<>();
        Set<UUID> wanted = new LinkedHashSet<>();
        if (ids != null) {
            ids.stream().filter(java.util.Objects::nonNull).forEach(wanted::add);
        }
        if (wanted.isEmpty()) {
            return out;
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql).setParameter("ids", wanted).getResultList();
        rows.forEach(r -> out.put(uuid(r[0]), (String) r[1]));
        return out;
    }

    static UUID uuid(Object o) {
        return o == null ? null : o instanceof UUID u ? u : UUID.fromString(o.toString());
    }

    private static Set<UUID> uuids(List<?> rows) {
        Set<UUID> out = new LinkedHashSet<>();
        rows.forEach(r -> out.add(uuid(r)));
        return out;
    }
}
