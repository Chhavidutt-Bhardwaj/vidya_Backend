package com.vidya.tenantdata.academic.config.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.DeliverRequest;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.LessonPlanRequest;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.LessonPlanResponse;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.ReviewDecision;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.ReviewRequest;
import com.vidya.tenantdata.academic.config.entity.LessonPlan;
import com.vidya.tenantdata.academic.config.entity.Syllabus;
import com.vidya.tenantdata.academic.config.entity.SyllabusTopic;
import com.vidya.tenantdata.academic.config.entity.SyllabusUnit;
import com.vidya.tenantdata.academic.config.enums.LessonPlanStatus;
import com.vidya.tenantdata.academic.config.repository.LessonPlanRepository;
import com.vidya.tenantdata.academic.config.repository.SyllabusTopicRepository;
import com.vidya.tenantdata.academic.config.repository.SyllabusUnitRepository;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.staff.entity.Staff;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lesson plans. A teacher writes plans for what they teach, optionally against a syllabus topic, and sends
 * them for review; a coordinator, HOD / subject in-charge or the principal approves or returns them; after
 * the lesson the teacher marks the plan delivered, which also marks the topic covered.
 *
 * <p>Users with STAFF data scope ALL see and manage every plan; everyone else only their own (plus, for
 * reviewing, the plans of the subject, class or stage they hold a duty for).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LessonPlanService {

    private static final String DUTY_OVER = "EXISTS (SELECT 1 FROM staff_duty d WHERE d.staff_id IN (:own) AND d.status = 'ACTIVE' "
            + "AND (d.subject_id = lp.subject_id OR d.academic_class_id = lp.academic_class_id "
            + "OR d.class_stage = (SELECT c.stage FROM academic_class c WHERE c.id = lp.academic_class_id)))";

    private final LessonPlanRepository planRepository;
    private final SyllabusTopicRepository topicRepository;
    private final SyllabusUnitRepository unitRepository;
    private final SyllabusService syllabusService;
    private final AcademicYearRepository yearRepository;
    private final AcademicClassRepository classRepository;
    private final SectionRepository sectionRepository;
    private final SubjectRepository subjectRepository;
    private final StaffRepository staffRepository;
    private final TeachingScope scope;

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    // ── read ─────────────────────────────────────────────────────────────────

    /** Newest first. Without full scope: own plans, and submitted plans the user may review through a duty. */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<LessonPlanResponse> list(UUID academicYearId, UUID teacherId, UUID academicClassId, UUID sectionId, UUID subjectId,
                                         LessonPlanStatus status, LocalDate from, LocalDate to, Integer limit) {
        boolean all = scope.all();
        Set<UUID> own = all ? Set.of() : scope.ownStaffIds();
        if (!all && own.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("SELECT lp.* FROM lesson_plan lp WHERE lp.deleted_at IS NULL");
        Map<String, Object> params = new HashMap<>();
        filter(sql, params, "lp.academic_year_id = :y", "y", academicYearId);
        filter(sql, params, "lp.teacher_id = :teacher", "teacher", teacherId);
        filter(sql, params, "lp.academic_class_id = :c", "c", academicClassId);
        filter(sql, params, "(lp.section_id = :sec OR lp.section_id IS NULL)", "sec", sectionId);
        filter(sql, params, "lp.subject_id = :s", "s", subjectId);
        filter(sql, params, "lp.status = :st", "st", status != null ? status.name() : null);
        filter(sql, params, "lp.plan_date >= :from", "from", from);
        filter(sql, params, "lp.plan_date <= :to", "to", to);
        if (!all) {
            sql.append(" AND (lp.teacher_id IN (:own) OR (lp.status <> 'DRAFT' AND ").append(DUTY_OVER).append("))");
            params.put("own", own);
        }
        sql.append(" ORDER BY lp.plan_date DESC, lp.created_at DESC");
        var q = em.createNativeQuery(sql.toString(), LessonPlan.class);
        params.forEach(q::setParameter);
        q.setMaxResults(limit == null ? 100 : Math.max(1, Math.min(limit, 500)));
        @SuppressWarnings("unchecked")
        List<LessonPlan> plans = q.getResultList();
        return respond(plans);
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public LessonPlanResponse get(UUID id) {
        LessonPlan p = plan(id);
        if (!scope.all()) {
            Set<UUID> own = scope.ownStaffIds();
            if (!own.contains(p.getTeacherId())
                    && !(p.getStatus() != LessonPlanStatus.DRAFT && scope.hasDutyOver(own, p.getAcademicClassId(), p.getSubjectId()))) {
                throw new AccessDeniedException("You can see only your own lesson plans");
            }
        }
        return respond(List.of(p)).get(0);
    }

    // ── write ────────────────────────────────────────────────────────────────

    @Transactional(transactionManager = "tenantTransactionManager")
    public LessonPlanResponse create(LessonPlanRequest r) {
        LessonPlan p = new LessonPlan(code());
        p.setCreatedBy(scope.actorId());
        apply(p, r, true);
        if (Boolean.TRUE.equals(r.submit())) {
            p.setStatus(LessonPlanStatus.SUBMITTED);
            p.setSubmittedAt(Instant.now());
        }
        p = planRepository.save(p);
        log.info("Lesson plan [{}] created for teacher [{}]", p.getPlanCode(), p.getTeacherId());
        return respond(List.of(p)).get(0);
    }

    /** The teacher may change a plan until it is approved; a returned plan stays RETURNED until it is submitted again. */
    @Transactional(transactionManager = "tenantTransactionManager")
    public LessonPlanResponse update(UUID id, LessonPlanRequest r) {
        LessonPlan p = plan(id);
        requireOwner(p, "change");
        if (!p.getStatus().editable()) {
            throw new BusinessException("A lesson plan that is " + p.getStatus() + " cannot be changed");
        }
        apply(p, r, false);
        if (Boolean.TRUE.equals(r.submit()) && p.getStatus() != LessonPlanStatus.SUBMITTED) {
            p.setStatus(LessonPlanStatus.SUBMITTED);
            p.setSubmittedAt(Instant.now());
        }
        return respond(List.of(planRepository.save(p))).get(0);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public LessonPlanResponse submit(UUID id) {
        LessonPlan p = plan(id);
        requireOwner(p, "submit");
        if (p.getStatus() != LessonPlanStatus.DRAFT && p.getStatus() != LessonPlanStatus.RETURNED) {
            throw new BusinessException("Only a draft or returned lesson plan can be submitted; this one is " + p.getStatus());
        }
        p.setStatus(LessonPlanStatus.SUBMITTED);
        p.setSubmittedAt(Instant.now());
        p.setUpdatedBy(scope.actorId());
        return respond(List.of(planRepository.save(p))).get(0);
    }

    /** Approve or return a submitted plan. Full scope, or a duty for the plan's subject, class or stage; never one's own plan. */
    @Transactional(transactionManager = "tenantTransactionManager")
    public LessonPlanResponse review(UUID id, ReviewRequest r) {
        LessonPlan p = plan(id);
        if (!scope.all()) {
            Set<UUID> own = scope.ownStaffIds();
            if (own.contains(p.getTeacherId())) {
                throw new AccessDeniedException("You cannot review your own lesson plan");
            }
            if (!scope.hasDutyOver(own, p.getAcademicClassId(), p.getSubjectId())) {
                throw new AccessDeniedException("You can review lesson plans only for the subject, class or stage you are responsible for");
            }
        }
        if (p.getStatus() != LessonPlanStatus.SUBMITTED) {
            throw new BusinessException("Only a submitted lesson plan can be reviewed; this one is " + p.getStatus());
        }
        if (r.decision() == ReviewDecision.RETURN && (r.remarks() == null || r.remarks().isBlank())) {
            throw new BusinessException("Say what should be changed when returning a lesson plan");
        }
        p.setStatus(r.decision() == ReviewDecision.APPROVE ? LessonPlanStatus.APPROVED : LessonPlanStatus.RETURNED);
        p.setReviewRemarks(r.remarks() == null || r.remarks().isBlank() ? null : r.remarks().trim());
        p.setReviewedBy(scope.actorId());
        p.setReviewedAt(Instant.now());
        return respond(List.of(planRepository.save(p))).get(0);
    }

    /** The lesson was taught. Marks the plan's syllabus topic completed in its section(s) unless told not to. */
    @Transactional(transactionManager = "tenantTransactionManager")
    public LessonPlanResponse deliver(UUID id, DeliverRequest r) {
        LessonPlan p = plan(id);
        requireOwner(p, "mark delivered");
        if (p.getStatus() == LessonPlanStatus.RETURNED || p.getStatus() == LessonPlanStatus.DELIVERED) {
            throw new BusinessException(p.getStatus() == LessonPlanStatus.DELIVERED ? "This lesson plan is already delivered"
                    : "This lesson plan was returned; update and submit it again first");
        }
        LocalDate on = r != null && r.deliveredOn() != null ? r.deliveredOn() : LocalDate.now();
        if (on.isAfter(LocalDate.now())) {
            throw new BusinessException("deliveredOn is in the future");
        }
        p.setStatus(LessonPlanStatus.DELIVERED);
        p.setDeliveredOn(on);
        if (r != null && r.reflection() != null) {
            p.setReflection(r.reflection().isBlank() ? null : r.reflection().trim());
        }
        p.setUpdatedBy(scope.actorId());
        if (p.getSyllabusTopicId() != null && (r == null || !Boolean.FALSE.equals(r.markTopicCompleted()))) {
            List<UUID> sections = new ArrayList<>();
            if (p.getSectionId() != null) {
                sections.add(p.getSectionId());
            } else {
                // a whole-class plan covers the sections this teacher takes the subject in
                for (UUID sec : scope.sectionsOfYear(p.getAcademicYearId()).getOrDefault(p.getAcademicClassId(), Map.of()).keySet()) {
                    if (scope.teaches(Set.of(p.getTeacherId()), p.getAcademicYearId(), p.getAcademicClassId(), sec, p.getSubjectId())) {
                        sections.add(sec);
                    }
                }
            }
            syllabusService.completeTopic(p.getSyllabusTopicId(), sections, on);
        }
        return respond(List.of(planRepository.save(p))).get(0);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void delete(UUID id) {
        LessonPlan p = plan(id);
        requireOwner(p, "delete");
        if (!scope.all() && !p.getStatus().editable()) {
            throw new BusinessException("A lesson plan that is " + p.getStatus() + " cannot be deleted");
        }
        p.setDeletedAt(Instant.now());
        p.setUpdatedBy(scope.actorId());
        planRepository.save(p);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void apply(LessonPlan p, LessonPlanRequest r, boolean creating) {
        AcademicYear year = yearRepository.findByIdAndNotDeleted(r.academicYearId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", r.academicYearId()));
        if (year.getStatus() == AcademicYearStatus.CLOSED || year.getStatus() == AcademicYearStatus.CANCELLED) {
            throw new BusinessException("Academic year " + year.getCode() + " is " + year.getStatus() + " and cannot be changed");
        }
        AcademicClass clazz = classRepository.findByIdAndNotDeleted(r.academicClassId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicClass", r.academicClassId()));
        Subject subject = subjectRepository.findByIdAndNotDeleted(r.subjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Subject", r.subjectId()));
        Section section = null;
        if (r.sectionId() != null) {
            section = sectionRepository.findByIdAndNotDeleted(r.sectionId()).orElseThrow(() -> new ResourceNotFoundException("Section", r.sectionId()));
            if (!section.getAcademicYear().getId().equals(year.getId()) || !section.getAcademicClass().getId().equals(clazz.getId())) {
                throw new BusinessException("The section is not a section of " + clazz.getName() + " in " + year.getCode());
            }
        }

        boolean all = scope.all();
        Set<UUID> own = scope.ownStaffIds();
        UUID teacherId = r.teacherId() != null ? r.teacherId() : !creating ? p.getTeacherId() : own.stream().findFirst().orElse(null);
        if (teacherId == null) {
            throw new BusinessException("teacherId is required: your login is not linked to a staff record");
        }
        if (!all && !own.contains(teacherId)) {
            throw new AccessDeniedException("You can write lesson plans only for yourself");
        }
        Staff teacher = staffRepository.findByIdAndNotDeleted(teacherId).orElseThrow(() -> new ResourceNotFoundException("Staff", teacherId));
        if (!scope.teaches(Set.of(teacher.getId()), year.getId(), clazz.getId(), r.sectionId(), subject.getId())) {
            throw new BusinessException(scope.staffNames(List.of(teacher.getId())).getOrDefault(teacher.getId(), "The teacher") + " does not teach "
                    + subject.getName() + " in " + clazz.getName() + (section != null ? " " + section.getName() : "") + " in " + year.getCode());
        }
        if (r.planDate().isBefore(year.getStartDate()) || r.planDate().isAfter(year.getEndDate())) {
            throw new BusinessException("planDate is outside " + year.getCode() + " (" + year.getStartDate() + " – " + year.getEndDate() + ")");
        }

        UUID unitId = r.syllabusUnitId();
        if (r.syllabusTopicId() != null) {
            SyllabusTopic topic = topicRepository.findById(r.syllabusTopicId())
                    .orElseThrow(() -> new ResourceNotFoundException("SyllabusTopic", r.syllabusTopicId()));
            requireSameSyllabus(syllabusService.syllabus(topic.getSyllabusId()), year, clazz, subject);
            unitId = topic.getUnitId();
        } else if (unitId != null) {
            SyllabusUnit unit = unitRepository.findById(unitId).orElseThrow(() -> new ResourceNotFoundException("SyllabusUnit", r.syllabusUnitId()));
            requireSameSyllabus(syllabusService.syllabus(unit.getSyllabusId()), year, clazz, subject);
        }

        p.setAcademicYearId(year.getId());
        p.setAcademicClassId(clazz.getId());
        p.setSectionId(r.sectionId());
        p.setSubjectId(subject.getId());
        p.setTeacherId(teacher.getId());
        p.setSyllabusUnitId(unitId);
        p.setSyllabusTopicId(r.syllabusTopicId());
        p.setTitle(r.title().trim());
        p.setPlanDate(r.planDate());
        p.setPeriods(r.periods() != null ? r.periods() : 1);
        p.setObjectives(r.objectives());
        p.setTeachingMethod(r.teachingMethod());
        p.setResources(r.resources());
        p.setHomework(r.homework());
        p.setAssessment(r.assessment());
        p.setUpdatedBy(scope.actorId());
    }

    private static void requireSameSyllabus(Syllabus s, AcademicYear year, AcademicClass clazz, Subject subject) {
        if (!s.getAcademicYearId().equals(year.getId()) || !s.getAcademicClassId().equals(clazz.getId()) || !s.getSubjectId().equals(subject.getId())) {
            throw new BusinessException("The syllabus topic belongs to another class, subject or year");
        }
    }

    /** The plan's teacher, or a user with full scope. */
    private void requireOwner(LessonPlan p, String action) {
        if (!scope.all() && !scope.ownStaffIds().contains(p.getTeacherId())) {
            throw new AccessDeniedException("You can " + action + " only your own lesson plans");
        }
    }

    private List<LessonPlanResponse> respond(List<LessonPlan> plans) {
        if (plans.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> classes = scope.classNames(plans.stream().map(LessonPlan::getAcademicClassId).toList());
        Map<UUID, String> sections = scope.sectionNames(plans.stream().map(LessonPlan::getSectionId).toList());
        Map<UUID, String> subjects = scope.subjectNames(plans.stream().map(LessonPlan::getSubjectId).toList());
        Map<UUID, String> teachers = scope.staffNames(plans.stream().map(LessonPlan::getTeacherId).toList());
        Map<UUID, String> units = scope.names("SELECT id, title FROM syllabus_unit WHERE id IN (:ids)", plans.stream().map(LessonPlan::getSyllabusUnitId).toList());
        Map<UUID, String> topics = scope.names("SELECT id, title FROM syllabus_topic WHERE id IN (:ids)", plans.stream().map(LessonPlan::getSyllabusTopicId).toList());
        List<LessonPlanResponse> out = new ArrayList<>();
        for (LessonPlan p : plans) {
            out.add(new LessonPlanResponse(p.getId(), p.getPlanCode(), p.getAcademicYearId(), p.getAcademicClassId(), classes.get(p.getAcademicClassId()),
                    p.getSectionId(), sections.get(p.getSectionId()), p.getSubjectId(), subjects.get(p.getSubjectId()), p.getTeacherId(),
                    teachers.get(p.getTeacherId()), p.getSyllabusUnitId(), units.get(p.getSyllabusUnitId()), p.getSyllabusTopicId(),
                    topics.get(p.getSyllabusTopicId()), p.getTitle(), p.getPlanDate(), p.getPeriods(), p.getObjectives(), p.getTeachingMethod(),
                    p.getResources(), p.getHomework(), p.getAssessment(), p.getStatus(), p.getSubmittedAt(), p.getReviewedAt(),
                    p.getReviewRemarks(), p.getDeliveredOn(), p.getReflection(), p.getUpdatedAt()));
        }
        return out;
    }

    private LessonPlan plan(UUID id) {
        return planRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("LessonPlan", id));
    }

    private String code() {
        for (int i = 0; i < 6; i++) {
            String c = CodeGenerator.lessonPlanCode();
            if (!planRepository.existsByPlanCode(c)) {
                return c;
            }
        }
        throw new BusinessException("Could not generate a unique code; try again");
    }

    private static void filter(StringBuilder sql, Map<String, Object> params, String clause, String name, Object value) {
        if (value != null) {
            sql.append(" AND ").append(clause);
            params.put(name, value);
        }
    }
}
