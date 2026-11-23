package com.vidya.tenantdata.engagement.service;

import com.vidya.tenantdata.engagement.repository.DailyReportQueries;
import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.academic.config.service.TeachingScope;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.HomeworkRequest;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.HomeworkResponse;
import com.vidya.tenantdata.engagement.entity.Homework;
import com.vidya.tenantdata.engagement.enums.HomeworkStatus;
import com.vidya.tenantdata.engagement.repository.HomeworkRepository;
import com.vidya.tenantdata.engagement.repository.HomeworkRepositoryCustom;
import com.vidya.tenantdata.engagement.repository.OwnScope;
import com.vidya.tenantdata.engagement.repository.DailyReportQueries.EnrolledStudent;
import com.vidya.tenantdata.engagement.service.EngagementAccess.Place;
import com.vidya.tenantdata.security.OwnRecords;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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

    // ── read ─────────────────────────────────────────────────────────────────

    /**
     * Staff list, newest first. Without full scope: homework the user set, or set for a section / class they teach.
     *
     * @param from / to filter on the assigned date
     */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<HomeworkResponse> list(UUID academicYearId, UUID academicClassId, UUID sectionId, UUID subjectId, UUID teacherId,
                                       LocalDate from, LocalDate to, Integer limit) {
        OwnScope scope = null;
        if (!access.all()) {
            OwnRecords own = access.own();
            if (own.staffIds().isEmpty()) {
                return List.of();
            }
            scope = new OwnScope(access.actorId(), own.staffIds(), own.sectionIds(), own.teachingClassIds());
        }
        var filter = new HomeworkRepositoryCustom.Filter(academicYearId, academicClassId, sectionId, subjectId, teacherId, from, to);
        return respond(repository.search(filter, scope, limit(limit, 100, 500)));
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
        return respond(repository.findPublishedDue(s.academicYearId(), s.academicClassId(), s.sectionId(),
                from != null ? from : today, to != null ? to : today.plusWeeks(2), 200));
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

    /** Requested page size, or the default, kept within 1..max. */
    static int limit(Integer requested, int dflt, int max) {
        return requested == null ? dflt : Math.max(1, Math.min(requested, max));
    }
}
