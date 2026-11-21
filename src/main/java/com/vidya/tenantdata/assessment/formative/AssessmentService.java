package com.vidya.tenantdata.assessment.formative;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.assessment.AssessmentAccess;
import com.vidya.tenantdata.assessment.repository.AssessmentQueries;
import com.vidya.tenantdata.assessment.repository.AssessmentQueries.Enrolled;
import com.vidya.tenantdata.assessment.repository.AssessmentQueries.Score;
import com.vidya.tenantdata.assessment.repository.AssessmentQueries.TermInfo;
import com.vidya.tenantdata.assessment.entity.Assessment;
import com.vidya.tenantdata.assessment.entity.AssessmentComponent;
import com.vidya.tenantdata.assessment.entity.AssessmentScheme;
import com.vidya.tenantdata.assessment.enums.AssessmentMode;
import com.vidya.tenantdata.assessment.enums.AssessmentStatus;
import com.vidya.tenantdata.assessment.enums.ComponentSource;
import com.vidya.tenantdata.assessment.formative.AssessmentDtos.AssessmentResponse;
import com.vidya.tenantdata.assessment.formative.AssessmentDtos.CreateAssessmentRequest;
import com.vidya.tenantdata.assessment.formative.AssessmentDtos.ScoreItem;
import com.vidya.tenantdata.assessment.formative.AssessmentDtos.ScoreResponse;
import com.vidya.tenantdata.assessment.formative.AssessmentDtos.UpdateAssessmentRequest;
import com.vidya.tenantdata.assessment.repository.AssessmentComponentRepository;
import com.vidya.tenantdata.assessment.repository.AssessmentRepository;
import com.vidya.tenantdata.assessment.repository.AssessmentSchemeRepository;
import com.vidya.tenantdata.assessment.result.ResultCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.vidya.tenantdata.assessment.repository.AssessmentQueries.params;
import static com.vidya.tenantdata.assessment.repository.AssessmentQueries.uuid;

/**
 * Formative and summative assessments recorded under a component of the class's active scheme (class tests, projects,
 * quizzes, orals, practicals ...), and their scores. Online exams and OMR batches post into them too.
 */
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "tenantTransactionManager")
public class AssessmentService {

    private final AssessmentRepository repository;
    private final AssessmentComponentRepository componentRepository;
    private final AssessmentSchemeRepository schemeRepository;
    private final AssessmentQueries queries;
    private final AssessmentAccess access;

