package com.vidya.tenantdata.engagement.service;

import com.vidya.tenantdata.engagement.dto.EngagementDtos.ActivityItem;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.AnnouncementItem;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.AttendanceInfo;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.HomeworkItem;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.LessonItem;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.RemarkItem;
import com.vidya.tenantdata.engagement.enums.ActivityType;
import com.vidya.tenantdata.engagement.enums.AnnouncementAudience;
import com.vidya.tenantdata.engagement.enums.AnnouncementPriority;
import com.vidya.tenantdata.engagement.enums.RemarkCategory;
import com.vidya.tenantdata.engagement.enums.RemarkSentiment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only SQL behind the daily report and notification fan-out. Callers own the transaction.
 * Class-level queries take {@code (year, class, section)} so that a whole section's report needs one query
 * per kind of content, not one per student.
 */
@Component
public class DailyReportQueries {

    private static final String ACTIVE_ENROLLMENT = "('ADMITTED', 'ACTIVE')";
    /** Display name of a tenant_user joined as {@code u} with profile {@code p}. */
    private static final String PERSON = "COALESCE(NULLIF(trim(concat_ws(' ', p.first_name, p.last_name)), ''), u.user_code)";
    private static final String TEACHER = "COALESCE(NULLIF(trim(concat_ws(' ', tp.first_name, tp.last_name)), ''), tu.user_code)";
    private static final String TEACHER_JOIN = " LEFT JOIN staff st ON st.id = %s LEFT JOIN tenant_user tu ON tu.id = st.tenant_user_id "
            + "LEFT JOIN tenant_user_profile tp ON tp.user_id = tu.id ";
    /** Class-wide rows, or rows of the section (a student without a section sees only class-wide rows). */
    private static final String SECTION_MATCH = "(%1$s.section_id IS NULL OR %1$s.section_id = CAST(:sec AS uuid))";

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    /** A student's enrollment that covers the date. */
    public record EnrolledStudent(UUID studentId, String studentCode, String name, UUID academicYearId, UUID academicClassId,
                                  String className, UUID sectionId, String sectionName, String rollNumber) {
    }

    public record StudentRef(UUID studentId, String studentCode, String name) {
    }

    /** An active guardian of a student. {@code guardianUserId} is the parent login (tenant_user), when linked. */
    public record GuardianContact(UUID studentId, UUID guardianId, String name, String email, String phone, UUID guardianUserId,
                                  boolean primary) {
    }

