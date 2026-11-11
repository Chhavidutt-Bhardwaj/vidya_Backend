package com.vidya.tenantdata.academic.config.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.AllocationResult;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.AllocationRow;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.ClassNode;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.ClassTeacherRow;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.ClassTeachersRequest;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.ConfigOverview;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.Issue;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.SectionNode;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.SubjectSlot;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.SubjectTeacherRow;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.SubjectTeachersRequest;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.TeacherLoad;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.TeacherRef;
import com.vidya.tenantdata.academic.config.enums.SyllabusStatus;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.assignment.entity.ClassTeacherAssignment;
import com.vidya.tenantdata.assignment.entity.TeacherSubjectAssignment;
import com.vidya.tenantdata.assignment.repository.ClassTeacherAssignmentRepository;
import com.vidya.tenantdata.assignment.repository.TeacherSubjectAssignmentRepository;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.entity.StaffSubject;
import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.enums.StaffType;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.staff.repository.StaffSubjectRepository;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.enums.SubjectStatus;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import com.vidya.tenantdata.user.account.TenantUserAccountService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.vidya.tenantdata.academic.config.service.TeachingScope.PERSON_NAME;
import static com.vidya.tenantdata.academic.config.service.TeachingScope.uuid;

/**
 * Who teaches what: class teachers and subject teachers for a whole year in one call each, the allocation
 * overview (class → section → subject → teacher, with problems to fix) and teacher workload.
 *
 * <p>Single assignments keep working through the older class-teacher and teacher-assignment APIs; this
 * service writes the same rows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeacherAllocationService {

    private static final String STAFF_JOIN = " JOIN staff st ON st.id = t.%s JOIN tenant_user u ON u.id = st.tenant_user_id "
            + "LEFT JOIN tenant_user_profile p ON p.user_id = u.id ";

    private final AcademicYearRepository yearRepository;
    private final AcademicClassRepository classRepository;
    private final SectionRepository sectionRepository;
    private final SubjectRepository subjectRepository;
    private final StaffRepository staffRepository;
    private final StaffSubjectRepository staffSubjectRepository;
    private final ClassTeacherAssignmentRepository classTeacherRepository;
    private final TeacherSubjectAssignmentRepository subjectTeacherRepository;

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    // =========================================================================================
    // Class teachers
    // =========================================================================================

    private record PreparedClassTeacher(Section section, Staff staff, ClassTeacherAssignment current) {
    }

    /**
     * Sets the class teacher of many sections: assigns, changes (the old assignment is completed, history kept)
     * or removes. All or nothing; with any invalid row nothing is saved.
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public AllocationResult setClassTeachers(ClassTeachersRequest request) {
        AcademicYear year = editableYear(request.academicYearId());
        LocalDate effective = effectiveDate(year, request.effectiveDate());
        boolean dryRun = Boolean.TRUE.equals(request.dryRun());

        List<AllocationRow> rows = new ArrayList<>();
        List<PreparedClassTeacher> prepared = new ArrayList<>();
        Set<UUID> seenSections = new HashSet<>();
        Map<UUID, Integer> sectionsPerStaff = new HashMap<>();
        boolean valid = true;
        for (int i = 0; i < request.assignments().size(); i++) {
            ClassTeacherRow r = request.assignments().get(i);
            List<String> warnings = new ArrayList<>();
            String label = "Section " + r.sectionId();
            String error = null;
            Section section = sectionRepository.findByIdAndNotDeleted(r.sectionId()).orElse(null);
            Staff staff = null;
            if (section == null || !section.getAcademicYear().getId().equals(year.getId())) {
                error = "not a section of " + year.getCode();
            } else {
                label = sectionLabel(section);
                if (!seenSections.add(section.getId())) {
                    error = "section listed twice";
                } else if (r.staffId() != null) {
                    staff = staffRepository.findByIdAndNotDeleted(r.staffId()).orElse(null);
                    error = teacherError(staff);
                }
            }
            ClassTeacherAssignment current = null;
            if (error == null) {
                current = classTeacherRepository.findActiveBySectionAndAcademicYear(year.getId(), section.getId()).orElse(null);
                if (staff != null) {
                    UUID sectionId = section.getId();
                    long elsewhere = classTeacherRepository.findActiveByStaffId(staff.getId(), year.getId()).stream()
                            .filter(a -> !a.getSection().getId().equals(sectionId)).count();
                    int inRequest = sectionsPerStaff.merge(staff.getId(), 1, Integer::sum);
                    if (elsewhere > 0 || inRequest > 1) {
                        warnings.add(name(staff) + " is class teacher of more than one section");
                    }
                }
            }
            valid &= error == null;
            rows.add(new AllocationRow(i, label, null, error, warnings));
            prepared.add(error == null ? new PreparedClassTeacher(section, staff, current) : null);
        }
        if (!valid) {
            return new AllocationResult(dryRun, false, 0, 0, 0, 0, rows);
        }

        int[] counts = new int[4];
        for (int i = 0; i < prepared.size(); i++) {
            PreparedClassTeacher p = prepared.get(i);
            String action;
            if (p.current() != null && p.staff() != null && p.current().getStaff().getId().equals(p.staff().getId())) {
                action = "UNCHANGED";
            } else if (p.staff() == null) {
                action = p.current() == null ? "UNCHANGED" : "REMOVED";
            } else {
                action = p.current() == null ? "ASSIGNED" : "CHANGED";
            }
            if (!dryRun && !"UNCHANGED".equals(action)) {
                if (p.current() != null) {
                    p.current().complete(endDate(p.current().getStartDate(), effective));
                    classTeacherRepository.saveAndFlush(p.current()); // free the one-active-per-section slot before the insert
                }
                if (p.staff() != null) {
                    classTeacherRepository.save(new ClassTeacherAssignment(
                            code(CodeGenerator::classTeacherAssignmentCode, classTeacherRepository::existsByAssignmentCode),
                            p.staff(), year, p.section().getAcademicClass(), p.section(), effective, null));
                }
            }
            count(counts, action);
            rows.set(i, new AllocationRow(i, rows.get(i).label(), action, null, rows.get(i).warnings()));
        }
        log.info("Class teachers for [{}]{}: {} assigned, {} changed, {} removed", year.getCode(), dryRun ? " (dry run)" : "",
                counts[0], counts[1], counts[2]);
        return new AllocationResult(dryRun, true, counts[0], counts[1], counts[2], counts[3], rows);
    }

    // =========================================================================================
    // Subject teachers
    // =========================================================================================

    private record PreparedSubjectTeacher(AcademicClass clazz, Section section, Subject subject, Staff teacher,
                                          TeacherSubjectAssignment primary, TeacherSubjectAssignment own) {
    }

    /**
     * Sets the (primary) teacher of many class × section × subject combinations. A row without a section is the
     * teacher for every section of the class. Changing a teacher completes the old assignment and keeps it as
     * history. All or nothing.
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public AllocationResult setSubjectTeachers(SubjectTeachersRequest request) {
        AcademicYear year = editableYear(request.academicYearId());
        LocalDate effective = effectiveDate(year, request.effectiveDate());
        boolean dryRun = Boolean.TRUE.equals(request.dryRun());

        Map<UUID, Optional<AcademicClass>> classes = new HashMap<>();
        Map<UUID, Optional<Subject>> subjects = new HashMap<>();
        Map<UUID, Optional<Staff>> staffCache = new HashMap<>();
        Map<UUID, List<StaffSubject>> skills = new HashMap<>();
        Set<String> seen = new HashSet<>();
        List<AllocationRow> rows = new ArrayList<>();
        List<PreparedSubjectTeacher> prepared = new ArrayList<>();
        boolean valid = true;
        for (int i = 0; i < request.assignments().size(); i++) {
            SubjectTeacherRow r = request.assignments().get(i);
            List<String> warnings = new ArrayList<>();
            String error = null;
            AcademicClass clazz = classes.computeIfAbsent(r.academicClassId(), classRepository::findByIdAndNotDeleted).orElse(null);
            Subject subject = subjects.computeIfAbsent(r.subjectId(), subjectRepository::findByIdAndNotDeleted).orElse(null);
            Section section = r.sectionId() == null ? null : sectionRepository.findByIdAndNotDeleted(r.sectionId()).orElse(null);
            Staff teacher = r.teacherId() == null ? null : staffCache.computeIfAbsent(r.teacherId(), staffRepository::findByIdAndNotDeleted).orElse(null);
            String label = (clazz != null ? clazz.getName() : "Class " + r.academicClassId())
                    + (section != null ? " " + section.getName() : r.sectionId() != null ? " (section?)" : "")
                    + " – " + (subject != null ? subject.getName() : "subject " + r.subjectId());

            if (clazz == null) {
                error = "class not found";
            } else if (subject == null || subject.getStatus() != SubjectStatus.ACTIVE) {
                error = "subject not found or inactive";
            } else if (r.sectionId() != null && (section == null || !section.getAcademicYear().getId().equals(year.getId())
                    || !section.getAcademicClass().getId().equals(clazz.getId()))) {
                error = "section is not a section of this class in " + year.getCode();
            } else if (!seen.add(r.academicClassId() + ":" + r.sectionId() + ":" + r.subjectId())) {
                error = "listed twice";
            } else if (r.teacherId() != null && teacherError(teacher) != null) {
                error = teacherError(teacher);
            } else if (r.teacherId() != null && !isClassSubject(year.getId(), clazz.getId(), r.sectionId(), subject.getId())) {
                error = subject.getName() + " is not a subject of " + clazz.getName() + (section != null ? " " + section.getName() : "")
                        + " in " + year.getCode() + "; add it to the class subjects first";
            }

            TeacherSubjectAssignment primary = null;
            TeacherSubjectAssignment own = null;
            if (error == null) {
                UUID subjectId = subject.getId();
                List<TeacherSubjectAssignment> active = subjectTeacherRepository
                        .findActiveByClassAndSection(year.getId(), clazz.getId(), r.sectionId()).stream()
                        .filter(a -> a.getSubject().getId().equals(subjectId)).toList();
                primary = active.stream().filter(TeacherSubjectAssignment::isPrimary).findFirst().orElse(null);
                if (teacher != null) {
                    UUID teacherId = teacher.getId();
                    own = active.stream().filter(a -> a.getTeacher().getId().equals(teacherId)).findFirst().orElse(null);
                    if (own == null && subjectTeacherRepository.existsActiveDuplicate(teacherId, subjectId, year.getId(), clazz.getId(), r.sectionId(), null)) {
                        error = name(teacher) + " already has a draft assignment here; activate or delete it first";
                    }
                    List<StaffSubject> can = skills.computeIfAbsent(teacherId, staffSubjectRepository::findByStaffId);
                    if (!can.isEmpty() && can.stream().noneMatch(s -> s.getSubjectId().equals(subjectId) && s.covers(clazz.getGradeLevel()))) {
                        warnings.add(subject.getName() + " for " + clazz.getName() + " is not among " + name(teacher) + "'s teaching subjects");
                    }
                }
            }
            valid &= error == null;
            rows.add(new AllocationRow(i, label, null, error, warnings));
            prepared.add(error == null ? new PreparedSubjectTeacher(clazz, section, subject, teacher, primary, own) : null);
        }
        if (!valid) {
            return new AllocationResult(dryRun, false, 0, 0, 0, 0, rows);
        }

        int[] counts = new int[4];
        Map<UUID, List<Integer>> rowsOfTeacher = new HashMap<>();
        for (int i = 0; i < prepared.size(); i++) {
            PreparedSubjectTeacher p = prepared.get(i);
            String action;
            if (p.primary() != null && p.teacher() != null && p.primary().getTeacher().getId().equals(p.teacher().getId())) {
                action = "UNCHANGED";
            } else if (p.teacher() == null) {
                action = p.primary() == null ? "UNCHANGED" : "REMOVED";
            } else {
                action = p.primary() == null ? "ASSIGNED" : "CHANGED";
            }
            if (!"UNCHANGED".equals(action)) {
                if (p.primary() != null) {
                    p.primary().complete(endDate(p.primary().getStartDate(), effective));
                    subjectTeacherRepository.saveAndFlush(p.primary()); // free the one-primary-per-subject slot before the insert
                }
                if (p.teacher() != null) {
                    if (p.own() != null) {
                        p.own().setPrimary(true); // was a co-teacher of the same subject
                        subjectTeacherRepository.save(p.own());
                    } else {
                        subjectTeacherRepository.save(new TeacherSubjectAssignment(
                                code(CodeGenerator::teacherAssignmentCode, subjectTeacherRepository::existsByAssignmentCode),
                                p.teacher(), p.subject(), year, p.clazz(), p.section(), null, true, effective, null));
                    }
                    rowsOfTeacher.computeIfAbsent(p.teacher().getId(), k -> new ArrayList<>()).add(i);
                }
            }
            count(counts, action);
            rows.set(i, new AllocationRow(i, rows.get(i).label(), action, null, rows.get(i).warnings()));
        }

        // Workload after the change (also in a dry run: the changes are applied, measured and rolled back).
        if (!rowsOfTeacher.isEmpty()) {
            em.flush();
            Map<UUID, long[]> load = plannedLoad(year.getId());
            rowsOfTeacher.forEach((teacherId, indexes) -> {
                Staff t = staffCache.get(teacherId).orElseThrow();
                long planned = load.getOrDefault(teacherId, new long[2])[0];
                if (t.getMaxPeriodsPerWeek() != null && planned > t.getMaxPeriodsPerWeek()) {
                    for (int i : indexes) {
                        rows.get(i).warnings().add(name(t) + " would have " + planned + " periods a week (limit " + t.getMaxPeriodsPerWeek() + ")");
                    }
                }
            });
        }
        if (dryRun) {
            rollbackPreview();
        }
        log.info("Subject teachers for [{}]{}: {} assigned, {} changed, {} removed", year.getCode(), dryRun ? " (dry run)" : "",
                counts[0], counts[1], counts[2]);
        return new AllocationResult(dryRun, true, counts[0], counts[1], counts[2], counts[3], rows);
    }

    // =========================================================================================
    // Overview
    // =========================================================================================

    private record Assigned(UUID assignmentId, TeacherRef teacher, boolean active) {
    }

    private record ClassSubject(UUID id, UUID classId, UUID sectionId, UUID subjectId, String code, String name, boolean compulsory,
                                Integer periods) {
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public ConfigOverview overview(UUID academicYearId) {
        AcademicYear year = academicYearId != null
                ? yearRepository.findByIdAndNotDeleted(academicYearId).orElseThrow(() -> new ResourceNotFoundException("AcademicYear", academicYearId))
                : currentYear();
        UUID y = year.getId();

        Map<UUID, Assigned> classTeachers = new HashMap<>();
        for (Object[] r : rows("SELECT t.section_id, t.id, st.id, st.staff_code, " + PERSON_NAME + ", st.employment_status "
                + "FROM class_teacher_assignment t" + STAFF_JOIN.formatted("staff_id")
                + "WHERE t.academic_year_id = :y AND t.deleted_at IS NULL AND t.status = 'ACTIVE'", y)) {
            classTeachers.put(uuid(r[0]), new Assigned(uuid(r[1]), new TeacherRef(uuid(r[2]), (String) r[3], (String) r[4]), "ACTIVE".equals(r[5])));
        }

        // subject teachers: section-specific first, then whole-class; primary before co-teachers
        Map<String, Assigned> sectionTeachers = new HashMap<>();
        Map<String, Assigned> classWideTeachers = new HashMap<>();
        for (Object[] r : rows("SELECT t.id, t.academic_class_id, t.section_id, t.subject_id, st.id, st.staff_code, " + PERSON_NAME
                + ", st.employment_status FROM teacher_subject_assignment t" + STAFF_JOIN.formatted("teacher_id")
                + "WHERE t.academic_year_id = :y AND t.deleted_at IS NULL AND t.status = 'ACTIVE' ORDER BY t.is_primary DESC, t.created_at", y)) {
            Assigned a = new Assigned(uuid(r[0]), new TeacherRef(uuid(r[4]), (String) r[5], (String) r[6]), "ACTIVE".equals(r[7]));
            if (r[2] != null) {
                sectionTeachers.putIfAbsent(uuid(r[2]) + ":" + uuid(r[3]), a);
            } else {
                classWideTeachers.putIfAbsent(uuid(r[1]) + ":" + uuid(r[3]), a);
            }
        }

        List<ClassSubject> classSubjects = new ArrayList<>();
        for (Object[] r : rows("SELECT a.id, a.academic_class_id, a.section_id, a.subject_id, sub.subject_code, sub.name, a.is_compulsory, "
                + "a.periods_per_week FROM class_subject_assignment a JOIN subject sub ON sub.id = a.subject_id "
                + "WHERE a.academic_year_id = :y AND a.deleted_at IS NULL AND a.status = 'ACTIVE' ORDER BY a.display_order, sub.name", y)) {
            classSubjects.add(new ClassSubject(uuid(r[0]), uuid(r[1]), uuid(r[2]), uuid(r[3]), (String) r[4], (String) r[5],
                    Boolean.TRUE.equals(r[6]), r[7] == null ? null : ((Number) r[7]).intValue()));
        }

        Map<String, Object[]> syllabi = new HashMap<>(); // class:subject → id, status, topics
        int published = 0;
        int emptySyllabi = 0;
        for (Object[] r : rows("SELECT sy.id, sy.academic_class_id, sy.subject_id, sy.status, "
                + "(SELECT count(*) FROM syllabus_topic tp WHERE tp.syllabus_id = sy.id) FROM syllabus sy "
                + "WHERE sy.academic_year_id = :y AND sy.deleted_at IS NULL AND sy.status <> 'ARCHIVED'", y)) {
            syllabi.put(uuid(r[1]) + ":" + uuid(r[2]), r);
            published += "PUBLISHED".equals(r[3]) ? 1 : 0;
            emptySyllabi += ((Number) r[4]).intValue() == 0 ? 1 : 0;
        }
        Map<String, Integer> completed = new HashMap<>(); // syllabus:section → completed topics
        for (Object[] r : rows("SELECT tp.syllabus_id, pr.section_id, count(*) FROM syllabus_topic_progress pr "
                + "JOIN syllabus_topic tp ON tp.id = pr.topic_id JOIN syllabus sy ON sy.id = tp.syllabus_id "
                + "WHERE sy.academic_year_id = :y AND sy.deleted_at IS NULL AND pr.status = 'COMPLETED' GROUP BY tp.syllabus_id, pr.section_id", y)) {
            completed.put(uuid(r[0]) + ":" + uuid(r[1]), ((Number) r[2]).intValue());
        }

        Map<UUID, ClassNode> classes = new LinkedHashMap<>();
        List<String> noClassTeacher = new ArrayList<>();
        List<String> noSubjectTeacher = new ArrayList<>();
        List<String> noSubjects = new ArrayList<>();
        List<String> notTeachingOwnSection = new ArrayList<>();
        Set<String> inactive = new java.util.LinkedHashSet<>();
        Set<String> expected = new java.util.LinkedHashSet<>(); // class:subject taught this year
        Map<String, String> expectedLabel = new HashMap<>();
        Map<UUID, List<String>> sectionsOfClassTeacher = new LinkedHashMap<>();
        Map<UUID, String> teacherNames = new HashMap<>();
        int sections = 0;
        int withClassTeacher = 0;
        int slots = 0;
        int slotsWithTeacher = 0;
        long noPeriods = 0;

        for (Object[] r : rows("SELECT s.id, s.code, s.name, c.id, c.code, c.name, c.grade_level FROM section s "
                + "JOIN academic_class c ON c.id = s.academic_class_id WHERE s.academic_year_id = :y AND s.deleted_at IS NULL "
                + "AND s.status = 'ACTIVE' AND c.deleted_at IS NULL ORDER BY c.grade_level NULLS LAST, c.sort_order, c.name, s.code", y)) {
            UUID sectionId = uuid(r[0]);
            UUID classId = uuid(r[3]);
            String sectionLabel = r[5] + " " + r[2];
            sections++;

            Map<UUID, ClassSubject> subjectsOfSection = new LinkedHashMap<>();
            for (ClassSubject cs : classSubjects) {
                if (cs.classId().equals(classId) && (cs.sectionId() == null || cs.sectionId().equals(sectionId))) {
                    // a section-level row replaces the class-level row of the same subject
                    subjectsOfSection.merge(cs.subjectId(), cs, (a, b) -> b.sectionId() != null ? b : a);
                }
            }
            List<SubjectSlot> subjectSlots = new ArrayList<>();
            boolean classTeacherTeaches = false;
            Assigned ct = classTeachers.get(sectionId);
            for (ClassSubject cs : subjectsOfSection.values()) {
                slots++;
                noPeriods += cs.periods() == null ? 1 : 0;
                Assigned own = sectionTeachers.get(sectionId + ":" + cs.subjectId());
                Assigned t = own != null ? own : classWideTeachers.get(classId + ":" + cs.subjectId());
                if (t == null) {
                    noSubjectTeacher.add(sectionLabel + " – " + cs.name());
                } else {
                    slotsWithTeacher++;
                    if (!t.active()) {
                        inactive.add(t.teacher().name() + " (" + sectionLabel + " – " + cs.name() + ")");
                    }
                    classTeacherTeaches |= ct != null && ct.teacher().staffId().equals(t.teacher().staffId());
                }
                String key = classId + ":" + cs.subjectId();
                expected.add(key);
                expectedLabel.put(key, r[5] + " – " + cs.name());
                Object[] sy = syllabi.get(key);
                UUID syllabusId = sy != null ? uuid(sy[0]) : null;
                subjectSlots.add(new SubjectSlot(cs.id(), cs.subjectId(), cs.code(), cs.name(), cs.compulsory(), cs.periods(),
                        cs.sectionId() != null, t != null ? t.assignmentId() : null, t != null ? t.teacher() : null,
                        t != null && own == null, syllabusId, sy != null ? SyllabusStatus.valueOf((String) sy[3]) : null,
                        sy != null ? ((Number) sy[4]).intValue() : 0, syllabusId != null ? completed.getOrDefault(syllabusId + ":" + sectionId, 0) : 0));
            }
            if (subjectSlots.isEmpty()) {
                noSubjects.add(sectionLabel);
            }
            if (ct == null) {
                noClassTeacher.add(sectionLabel);
            } else {
                withClassTeacher++;
                if (!ct.active()) {
                    inactive.add(ct.teacher().name() + " (class teacher of " + sectionLabel + ")");
                }
                if (!subjectSlots.isEmpty() && !classTeacherTeaches) {
                    notTeachingOwnSection.add(ct.teacher().name() + " (" + sectionLabel + ")");
                }
                sectionsOfClassTeacher.computeIfAbsent(ct.teacher().staffId(), k -> new ArrayList<>()).add(sectionLabel);
                teacherNames.put(ct.teacher().staffId(), ct.teacher().name());
            }
            classes.computeIfAbsent(classId, k -> new ClassNode(classId, (String) r[4], (String) r[5],
                            r[6] == null ? null : ((Number) r[6]).intValue(), new ArrayList<>()))
                    .sections().add(new SectionNode(sectionId, (String) r[1], (String) r[2], ct != null ? ct.assignmentId() : null,
                            ct != null ? ct.teacher() : null, subjectSlots));
        }

        Object[] plans = rows("SELECT count(*), count(*) FILTER (WHERE status = 'SUBMITTED') FROM lesson_plan "
                + "WHERE academic_year_id = :y AND deleted_at IS NULL", y).stream().findFirst().orElse(new Object[]{0, 0});
        long lessonPlans = ((Number) plans[0]).longValue();
        long toReview = ((Number) plans[1]).longValue();

        List<Issue> issues = new ArrayList<>();
        issue(issues, "ERROR", "TEACHER_NOT_ACTIVE", new ArrayList<>(inactive), "assignment(s) belong to staff who left or are suspended; choose another teacher");
        issue(issues, "WARNING", "NO_CLASS_SUBJECTS", noSubjects, "section(s) have no subjects; create class subjects from the curriculum");
        issue(issues, "WARNING", "NO_CLASS_TEACHER", noClassTeacher, "section(s) have no class teacher");
        issue(issues, "WARNING", "NO_SUBJECT_TEACHER", noSubjectTeacher, "subject(s) have no teacher");
        List<String> overloaded = new ArrayList<>();
        for (TeacherLoad l : workload(y)) {
            if (l.overloaded()) {
                overloaded.add(l.name() + " (" + Math.max(l.plannedPeriods(), l.timetablePeriods()) + "/" + l.maxPeriodsPerWeek() + ")");
            }
        }
        issue(issues, "WARNING", "TEACHER_OVERLOADED", overloaded, "teacher(s) have more periods a week than their limit");
        List<String> many = new ArrayList<>();
        sectionsOfClassTeacher.forEach((staffId, list) -> {
            if (list.size() > 1) {
                many.add(teacherNames.get(staffId) + " (" + String.join(", ", list) + ")");
            }
        });
        issue(issues, "INFO", "CLASS_TEACHER_OF_MANY", many, "teacher(s) are class teacher of more than one section");
        issue(issues, "INFO", "CLASS_TEACHER_NOT_TEACHING", notTeachingOwnSection, "class teacher(s) teach no subject in their own section");
        if (noPeriods > 0) {
            issues.add(new Issue("INFO", "NO_PERIODS_PER_WEEK", noPeriods,
                    noPeriods + " subject(s) have no periods per week; teacher workload and syllabus pacing cannot be checked", List.of()));
        }
        List<String> noSyllabus = expected.stream().filter(k -> !syllabi.containsKey(k)).map(expectedLabel::get).toList();
        issue(issues, "INFO", "NO_SYLLABUS", noSyllabus, "class subject(s) have no syllabus");
        List<String> drafts = syllabi.entrySet().stream().filter(e -> "DRAFT".equals(e.getValue()[3]) && expected.contains(e.getKey()))
                .map(e -> expectedLabel.get(e.getKey())).toList();
        issue(issues, "INFO", "SYLLABUS_NOT_PUBLISHED", drafts, "syllabus/syllabi are still drafts; teachers follow published syllabi");
        if (emptySyllabi > 0) {
            issues.add(new Issue("INFO", "SYLLABUS_WITHOUT_TOPICS", emptySyllabi, emptySyllabi + " syllabus/syllabi have no topics; coverage cannot be tracked", List.of()));
        }
        if (toReview > 0) {
            issues.add(new Issue("INFO", "LESSON_PLANS_TO_REVIEW", toReview, toReview + " lesson plan(s) are waiting for review", List.of()));
        }

        return new ConfigOverview(y, year.getCode(), sections, withClassTeacher, slots, slotsWithTeacher, expected.size(), syllabi.size(),
                published, lessonPlans, toReview, new ArrayList<>(classes.values()), issues);
    }

    /** Every active teacher with what they teach this year and how many periods a week that is. */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<TeacherLoad> workload(UUID academicYearId) {
        UUID y = academicYearId != null ? academicYearId : currentYear().getId();
        Map<UUID, long[]> planned = plannedLoad(y);
        Map<UUID, Long> timetable = new HashMap<>();
        for (Object[] r : rows("SELECT e.teacher_id, count(*) FROM timetable_entry e JOIN timetable t ON t.id = e.timetable_id "
                + "WHERE t.academic_year_id = :y AND t.status = 'ACTIVE' AND t.deleted_at IS NULL AND e.deleted_at IS NULL "
                + "AND e.teacher_id IS NOT NULL GROUP BY e.teacher_id", y)) {
            timetable.put(uuid(r[0]), ((Number) r[1]).longValue());
        }
        Map<UUID, Integer> classTeacherOf = new HashMap<>();
        for (Object[] r : rows("SELECT staff_id, count(*) FROM class_teacher_assignment WHERE academic_year_id = :y "
                + "AND deleted_at IS NULL AND status = 'ACTIVE' GROUP BY staff_id", y)) {
            classTeacherOf.put(uuid(r[0]), ((Number) r[1]).intValue());
        }
        Map<UUID, List<String>> teaches = new HashMap<>();
        for (Object[] r : rows("SELECT t.teacher_id, sub.name, c.name, sec.name FROM teacher_subject_assignment t "
                + "JOIN subject sub ON sub.id = t.subject_id JOIN academic_class c ON c.id = t.academic_class_id "
                + "LEFT JOIN section sec ON sec.id = t.section_id WHERE t.academic_year_id = :y AND t.deleted_at IS NULL "
                + "AND t.status = 'ACTIVE' ORDER BY c.grade_level NULLS LAST, c.sort_order, sec.name, sub.name", y)) {
            teaches.computeIfAbsent(uuid(r[0]), k -> new ArrayList<>()).add(r[1] + " – " + r[2] + (r[3] != null ? " " + r[3] : " (all sections)"));
        }
        List<TeacherLoad> out = new ArrayList<>();
        for (Object[] r : rows("SELECT st.id, st.staff_code, " + PERSON_NAME + ", st.designation, st.max_periods_per_week FROM staff st "
                + "JOIN tenant_user u ON u.id = st.tenant_user_id LEFT JOIN tenant_user_profile p ON p.user_id = u.id "
                + "WHERE st.deleted_at IS NULL AND st.employment_status = 'ACTIVE' AND st.staff_type = 'TEACHER' ORDER BY 3", null)) {
            UUID id = uuid(r[0]);
            long[] load = planned.getOrDefault(id, new long[2]);
            long inTimetable = timetable.getOrDefault(id, 0L);
            Integer max = r[4] == null ? null : ((Number) r[4]).intValue();
            List<String> list = teaches.getOrDefault(id, List.of());
            out.add(new TeacherLoad(id, (String) r[1], (String) r[2], (String) r[3], classTeacherOf.getOrDefault(id, 0), list.size(),
                    (int) load[1], load[0], inTimetable, max, max != null && Math.max(load[0], inTimetable) > max, list));
        }
        return out;
    }

    // =========================================================================================
    // helpers
    // =========================================================================================

    /**
     * teacher → [periods per week, sections taught], from the periods of the class subjects they are assigned to.
     * A whole-class assignment counts every section that has no section-specific primary teacher of its own.
     */
    private Map<UUID, long[]> plannedLoad(UUID yearId) {
        Map<UUID, long[]> out = new HashMap<>();
        for (Object[] r : rows("""
                SELECT t.teacher_id,
                       COALESCE(sum(COALESCE((SELECT a.periods_per_week FROM class_subject_assignment a
                             WHERE a.academic_year_id = :y AND a.academic_class_id = t.academic_class_id AND a.subject_id = t.subject_id
                               AND a.deleted_at IS NULL AND a.status = 'ACTIVE' AND (a.section_id = s.id OR a.section_id IS NULL)
                             ORDER BY a.section_id NULLS LAST LIMIT 1), 0)), 0),
                       count(DISTINCT s.id)
                FROM teacher_subject_assignment t
                JOIN section s ON s.academic_year_id = :y AND s.academic_class_id = t.academic_class_id AND s.deleted_at IS NULL
                     AND s.status = 'ACTIVE' AND (t.section_id IS NULL OR t.section_id = s.id)
                WHERE t.academic_year_id = :y AND t.deleted_at IS NULL AND t.status = 'ACTIVE'
                  AND (t.section_id IS NOT NULL OR NOT EXISTS (SELECT 1 FROM teacher_subject_assignment o
                        WHERE o.academic_year_id = :y AND o.academic_class_id = t.academic_class_id AND o.subject_id = t.subject_id
                          AND o.section_id = s.id AND o.is_primary AND o.deleted_at IS NULL AND o.status = 'ACTIVE'))
                GROUP BY t.teacher_id
                """, yearId)) {
            out.put(uuid(r[0]), new long[]{((Number) r[1]).longValue(), ((Number) r[2]).longValue()});
        }
        return out;
    }

    private boolean isClassSubject(UUID yearId, UUID classId, UUID sectionId, UUID subjectId) {
        return ((Number) em.createNativeQuery("SELECT count(*) FROM class_subject_assignment WHERE academic_year_id = :y "
                        + "AND academic_class_id = :c AND subject_id = :s AND deleted_at IS NULL AND status = 'ACTIVE' "
                        + "AND (CAST(:sec AS uuid) IS NULL OR section_id IS NULL OR section_id = :sec)")
                .setParameter("y", yearId).setParameter("c", classId).setParameter("s", subjectId).setParameter("sec", sectionId)
                .getSingleResult()).longValue() > 0;
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(String sql, UUID yearId) {
        var q = em.createNativeQuery(sql);
        if (yearId != null) {
            q.setParameter("y", yearId);
        }
        return q.getResultList();
    }

    private AcademicYear currentYear() {
        List<Object[]> r = rows("SELECT id, code FROM academic_year WHERE is_current AND deleted_at IS NULL LIMIT 1", null);
        if (r.isEmpty()) {
            throw new BusinessException("There is no current academic year; pass academicYearId");
        }
        UUID id = uuid(r.get(0)[0]);
        return yearRepository.findByIdAndNotDeleted(id).orElseThrow(() -> new ResourceNotFoundException("AcademicYear", id));
    }

    private AcademicYear editableYear(UUID id) {
        AcademicYear year = yearRepository.findByIdAndNotDeleted(id).orElseThrow(() -> new ResourceNotFoundException("AcademicYear", id));
        if (year.getStatus() == AcademicYearStatus.CLOSED || year.getStatus() == AcademicYearStatus.CANCELLED) {
            throw new BusinessException("Academic year " + year.getCode() + " is " + year.getStatus() + " and cannot be changed");
        }
        return year;
    }

    /** Default: today, or the first day of a year that has not started yet. */
    private static LocalDate effectiveDate(AcademicYear year, LocalDate requested) {
        LocalDate d = requested != null ? requested : LocalDate.now();
        if (requested != null && (d.isBefore(year.getStartDate()) || d.isAfter(year.getEndDate()))) {
            throw new BusinessException("effectiveDate must be inside " + year.getCode() + " (" + year.getStartDate() + " – " + year.getEndDate() + ")");
        }
        if (d.isBefore(year.getStartDate())) {
            return year.getStartDate();
        }
        return d.isAfter(year.getEndDate()) ? year.getEndDate() : d;
    }

    /** The old assignment ends the day before the new one starts, never before it began. */
    private static LocalDate endDate(LocalDate start, LocalDate effective) {
        LocalDate end = effective.minusDays(1);
        return start != null && end.isBefore(start) ? start : end;
    }

    private static String teacherError(Staff staff) {
        if (staff == null) {
            return "teacher not found";
        }
        if (staff.getStaffType() != StaffType.TEACHER) {
            return name(staff) + " is not a teacher (" + staff.getStaffType() + ")";
        }
        if (staff.getEmploymentStatus() != EmploymentStatus.ACTIVE) {
            return name(staff) + " is " + staff.getEmploymentStatus();
        }
        return null;
    }

    private static String name(Staff s) {
        return s.getTenantUser() != null ? TenantUserAccountService.name(s.getTenantUser()) : s.getStaffCode();
    }

    private static String sectionLabel(Section s) {
        return s.getAcademicClass().getName() + " " + s.getName();
    }

    private static void count(int[] counts, String action) {
        switch (action) {
            case "ASSIGNED" -> counts[0]++;
            case "CHANGED" -> counts[1]++;
            case "REMOVED" -> counts[2]++;
            default -> counts[3]++;
        }
    }

    private static void issue(List<Issue> issues, String level, String code, List<String> items, String message) {
        if (!items.isEmpty()) {
            issues.add(new Issue(level, code, items.size(), items.size() + " " + message, items.stream().limit(5).toList()));
        }
    }

    private static String code(Supplier<String> generator, Predicate<String> exists) {
        for (int i = 0; i < 6; i++) {
            String c = generator.get();
            if (!exists.test(c)) {
                return c;
            }
        }
        throw new BusinessException("Could not generate a unique code; try again");
    }

    private static void rollbackPreview() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (NoTransactionException ignored) {
            // no transaction to roll back (unit tests)
        }
    }
}
