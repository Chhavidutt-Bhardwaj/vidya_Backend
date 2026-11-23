package com.vidya.tenantdata.academic.setup.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.common.util.SecurityUtils;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.setup.dto.TimetableCarryOverRequest;
import com.vidya.tenantdata.academic.setup.dto.TimetableCarryOverRequest.TeacherMode;
import com.vidya.tenantdata.academic.setup.dto.TimetableCarryOverResponse;
import com.vidya.tenantdata.timetable.dto.CreateTimetableRequest;
import com.vidya.tenantdata.timetable.dto.TimetableResponse;
import com.vidya.tenantdata.timetable.service.TimetableService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Copies a timetable of an earlier year into a new academic year as a DRAFT.
 *
 * <p>Each slot is mapped to the new year: the section with the same class and code, the class
 * subject of the new year, the same room (if still active), and the period with the same sequence in
 * the chosen bell schedule. Teachers come from this year's subject-teacher assignments (last year's
 * teacher preferred). A slot never gets a teacher who is already busy in that period. Slots that
 * cannot be mapped are skipped and counted by reason. One transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimetableCarryOverService {

    private final AcademicYearRepository academicYearRepository;
    private final TimetableService timetableService;

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    @Transactional(transactionManager = "tenantTransactionManager")
    public TimetableCarryOverResponse carryOver(UUID academicYearId, TimetableCarryOverRequest request) {
        AcademicYear target = academicYearRepository.findByIdAndNotDeleted(academicYearId)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", academicYearId));

        @SuppressWarnings("unchecked")
        List<Object[]> src = em.createNativeQuery("""
                        SELECT t.academic_year_id, t.bell_schedule_id, t.name, t.description, y.start_date
                        FROM timetable t JOIN academic_year y ON y.id = t.academic_year_id
                        WHERE t.id = :id AND t.deleted_at IS NULL
                        """)
                .setParameter("id", request.sourceTimetableId()).getResultList();
        if (src.isEmpty()) {
            throw new ResourceNotFoundException("Timetable", request.sourceTimetableId());
        }
        UUID sourceYear = uuid(src.get(0)[0]);
        if (sourceYear.equals(target.getId())) {
            throw new BusinessException("The source timetable is already in this academic year; use POST /api/v1/timetables/{id}/clone");
        }
        UUID bellSchedule = request.bellScheduleId() != null ? request.bellScheduleId() : uuid(src.get(0)[1]);
        String name = request.name() != null && !request.name().isBlank()
                ? request.name().trim() : src.get(0)[2] + " " + target.getCode();

        TimetableResponse created = timetableService.createTimetable(new CreateTimetableRequest(
                target.getId(), bellSchedule, name, (String) src.get(0)[3],
                request.effectiveFrom() != null ? request.effectiveFrom() : target.getStartDate(),
                request.effectiveTo() != null ? request.effectiveTo() : target.getEndDate()));
        UUID newTimetable = created.id();

        // One row per source slot: why it can't be copied, or its mapped values and teacher candidate.
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT CASE WHEN np.id IS NULL THEN 'PERIOD_NOT_IN_BELL_SCHEDULE'
                                    WHEN c.id IS NULL THEN 'CLASS_INACTIVE'
                                    WHEN e.section_id IS NOT NULL AND ns.id IS NULL THEN 'SECTION_NOT_IN_NEW_YEAR'
                                    WHEN e.subject_id IS NOT NULL AND sub.id IS NULL THEN 'SUBJECT_INACTIVE'
                                    ELSE 'OK' END                              AS reason,
                               np.id, e.day_of_week, e.academic_class_id, ns.id, e.subject_id, csa.id, r.id, e.entry_type,
                               ta.id, ta.teacher_id, CASE WHEN ost.id IS NOT NULL THEN e.teacher_id END
                        FROM timetable_entry e
                        JOIN bell_schedule_period op ON op.id = e.period_id
                        LEFT JOIN bell_schedule_period np ON np.bell_schedule_id = :bs AND np.period_sequence = op.period_sequence
                                                         AND np.deleted_at IS NULL
                        LEFT JOIN academic_class c ON c.id = e.academic_class_id AND c.deleted_at IS NULL AND c.status = 'ACTIVE'
                        LEFT JOIN section os ON os.id = e.section_id
                        LEFT JOIN section ns ON ns.academic_year_id = :tgt AND ns.academic_class_id = e.academic_class_id
                                            AND ns.code = os.code AND ns.deleted_at IS NULL AND ns.status = 'ACTIVE'
                        LEFT JOIN subject sub ON sub.id = e.subject_id AND sub.deleted_at IS NULL AND sub.status = 'ACTIVE'
                        LEFT JOIN room r ON r.id = e.room_id AND r.deleted_at IS NULL AND r.status = 'ACTIVE'
                        LEFT JOIN staff ost ON ost.id = e.teacher_id AND ost.deleted_at IS NULL AND ost.employment_status = 'ACTIVE'
                        LEFT JOIN LATERAL (
                            SELECT x.id FROM class_subject_assignment x
                            WHERE x.academic_year_id = :tgt AND x.academic_class_id = e.academic_class_id AND x.subject_id = e.subject_id
                              AND x.deleted_at IS NULL AND x.status = 'ACTIVE'
                              AND (x.section_id IS NULL OR x.section_id = ns.id)
                            ORDER BY x.section_id NULLS LAST LIMIT 1) csa ON TRUE
                        LEFT JOIN LATERAL (
                            SELECT t.id, t.teacher_id FROM teacher_subject_assignment t
                            JOIN staff st ON st.id = t.teacher_id AND st.deleted_at IS NULL AND st.employment_status = 'ACTIVE'
                            WHERE t.academic_year_id = :tgt AND t.subject_id = e.subject_id AND t.academic_class_id = e.academic_class_id
                              AND (t.section_id IS NULL OR t.section_id = ns.id)
                              AND t.deleted_at IS NULL AND t.status = 'ACTIVE'
                            ORDER BY (t.teacher_id = e.teacher_id) DESC, (t.section_id IS NOT NULL) DESC, t.is_primary DESC
                            LIMIT 1) ta ON TRUE
                        WHERE e.timetable_id = :src AND e.deleted_at IS NULL
                        ORDER BY e.day_of_week, op.period_sequence, e.academic_class_id, os.code
                        """)
                .setParameter("src", request.sourceTimetableId()).setParameter("tgt", target.getId())
                .setParameter("bs", bellSchedule).getResultList();

        boolean withTeachers = request.teachers() != TeacherMode.NONE;
        Map<String, Integer> skipped = new TreeMap<>();
        Set<String> busy = new HashSet<>();
        List<Object[]> inserts = new ArrayList<>();
        int withTeacher = 0;
        int clashes = 0;
        for (Object[] r : rows) {
            String reason = (String) r[0];
            if (!"OK".equals(reason)) {
                skipped.merge(reason, 1, Integer::sum);
                continue;
            }
            UUID period = uuid(r[1]);
            String day = (String) r[2];
            UUID assignment = null;
            UUID teacher = null;
            if (withTeachers) {
                UUID candidateAssignment = uuid(r[9]);
                UUID candidateTeacher = uuid(r[10]);
                if (candidateTeacher == null && r[5] == null) {
                    candidateTeacher = uuid(r[11]); // no subject (assembly, activity): keep last year's active teacher
                }
                if (candidateTeacher != null) {
                    if (busy.add(candidateTeacher + "|" + day + "|" + period)) {
                        teacher = candidateTeacher;
                        assignment = candidateAssignment;
                        withTeacher++;
                    } else {
                        clashes++;
                    }
                }
            }
            inserts.add(new Object[]{CodeGenerator.timetableEntryCode(), newTimetable, target.getId(), period, day,
                    uuid(r[3]), uuid(r[4]), uuid(r[5]), assignment, teacher, uuid(r[6]), uuid(r[7]), r[8]});
        }

        UUID user = SecurityUtils.getCurrentUserId().orElse(null);
        em.flush();
        em.unwrap(Session.class).doWork(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO timetable_entry (entry_code, timetable_id, academic_year_id, period_id, day_of_week,
                        academic_class_id, section_id, subject_id, teacher_assignment_id, teacher_id,
                        class_subject_assignment_id, room_id, entry_type, created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (Object[] v : inserts) {
                    for (int i = 0; i < v.length; i++) {
                        ps.setObject(i + 1, v[i]);
                    }
                    ps.setObject(14, user);
                    ps.setObject(15, user);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });

        List<String> next = new ArrayList<>();
        int without = inserts.size() - withTeacher;
        if (without > 0) {
            next.add(without + " slot(s) have no teacher: add subject-teacher assignments for " + target.getCode()
                    + " or set teachers in the grid.");
        }
        if (!skipped.isEmpty()) {
            next.add("Some slots were not copied (see 'skipped'). Create the missing sections or periods and add them in the grid.");
        }
        next.add("Check conflicts: POST /api/v1/timetables/" + newTimetable + "/validate");
        next.add("Publish: POST /api/v1/timetables/" + newTimetable + "/publish; it becomes ACTIVE when the year starts (POST /api/v1/academic-years/"
                + target.getId() + "/start).");

        log.info("Carried timetable [{}] into [{}]: {} of {} slots, {} with teacher, {} clashes, skipped {}",
                request.sourceTimetableId(), target.getCode(), inserts.size(), rows.size(), withTeacher, clashes, skipped);
        return new TimetableCarryOverResponse(created, rows.size(), inserts.size(), withTeacher, without, clashes, skipped, next);
    }

    private static UUID uuid(Object o) {
        if (o == null) {
            return null;
        }
        return o instanceof UUID u ? u : UUID.fromString(o.toString());
    }
}
