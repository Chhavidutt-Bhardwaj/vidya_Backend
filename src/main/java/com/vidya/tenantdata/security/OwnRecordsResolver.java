package com.vidya.tenantdata.security;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reads, from the current tenant DB, which students / sections a platform login is linked to.
 * Links: {@code tenant_user.master_user_id} → login; {@code student/staff.tenant_user_id} → tenant_user;
 * {@code student_guardian.guardian_user_id} → parent's tenant_user; class-teacher and subject-teacher
 * assignments → sections; active enrollments → students of those sections.
 */
@Component
public class OwnRecordsResolver {

    private static final String ACTIVE_ENROLLMENT = "('ADMITTED', 'ACTIVE')";

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public OwnRecords resolve(UUID platformUserId) {
        if (platformUserId == null) {
            return OwnRecords.NONE;
        }
        Set<UUID> tenantUserIds = uuids(em.createNativeQuery(
                        "SELECT id FROM tenant_user WHERE master_user_id = :uid AND deleted_at IS NULL")
                .setParameter("uid", platformUserId).getResultList());
        if (tenantUserIds.isEmpty()) {
            return OwnRecords.NONE;
        }

        Set<UUID> selfStudents = uuids(em.createNativeQuery(
                        "SELECT id FROM student WHERE tenant_user_id IN (:tu) AND deleted_at IS NULL")
                .setParameter("tu", tenantUserIds).getResultList());

        Set<UUID> children = uuids(em.createNativeQuery(
                        "SELECT g.student_id FROM student_guardian g JOIN student s ON s.id = g.student_id "
                                + "WHERE g.guardian_user_id IN (:tu) AND g.deleted_at IS NULL AND g.status = 'ACTIVE' "
                                + "AND s.deleted_at IS NULL")
                .setParameter("tu", tenantUserIds).getResultList());

        Set<UUID> staff = uuids(em.createNativeQuery(
                        "SELECT id FROM staff WHERE tenant_user_id IN (:tu) AND deleted_at IS NULL")
                .setParameter("tu", tenantUserIds).getResultList());

        Set<UUID> sections = new LinkedHashSet<>();
        Set<UUID> wholeClasses = new LinkedHashSet<>();
        Set<String> subjectClass = new LinkedHashSet<>();
        Set<UUID> taughtStudents = Set.of();
        if (!staff.isEmpty()) {
            sections.addAll(uuids(em.createNativeQuery(
                            "SELECT section_id FROM class_teacher_assignment WHERE staff_id IN (:staff) "
                                    + "AND deleted_at IS NULL AND status = 'ACTIVE' " + current())
                    .setParameter("staff", staff).getResultList()));

            @SuppressWarnings("unchecked")
            List<Object[]> tsa = em.createNativeQuery(
                            "SELECT section_id, academic_class_id, subject_id FROM teacher_subject_assignment "
                                    + "WHERE teacher_id IN (:staff) AND deleted_at IS NULL AND status = 'ACTIVE' " + current())
                    .setParameter("staff", staff).getResultList();
            for (Object[] row : tsa) {
                UUID section = uuid(row[0]);
                UUID clazz = uuid(row[1]);
                if (section != null) {
                    sections.add(section);
                } else if (clazz != null) {
                    wholeClasses.add(clazz);
                }
                if (row[2] != null && clazz != null) {
                    subjectClass.add(uuid(row[2]) + ":" + clazz);
                }
            }

            if (!sections.isEmpty() || !wholeClasses.isEmpty()) {
                taughtStudents = uuids(em.createNativeQuery(
                                "SELECT DISTINCT student_id FROM student_academic_enrollment WHERE deleted_at IS NULL "
                                        + "AND enrollment_status IN " + ACTIVE_ENROLLMENT + " "
                                        + "AND (section_id IN (:sections) OR academic_class_id IN (:classes))")
                        .setParameter("sections", nonEmpty(sections))
                        .setParameter("classes", nonEmpty(wholeClasses))
                        .getResultList());
            }
        }
        Set<UUID> teachingClasses = new LinkedHashSet<>(wholeClasses);
        if (!sections.isEmpty()) {
            teachingClasses.addAll(uuids(em.createNativeQuery(
                            "SELECT DISTINCT academic_class_id FROM section WHERE id IN (:sections)")
                    .setParameter("sections", sections).getResultList()));
        }

        Set<UUID> linkedStudents = new LinkedHashSet<>(selfStudents);
        linkedStudents.addAll(children);
        Set<UUID> studentSections = linkedStudents.isEmpty() ? Set.of() : uuids(em.createNativeQuery(
                        "SELECT DISTINCT section_id FROM student_academic_enrollment WHERE deleted_at IS NULL "
                                + "AND enrollment_status IN " + ACTIVE_ENROLLMENT + " AND student_id IN (:students)")
                .setParameter("students", linkedStudents).getResultList());

        return OwnRecords.builder()
                .tenantUserIds(tenantUserIds)
                .selfStudentIds(selfStudents)
                .childStudentIds(children)
                .staffIds(staff)
                .sectionIds(sections)
                .wholeClassIds(wholeClasses)
                .teachingClassIds(teachingClasses)
                .subjectClassPairs(subjectClass)
                .taughtStudentIds(taughtStudents)
                .studentSectionIds(studentSections)
                .build();
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Optional<UUID> studentIdByCode(String studentCode) {
        return first(em.createNativeQuery("SELECT id FROM student WHERE student_code = :c AND deleted_at IS NULL")
                .setParameter("c", studentCode).getResultList());
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Optional<UUID> studentIdOfAttendance(UUID attendanceId) {
        return first(em.createNativeQuery("SELECT student_id FROM student_attendance WHERE id = :id")
                .setParameter("id", attendanceId).getResultList());
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Optional<UUID> studentIdOfMarkEntry(UUID markEntryId) {
        return first(em.createNativeQuery("SELECT student_id FROM student_mark_entry WHERE id = :id")
                .setParameter("id", markEntryId).getResultList());
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Optional<UUID> classOfSection(UUID sectionId) {
        return first(em.createNativeQuery("SELECT academic_class_id FROM section WHERE id = :id")
                .setParameter("id", sectionId).getResultList());
    }

    /** "subjectId:classId" of an exam subject config, if it exists. */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Optional<String> subjectClassOfExamConfig(UUID configId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT c.subject_id, s.academic_class_id FROM exam_subject_config c "
                                + "JOIN exam_schedule s ON s.id = c.exam_schedule_id WHERE c.id = :id")
                .setParameter("id", configId).getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of(uuid(rows.get(0)[0]) + ":" + uuid(rows.get(0)[1]));
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Optional<UUID> studentIdOfEnrollment(UUID enrollmentId) {
        return first(em.createNativeQuery("SELECT student_id FROM student_academic_enrollment WHERE id = :id")
                .setParameter("id", enrollmentId).getResultList());
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Optional<UUID> studentIdOfEnrollmentCode(String code) {
        return first(em.createNativeQuery("SELECT student_id FROM student_academic_enrollment WHERE enrollment_code = :c")
                .setParameter("c", code).getResultList());
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Optional<UUID> staffIdByCode(String staffCode) {
        return first(em.createNativeQuery("SELECT id FROM staff WHERE staff_code = :c AND deleted_at IS NULL")
                .setParameter("c", staffCode).getResultList());
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Optional<UUID> tenantUserOfStaff(UUID staffId) {
        return first(em.createNativeQuery("SELECT tenant_user_id FROM staff WHERE id = :id")
                .setParameter("id", staffId).getResultList());
    }

    /** [staffId, sectionId, classId] of a class-teacher assignment, by id or code. */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Optional<UUID[]> classTeacherAssignment(UUID id, String code) {
        return triple(em.createNativeQuery("SELECT staff_id, section_id, academic_class_id FROM class_teacher_assignment WHERE "
                        + (id != null ? "id = :k" : "assignment_code = :k"))
                .setParameter("k", id != null ? id : code).getResultList());
    }

    /** [teacherId, sectionId (nullable), classId] of a teacher-subject assignment, by id or code. */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Optional<UUID[]> teacherSubjectAssignment(UUID id, String code) {
        return triple(em.createNativeQuery("SELECT teacher_id, section_id, academic_class_id FROM teacher_subject_assignment WHERE "
                        + (id != null ? "id = :k" : "assignment_code = :k"))
                .setParameter("k", id != null ? id : code).getResultList());
    }

    /** [teacherId, sectionId, classId] of a timetable entry. */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Optional<UUID[]> timetableEntry(UUID id) {
        return triple(em.createNativeQuery("SELECT teacher_id, section_id, academic_class_id FROM timetable_entry WHERE id = :k")
                .setParameter("k", id).getResultList());
    }

    /** [originalTeacherId, substituteTeacherId, sectionId] of a substitution. */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Optional<UUID[]> substitution(UUID id) {
        return triple(em.createNativeQuery("SELECT original_teacher_id, substitute_teacher_id, section_id FROM timetable_substitution WHERE id = :k")
                .setParameter("k", id).getResultList());
    }

    private static Optional<UUID[]> triple(List<?> rows) {
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] r = (Object[]) rows.get(0);
        return Optional.of(new UUID[]{uuid(r[0]), uuid(r[1]), uuid(r[2])});
    }

    private static String current() {
        return "AND (start_date IS NULL OR start_date <= CURRENT_DATE) AND (end_date IS NULL OR end_date >= CURRENT_DATE)";
    }

    /** IN () with an empty list is invalid SQL; use a value that never matches. */
    private static Collection<UUID> nonEmpty(Set<UUID> ids) {
        return ids.isEmpty() ? List.of(new UUID(0L, 0L)) : ids;
    }

    private static Optional<UUID> first(List<?> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(uuid(rows.get(0)));
    }

    private static Set<UUID> uuids(List<?> rows) {
        Set<UUID> out = new LinkedHashSet<>();
        for (Object o : rows) {
            UUID u = uuid(o);
            if (u != null) {
                out.add(u);
            }
        }
        return out;
    }

    private static UUID uuid(Object o) {
        if (o == null) {
            return null;
        }
        return o instanceof UUID u ? u : UUID.fromString(o.toString());
    }
}
