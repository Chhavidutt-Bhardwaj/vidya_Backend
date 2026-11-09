package com.vidya.tenantdata.academic.setup.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.academic.dto.AcademicYearResponse;
import com.vidya.tenantdata.academic.dto.CreateAcademicYearRequest;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.service.AcademicYearService;
import com.vidya.tenantdata.academic.setup.dto.AcademicYearSetupRequest;
import com.vidya.tenantdata.academic.setup.dto.AcademicYearSetupRequest.CopyFrom;
import com.vidya.tenantdata.academic.setup.dto.AcademicYearSetupRequest.Term;
import com.vidya.tenantdata.academic.setup.dto.AcademicYearSetupRequest.WorkingDays;
import com.vidya.tenantdata.academic.setup.dto.AcademicYearSetupResponse;
import com.vidya.tenantdata.academic.setup.dto.SetupStatusResponse;
import com.vidya.tenantdata.academic.setup.dto.SetupStatusResponse.Level;
import com.vidya.tenantdata.academic.setup.dto.SetupStatusResponse.Step;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Academic year setup: one-call creation (year + terms + working days + structure copied from an
 * earlier year) and the setup checklist the school dashboard shows.
 *
 * <p>Copies use set-based SQL in one tenant transaction, so a large school is copied in a few
 * statements and a failure leaves nothing half-created.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcademicSetupService {

    /** Code for rows copied in SQL: PREFIX-XXXXXXXX, same shape as {@link CodeGenerator}. */
    private static final String SQL_CODE = "upper(substr(md5(gen_random_uuid()::text), 1, 8))";

    private final AcademicYearService academicYearService;
    private final AcademicYearRepository academicYearRepository;

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    // =========================================================================================
    // One-call setup
    // =========================================================================================

    @Transactional(transactionManager = "tenantTransactionManager")
    public AcademicYearSetupResponse setUpYear(AcademicYearSetupRequest request) {
        List<String> warnings = new ArrayList<>();
        Map<String, Integer> created = new LinkedHashMap<>();

        AcademicYear source = null;
        CopyFrom copy = request.copyFrom();
        if (copy != null) {
            source = academicYearRepository.findByIdAndNotDeleted(copy.academicYearId())
                    .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", copy.academicYearId()));
            if (!source.getStartDate().isBefore(request.startDate())) {
                throw new BusinessException("copyFrom must be an earlier academic year");
            }
        }
        if (request.terms() != null && !request.terms().isEmpty()) {
            validateTerms(request.terms(), request.startDate(), request.endDate());
        }

        AcademicYearResponse year = academicYearService.createAcademicYear(CreateAcademicYearRequest.builder()
                .code(request.code())
                .name(request.name())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .isCurrent(Boolean.TRUE.equals(request.makeCurrent()))
                .build());
        UUID target = year.getId();
        log.info("Setting up academic year [{}] {}", year.getCode(), source != null ? "from " + source.getCode() : "from scratch");

        // ---- structure copied from the earlier year ---------------------------------------
        if (source != null) {
            UUID src = source.getId();
            if (on(copy.sections(), true)) {
                created.put("sections", copySections(src, target));
            }
            Map<UUID, UUID> curricula = on(copy.curricula(), true) ? copyCurricula(src, target, created) : Map.of();
            if (on(copy.classSubjects(), true)) {
                if (!on(copy.sections(), true)) {
                    warnings.add("Section-specific class subjects were not copied because sections were not copied.");
                }
                created.put("classSubjects", copyClassSubjects(src, target, curricula));
            }
            if (on(copy.classTeachers(), false)) {
                created.put("classTeachers", copyClassTeachers(src, target, request.startDate()));
            }
            if (on(copy.subjectTeachers(), false)) {
                created.put("subjectTeachers", copySubjectTeachers(src, target, curricula, request.startDate(), warnings));
            }
        }

        // ---- terms ----------------------------------------------------------------------------
        if (request.terms() != null && !request.terms().isEmpty()) {
            created.put("terms", insertTerms(target, request.terms()));
        } else if (source != null && on(copy.terms(), true)) {
            created.put("terms", insertTerms(target, shiftedTerms(source, request.startDate(), request.endDate(), warnings)));
        }

        // ---- working days -----------------------------------------------------------------------
        if (request.workingDays() != null) {
            created.put("workingDays", insertWorkingDays(target, request.workingDays()));
        } else if (source != null && on(copy.workingDays(), true)) {
            created.put("workingDays", copyWorkingDays(source.getId(), target));
        }

        if (source != null) {
            warnings.add("Holidays, timetables and bell-schedule changes are not copied: add this year's holidays and build its timetable.");
        }
        em.flush();
        return new AcademicYearSetupResponse(year, created, warnings, status(target));
    }

    // =========================================================================================
    // Checklist
    // =========================================================================================

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public SetupStatusResponse status(UUID academicYearId) {
        AcademicYear y = academicYearRepository.findByIdAndNotDeleted(academicYearId)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", academicYearId));
        UUID id = y.getId();

        long classes = count("SELECT count(*) FROM academic_class WHERE deleted_at IS NULL AND status = 'ACTIVE'", null);
        long sections = count("SELECT count(*) FROM section WHERE academic_year_id = :y AND deleted_at IS NULL AND status = 'ACTIVE'", id);
        long classesWithoutSection = count("SELECT count(*) FROM academic_class c WHERE c.deleted_at IS NULL AND c.status = 'ACTIVE' "
                + "AND NOT EXISTS (SELECT 1 FROM section s WHERE s.academic_class_id = c.id AND s.academic_year_id = :y "
                + "AND s.deleted_at IS NULL AND s.status = 'ACTIVE')", id);
        long subjects = count("SELECT count(*) FROM subject WHERE deleted_at IS NULL AND status = 'ACTIVE'", null);
        long curricula = count("SELECT count(*) FROM curriculum WHERE academic_year_id = :y AND deleted_at IS NULL AND status <> 'ARCHIVED'", id);
        long classSubjects = count("SELECT count(*) FROM class_subject_assignment WHERE academic_year_id = :y AND deleted_at IS NULL AND status = 'ACTIVE'", id);
        long classesWithoutSubjects = count("SELECT count(*) FROM academic_class c WHERE c.deleted_at IS NULL AND c.status = 'ACTIVE' "
                + "AND EXISTS (SELECT 1 FROM section s WHERE s.academic_class_id = c.id AND s.academic_year_id = :y AND s.deleted_at IS NULL) "
                + "AND NOT EXISTS (SELECT 1 FROM class_subject_assignment a WHERE a.academic_class_id = c.id AND a.academic_year_id = :y "
                + "AND a.deleted_at IS NULL AND a.status = 'ACTIVE')", id);
        long sectionsWithoutClassTeacher = count("SELECT count(*) FROM section s WHERE s.academic_year_id = :y AND s.deleted_at IS NULL "
                + "AND s.status = 'ACTIVE' AND NOT EXISTS (SELECT 1 FROM class_teacher_assignment t WHERE t.section_id = s.id "
                + "AND t.academic_year_id = :y AND t.deleted_at IS NULL AND t.status = 'ACTIVE')", id);
        long subjectTeachers = count("SELECT count(*) FROM teacher_subject_assignment WHERE academic_year_id = :y AND deleted_at IS NULL AND status = 'ACTIVE'", id);
        long classSubjectsWithoutTeacher = count("SELECT count(*) FROM class_subject_assignment a WHERE a.academic_year_id = :y "
                + "AND a.deleted_at IS NULL AND a.status = 'ACTIVE' AND NOT EXISTS (SELECT 1 FROM teacher_subject_assignment t "
                + "WHERE t.academic_year_id = :y AND t.subject_id = a.subject_id AND t.academic_class_id = a.academic_class_id "
                + "AND (t.section_id IS NULL OR a.section_id IS NULL OR t.section_id = a.section_id) "
                + "AND t.deleted_at IS NULL AND t.status = 'ACTIVE')", id);
        long sectionsWithoutStream = count("SELECT count(*) FROM section s JOIN academic_class c ON c.id = s.academic_class_id "
                + "WHERE s.academic_year_id = :y AND s.deleted_at IS NULL AND s.status = 'ACTIVE' AND c.has_streams AND s.stream_id IS NULL", id);
        long streamClasses = count("SELECT count(*) FROM academic_class WHERE deleted_at IS NULL AND status = 'ACTIVE' AND has_streams", null);
        long classesWithoutScale = count("SELECT count(*) FROM academic_class c WHERE c.deleted_at IS NULL AND c.status = 'ACTIVE' "
                + "AND NOT EXISTS (SELECT 1 FROM grading_scale g WHERE g.deleted_at IS NULL AND (g.id = c.grading_scale_id OR g.is_default))", null);
        long terms = count("SELECT count(*) FROM exam_term WHERE academic_year_id = :y AND deleted_at IS NULL", id);
        long workingDays = count("SELECT count(*) FROM working_day_config WHERE academic_year_id = :y AND deleted_at IS NULL", id);
        long holidays = count("SELECT count(*) FROM school_holiday WHERE academic_year_id = :y AND deleted_at IS NULL", id);
        long bellSchedules = count("SELECT count(*) FROM bell_schedule WHERE deleted_at IS NULL AND status = 'ACTIVE'", null);
        long timetables = count("SELECT count(*) FROM timetable WHERE academic_year_id = :y AND deleted_at IS NULL AND status IN ('PUBLISHED', 'ACTIVE')", id);
        long students = count("SELECT count(*) FROM student_academic_enrollment WHERE academic_year_id = :y AND deleted_at IS NULL "
                + "AND enrollment_status IN ('ADMITTED', 'ACTIVE')", id);
        long studentsWithoutSection = count("SELECT count(*) FROM student_academic_enrollment WHERE academic_year_id = :y "
                + "AND deleted_at IS NULL AND enrollment_status IN ('ADMITTED', 'ACTIVE') AND section_id IS NULL", id);

        List<Step> steps = new ArrayList<>();
        steps.add(step("CLASSES", "Classes (grades)", Level.REQUIRED, classes > 0, classes, 0,
                "Create the classes the school teaches, with sortOrder in promotion order (Nursery=1, LKG=2, …).",
                "POST /api/v1/academic-classes"));
        steps.add(step("SECTIONS", "Sections for this year", Level.REQUIRED, sections > 0 && classesWithoutSection == 0, sections,
                classesWithoutSection, classesWithoutSection > 0 ? classesWithoutSection + " class(es) have no section this year." : "Add sections (A, B, …) per class.",
                "POST /api/v1/academic-years/{id}/sections/bulk"));
        steps.add(step("SUBJECTS", "Subjects", Level.REQUIRED, subjects > 0, subjects, 0,
                "Create the subject catalogue.", "POST /api/v1/subjects"));
        steps.add(step("CURRICULUM", "Curriculum", Level.RECOMMENDED, curricula > 0, curricula, 0,
                "Group the subjects each class studies this year.", "POST /api/v1/curricula"));
        steps.add(step("CLASS_SUBJECTS", "Subjects per class", Level.REQUIRED, classSubjects > 0 && classesWithoutSubjects == 0,
                classSubjects, classesWithoutSubjects,
                classesWithoutSubjects > 0 ? classesWithoutSubjects + " class(es) have sections but no subjects." : "Assign subjects to classes.",
                "POST /api/v1/academic-years/{id}/class-subjects/from-curriculum"));
        if (streamClasses > 0) {
            steps.add(step("STREAMS", "Streams for XI–XII sections", Level.RECOMMENDED, sectionsWithoutStream == 0, streamClasses, sectionsWithoutStream,
                    sectionsWithoutStream + " section(s) of classes with streams have no stream; their stream subjects are not assigned.",
                    "PUT /api/v1/academic-structure/sections/streams"));
        }
        steps.add(step("GRADING", "Grading scales", Level.RECOMMENDED, classesWithoutScale == 0, classes - classesWithoutScale, classesWithoutScale,
                classesWithoutScale + " class(es) have no grading scale and there is no school default; marks get no grade.",
                "POST /api/v1/exams/grading-scales/presets/{key}"));
        steps.add(step("CLASS_TEACHERS", "Class teachers", Level.RECOMMENDED, sections > 0 && sectionsWithoutClassTeacher == 0,
                sections - sectionsWithoutClassTeacher, sectionsWithoutClassTeacher,
                sectionsWithoutClassTeacher + " section(s) have no class teacher. Teachers only see their own sections.",
                "POST /api/v1/academic-years/{id}/class-teachers/bulk"));
        steps.add(step("SUBJECT_TEACHERS", "Subject teachers", Level.RECOMMENDED, classSubjects > 0 && classSubjectsWithoutTeacher == 0,
                subjectTeachers, classSubjectsWithoutTeacher,
                classSubjectsWithoutTeacher + " class subject(s) have no teacher.", "POST /api/v1/teacher-assignments"));
        steps.add(step("TERMS", "Terms / exam periods", Level.RECOMMENDED, terms > 0, terms, 0,
                "Add terms (e.g. Term 1, Term 2) for exams and report cards.", "POST /api/v1/exams/terms"));
        steps.add(step("WORKING_DAYS", "Working days", Level.RECOMMENDED, workingDays > 0, workingDays, 0,
                "Set working days and the Saturday pattern; Mon–Sat is assumed until then.", "PUT /api/v1/calendar/config?academicYearId=…"));
        steps.add(step("HOLIDAYS", "Holidays and vacations", Level.OPTIONAL, holidays > 0, holidays, 0,
                "Add public holidays and vacations so attendance skips them (see suggestions).", "POST /api/v1/academic-years/{id}/holidays/bulk"));
        steps.add(step("BELL_SCHEDULE", "Bell schedule (periods)", Level.RECOMMENDED, bellSchedules > 0, bellSchedules, 0,
                "Define the school day's periods before building the timetable.", "POST /api/v1/bell-schedules"));
        steps.add(step("TIMETABLE", "Timetable", Level.RECOMMENDED, timetables > 0, timetables, 0,
                "Build and publish this year's timetable, or carry over last year's.", "POST /api/v1/academic-years/{id}/timetable/carry-over"));
        steps.add(step("STUDENTS", "Students enrolled", Level.REQUIRED, students > 0, students, studentsWithoutSection,
                students == 0 ? "Admit new students and promote last year's students." : studentsWithoutSection + " student(s) have no section.",
                "POST /api/v1/academic-years/{previousYearId}/promotion"));

        int requiredTotal = (int) steps.stream().filter(s -> s.level() == Level.REQUIRED).count();
        int requiredDone = (int) steps.stream().filter(s -> s.level() == Level.REQUIRED && s.done()).count();
        int countedTotal = (int) steps.stream().filter(s -> s.level() != Level.OPTIONAL).count();
        int countedDone = (int) steps.stream().filter(s -> s.level() != Level.OPTIONAL && s.done()).count();

        return new SetupStatusResponse(id, y.getCode(), y.getName(), y.getStartDate(), y.getEndDate(), y.getStatus(), y.isCurrent(),
                requiredDone, requiredTotal, countedTotal == 0 ? 0 : countedDone * 100 / countedTotal,
                requiredDone == requiredTotal, steps);
    }

    // =========================================================================================
    // Copy helpers (set-based SQL)
    // =========================================================================================

    private int copySections(UUID src, UUID tgt) {
        return em.createNativeQuery("""
                        INSERT INTO section (academic_year_id, academic_class_id, code, name, capacity, status, stream_id)
                        SELECT :tgt, s.academic_class_id, s.code, s.name, s.capacity, 'ACTIVE',
                               CASE WHEN c.has_streams THEN s.stream_id END
                        FROM section s JOIN academic_class c ON c.id = s.academic_class_id
                        WHERE s.academic_year_id = :src AND s.deleted_at IS NULL AND s.status = 'ACTIVE'
                          AND c.deleted_at IS NULL AND c.status = 'ACTIVE'
                          AND NOT EXISTS (SELECT 1 FROM section t WHERE t.academic_year_id = :tgt
                                          AND t.academic_class_id = s.academic_class_id AND t.code = s.code)
                        """)
                .setParameter("src", src).setParameter("tgt", tgt).executeUpdate();
    }

    /** Copies curricula and their subjects; returns old id → new id. */
    private Map<UUID, UUID> copyCurricula(UUID src, UUID tgt, Map<String, Integer> created) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT id, name, description, status FROM curriculum
                        WHERE academic_year_id = :src AND deleted_at IS NULL AND status IN ('DRAFT', 'ACTIVE')
                        """)
                .setParameter("src", src).getResultList();
        Map<UUID, UUID> map = new LinkedHashMap<>();
        int subjects = 0;
        for (Object[] r : rows) {
            UUID oldId = uuid(r[0]);
            UUID newId = UUID.randomUUID();
            em.createNativeQuery("""
                            INSERT INTO curriculum (id, curriculum_code, academic_year_id, name, description, status)
                            VALUES (:id, :code, :tgt, :name, :description, :status)
                            """)
                    .setParameter("id", newId).setParameter("code", CodeGenerator.curriculumCode()).setParameter("tgt", tgt)
                    .setParameter("name", r[1]).setParameter("description", r[2]).setParameter("status", r[3])
                    .executeUpdate();
            subjects += em.createNativeQuery("""
                            INSERT INTO curriculum_subject (curriculum_id, subject_id, academic_class_id, is_compulsory, display_order, stream_id)
                            SELECT :newId, cs.subject_id, cs.academic_class_id, cs.is_compulsory, cs.display_order, cs.stream_id
                            FROM curriculum_subject cs
                            JOIN subject sub ON sub.id = cs.subject_id AND sub.deleted_at IS NULL AND sub.status = 'ACTIVE'
                            JOIN academic_class c ON c.id = cs.academic_class_id AND c.deleted_at IS NULL AND c.status = 'ACTIVE'
                            WHERE cs.curriculum_id = :oldId AND cs.deleted_at IS NULL
                            """)
                    .setParameter("newId", newId).setParameter("oldId", oldId).executeUpdate();
            map.put(oldId, newId);
        }
        created.put("curricula", map.size());
        created.put("curriculumSubjects", subjects);
        return map;
    }

    /**
     * Class / section subjects. Section rows map to the new year's section with the same class and
     * code; rows whose section was not copied are skipped. Curriculum links follow the copied curricula.
     */
    private int copyClassSubjects(UUID src, UUID tgt, Map<UUID, UUID> curricula) {
        int total = 0;
        List<UUID[]> pairs = new ArrayList<>();
        pairs.add(new UUID[]{null, null});
        curricula.forEach((o, n) -> pairs.add(new UUID[]{o, n}));
        for (UUID[] pair : pairs) {
            // Rows whose curriculum was not copied keep no curriculum link.
            String curriculumFilter = pair[0] == null
                    ? "(a.curriculum_id IS NULL OR a.curriculum_id NOT IN (:copied))"
                    : "a.curriculum_id = :oldCurriculum";
            var q = em.createNativeQuery("""
                            INSERT INTO class_subject_assignment (assignment_code, academic_year_id, academic_class_id, section_id,
                                subject_id, curriculum_id, is_compulsory, display_order, periods_per_week, status)
                            SELECT 'CSA-' || %s, :tgt, a.academic_class_id, ns.id, a.subject_id, %s,
                                   a.is_compulsory, a.display_order, a.periods_per_week, 'ACTIVE'
                            FROM class_subject_assignment a
                            JOIN subject sub ON sub.id = a.subject_id AND sub.deleted_at IS NULL AND sub.status = 'ACTIVE'
                            JOIN academic_class c ON c.id = a.academic_class_id AND c.deleted_at IS NULL AND c.status = 'ACTIVE'
                            LEFT JOIN section os ON os.id = a.section_id
                            LEFT JOIN section ns ON ns.academic_year_id = :tgt AND ns.academic_class_id = a.academic_class_id
                                                AND ns.code = os.code AND ns.deleted_at IS NULL
                            WHERE a.academic_year_id = :src AND a.deleted_at IS NULL AND a.status = 'ACTIVE'
                              AND (a.section_id IS NULL OR ns.id IS NOT NULL)
                              AND %s
                              AND NOT EXISTS (SELECT 1 FROM class_subject_assignment x WHERE x.academic_year_id = :tgt
                                    AND x.academic_class_id = a.academic_class_id AND x.subject_id = a.subject_id
                                    AND x.deleted_at IS NULL
                                    AND COALESCE(x.section_id, '00000000-0000-0000-0000-000000000000'::uuid)
                                      = COALESCE(ns.id, '00000000-0000-0000-0000-000000000000'::uuid))
                            """.formatted(SQL_CODE, pair[1] == null ? "NULL" : ":newCurriculum", curriculumFilter))
                    .setParameter("src", src).setParameter("tgt", tgt);
            if (pair[0] != null) {
                q.setParameter("oldCurriculum", pair[0]).setParameter("newCurriculum", pair[1]);
            } else {
                q.setParameter("copied", curricula.isEmpty() ? List.of(new UUID(0L, 0L)) : curricula.keySet());
            }
            total += q.executeUpdate();
        }
        return total;
    }

    private int copyClassTeachers(UUID src, UUID tgt, LocalDate start) {
        return em.createNativeQuery("""
                        INSERT INTO class_teacher_assignment (assignment_code, staff_id, academic_year_id, academic_class_id,
                            section_id, start_date, status)
                        SELECT 'CTA-' || %s, a.staff_id, :tgt, a.academic_class_id, ns.id, :start, 'ACTIVE'
                        FROM class_teacher_assignment a
                        JOIN staff st ON st.id = a.staff_id AND st.deleted_at IS NULL
                        JOIN section os ON os.id = a.section_id
                        JOIN section ns ON ns.academic_year_id = :tgt AND ns.academic_class_id = a.academic_class_id
                                       AND ns.code = os.code AND ns.deleted_at IS NULL
                        WHERE a.academic_year_id = :src AND a.deleted_at IS NULL AND a.status IN ('ACTIVE', 'COMPLETED')
                          AND NOT EXISTS (SELECT 1 FROM class_teacher_assignment x WHERE x.academic_year_id = :tgt
                                          AND x.section_id = ns.id AND x.deleted_at IS NULL AND x.status = 'ACTIVE')
                          AND a.id = (SELECT a2.id FROM class_teacher_assignment a2 WHERE a2.academic_year_id = :src
                                      AND a2.section_id = a.section_id AND a2.deleted_at IS NULL AND a2.status IN ('ACTIVE', 'COMPLETED')
                                      ORDER BY (a2.status = 'ACTIVE') DESC, a2.start_date DESC NULLS LAST, a2.created_at DESC LIMIT 1)
                        """.formatted(SQL_CODE))
                .setParameter("src", src).setParameter("tgt", tgt).setParameter("start", start).executeUpdate();
    }

    private int copySubjectTeachers(UUID src, UUID tgt, Map<UUID, UUID> curricula, LocalDate start, List<String> warnings) {
        int copied = em.createNativeQuery("""
                        INSERT INTO teacher_subject_assignment (assignment_code, teacher_id, subject_id, academic_year_id,
                            academic_class_id, section_id, curriculum_id, is_primary, start_date, status)
                        SELECT 'TSA-' || %s, a.teacher_id, a.subject_id, :tgt, a.academic_class_id, ns.id, NULL,
                               a.is_primary, :start, 'ACTIVE'
                        FROM teacher_subject_assignment a
                        JOIN staff st ON st.id = a.teacher_id AND st.deleted_at IS NULL
                        JOIN subject sub ON sub.id = a.subject_id AND sub.deleted_at IS NULL AND sub.status = 'ACTIVE'
                        LEFT JOIN section os ON os.id = a.section_id
                        LEFT JOIN section ns ON ns.academic_year_id = :tgt AND ns.academic_class_id = a.academic_class_id
                                            AND ns.code = os.code AND ns.deleted_at IS NULL
                        WHERE a.academic_year_id = :src AND a.deleted_at IS NULL AND a.status = 'ACTIVE'
                          AND (a.section_id IS NULL OR ns.id IS NOT NULL)
                          AND NOT EXISTS (SELECT 1 FROM teacher_subject_assignment x WHERE x.academic_year_id = :tgt
                                AND x.academic_class_id = a.academic_class_id AND x.subject_id = a.subject_id
                                AND x.deleted_at IS NULL AND x.status IN ('DRAFT', 'ACTIVE')
                                AND (x.teacher_id = a.teacher_id OR (x.is_primary AND a.is_primary))
                                AND COALESCE(x.section_id, '00000000-0000-0000-0000-000000000000'::uuid)
                                  = COALESCE(ns.id, '00000000-0000-0000-0000-000000000000'::uuid))
                        """.formatted(SQL_CODE))
                .setParameter("src", src).setParameter("tgt", tgt).setParameter("start", start).executeUpdate();
        if (copied > 0) {
            for (Map.Entry<UUID, UUID> c : curricula.entrySet()) {
                em.createNativeQuery("""
                                UPDATE teacher_subject_assignment t SET curriculum_id = :newCurriculum
                                WHERE t.academic_year_id = :tgt AND t.curriculum_id IS NULL
                                  AND EXISTS (SELECT 1 FROM teacher_subject_assignment o WHERE o.academic_year_id = :src
                                      AND o.curriculum_id = :oldCurriculum AND o.teacher_id = t.teacher_id
                                      AND o.subject_id = t.subject_id AND o.academic_class_id = t.academic_class_id
                                      AND o.deleted_at IS NULL AND o.status = 'ACTIVE')
                                """)
                        .setParameter("src", src).setParameter("tgt", tgt)
                        .setParameter("oldCurriculum", c.getKey()).setParameter("newCurriculum", c.getValue())
                        .executeUpdate();
            }
        }
        warnings.add("Subject teachers were copied as-is. Check them against this year's staffing before publishing the timetable.");
        return copied;
    }

    private int copyWorkingDays(UUID src, UUID tgt) {
        return em.createNativeQuery("""
                        INSERT INTO working_day_config (academic_year_id, monday, tuesday, wednesday, thursday, friday,
                            saturday, sunday, saturday_pattern)
                        SELECT :tgt, monday, tuesday, wednesday, thursday, friday, saturday, sunday, saturday_pattern
                        FROM working_day_config
                        WHERE academic_year_id = :src AND deleted_at IS NULL
                          AND NOT EXISTS (SELECT 1 FROM working_day_config x WHERE x.academic_year_id = :tgt AND x.deleted_at IS NULL)
                        LIMIT 1
                        """)
                .setParameter("src", src).setParameter("tgt", tgt).executeUpdate();
    }

    private int insertWorkingDays(UUID tgt, WorkingDays d) {
        return em.createNativeQuery("""
                        INSERT INTO working_day_config (academic_year_id, monday, tuesday, wednesday, thursday, friday,
                            saturday, sunday, saturday_pattern)
                        VALUES (:tgt, :mon, :tue, :wed, :thu, :fri, :sat, :sun, :pattern)
                        """)
                .setParameter("tgt", tgt)
                .setParameter("mon", on(d.monday(), true)).setParameter("tue", on(d.tuesday(), true))
                .setParameter("wed", on(d.wednesday(), true)).setParameter("thu", on(d.thursday(), true))
                .setParameter("fri", on(d.friday(), true)).setParameter("sat", on(d.saturday(), true))
                .setParameter("sun", on(d.sunday(), false))
                .setParameter("pattern", d.saturdayPattern() != null ? d.saturdayPattern() : "ALL")
                .executeUpdate();
    }

    private int insertTerms(UUID tgt, List<Term> terms) {
        int n = 0;
        for (Term t : terms) {
            n += em.createNativeQuery("""
                            INSERT INTO exam_term (term_code, academic_year_id, name, start_date, end_date, weightage_percentage, status)
                            VALUES (:code, :tgt, :name, :start, :end, :weight, 'PLANNED')
                            """)
                    .setParameter("code", CodeGenerator.examTermCode()).setParameter("tgt", tgt)
                    .setParameter("name", t.name().trim()).setParameter("start", t.startDate()).setParameter("end", t.endDate())
                    .setParameter("weight", t.weightagePercentage() != null ? t.weightagePercentage() : BigDecimal.ZERO)
                    .executeUpdate();
        }
        return n;
    }

    /** Last year's terms moved by the distance between the two start dates, clipped to the new year. */
    private List<Term> shiftedTerms(AcademicYear source, LocalDate start, LocalDate end, List<String> warnings) {
        long shift = ChronoUnit.DAYS.between(source.getStartDate(), start);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT name, start_date, end_date, weightage_percentage FROM exam_term
                        WHERE academic_year_id = :src AND deleted_at IS NULL ORDER BY start_date
                        """)
                .setParameter("src", source.getId()).getResultList();
        List<Term> out = new ArrayList<>();
        for (Object[] r : rows) {
            LocalDate s = date(r[1]).plusDays(shift);
            LocalDate e = date(r[2]).plusDays(shift);
            if (s.isAfter(end) || e.isBefore(start)) {
                warnings.add("Term '" + r[0] + "' falls outside the new year and was not copied.");
                continue;
            }
            out.add(new Term((String) r[0], s.isBefore(start) ? start : s, e.isAfter(end) ? end : e,
                    r[3] instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(r[3]))));
        }
        if (!out.isEmpty()) {
            warnings.add("Term dates were shifted by " + shift + " day(s) from " + source.getCode() + "; review them.");
        }
        return out;
    }

    // =========================================================================================

    static void validateTerms(List<Term> terms, LocalDate yearStart, LocalDate yearEnd) {
        Set<String> names = new HashSet<>();
        BigDecimal weight = BigDecimal.ZERO;
        for (Term t : terms) {
            if (t.endDate().isBefore(t.startDate())) {
                throw new BusinessException("Term '" + t.name() + "' ends before it starts");
            }
            if (t.startDate().isBefore(yearStart) || t.endDate().isAfter(yearEnd)) {
                throw new BusinessException("Term '" + t.name() + "' must lie within the academic year (" + yearStart + " to " + yearEnd + ")");
            }
            if (!names.add(t.name().trim().toLowerCase(Locale.ROOT))) {
                throw new BusinessException("Duplicate term name '" + t.name() + "'");
            }
            if (t.weightagePercentage() != null) {
                weight = weight.add(t.weightagePercentage());
            }
        }
        List<Term> sorted = terms.stream().sorted(Comparator.comparing(Term::startDate)).toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (!sorted.get(i).startDate().isAfter(sorted.get(i - 1).endDate())) {
                throw new BusinessException("Terms '" + sorted.get(i - 1).name() + "' and '" + sorted.get(i).name() + "' overlap");
            }
        }
        if (weight.compareTo(new BigDecimal("100")) > 0) {
            throw new BusinessException("Term weightages add up to " + weight + "%, more than 100%");
        }
    }

    private long count(String sql, UUID yearId) {
        var q = em.createNativeQuery(sql);
        if (yearId != null) {
            q.setParameter("y", yearId);
        }
        return ((Number) q.getSingleResult()).longValue();
    }

    private static Step step(String key, String label, Level level, boolean done, long count, long pending, String hint, String api) {
        return new Step(key, label, level, done, count, pending, done ? "" : hint, api);
    }

    private static boolean on(Boolean flag, boolean dflt) {
        return flag == null ? dflt : flag;
    }

    private static UUID uuid(Object o) {
        return o instanceof UUID u ? u : UUID.fromString(o.toString());
    }

    private static LocalDate date(Object o) {
        if (o instanceof LocalDate d) {
            return d;
        }
        if (o instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        return LocalDate.parse(o.toString());
    }
}