    /** A mark to store for a student; null marks with absent=false clears nothing (the student is skipped). */
    public record Mark(BigDecimal marks, boolean absent, boolean exempt, String remarks) {
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<AssessmentResponse> list(UUID termId, UUID classId, UUID sectionId, UUID subjectId) {
        access.requireClass(classId, sectionId, "see assessments");
        return toResponses(repository.search(termId, classId, sectionId, subjectId), false);
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public AssessmentResponse get(UUID id) {
        Assessment a = find(id);
        access.requireClass(a.getAcademicClassId(), a.getSectionId(), "see assessments");
        return toResponses(List.of(a), true).get(0);
    }

    public AssessmentResponse create(CreateAssessmentRequest r) {
        TermInfo term = queries.term(r.examTermId()).orElseThrow(() -> new ResourceNotFoundException("ExamTerm", r.examTermId()));
        access.requireTeaches(r.subjectId(), r.academicClassId(), "record assessments");
        if (r.sectionId() != null) {
            access.requireClass(r.academicClassId(), r.sectionId(), "record assessments");
            requireSectionOf(r.sectionId(), term.yearId(), r.academicClassId());
        }
        AssessmentComponent component = component(r.componentId(), term, r.academicClassId());
        Assessment a = new Assessment();
        a.setAssessmentCode(CodeGenerator.assessmentCode());
        a.setComponentId(component.getId());
        a.setExamTermId(term.id());
        a.setAcademicYearId(term.yearId());
        a.setAcademicClassId(r.academicClassId());
        a.setSectionId(r.sectionId());
        a.setSubjectId(r.subjectId());
        a.setTitle(r.title().trim());
        a.setAssessmentDate(r.assessmentDate());
        a.setMaxMarks(r.maxMarks());
        a.setMode(r.mode() != null ? r.mode() : AssessmentMode.OFFLINE);
        a.setCreatedBy(access.actorId());
        return toResponses(List.of(repository.save(a)), true).get(0);
    }

    public AssessmentResponse update(UUID id, UpdateAssessmentRequest r) {
        Assessment a = editable(id, "change assessments");
        if (r.title() != null) a.setTitle(r.title().trim());
        if (r.assessmentDate() != null) a.setAssessmentDate(r.assessmentDate());
        if (r.mode() != null) a.setMode(r.mode());
        if (r.maxMarks() != null) {
            BigDecimal highest = queries.scores(List.of(id)).stream().map(Score::raw).filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            if (r.maxMarks().compareTo(highest) < 0) {
                throw new BusinessException("MAX_BELOW_SCORES", "A student already has " + highest.stripTrailingZeros().toPlainString() + " marks");
            }
            a.setMaxMarks(r.maxMarks());
        }
        a.setUpdatedBy(access.actorId());
        return toResponses(List.of(repository.save(a)), true).get(0);
    }

    public void delete(UUID id) {
        Assessment a = editable(id, "delete assessments");
        a.setDeletedAt(Instant.now());
        a.setUpdatedBy(access.actorId());
        repository.save(a);
    }

    public AssessmentResponse saveScores(UUID id, List<ScoreItem> items) {
        Assessment a = editable(id, "enter scores");
        Map<UUID, Mark> marks = new HashMap<>();
        for (ScoreItem i : items) {
            if (marks.put(i.studentId(), new Mark(i.marks(), i.absent(), i.exempt(), i.remarks())) != null) {
                throw new BusinessException("DUPLICATE_STUDENT", "A student appears twice");
            }
        }
        access.requireStudents(marks.keySet(), "enter scores");
        store(a, marks);
        return toResponses(List.of(a), true).get(0);
    }

    /** Stores marks without a teaching check (online exams and OMR post here after their own checks). */
    public int store(Assessment a, Map<UUID, Mark> marks) {
        if (a.getStatus() == AssessmentStatus.LOCKED) {
            throw new BusinessException("ASSESSMENT_LOCKED", "The assessment is locked; ask the exam cell to unlock it");
        }
        Set<UUID> enrolled = queries.enrolled(a.getAcademicYearId(), a.getAcademicClassId(), a.getSectionId()).stream()
                .map(Enrolled::studentId).collect(Collectors.toSet());
        for (Map.Entry<UUID, Mark> e : marks.entrySet()) {
            Mark m = e.getValue();
            if (!enrolled.contains(e.getKey())) {
                throw new BusinessException("NOT_ENROLLED", "Student " + e.getKey() + " is not in the assessment's class / section");
            }
            if (!m.absent() && !m.exempt() && m.marks() == null) {
                throw new BusinessException("MARKS_REQUIRED", "Give marks, absent or exempt for every student");
            }
            if (m.marks() != null && m.marks().compareTo(a.getMaxMarks()) > 0) {
                throw new BusinessException("MARKS_ABOVE_MAX", "Marks above the maximum of " + a.getMaxMarks().stripTrailingZeros().toPlainString());
            }
        }
        UUID staff = access.staffId();
        UUID actor = access.actorId();
        marks.forEach((student, m) -> queries.update("INSERT INTO assessment_score (assessment_id, student_id, marks, is_absent, is_exempt, remarks, "
                + "evaluated_by_staff_id, created_by, updated_by) VALUES (:a, :s, :m, :ab, :ex, :r, :staff, :u, :u) "
                + "ON CONFLICT (assessment_id, student_id) DO UPDATE SET marks = EXCLUDED.marks, is_absent = EXCLUDED.is_absent, "
                + "is_exempt = EXCLUDED.is_exempt, remarks = EXCLUDED.remarks, evaluated_by_staff_id = EXCLUDED.evaluated_by_staff_id, "
                + "updated_by = EXCLUDED.updated_by, updated_at = NOW(), version = assessment_score.version + 1",
                params("a", a.getId(), "s", student, "m", m.absent() || m.exempt() ? null : m.marks(), "ab", m.absent(), "ex", m.exempt(),
                        "r", m.remarks(), "staff", staff, "u", actor)));
        return marks.size();
    }

    public AssessmentResponse lock(UUID id) {
        Assessment a = find(id);
        access.requireTeaches(a.getSubjectId(), a.getAcademicClassId(), "lock assessments");
        a.setStatus(AssessmentStatus.LOCKED);
        return toResponses(List.of(repository.save(a)), true).get(0);
    }

    public AssessmentResponse unlock(UUID id) {
        Assessment a = find(id);
        if (!access.all()) {
            throw new AccessDeniedException("Only the exam cell (MARKS scope ALL) can unlock an assessment");
        }
        a.setStatus(AssessmentStatus.OPEN);
        return toResponses(List.of(repository.save(a)), true).get(0);
    }

    // ───────────────────────────── helpers ─────────────────────────────

    public Assessment find(UUID id) {
        return repository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("Assessment", id));
    }

    private Assessment editable(UUID id, String what) {
        Assessment a = find(id);
        access.requireTeaches(a.getSubjectId(), a.getAcademicClassId(), what);
        if (a.getStatus() == AssessmentStatus.LOCKED) {
            throw new BusinessException("ASSESSMENT_LOCKED", "The assessment is locked");
        }
        return a;
    }

