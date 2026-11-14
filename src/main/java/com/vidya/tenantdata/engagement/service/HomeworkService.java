package com.vidya.tenantdata.engagement.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.academic.config.service.TeachingScope;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.HomeworkRequest;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.HomeworkResponse;
import com.vidya.tenantdata.engagement.entity.Homework;
import com.vidya.tenantdata.engagement.enums.HomeworkStatus;
import com.vidya.tenantdata.engagement.repository.HomeworkRepository;
import com.vidya.tenantdata.engagement.service.DailyReportQueries.EnrolledStudent;
import com.vidya.tenantdata.engagement.service.EngagementAccess.Place;
import com.vidya.tenantdata.security.OwnRecords;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Homework. A subject teacher sets it for a class (every section) or one section of a subject they teach;
 * users with STUDENT scope ALL may set it for any teacher. Families read it through their child.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeworkService {

    private final HomeworkRepository repository;
    private final SubjectRepository subjectRepository;
    private final StaffRepository staffRepository;
    private final DailyReportQueries queries;
    private final EngagementAccess access;
    private final Clock clock;

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    // ── read ─────────────────────────────────────────────────────────────────

    /**
     * Staff list, newest first. Without full scope: homework the user set, or set for a section / class they teach.
     *
     * @param from / to filter on the assigned date
     */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<HomeworkResponse> list(UUID academicYearId, UUID academicClassId, UUID sectionId, UUID subjectId, UUID teacherId,
                                       LocalDate from, LocalDate to, Integer limit) {
        StringBuilder sql = new StringBuilder("SELECT h.* FROM homework h WHERE h.deleted_at IS NULL");
        Map<String, Object> params = new HashMap<>();
        filter(sql, params, "h.academic_year_id = :y", "y", academicYearId);
        filter(sql, params, "h.academic_class_id = :c", "c", academicClassId);
        filter(sql, params, "(h.section_id = :sec OR h.section_id IS NULL)", "sec", sectionId);
        filter(sql, params, "h.subject_id = :s", "s", subjectId);
        filter(sql, params, "h.teacher_id = :t", "t", teacherId);
        filter(sql, params, "h.assigned_on >= :from", "from", from);
        filter(sql, params, "h.assigned_on <= :to", "to", to);
        if (!access.all()) {
            OwnRecords own = access.own();
            if (own.staffIds().isEmpty()) {
                return List.of();
            }
            sql.append(" AND (h.teacher_id IN (:own) OR h.section_id IN (:ownSections) OR (h.section_id IS NULL AND h.academic_class_id IN (:ownClasses)))");
            params.put("own", own.staffIds());
            params.put("ownSections", nonEmpty(own.sectionIds()));
            params.put("ownClasses", nonEmpty(own.teachingClassIds()));
        }
        sql.append(" ORDER BY h.assigned_on DESC, h.created_at DESC");
        return respond(query(sql.toString(), params, limit));
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public HomeworkResponse get(UUID id) {
        Homework h = homework(id);
        if (!access.all()) {
            OwnRecords own = access.own();
            boolean ok = own.staffIds().contains(h.getTeacherId())
                    || (h.getSectionId() != null ? own.sectionIds().contains(h.getSectionId()) : own.teachingClassIds().contains(h.getAcademicClassId()));
            if (!ok) {
                throw new AccessDeniedException("You can see only homework of the classes you teach");
            }
        }
        return respond(List.of(h)).get(0);
    }

    /**
     * A child's homework (controller checks the child is the user's): published, for the child's class or section this year,
     * due between {@code from} (default today) and {@code to} (default two weeks on).
     */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<HomeworkResponse> forStudent(UUID studentId, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        List<EnrolledStudent> e = queries.enrolledStudents(today, List.of(studentId), null, null);
        if (e.isEmpty()) {
            return List.of();
        }
        EnrolledStudent s = e.get(0);
        Map<String, Object> params = new HashMap<>();
        params.put("y", s.academicYearId());
        params.put("c", s.academicClassId());
        params.put("sec", s.sectionId());
        params.put("from", from != null ? from : today);
        params.put("to", to != null ? to : today.plusWeeks(2));
        return respond(query("SELECT h.* FROM homework h WHERE h.deleted_at IS NULL AND h.status = 'PUBLISHED' AND h.academic_year_id = :y "
                + "AND h.academic_class_id = :c AND (h.section_id IS NULL OR h.section_id = CAST(:sec AS uuid)) "
                + "AND h.due_on BETWEEN :from AND :to ORDER BY h.due_on, h.assigned_on", params, 200));
    }

    // ── write ────────────────────────────────────────────────────────────────

    @Transactional(transactionManager = "tenantTransactionManager")
    public HomeworkResponse create(HomeworkRequest r) {
        Homework h = new Homework(code());
        h.setCreatedBy(access.actorId());
        apply(h, r, true);
        h = repository.save(h);
        log.info("Homework [{}] set for class [{}] section [{}]", h.getHomeworkCode(), h.getAcademicClassId(), h.getSectionId());
        return respond(List.of(h)).get(0);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public HomeworkResponse update(UUID id, HomeworkRequest r) {
        Homework h = homework(id);
        requireOwner(h, "change");
        if (h.getStatus() == HomeworkStatus.CANCELLED) {
            throw new BusinessException("Cancelled homework cannot be changed");
        }
        apply(h, r, false);
        return respond(List.of(repository.save(h))).get(0);
    }

    /** Cancelled homework stays in the teacher's list but disappears for families. */
    @Transactional(transactionManager = "tenantTransactionManager")
    public HomeworkResponse cancel(UUID id) {
        Homework h = homework(id);
        requireOwner(h, "cancel");
        h.setStatus(HomeworkStatus.CANCELLED);
        h.setUpdatedBy(access.actorId());
        return respond(List.of(repository.save(h))).get(0);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void delete(UUID id) {
        Homework h = homework(id);
        requireOwner(h, "delete");
        h.setDeletedAt(Instant.now(clock));
        h.setUpdatedBy(access.actorId());
        repository.save(h);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void apply(Homework h, HomeworkRequest r, boolean creating) {
        Place place = access.place(r.academicYearId(), r.academicClassId(), r.sectionId());
        Subject subject = subjectRepository.findByIdAndNotDeleted(r.subjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Subject", r.subjectId()));
        TeachingScope teaching = access.teaching();
        boolean all = access.all();
        Set<UUID> own = access.ownStaffIds();
        UUID teacherId = r.teacherId() != null ? r.teacherId() : !creating ? h.getTeacherId() : own.stream().findFirst().orElse(null);
        if (teacherId == null) {
            throw new BusinessException("teacherId is required: your login is not linked to a staff record");
        }
        if (!all && !own.contains(teacherId)) {
            throw new AccessDeniedException("You can set homework only as yourself");
        }
        staffRepository.findByIdAndNotDeleted(teacherId).orElseThrow(() -> new ResourceNotFoundException("Staff", teacherId));
        if (!teaching.teaches(Set.of(teacherId), place.year().getId(), place.clazz().getId(), r.sectionId(), subject.getId())) {
            throw new BusinessException(teaching.staffNames(List.of(teacherId)).getOrDefault(teacherId, "The teacher") + " does not teach "
                    + subject.getName() + " in " + place.clazz().getName() + (place.section() != null ? " " + place.section().getName() : ""));
        }
        LocalDate assigned = r.assignedOn() != null ? r.assignedOn() : !creating ? h.getAssignedOn() : LocalDate.now(clock);
        if (r.dueOn().isBefore(assigned)) {
            throw new BusinessException("dueOn is before assignedOn");
        }
        if (assigned.isBefore(place.year().getStartDate()) || r.dueOn().isAfter(place.year().getEndDate())) {
            throw new BusinessException("The dates are outside " + place.year().getCode() + " (" + place.year().getStartDate() + " – "
                    + place.year().getEndDate() + ")");
        }
        h.setAcademicYearId(place.year().getId());
        h.setAcademicClassId(place.clazz().getId());
        h.setSectionId(r.sectionId());
        h.setSubjectId(subject.getId());
        h.setTeacherId(teacherId);
        h.setTitle(r.title().trim());
        h.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        h.setAssignedOn(assigned);
        h.setDueOn(r.dueOn());
        h.setUpdatedBy(access.actorId());
    }

    private void requireOwner(Homework h, String action) {
        if (!access.all() && !access.ownStaffIds().contains(h.getTeacherId())) {
            throw new AccessDeniedException("You can " + action + " only homework you set");
        }
    }

    private List<HomeworkResponse> respond(List<Homework> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        TeachingScope n = access.teaching();
        Map<UUID, String> classes = n.classNames(list.stream().map(Homework::getAcademicClassId).toList());
        Map<UUID, String> sections = n.sectionNames(list.stream().map(Homework::getSectionId).toList());
        Map<UUID, String> subjects = n.subjectNames(list.stream().map(Homework::getSubjectId).toList());
        Map<UUID, String> teachers = n.staffNames(list.stream().map(Homework::getTeacherId).toList());
        List<HomeworkResponse> out = new ArrayList<>();
        for (Homework h : list) {
            out.add(new HomeworkResponse(h.getId(), h.getHomeworkCode(), h.getAcademicYearId(), h.getAcademicClassId(),
                    classes.get(h.getAcademicClassId()), h.getSectionId(), sections.get(h.getSectionId()), h.getSubjectId(),
                    subjects.get(h.getSubjectId()), h.getTeacherId(), teachers.get(h.getTeacherId()), h.getTitle(), h.getDescription(),
                    h.getAssignedOn(), h.getDueOn(), h.getStatus(), h.getUpdatedAt()));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Homework> query(String sql, Map<String, Object> params, Integer limit) {
        var q = em.createNativeQuery(sql, Homework.class);
        params.forEach(q::setParameter);
        q.setMaxResults(limit == null ? 100 : Math.max(1, Math.min(limit, 500)));
        return q.getResultList();
    }

    private Homework homework(UUID id) {
        return repository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("Homework", id));
    }

    private String code() {
        for (int i = 0; i < 6; i++) {
            String c = CodeGenerator.homeworkCode();
            if (!repository.existsByHomeworkCode(c)) {
                return c;
            }
        }
        throw new BusinessException("Could not generate a unique code; try again");
    }

    static Set<UUID> nonEmpty(Set<UUID> ids) {
        return ids.isEmpty() ? Set.of(new UUID(0, 0)) : ids;
    }

    static void filter(StringBuilder sql, Map<String, Object> params, String clause, String name, Object value) {
        if (value != null) {
            sql.append(" AND ").append(clause);
            params.put(name, value);
        }
    }
}