    /**
     * Students with an active enrollment in an academic year that covers {@code date}; the latest enrollment wins.
     *
     * @param studentIds null = every student of the school
     * @param classId    optional filter
     * @param sectionId  optional filter
     */
    public List<EnrolledStudent> enrolledStudents(LocalDate date, Collection<UUID> studentIds, UUID classId, UUID sectionId) {
        if (studentIds != null && studentIds.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("SELECT DISTINCT ON (e.student_id) e.student_id, s.student_code, ").append(PERSON)
                .append(", e.academic_year_id, e.academic_class_id, c.name, e.section_id, sec.name, e.roll_number "
                        + "FROM student_academic_enrollment e JOIN student s ON s.id = e.student_id AND s.deleted_at IS NULL "
                        + "JOIN tenant_user u ON u.id = s.tenant_user_id LEFT JOIN tenant_user_profile p ON p.user_id = u.id "
                        + "JOIN academic_year y ON y.id = e.academic_year_id JOIN academic_class c ON c.id = e.academic_class_id "
                        + "LEFT JOIN section sec ON sec.id = e.section_id "
                        + "WHERE e.deleted_at IS NULL AND e.enrollment_status IN " + ACTIVE_ENROLLMENT
                        + " AND :d BETWEEN y.start_date AND y.end_date");
        Map<String, Object> params = new HashMap<>();
        params.put("d", date);
        if (studentIds != null) {
            sql.append(" AND e.student_id IN (:ids)");
            params.put("ids", studentIds);
        }
        if (classId != null) {
            sql.append(" AND e.academic_class_id = :c");
            params.put("c", classId);
        }
        if (sectionId != null) {
            sql.append(" AND e.section_id = :sec");
            params.put("sec", sectionId);
        }
        sql.append(" ORDER BY e.student_id, e.enrollment_date DESC, e.created_at DESC");
        List<EnrolledStudent> out = new ArrayList<>();
        for (Object[] r : rows(sql.toString(), params)) {
            out.add(new EnrolledStudent(uuid(r[0]), (String) r[1], (String) r[2], uuid(r[3]), uuid(r[4]), (String) r[5], uuid(r[6]),
                    (String) r[7], (String) r[8]));
        }
        return out;
    }

    /** Code and name of students (for a child with no enrollment on the date). */
    public Map<UUID, StudentRef> students(Collection<UUID> studentIds) {
        Map<UUID, StudentRef> out = new LinkedHashMap<>();
        if (studentIds == null || studentIds.isEmpty()) {
            return out;
        }
        for (Object[] r : rows("SELECT s.id, s.student_code, " + PERSON + " FROM student s JOIN tenant_user u ON u.id = s.tenant_user_id "
                + "LEFT JOIN tenant_user_profile p ON p.user_id = u.id WHERE s.id IN (:ids) AND s.deleted_at IS NULL", Map.of("ids", studentIds))) {
            out.put(uuid(r[0]), new StudentRef(uuid(r[0]), (String) r[1], (String) r[2]));
        }
        return out;
    }

    public Map<UUID, AttendanceInfo> attendance(Collection<UUID> studentIds, LocalDate date) {
        Map<UUID, AttendanceInfo> out = new HashMap<>();
        if (studentIds.isEmpty()) {
            return out;
        }
        for (Object[] r : rows("SELECT student_id, status, remarks, marked_at FROM student_attendance WHERE student_id IN (:ids) "
                + "AND attendance_date = :d AND deleted_at IS NULL AND status <> 'DELETED'", Map.of("ids", studentIds, "d", date))) {
            out.put(uuid(r[0]), new AttendanceInfo((String) r[1], (String) r[2], instant(r[3])));
        }
        return out;
    }

    /** Homework set on {@code date} or due the next day. */
    public List<HomeworkItem> homework(UUID yearId, UUID classId, UUID sectionId, LocalDate date) {
        String sql = "SELECT h.id, sub.name, h.title, h.description, h.assigned_on, h.due_on, " + TEACHER
                + " FROM homework h JOIN subject sub ON sub.id = h.subject_id" + TEACHER_JOIN.formatted("h.teacher_id")
                + "WHERE h.deleted_at IS NULL AND h.status = 'PUBLISHED' AND h.academic_year_id = :y AND h.academic_class_id = :c AND "
                + SECTION_MATCH.formatted("h") + " AND (h.assigned_on = :d OR h.due_on = :next) ORDER BY sub.name, h.title";
        Map<String, Object> params = classParams(yearId, classId, sectionId);
        params.put("d", date);
        params.put("next", date.plusDays(1));
        List<HomeworkItem> out = new ArrayList<>();
        for (Object[] r : rows(sql, params)) {
            out.add(new HomeworkItem(uuid(r[0]), (String) r[1], (String) r[2], (String) r[3], localDate(r[4]), localDate(r[5]), (String) r[6]));
        }
        return out;
    }

    /** Lessons whose plan was marked delivered on {@code date}. */
    public List<LessonItem> lessonsDelivered(UUID yearId, UUID classId, UUID sectionId, LocalDate date) {
        String sql = "SELECT sub.name, lp.title, t.title, " + TEACHER + " FROM lesson_plan lp JOIN subject sub ON sub.id = lp.subject_id "
                + "LEFT JOIN syllabus_topic t ON t.id = lp.syllabus_topic_id" + TEACHER_JOIN.formatted("lp.teacher_id")
                + "WHERE lp.deleted_at IS NULL AND lp.status = 'DELIVERED' AND lp.delivered_on = :d AND lp.academic_year_id = :y "
                + "AND lp.academic_class_id = :c AND " + SECTION_MATCH.formatted("lp") + " ORDER BY sub.name, lp.title";
        Map<String, Object> params = classParams(yearId, classId, sectionId);
        params.put("d", date);
        List<LessonItem> out = new ArrayList<>();
        for (Object[] r : rows(sql, params)) {
            out.add(new LessonItem((String) r[0], (String) r[1], (String) r[2], (String) r[3]));
        }
        return out;
    }

    public List<ActivityItem> activities(UUID yearId, UUID classId, UUID sectionId, LocalDate date) {
        String sql = "SELECT a.id, a.activity_type, sub.name, a.title, a.description, " + TEACHER
                + " FROM class_activity a LEFT JOIN subject sub ON sub.id = a.subject_id" + TEACHER_JOIN.formatted("a.teacher_id")
                + "WHERE a.deleted_at IS NULL AND a.activity_date = :d AND a.academic_year_id = :y AND a.academic_class_id = :c AND "
                + SECTION_MATCH.formatted("a") + " ORDER BY a.created_at";
        Map<String, Object> params = classParams(yearId, classId, sectionId);
        params.put("d", date);
        List<ActivityItem> out = new ArrayList<>();
        for (Object[] r : rows(sql, params)) {
            out.add(new ActivityItem(uuid(r[0]), ActivityType.valueOf((String) r[1]), (String) r[2], (String) r[3], (String) r[4],
                    tu(r[5])));
        }
        return out;
    }

    /** Announcements published on {@code date} for the school, the class or the section. */
    public List<AnnouncementItem> announcements(UUID classId, UUID sectionId, LocalDate date) {
        String sql = "SELECT id, title, body, priority, audience FROM announcement WHERE deleted_at IS NULL AND status = 'PUBLISHED' "
                + "AND publish_on = :d AND (audience = 'SCHOOL' OR (audience = 'CLASS' AND academic_class_id = CAST(:c AS uuid)) "
                + "OR (audience = 'SECTION' AND section_id = CAST(:sec AS uuid))) "
                + "ORDER BY CASE priority WHEN 'EMERGENCY' THEN 0 WHEN 'IMPORTANT' THEN 1 ELSE 2 END, published_at";
        Map<String, Object> params = new HashMap<>();
        params.put("d", date);
        params.put("c", classId);
        params.put("sec", sectionId);
        List<AnnouncementItem> out = new ArrayList<>();
        for (Object[] r : rows(sql, params)) {
            out.add(new AnnouncementItem(uuid(r[0]), (String) r[1], (String) r[2], AnnouncementPriority.valueOf((String) r[3]),
                    AnnouncementAudience.valueOf((String) r[4])));
        }
        return out;
    }

    /** Remarks per student for the date. {@code includeStaffOnly} false = only remarks families may see. */
    public Map<UUID, List<RemarkItem>> remarks(Collection<UUID> studentIds, LocalDate date, boolean includeStaffOnly) {
        Map<UUID, List<RemarkItem>> out = new HashMap<>();
        if (studentIds.isEmpty()) {
            return out;
        }
        String sql = "SELECT r.id, r.student_id, r.category, r.sentiment, r.remark, sub.name, " + TEACHER + ", r.visible_to_guardians "
                + "FROM student_daily_remark r LEFT JOIN subject sub ON sub.id = r.subject_id" + TEACHER_JOIN.formatted("r.teacher_id")
                + "WHERE r.deleted_at IS NULL AND r.student_id IN (:ids) AND r.remark_date = :d"
                + (includeStaffOnly ? "" : " AND r.visible_to_guardians = TRUE") + " ORDER BY r.created_at";
        for (Object[] r : rows(sql, Map.of("ids", studentIds, "d", date))) {
            out.computeIfAbsent(uuid(r[1]), k -> new ArrayList<>()).add(new RemarkItem(uuid(r[0]), RemarkCategory.valueOf((String) r[2]),
                    RemarkSentiment.valueOf((String) r[3]), (String) r[4], (String) r[5], tu(r[6]), Boolean.TRUE.equals(r[7])));
        }
        return out;
    }

    public List<GuardianContact> guardians(Collection<UUID> studentIds) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        List<GuardianContact> out = new ArrayList<>();
        for (Object[] r : rows("SELECT g.student_id, g.id, g.guardian_name, g.email, g.phone, g.guardian_user_id, g.is_primary "
                + "FROM student_guardian g WHERE g.deleted_at IS NULL AND g.status = 'ACTIVE' AND g.student_id IN (:ids) "
                + "ORDER BY g.is_primary DESC, g.created_at", Map.of("ids", studentIds))) {
            out.add(new GuardianContact(uuid(r[0]), uuid(r[1]), (String) r[2], (String) r[3], (String) r[4], uuid(r[5]),
                    Boolean.TRUE.equals(r[6])));
        }
        return out;
    }