    private AssessmentComponent component(UUID componentId, TermInfo term, UUID classId) {
        AssessmentScheme scheme = schemeRepository.findActiveFor(term.yearId(), classId).orElseThrow(() -> new BusinessException("NO_SCHEME",
                "The class has no active assessment scheme for the year; set one up under /api/v1/assessment-schemes"));
        AssessmentComponent c = componentRepository.findById(componentId).orElseThrow(() -> new ResourceNotFoundException("AssessmentComponent", componentId));
        if (!c.getSchemeId().equals(scheme.getId())) {
            throw new BusinessException("WRONG_COMPONENT", "The component is not part of the class's scheme (" + scheme.getName() + ")");
        }
        if (c.getSource() != ComponentSource.ASSESSMENTS) {
            throw new BusinessException("WRONG_COMPONENT", c.getName() + " takes the term's exam paper marks; record those through exam marks");
        }
        if (c.getExamTermId() != null && !c.getExamTermId().equals(term.id())) {
            throw new BusinessException("WRONG_COMPONENT", c.getName() + " belongs to another term");
        }
        return c;
    }

    private void requireSectionOf(UUID sectionId, UUID yearId, UUID classId) {
        if (queries.rows("SELECT id FROM section WHERE id = :s AND academic_year_id = :y AND academic_class_id = :c AND deleted_at IS NULL",
                params("s", sectionId, "y", yearId, "c", classId)).isEmpty()) {
            throw new BusinessException("WRONG_SECTION", "The section is not a section of the class in the term's year");
        }
    }

    private List<AssessmentResponse> toResponses(List<Assessment> list, boolean withScores) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<UUID, AssessmentComponent> components = componentRepository.findAllById(list.stream().map(Assessment::getComponentId).distinct().toList())
                .stream().collect(Collectors.toMap(AssessmentComponent::getId, Function.identity()));
        Set<UUID> ids = new HashSet<>();
        list.forEach(a -> ids.add(a.getId()));
        Map<UUID, List<Score>> scores = queries.scores(ids).stream().collect(Collectors.groupingBy(Score::assessmentId));
        Map<UUID, String> names = new HashMap<>();
        queries.rows("SELECT id, name FROM exam_term WHERE id IN (:t) UNION ALL SELECT id, name FROM academic_class WHERE id IN (:c) "
                        + "UNION ALL SELECT id, name FROM section WHERE id IN (:s) UNION ALL SELECT id, name FROM subject WHERE id IN (:sub)",
                params("t", list.stream().map(Assessment::getExamTermId).distinct().toList(),
                        "c", list.stream().map(Assessment::getAcademicClassId).distinct().toList(),
                        "s", list.stream().map(a -> a.getSectionId() != null ? a.getSectionId() : a.getAcademicClassId()).distinct().toList(),
                        "sub", list.stream().map(Assessment::getSubjectId).distinct().toList()))
                .forEach(r -> names.put(uuid(r[0]), (String) r[1]));
        return list.stream().map(a -> {
            AssessmentComponent c = components.get(a.getComponentId());
            List<Score> own = scores.getOrDefault(a.getId(), List.of());
            List<Enrolled> enrolled = queries.enrolled(a.getAcademicYearId(), a.getAcademicClassId(), a.getSectionId());
            List<BigDecimal> pcts = own.stream().filter(s -> !s.exempt()).map(s -> ResultCalculator.percent(s.absent() || s.marks() == null
                    ? BigDecimal.ZERO : s.marks(), a.getMaxMarks())).toList();
            BigDecimal avg = pcts.isEmpty() ? null : pcts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(pcts.size()), 2, RoundingMode.HALF_UP);
            List<ScoreResponse> scoreRows = null;
            if (withScores) {
                Map<UUID, Score> byStudent = own.stream().collect(Collectors.toMap(Score::studentId, Function.identity()));
                scoreRows = enrolled.stream().filter(e -> access.all() || access.canSeeStudent(e.studentId())).map(e -> {
                    Score s = byStudent.get(e.studentId());
                    return new ScoreResponse(e.studentId(), e.name(), e.rollNumber(), s == null ? null : s.raw(), s == null ? null : s.marks(),
                            s != null && s.absent(), s != null && s.exempt());
                }).toList();
            }
            return new AssessmentResponse(a.getId(), a.getAssessmentCode(), a.getComponentId(), c == null ? null : c.getCode(),
                    c == null ? null : c.getName(), c == null ? null : c.getAssessmentType(), a.getExamTermId(), names.get(a.getExamTermId()),
                    a.getAcademicYearId(), a.getAcademicClassId(), names.get(a.getAcademicClassId()), a.getSectionId(),
                    a.getSectionId() == null ? null : names.get(a.getSectionId()), a.getSubjectId(), names.get(a.getSubjectId()), a.getTitle(),
                    a.getAssessmentDate(), a.getMaxMarks(), a.getMode(), a.getStatus(), own.size(), enrolled.size(), avg, scoreRows);
        }).toList();
    }
}
