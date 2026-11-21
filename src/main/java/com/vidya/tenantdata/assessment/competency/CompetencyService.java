package com.vidya.tenantdata.assessment.competency;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.assessment.AssessmentAccess;
import com.vidya.tenantdata.assessment.repository.AssessmentQueries;
import com.vidya.tenantdata.assessment.repository.AssessmentQueries.Enrolled;
import com.vidya.tenantdata.assessment.repository.AssessmentQueries.TermInfo;
import com.vidya.tenantdata.assessment.competency.CompetencyDtos.CoScholasticItem;
import com.vidya.tenantdata.assessment.competency.CompetencyDtos.CoScholasticRequest;
import com.vidya.tenantdata.assessment.competency.CompetencyDtos.CoScholasticResponse;
import com.vidya.tenantdata.assessment.competency.CompetencyDtos.CompetencyRequest;
import com.vidya.tenantdata.assessment.competency.CompetencyDtos.CompetencyResponse;
import com.vidya.tenantdata.assessment.competency.CompetencyDtos.OutcomeRequest;
import com.vidya.tenantdata.assessment.competency.CompetencyDtos.OutcomeResponse;
import com.vidya.tenantdata.assessment.competency.CompetencyDtos.RatingItem;
import com.vidya.tenantdata.assessment.competency.CompetencyDtos.RatingResponse;
import com.vidya.tenantdata.assessment.competency.CompetencyDtos.RatingsRequest;
import com.vidya.tenantdata.assessment.competency.CompetencyDtos.SaveResult;
import com.vidya.tenantdata.assessment.entity.AssessmentScheme;
import com.vidya.tenantdata.assessment.entity.CoScholasticArea;
import com.vidya.tenantdata.assessment.entity.Competency;
import com.vidya.tenantdata.assessment.entity.LearningOutcome;
import com.vidya.tenantdata.assessment.enums.AreaGroup;
import com.vidya.tenantdata.assessment.enums.CompetencyCategory;
import com.vidya.tenantdata.assessment.enums.RecordStatus;
import com.vidya.tenantdata.assessment.repository.AssessmentSchemeRepository;
import com.vidya.tenantdata.assessment.repository.CoScholasticAreaRepository;
import com.vidya.tenantdata.assessment.repository.CompetencyRepository;
import com.vidya.tenantdata.assessment.repository.LearningOutcomeRepository;
import com.vidya.tenantdata.assessment.scheme.SchemeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.vidya.tenantdata.assessment.repository.AssessmentQueries.params;
import static com.vidya.tenantdata.assessment.repository.AssessmentQueries.uuid;

/**
 * Competency framework (competencies → learning outcomes → syllabus topics and questions) and the term judgements
 * teachers record against it: competency / outcome ratings and co-scholastic grades. Allowed levels and grades come
 * from the scheme of the student's class.
 */
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "tenantTransactionManager")
public class CompetencyService {

    static final List<String> DEFAULT_LEVELS = List.of("BEGINNING", "DEVELOPING", "PROFICIENT", "ADVANCED");
    static final List<String> DEFAULT_GRADES = List.of("A", "B", "C");

    private final CompetencyRepository competencyRepository;
    private final LearningOutcomeRepository outcomeRepository;
    private final AssessmentSchemeRepository schemeRepository;
    private final CoScholasticAreaRepository areaRepository;
    private final AssessmentQueries queries;
    private final AssessmentAccess access;