    /** tenant_user rows of the login, for the inbox. */
    public List<UUID> tenantUsersOfLogin(UUID masterUserId) {
        if (masterUserId == null) {
            return List.of();
        }
        List<UUID> out = new ArrayList<>();
        for (Object o : em.createNativeQuery("SELECT id FROM tenant_user WHERE master_user_id = :uid AND deleted_at IS NULL")
                .setParameter("uid", masterUserId).getResultList()) {
            out.add(uuid(o));
        }
        return out;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Map<String, Object> classParams(UUID yearId, UUID classId, UUID sectionId) {
        Map<String, Object> params = new HashMap<>();
        params.put("y", yearId);
        params.put("c", classId);
        params.put("sec", sectionId);
        return params;
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(String sql, Map<String, Object> params) {
        Query q = em.createNativeQuery(sql);
        params.forEach(q::setParameter);
        return q.getResultList();
    }

    /** A teacher name column that is empty when no teacher is set. */
    private static String tu(Object o) {
        return o == null ? null : o.toString();
    }

    static UUID uuid(Object o) {
        return o == null ? null : o instanceof UUID u ? u : UUID.fromString(o.toString());
    }

    static LocalDate localDate(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof LocalDate d) {
            return d;
        }
        if (o instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        return LocalDate.parse(o.toString());
    }

    static Instant instant(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Instant i) {
            return i;
        }
        if (o instanceof Timestamp t) {
            return t.toInstant();
        }
        if (o instanceof OffsetDateTime t) {
            return t.toInstant();
        }
        return Instant.parse(o.toString());
    }
}
