package com.vidya.tenantdata.academic.setup.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.service.AcademicYearService;
import com.vidya.tenantdata.academic.setup.dto.SetupStatusResponse;
import com.vidya.tenantdata.academic.setup.dto.SetupStatusResponse.Level;
import com.vidya.tenantdata.academic.setup.dto.YearSetupActions.BulkResult;
import com.vidya.tenantdata.academic.setup.dto.YearSetupActions.BulkSectionsRequest;
import com.vidya.tenantdata.academic.setup.dto.YearSetupActions.ClassTeachersRequest;
import com.vidya.tenantdata.academic.setup.dto.YearSetupActions.FromCurriculumRequest;
import com.vidya.tenantdata.academic.setup.dto.YearSetupActions.StartYearRequest;
import com.vidya.tenantdata.academic.setup.dto.YearSetupActions.StartYearResponse;
import com.vidya.tenantdata.assignment.dto.CreateClassTeacherAssignmentRequest;
import com.vidya.tenantdata.assignment.service.ClassTeacherAssignmentService;
import com.vidya.tenantdata.timetable.dto.UpdateTimetableStatusRequest;
import com.vidya.tenantdata.timetable.enums.TimetableStatus;
import com.vidya.tenantdata.timetable.service.TimetableService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bulk actions that finish an academic year's setup quickly (sections for many classes, class
 * subjects from the curriculum, class teachers for many sections) and the final "start year" step.
 * Each action is one transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YearSetupActionService {

    private final AcademicYearRepository academicYearRepository;
    private final AcademicYearService academicYearService;
    private final AcademicSetupService setupService;
    private final ClassTeacherAssignmentService classTeacherAssignmentService;
    private final TimetableService timetableService;

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    /** Same section codes for many classes; existing (class, code) pairs are left alone. */
    @Transactional(transactionManager = "tenantTransactionManager")
    public BulkResult bulkSections(UUID yearId, BulkSectionsRequest request) {
        AcademicYear year = editableYear(yearId);
        boolean allClasses = request.academicClassIds() == null || request.academicClassIds().isEmpty();
        var classQuery = em.createNativeQuery("SELECT id FROM academic_class WHERE deleted_at IS NULL AND status = 'ACTIVE'"
                + (allClasses ? "" : " AND id IN (:ids)"));
        if (!allClasses) {
            classQuery.setParameter("ids", request.academicClassIds());
        }
        @SuppressWarnings("unchecked")
        List<Object> classes = classQuery.getResultList();
        if (request.academicClassIds() != null && !request.academicClassIds().isEmpty()
                && classes.size() != new HashSet<>(request.academicClassIds()).size()) {
            throw new BusinessException("Some academicClassIds are not active classes");
        }
        Set<String> codes = request.sectionCodes().stream().map(c -> c.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        int created = 0;
        int requested = 0;
        for (Object c : classes) {
            for (String code : codes) {
                requested++;
                created += em.createNativeQuery("""
                                INSERT INTO section (academic_year_id, academic_class_id, code, name, capacity, status)
                                SELECT :y, :c, :code, :code, :cap, 'ACTIVE'
                                WHERE NOT EXISTS (SELECT 1 FROM section WHERE academic_year_id = :y AND academic_class_id = :c AND code = :code)
                                """)
                        .setParameter("y", year.getId()).setParameter("c", c).setParameter("code", code)
                        .setParameter("cap", request.capacity())
                        .executeUpdate();
            }
        }
        log.info("Bulk sections for [{}]: {} created of {}", year.getCode(), created, requested);
        return new BulkResult(created, requested - created, Map.of("classes", classes.size(), "codes", codes));
    }

    /** Class-level class subjects for every subject of the curriculum (compulsory flag and order kept). */
    @Transactional(transactionManager = "tenantTransactionManager")
    public BulkResult classSubjectsFromCurriculum(UUID yearId, FromCurriculumRequest request) {
        AcademicYear year = editableYear(yearId);
        UUID curriculumId = request != null ? request.curriculumId() : null;
        long candidates = ((Number) em.createNativeQuery("""
                        SELECT count(*) FROM curriculum_subject cs JOIN curriculum cu ON cu.id = cs.curriculum_id
                        WHERE cu.academic_year_id = :y AND cu.deleted_at IS NULL AND cu.status IN ('DRAFT', 'ACTIVE')
                          AND cs.deleted_at IS NULL AND (CAST(:cid AS uuid) IS NULL OR cu.id = :cid)
                        """)
                .setParameter("y", year.getId()).setParameter("cid", curriculumId).getSingleResult()).longValue();
        if (candidates == 0) {
            throw new BusinessException("No curriculum subjects found for " + year.getCode()
                    + (curriculumId != null ? " in curriculum " + curriculumId : "") + ". Add subjects to the curriculum first.");
        }
        int created = em.createNativeQuery("""
                        INSERT INTO class_subject_assignment (assignment_code, academic_year_id, academic_class_id, section_id,
                            subject_id, curriculum_id, is_compulsory, display_order, status)
                        SELECT DISTINCT ON (cs.academic_class_id, cs.subject_id)
                               'CSA-' || upper(substr(md5(gen_random_uuid()::text), 1, 8)), :y, cs.academic_class_id, NULL,
                               cs.subject_id, cu.id, cs.is_compulsory, cs.display_order, 'ACTIVE'
                        FROM curriculum_subject cs
                        JOIN curriculum cu ON cu.id = cs.curriculum_id
                        JOIN subject s ON s.id = cs.subject_id AND s.deleted_at IS NULL AND s.status = 'ACTIVE'
                        JOIN academic_class c ON c.id = cs.academic_class_id AND c.deleted_at IS NULL AND c.status = 'ACTIVE'
                        WHERE cu.academic_year_id = :y AND cu.deleted_at IS NULL AND cu.status IN ('DRAFT', 'ACTIVE')
                          AND cs.deleted_at IS NULL AND (CAST(:cid AS uuid) IS NULL OR cu.id = :cid)
                          AND NOT EXISTS (SELECT 1 FROM class_subject_assignment x WHERE x.academic_year_id = :y
                                AND x.academic_class_id = cs.academic_class_id AND x.subject_id = cs.subject_id
                                AND x.section_id IS NULL AND x.deleted_at IS NULL)
                          AND cs.stream_id IS NULL
                        ORDER BY cs.academic_class_id, cs.subject_id, (cu.status = 'ACTIVE') DESC
                        """)
                .setParameter("y", year.getId()).setParameter("cid", curriculumId).executeUpdate();
        // Stream subjects (Physics for Science) become section-level subjects of that stream's sections.
        int sectionLevel = em.createNativeQuery("""
                        INSERT INTO class_subject_assignment (assignment_code, academic_year_id, academic_class_id, section_id,
                            subject_id, curriculum_id, is_compulsory, display_order, status)
                        SELECT DISTINCT ON (sec.id, cs.subject_id)
                               'CSA-' || upper(substr(md5(gen_random_uuid()::text), 1, 8)), :y, cs.academic_class_id, sec.id,
                               cs.subject_id, cu.id, cs.is_compulsory, cs.display_order, 'ACTIVE'
                        FROM curriculum_subject cs
                        JOIN curriculum cu ON cu.id = cs.curriculum_id
                        JOIN subject s ON s.id = cs.subject_id AND s.deleted_at IS NULL AND s.status = 'ACTIVE'
                        JOIN academic_class c ON c.id = cs.academic_class_id AND c.deleted_at IS NULL AND c.status = 'ACTIVE'
                        JOIN section sec ON sec.academic_year_id = :y AND sec.academic_class_id = cs.academic_class_id
                             AND sec.stream_id = cs.stream_id AND sec.deleted_at IS NULL AND sec.status = 'ACTIVE'
                        WHERE cu.academic_year_id = :y AND cu.deleted_at IS NULL AND cu.status IN ('DRAFT', 'ACTIVE')
                          AND cs.deleted_at IS NULL AND cs.stream_id IS NOT NULL AND (CAST(:cid AS uuid) IS NULL OR cu.id = :cid)
                          AND NOT EXISTS (SELECT 1 FROM class_subject_assignment x WHERE x.academic_year_id = :y
                                AND x.academic_class_id = cs.academic_class_id AND x.subject_id = cs.subject_id
                                AND (x.section_id = sec.id OR x.section_id IS NULL) AND x.deleted_at IS NULL)
                        ORDER BY sec.id, cs.subject_id, (cu.status = 'ACTIVE') DESC
                        """)
                .setParameter("y", year.getId()).setParameter("cid", curriculumId).executeUpdate();
        log.info("Class subjects from curriculum for [{}]: {} class-level + {} stream section-level created ({} curriculum rows)",
                year.getCode(), created, sectionLevel, candidates);
        // skipped = class-level rows that already existed; stream rows count per section, so they are reported separately
        long classLevelCandidates = ((Number) em.createNativeQuery("""
                        SELECT count(DISTINCT (cs.academic_class_id, cs.subject_id)) FROM curriculum_subject cs JOIN curriculum cu ON cu.id = cs.curriculum_id
                        WHERE cu.academic_year_id = :y AND cu.deleted_at IS NULL AND cu.status IN ('DRAFT', 'ACTIVE')
                          AND cs.deleted_at IS NULL AND cs.stream_id IS NULL AND (CAST(:cid AS uuid) IS NULL OR cu.id = :cid)
                        """).setParameter("y", year.getId()).setParameter("cid", curriculumId).getSingleResult()).longValue();
        return new BulkResult(created + sectionLevel, (int) Math.max(0, classLevelCandidates - created),
                Map.of("classLevel", created, "streamSectionLevel", sectionLevel));
    }

    /**
     * Class teachers for many sections. Uses the regular assignment rules (active staff, one class
     * teacher per section); any invalid row cancels the whole request and is named in the error.
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public BulkResult bulkClassTeachers(UUID yearId, ClassTeachersRequest request) {
        AcademicYear year = editableYear(yearId);
        List<UUID> sectionIds = request.assignments().stream().map(ClassTeachersRequest.Row::sectionId).toList();
        if (new HashSet<>(sectionIds).size() != sectionIds.size()) {
            throw new BusinessException("A section appears more than once");
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT s.id, s.academic_class_id, s.name,
                               EXISTS (SELECT 1 FROM class_teacher_assignment t WHERE t.section_id = s.id AND t.academic_year_id = :y
                                       AND t.deleted_at IS NULL AND t.status = 'ACTIVE')
                        FROM section s WHERE s.id IN (:ids) AND s.academic_year_id = :y AND s.deleted_at IS NULL
                        """)
                .setParameter("ids", sectionIds).setParameter("y", year.getId()).getResultList();
        Map<UUID, Object[]> byId = new LinkedHashMap<>();
        for (Object[] r : rows) {
            byId.put(r[0] instanceof UUID u ? u : UUID.fromString(r[0].toString()), r);
        }
        List<String> errors = new ArrayList<>();
        int skipped = 0;
        for (ClassTeachersRequest.Row row : request.assignments()) {
            Object[] s = byId.get(row.sectionId());
            if (s == null) {
                errors.add("Section " + row.sectionId() + " is not a section of " + year.getCode());
            } else if (Boolean.TRUE.equals(s[3])) {
                skipped++;
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(String.join("; ", errors));
        }
        int created = 0;
        for (ClassTeachersRequest.Row row : request.assignments()) {
            Object[] s = byId.get(row.sectionId());
            if (Boolean.TRUE.equals(s[3])) {
                continue; // already has a class teacher: change it with POST /class-teacher-assignments/{id}/replace
            }
            UUID classId = s[1] instanceof UUID u ? u : UUID.fromString(s[1].toString());
            try {
                classTeacherAssignmentService.createAssignment(CreateClassTeacherAssignmentRequest.builder()
                        .staffId(row.staffId()).academicYearId(year.getId()).academicClassId(classId)
                        .sectionId(row.sectionId()).startDate(year.getStartDate()).build());
            } catch (RuntimeException e) {
                throw new BusinessException("Section " + s[2] + ": " + e.getMessage());
            }
            created++;
        }
        return new BulkResult(created, skipped, Map.of("note", skipped > 0
                ? skipped + " section(s) already had a class teacher and were left unchanged" : ""));
    }

    /**
     * Starts the year: checks the required setup steps, activates the year, makes it current, and
     * activates its published timetable.
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public StartYearResponse start(UUID yearId, StartYearRequest request) {
        AcademicYear year = academicYearRepository.findByIdAndNotDeleted(yearId)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", yearId));
        if (year.getStatus() == AcademicYearStatus.CLOSED || year.getStatus() == AcademicYearStatus.CANCELLED) {
            throw new BusinessException("A " + year.getStatus() + " academic year cannot be started");
        }
        List<String> warnings = new ArrayList<>();
        SetupStatusResponse before = setupService.status(yearId);
        List<String> missing = before.steps().stream()
                .filter(s -> s.level() == Level.REQUIRED && !s.done()).map(SetupStatusResponse.Step::label).toList();
        if (!missing.isEmpty()) {
            if (!Boolean.TRUE.equals(request != null ? request.force() : null)) {
                throw new BusinessException("Setup is not complete: " + String.join(", ", missing)
                        + ". Finish these steps or start with force=true.");
            }
            warnings.add("Started with incomplete setup: " + String.join(", ", missing));
        }

        if (year.getStatus() == AcademicYearStatus.DRAFT) {
            academicYearService.activateAcademicYear(yearId);
        }
        boolean makeCurrent = request == null || request.makeCurrent() == null || request.makeCurrent();
        if (makeCurrent && !year.isCurrent()) {
            academicYearService.setCurrentAcademicYear(yearId);
        }

        UUID timetableId = request != null ? request.timetableId() : null;
        if (timetableId == null) {
            @SuppressWarnings("unchecked")
            List<Object> published = em.createNativeQuery(
                            "SELECT id FROM timetable WHERE academic_year_id = :y AND deleted_at IS NULL AND status = 'PUBLISHED'")
                    .setParameter("y", yearId).getResultList();
            long active = ((Number) em.createNativeQuery(
                            "SELECT count(*) FROM timetable WHERE academic_year_id = :y AND deleted_at IS NULL AND status = 'ACTIVE'")
                    .setParameter("y", yearId).getSingleResult()).longValue();
            if (published.size() == 1 && active == 0) {
                timetableId = published.get(0) instanceof UUID u ? u : UUID.fromString(published.get(0).toString());
            } else if (published.size() > 1) {
                warnings.add(published.size() + " published timetables: pass timetableId to choose which one becomes active.");
            } else if (active == 0) {
                warnings.add("No published timetable yet: build one (or carry over last year's) and publish it.");
            }
        }
        if (timetableId != null) {
            timetableService.updateTimetableStatus(timetableId, new UpdateTimetableStatusRequest(TimetableStatus.ACTIVE, true));
        }
        log.info("Started academic year [{}] (current={}, timetable={})", year.getCode(), makeCurrent, timetableId);
        return new StartYearResponse(setupService.status(yearId), timetableId, warnings);
    }

    private AcademicYear editableYear(UUID id) {
        AcademicYear year = academicYearRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", id));
        if (year.getStatus() == AcademicYearStatus.CLOSED || year.getStatus() == AcademicYearStatus.CANCELLED) {
            throw new BusinessException("Academic year " + year.getCode() + " is " + year.getStatus() + " and cannot be changed");
        }
        return year;
    }
}