    // ───────────────────────────── competencies ─────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<CompetencyResponse> competencies(UUID subjectId, CompetencyCategory category) {
        List<Competency> list = competencyRepository.search(subjectId, category);
        Map<UUID, String> subjects = new HashMap<>();
        queries.subjects(list.stream().map(Competency::getSubjectId).filter(Objects::nonNull).toList()).forEach((k, v) -> subjects.put(k, v.name()));
        Map<UUID, Long> counts = new HashMap<>();
        if (!list.isEmpty()) {
            queries.rows("SELECT competency_id, count(*) FROM learning_outcome WHERE deleted_at IS NULL AND competency_id IN (:ids) GROUP BY competency_id",
                    Map.of("ids", list.stream().map(Competency::getId).toList())).forEach(r -> counts.put(uuid(r[0]), ((Number) r[1]).longValue()));
        }
        return list.stream().map(c -> toResponse(c, subjects.get(c.getSubjectId()), counts.getOrDefault(c.getId(), 0L))).toList();
    }

    public CompetencyResponse createCompetency(CompetencyRequest r) {
        if (competencyRepository.codeTaken(r.code())) {
            throw new BusinessException("CODE_TAKEN", "Competency code " + r.code() + " is already used");
        }
        Competency c = new Competency();
        c.setCreatedBy(access.actorId());
        return saveCompetency(c, r);
    }

    public CompetencyResponse updateCompetency(UUID id, CompetencyRequest r) {
        Competency c = competency(id);
        if (!c.getCode().equalsIgnoreCase(r.code()) && competencyRepository.codeTaken(r.code())) {
            throw new BusinessException("CODE_TAKEN", "Competency code " + r.code() + " is already used");
        }
        return saveCompetency(c, r);
    }

    private CompetencyResponse saveCompetency(Competency c, CompetencyRequest r) {
        if (r.subjectId() != null && queries.subjects(List.of(r.subjectId())).isEmpty()) {
            throw new ResourceNotFoundException("Subject", r.subjectId());
        }
        if (r.parentId() != null) {
            Competency parent = competency(r.parentId());
            for (Competency p = parent; p != null; p = p.getParentId() == null ? null : competencyRepository.findById(p.getParentId()).orElse(null)) {
                if (p.getId().equals(c.getId())) {
                    throw new BusinessException("CYCLE", "A competency cannot sit under itself");
                }
            }
        }
        c.setCode(r.code().trim());
        c.setName(r.name().trim());
        c.setDescription(r.description());
        c.setCategory(r.category() != null ? r.category() : CompetencyCategory.SUBJECT);
        c.setSubjectId(r.subjectId());
        c.setBoard(r.board());
        c.setParentId(r.parentId());
        c.setStatus(r.status() != null ? r.status() : RecordStatus.ACTIVE);
        c.setUpdatedBy(access.actorId());
        c = competencyRepository.save(c);
        String subject = c.getSubjectId() == null ? null : queries.subjects(List.of(c.getSubjectId())).get(c.getSubjectId()).name();
        return toResponse(c, subject, 0);
    }

    public void deleteCompetency(UUID id) {
        Competency c = competency(id);
        long used = count("SELECT count(*) FROM competency_rating WHERE competency_id = :id", id)
                + count("SELECT count(*) FROM learning_outcome WHERE competency_id = :id AND deleted_at IS NULL", id)
                + count("SELECT count(*) FROM competency WHERE parent_id = :id AND deleted_at IS NULL", id);
        if (used > 0) {
            throw new BusinessException("COMPETENCY_IN_USE", "The competency has outcomes, sub-competencies or ratings; retire it instead");
        }
        c.setDeletedAt(Instant.now());
        competencyRepository.save(c);
    }

    // ───────────────────────────── learning outcomes ─────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<OutcomeResponse> outcomes(UUID subjectId, UUID classId, UUID competencyId) {
        return toResponses(outcomeRepository.search(subjectId, classId, competencyId));
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public OutcomeResponse outcome(UUID id) {
        return toResponses(List.of(outcomeEntity(id))).get(0);
    }

    /** All-or-nothing: a list of outcomes (e.g. a board's published learning outcomes for a class). */
    public List<OutcomeResponse> createOutcomes(List<OutcomeRequest> requests) {
        Set<String> codes = new HashSet<>();
        for (OutcomeRequest r : requests) {
            if (!codes.add(r.code().toLowerCase(Locale.ROOT)) || outcomeRepository.codeTaken(r.code())) {
                throw new BusinessException("CODE_TAKEN", "Outcome code " + r.code() + " is repeated or already used");
            }
        }
        List<LearningOutcome> saved = new ArrayList<>();
        for (OutcomeRequest r : requests) {
            LearningOutcome o = new LearningOutcome();
            o.setCreatedBy(access.actorId());
            saved.add(saveOutcome(o, r));
        }
        return toResponses(saved);
    }

    public OutcomeResponse updateOutcome(UUID id, OutcomeRequest r) {
        LearningOutcome o = outcomeEntity(id);
        if (!o.getCode().equalsIgnoreCase(r.code()) && outcomeRepository.codeTaken(r.code())) {
            throw new BusinessException("CODE_TAKEN", "Outcome code " + r.code() + " is already used");
        }
        return toResponses(List.of(saveOutcome(o, r))).get(0);
    }

    private LearningOutcome saveOutcome(LearningOutcome o, OutcomeRequest r) {
        access.requireTeaches(r.subjectId(), r.academicClassId(), "write learning outcomes");
        if (queries.subjects(List.of(r.subjectId())).isEmpty()) {
            throw new ResourceNotFoundException("Subject", r.subjectId());
        }
        if (r.competencyId() != null) {
            competency(r.competencyId());
        }
        o.setCode(r.code().trim());
        o.setCompetencyId(r.competencyId());
        o.setSubjectId(r.subjectId());
        o.setAcademicClassId(r.academicClassId());
        o.setStatement(r.statement().trim());
        o.setBloomLevel(r.bloomLevel());
        o.setBoard(r.board());
        o.setSortOrder(r.sortOrder());
        o.setStatus(r.status() != null ? r.status() : RecordStatus.ACTIVE);
        o.setUpdatedBy(access.actorId());
        return outcomeRepository.save(o);
    }

    public void deleteOutcome(UUID id) {
        LearningOutcome o = outcomeEntity(id);
        access.requireTeaches(o.getSubjectId(), o.getAcademicClassId(), "delete learning outcomes");
        if (count("SELECT count(*) FROM competency_rating WHERE learning_outcome_id = :id", id) > 0
                || count("SELECT count(*) FROM question_outcome WHERE learning_outcome_id = :id", id) > 0) {
            throw new BusinessException("OUTCOME_IN_USE", "Questions or ratings use this outcome; retire it instead");
        }
        queries.update("DELETE FROM syllabus_topic_outcome WHERE learning_outcome_id = :id", Map.of("id", id));
        o.setDeletedAt(Instant.now());
        outcomeRepository.save(o);
    }

    /** Maps the outcome to syllabus topics of its subject. */
    public OutcomeResponse setTopics(UUID id, List<UUID> topicIds) {
        LearningOutcome o = outcomeEntity(id);
        access.requireTeaches(o.getSubjectId(), o.getAcademicClassId(), "map learning outcomes");
        Set<UUID> ids = new LinkedHashSet<>(topicIds);
        if (!ids.isEmpty()) {
            long ok = ((Number) queries.rows("SELECT count(*) FROM syllabus_topic t JOIN syllabus_unit u ON u.id = t.unit_id "
                    + "JOIN syllabus sy ON sy.id = u.syllabus_id WHERE t.id IN (:ids) AND sy.subject_id = :s",
                    params("ids", ids, "s", o.getSubjectId())).get(0)[0]).longValue();
            if (ok != ids.size()) {
                throw new BusinessException("INVALID_TOPICS", "Every topic must belong to a syllabus of the outcome's subject");
            }
        }
        queries.update("DELETE FROM syllabus_topic_outcome WHERE learning_outcome_id = :id", Map.of("id", id));
        for (UUID t : ids) {
            queries.update("INSERT INTO syllabus_topic_outcome (syllabus_topic_id, learning_outcome_id) VALUES (:t, :o)", params("t", t, "o", id));
        }
        return outcome(id);
    }

    // ───────────────────────────── ratings ─────────────────────────────

    public SaveResult saveRatings(RatingsRequest req) {
        TermInfo term = queries.term(req.examTermId()).orElseThrow(() -> new ResourceNotFoundException("ExamTerm", req.examTermId()));
        Set<UUID> studentIds = new LinkedHashSet<>();
        req.ratings().forEach(r -> studentIds.add(r.studentId()));
        access.requireStudents(studentIds, "rate competencies");
        Map<UUID, Enrolled> enrolments = queries.enrolments(term.yearId(), studentIds);
        Map<UUID, List<String>> levelsByClass = new HashMap<>();
        Set<UUID> competencyIds = new HashSet<>();
        Set<UUID> outcomeIds = new HashSet<>();
        Set<String> seen = new HashSet<>();
        for (RatingItem r : req.ratings()) {
            if ((r.competencyId() == null) == (r.learningOutcomeId() == null)) {
                throw new BusinessException("INVALID_RATING", "Each rating names either a competency or a learning outcome");
            }
            if (!seen.add(r.studentId() + ":" + r.competencyId() + ":" + r.learningOutcomeId())) {
                throw new BusinessException("INVALID_RATING", "A student is rated twice on the same competency or outcome");
            }
            Enrolled e = enrolments.get(r.studentId());
            if (e == null) {
                throw new BusinessException("NOT_ENROLLED", "Student " + r.studentId() + " is not enrolled in the term's year");
            }
            List<String> levels = levelsByClass.computeIfAbsent(e.classId(), c -> scheme(term.yearId(), c)
                    .map(s -> SchemeService.list(s.getCompetencyLevels())).filter(l -> !l.isEmpty()).orElse(DEFAULT_LEVELS));
            if (!levels.contains(r.level())) {
                throw new BusinessException("INVALID_LEVEL", "Level " + r.level() + " is not one of " + levels);
            }
            if (r.competencyId() != null) competencyIds.add(r.competencyId());
            if (r.learningOutcomeId() != null) outcomeIds.add(r.learningOutcomeId());
        }
        competencyIds.forEach(this::competency);
        if (!outcomeIds.isEmpty() && outcomeRepository.findByIdInAndDeletedAtIsNull(outcomeIds).size() != outcomeIds.size()) {
            throw new BusinessException("INVALID_RATING", "Unknown learning outcome in the list");
        }
        UUID staff = access.staffId();
        UUID actor = access.actorId();
        for (RatingItem r : req.ratings()) {
            String target = r.competencyId() != null ? "(student_id, exam_term_id, competency_id) WHERE competency_id IS NOT NULL"
                    : "(student_id, exam_term_id, learning_outcome_id) WHERE learning_outcome_id IS NOT NULL";
            queries.update("INSERT INTO competency_rating (student_id, exam_term_id, competency_id, learning_outcome_id, level, evidence, "
                    + "rated_by_staff_id, created_by, updated_by) VALUES (:st, :t, :c, :o, :l, :e, :staff, :a, :a) "
                    + "ON CONFLICT " + target + " DO UPDATE SET level = EXCLUDED.level, evidence = EXCLUDED.evidence, "
                    + "rated_by_staff_id = EXCLUDED.rated_by_staff_id, updated_by = EXCLUDED.updated_by, updated_at = NOW(), "
                    + "version = competency_rating.version + 1",
                    params("st", r.studentId(), "t", req.examTermId(), "c", r.competencyId(), "o", r.learningOutcomeId(), "l", r.level(),
                            "e", r.evidence(), "staff", staff, "a", actor));
        }
        return new SaveResult(req.ratings().size());
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<RatingResponse> ratings(UUID termId, UUID studentId, UUID classId, UUID sectionId) {
        List<UUID> students = studentsFor(termId, studentId, classId, sectionId);
        if (students.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> names = queries.studentNames(students);
        return queries.rows("SELECT r.id, r.student_id, r.exam_term_id, r.competency_id, c.name, r.learning_outcome_id, o.code, o.statement, "
                + "r.level, r.evidence, r.rated_by_staff_id FROM competency_rating r LEFT JOIN competency c ON c.id = r.competency_id "
                + "LEFT JOIN learning_outcome o ON o.id = r.learning_outcome_id WHERE r.exam_term_id = :t AND r.student_id IN (:s) "
                + "ORDER BY r.student_id, c.code, o.code", params("t", termId, "s", students)).stream()
                .map(r -> new RatingResponse(uuid(r[0]), uuid(r[1]), names.get(uuid(r[1])), uuid(r[2]), uuid(r[3]), (String) r[4], uuid(r[5]),
                        (String) r[6], (String) r[7], (String) r[8], (String) r[9], uuid(r[10]))).toList();
    }

    // ───────────────────────────── co-scholastic grades ─────────────────────────────

    public SaveResult saveCoScholastic(CoScholasticRequest req) {
        TermInfo term = queries.term(req.examTermId()).orElseThrow(() -> new ResourceNotFoundException("ExamTerm", req.examTermId()));
        Set<UUID> studentIds = new LinkedHashSet<>();
        req.grades().forEach(g -> studentIds.add(g.studentId()));
        access.requireStudents(studentIds, "grade co-scholastic areas");
        Map<UUID, Enrolled> enrolments = queries.enrolments(term.yearId(), studentIds);
        Map<UUID, CoScholasticArea> areas = new HashMap<>();
        areaRepository.findAllById(req.grades().stream().map(CoScholasticItem::areaId).distinct().toList()).forEach(a -> areas.put(a.getId(), a));
        Map<UUID, Optional<AssessmentScheme>> schemes = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (CoScholasticItem g : req.grades()) {
            CoScholasticArea area = areas.get(g.areaId());
            Enrolled e = enrolments.get(g.studentId());
            if (area == null) {
                throw new ResourceNotFoundException("CoScholasticArea", g.areaId());
            }
            if (e == null) {
                throw new BusinessException("NOT_ENROLLED", "Student " + g.studentId() + " is not enrolled in the term's year");
            }
            if (!seen.add(g.studentId() + ":" + g.areaId())) {
                throw new BusinessException("INVALID_GRADE", "A student is graded twice in the same area");
            }
            AssessmentScheme scheme = schemes.computeIfAbsent(e.classId(), c -> scheme(term.yearId(), c)).orElse(null);
            if (scheme == null || !scheme.getId().equals(area.getSchemeId())) {
                throw new BusinessException("WRONG_SCHEME", area.getName() + " is not an area of the active scheme of " + e.name() + "'s class");
            }
            List<String> grades = SchemeService.list(scheme.getCoScholasticGrades());
            if (!grades.isEmpty() && !grades.contains(g.grade().toUpperCase(Locale.ROOT))) {
                throw new BusinessException("INVALID_GRADE", "Grade " + g.grade() + " is not one of " + grades);
            }
        }
        UUID staff = access.staffId();
        UUID actor = access.actorId();
        for (CoScholasticItem g : req.grades()) {
            queries.update("INSERT INTO co_scholastic_grade (area_id, exam_term_id, student_id, grade, remarks, graded_by_staff_id, created_by, updated_by) "
                    + "VALUES (:a, :t, :s, :g, :r, :staff, :u, :u) ON CONFLICT (area_id, exam_term_id, student_id) DO UPDATE SET "
                    + "grade = EXCLUDED.grade, remarks = EXCLUDED.remarks, graded_by_staff_id = EXCLUDED.graded_by_staff_id, "
                    + "updated_by = EXCLUDED.updated_by, updated_at = NOW(), version = co_scholastic_grade.version + 1",
                    params("a", g.areaId(), "t", req.examTermId(), "s", g.studentId(), "g", g.grade().toUpperCase(Locale.ROOT),
                            "r", g.remarks(), "staff", staff, "u", actor));
        }
        return new SaveResult(req.grades().size());
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<CoScholasticResponse> coScholastic(UUID termId, UUID studentId, UUID classId, UUID sectionId) {
        List<UUID> students = studentsFor(termId, studentId, classId, sectionId);
        if (students.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> names = queries.studentNames(students);
        return queries.rows("SELECT g.id, g.student_id, g.exam_term_id, a.id, a.code, a.name, a.area_group, g.grade, g.remarks "
                + "FROM co_scholastic_grade g JOIN co_scholastic_area a ON a.id = g.area_id WHERE g.exam_term_id = :t AND g.student_id IN (:s) "
                + "ORDER BY g.student_id, a.sort_order, a.code", params("t", termId, "s", students)).stream()
                .map(r -> new CoScholasticResponse(uuid(r[0]), uuid(r[1]), names.get(uuid(r[1])), uuid(r[2]), uuid(r[3]), (String) r[4],
                        (String) r[5], AreaGroup.valueOf((String) r[6]), (String) r[7], (String) r[8])).toList();
    }

    // ───────────────────────────── helpers ─────────────────────────────

    /** One student (checked) or the visible students of a class / section in the term's year. */
    private List<UUID> studentsFor(UUID termId, UUID studentId, UUID classId, UUID sectionId) {
        if (studentId != null) {
            access.requireStudent(studentId, "see ratings and grades");
            return List.of(studentId);
        }
        if (classId == null) {
            throw new BusinessException("FILTER_REQUIRED", "Give a studentId or an academicClassId");
        }
        access.requireClass(classId, sectionId, "see ratings and grades");
        TermInfo term = queries.term(termId).orElseThrow(() -> new ResourceNotFoundException("ExamTerm", termId));
        return queries.enrolled(term.yearId(), classId, sectionId).stream().map(Enrolled::studentId).toList();
    }

    private Optional<AssessmentScheme> scheme(UUID yearId, UUID classId) {
        return schemeRepository.findActiveFor(yearId, classId);
    }

    private Competency competency(UUID id) {
        return competencyRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("Competency", id));
    }

    private LearningOutcome outcomeEntity(UUID id) {
        return outcomeRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("LearningOutcome", id));
    }

    private long count(String sql, UUID id) {
        return ((Number) queries.rows(sql, Map.of("id", id)).get(0)[0]).longValue();
    }

    private CompetencyResponse toResponse(Competency c, String subjectName, long outcomes) {
        return new CompetencyResponse(c.getId(), c.getCode(), c.getName(), c.getDescription(), c.getCategory(), c.getSubjectId(), subjectName,
                c.getBoard(), c.getParentId(), c.getStatus(), outcomes);
    }

    private List<OutcomeResponse> toResponses(List<LearningOutcome> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = list.stream().map(LearningOutcome::getId).toList();
        Map<UUID, String> subjects = new HashMap<>();
        queries.subjects(list.stream().map(LearningOutcome::getSubjectId).toList()).forEach((k, v) -> subjects.put(k, v.name()));
        Map<UUID, String> classes = new HashMap<>();
        Set<UUID> classIds = new HashSet<>();
        list.stream().map(LearningOutcome::getAcademicClassId).filter(Objects::nonNull).forEach(classIds::add);
        if (!classIds.isEmpty()) {
            queries.rows("SELECT id, name FROM academic_class WHERE id IN (:ids)", Map.of("ids", classIds)).forEach(r -> classes.put(uuid(r[0]), (String) r[1]));
        }
        Map<UUID, String> competencies = new HashMap<>();
        Set<UUID> compIds = new HashSet<>();
        list.stream().map(LearningOutcome::getCompetencyId).filter(Objects::nonNull).forEach(compIds::add);
        competencyRepository.findAllById(compIds).forEach(c -> competencies.put(c.getId(), c.getName()));
        Map<UUID, List<UUID>> topics = new HashMap<>();
        queries.rows("SELECT learning_outcome_id, syllabus_topic_id FROM syllabus_topic_outcome WHERE learning_outcome_id IN (:ids)", Map.of("ids", ids))
                .forEach(r -> topics.computeIfAbsent(uuid(r[0]), k -> new ArrayList<>()).add(uuid(r[1])));
        Map<UUID, Long> questions = new HashMap<>();
        queries.rows("SELECT learning_outcome_id, count(*) FROM question_outcome WHERE learning_outcome_id IN (:ids) GROUP BY learning_outcome_id",
                Map.of("ids", ids)).forEach(r -> questions.put(uuid(r[0]), ((Number) r[1]).longValue()));
        return list.stream().map(o -> new OutcomeResponse(o.getId(), o.getCode(), o.getCompetencyId(), competencies.get(o.getCompetencyId()),
                o.getSubjectId(), subjects.get(o.getSubjectId()), o.getAcademicClassId(), classes.get(o.getAcademicClassId()), o.getStatement(),
                o.getBloomLevel(), o.getBoard(), o.getSortOrder(), o.getStatus(), topics.getOrDefault(o.getId(), List.of()),
                questions.getOrDefault(o.getId(), 0L))).toList();
    }
}
