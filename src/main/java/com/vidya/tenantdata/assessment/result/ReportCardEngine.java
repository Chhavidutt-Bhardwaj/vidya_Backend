package com.vidya.tenantdata.assessment.result;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.assessment.AssessmentAccess;
import com.vidya.tenantdata.assessment.AssessmentQueries;
import com.vidya.tenantdata.assessment.AssessmentQueries.Attendance;
import com.vidya.tenantdata.assessment.AssessmentQueries.EffectiveMark;
import com.vidya.tenantdata.assessment.AssessmentQueries.Enrolled;
import com.vidya.tenantdata.assessment.AssessmentQueries.ExamPaper;
import com.vidya.tenantdata.assessment.AssessmentQueries.Score;
import com.vidya.tenantdata.assessment.AssessmentQueries.SubjectInfo;
import com.vidya.tenantdata.assessment.AssessmentQueries.TermInfo;
import com.vidya.tenantdata.assessment.entity.Assessment;
import com.vidya.tenantdata.assessment.entity.AssessmentComponent;
import com.vidya.tenantdata.assessment.entity.AssessmentScheme;
import com.vidya.tenantdata.assessment.enums.AssessmentType;
import com.vidya.tenantdata.assessment.enums.ComponentAggregation;
import com.vidya.tenantdata.assessment.enums.ComponentSource;
import com.vidya.tenantdata.assessment.enums.SubjectCategory;
import com.vidya.tenantdata.assessment.enums.TermAggregation;
import com.vidya.tenantdata.assessment.repository.AssessmentComponentRepository;
import com.vidya.tenantdata.assessment.repository.AssessmentRepository;
import com.vidya.tenantdata.assessment.repository.AssessmentSchemeRepository;
import com.vidya.tenantdata.assessment.result.ResultCalculator.ComponentInput;
import com.vidya.tenantdata.assessment.result.ResultCalculator.ComponentResult;
import com.vidya.tenantdata.assessment.result.ResultCalculator.Mark;
import com.vidya.tenantdata.assessment.result.ResultCalculator.Outcome;
import com.vidya.tenantdata.assessment.result.ResultCalculator.Rules;
import com.vidya.tenantdata.assessment.result.ResultCalculator.SubjectInput;
import com.vidya.tenantdata.assessment.result.ResultCalculator.SubjectResult;
import com.vidya.tenantdata.assessment.result.ResultCalculator.TermScore;
import com.vidya.tenantdata.examination.entity.ExamTerm;
import com.vidya.tenantdata.examination.entity.GradingScale;
import com.vidya.tenantdata.examination.entity.GradingScaleTier;
import com.vidya.tenantdata.examination.grading.GradingConfigService;
import com.vidya.tenantdata.examination.grading.GradingTiers;
import com.vidya.tenantdata.examination.repository.GradingScaleRepository;
import com.vidya.tenantdata.reportcard.dto.AddReportCardRemarkRequest;
import com.vidya.tenantdata.reportcard.dto.BatchReportCardGenerationResponse;
import com.vidya.tenantdata.reportcard.dto.GenerateReportCardsRequest;
import com.vidya.tenantdata.reportcard.dto.PublishReportCardsRequest;
import com.vidya.tenantdata.reportcard.dto.ReportCardRemarkResponse;
import com.vidya.tenantdata.reportcard.dto.StudentReportCardResponse;
import com.vidya.tenantdata.reportcard.entity.ReportCardRemark;
import com.vidya.tenantdata.reportcard.entity.ReportCardSubjectResult;
import com.vidya.tenantdata.reportcard.entity.StudentReportCard;
import com.vidya.tenantdata.reportcard.enums.AuthorRole;
import com.vidya.tenantdata.reportcard.enums.ReportCardStatus;
import com.vidya.tenantdata.reportcard.enums.ResultStatus;
import com.vidya.tenantdata.reportcard.mapper.ReportCardMapper;
import com.vidya.tenantdata.reportcard.repository.ReportCardRemarkRepository;
import com.vidya.tenantdata.reportcard.repository.StudentReportCardRepository;
import com.vidya.tenantdata.reportcard.service.ReportCardService;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.subject.entity.Subject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.vidya.tenantdata.assessment.AssessmentQueries.params;

/**
 * Term results and report cards, board-aware. For each enrolled student it gathers the term's exam papers (after
 * approved grace / moderation / re-evaluation) and the assessments under each component of the class's active scheme,
 * runs {@link ResultCalculator}, grades subjects and the overall result on the scheme's scale, adds attendance, the
 * year-to-date cumulative result and section / class ranks.
 *
 * <p>Without an active scheme the class keeps working as before: each subject is its exam paper, out of the paper's
 * maximum, passing at the paper's pass marks, graded on the schedule / class / school scale.</p>
 *
 * <p>A subject appears on a student's card only if the student has at least one mark in it this term (so elective
 * subjects of other students are left out). Within such a subject, a component that was conducted for the student's
 * class or section but has no mark for the student counts as zero ("Not assessed").</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "tenantTransactionManager")
public class ReportCardEngine implements ReportCardService {

    private final StudentReportCardRepository cardRepository;
    private final ReportCardRemarkRepository remarkRepository;
    private final ReportCardMapper mapper;
    private final AssessmentSchemeRepository schemeRepository;
    private final AssessmentComponentRepository componentRepository;
    private final AssessmentRepository assessmentRepository;
    private final GradingScaleRepository gradingScaleRepository;
    private final GradingConfigService gradingConfigService;
    private final AssessmentQueries queries;
    private final AssessmentAccess access;
    private final Clock clock;

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    // ───────────────────────────── generation ─────────────────────────────

    @Override
    public BatchReportCardGenerationResponse generateReportCards(GenerateReportCardsRequest request, UUID generatedByStaffId) {
        TermInfo term = queries.term(request.getTermId()).orElseThrow(() -> new ResourceNotFoundException("ExamTerm", request.getTermId()));
        access.requireClass(request.getClassId(), request.getSectionId(), "generate report cards");
        UUID classId = request.getClassId();
        AssessmentScheme scheme = schemeRepository.findActiveFor(term.yearId(), classId).orElse(null);
        List<Enrolled> students = queries.enrolled(term.yearId(), classId, request.getSectionId());
        if (students.isEmpty()) {
            throw new BusinessException("NO_STUDENTS", "No students are enrolled in that class / section this year");
        }

        Inputs in = inputs(term, classId, scheme);
        List<GradingScaleTier> tiers = tiers(scheme, in.papers(), classId);
        Rules rules = scheme == null ? Rules.plain(null) : new Rules(scheme.getSubjectPassPercent(), scheme.getOverallPassPercent(),
                scheme.isSeparateComponentPass(), scheme.getRoundingMode(), scheme.getRoundingScale(), scheme.getBestOfSubjects(),
                scheme.getGraceMaxPerSubject(), scheme.getGraceMaxTotal(), scheme.getGraceMaxSubjects(), scheme.getCompartmentMaxSubjects());
        Map<UUID, Attendance> attendance = queries.attendance(students.stream().map(Enrolled::studentId).toList(), term.start(), term.end());
        List<TermInfo> earlierTerms = queries.termsOfYear(term.yearId()).stream()
                .filter(t -> !t.id().equals(term.id()) && t.start() != null && term.start() != null && t.start().isBefore(term.start())).toList();
        Map<String, BigDecimal> earlier = earlierPercentages(earlierTerms, students);

        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (Enrolled e : students) {
            List<SubjectInput> subjects = subjectsFor(e, in, scheme);
            Outcome outcome = ResultCalculator.compute(rules, subjects);
            Optional<StudentReportCard> existing = cardRepository.findByTermAndStudent(term.id(), e.studentId());
            if (existing.isPresent() && existing.get().getStatus() == ReportCardStatus.PUBLISHED) {
                skipped++;
                continue;
            }
            StudentReportCard card = existing.orElseGet(() -> new StudentReportCard(CodeGenerator.reportCardCode(), em.getReference(ExamTerm.class, term.id()),
                    em.getReference(AcademicYear.class, term.yearId()), em.getReference(AcademicClass.class, classId),
                    e.sectionId() == null ? null : em.getReference(Section.class, e.sectionId()), em.getReference(Student.class, e.studentId())));
            if (existing.isPresent()) {
                updated++;
                card.clearSubjectResults();
                card.setSection(e.sectionId() == null ? null : em.getReference(Section.class, e.sectionId()));
            } else {
                created++;
            }
            fill(card, outcome, tiers, in, e, scheme, attendance.get(e.studentId()), earlierTerms, earlier, term);
            card.setStatus(ReportCardStatus.GENERATED);
            card.setUpdatedBy(access.actorId());
            if (card.getCreatedBy() == null) {
                card.setCreatedBy(access.actorId());
            }
            cardRepository.save(card);
        }
        cardRepository.flush();
        rank(term.id(), classId, scheme == null || scheme.isRankEnabled());
        log.info("Report cards for term [{}] class [{}]: {} created, {} updated, {} published left unchanged", term.id(), classId, created, updated, skipped);
        return BatchReportCardGenerationResponse.builder().examTermId(term.id()).academicClassId(classId).sectionId(request.getSectionId())
                .totalStudentsProcessed(students.size()).generatedCount(created).updatedCount(updated).completedAt(clock.instant()).build();
    }

    /** Everything recorded for the class in the term. */
    record Inputs(List<ExamPaper> papers, Map<String, EffectiveMark> paperMarks, List<AssessmentComponent> components,
                  List<Assessment> assessments, Map<String, Score> scores, Map<UUID, SubjectInfo> subjects,
                  Map<UUID, SubjectCategory> categories, Map<UUID, BigDecimal> passPercents) {
    }

    private Inputs inputs(TermInfo term, UUID classId, AssessmentScheme scheme) {
        List<ExamPaper> papers = queries.examPapers(term.id(), classId);
        Map<String, EffectiveMark> paperMarks = new HashMap<>();
        queries.effectiveMarks(papers.stream().map(ExamPaper::configId).toList()).forEach(m -> paperMarks.put(m.configId() + ":" + m.studentId(), m));
        List<AssessmentComponent> components = scheme == null ? List.of() : componentRepository.findBySchemeIdOrderBySortOrderAscCodeAsc(scheme.getId())
                .stream().filter(c -> c.getExamTermId() == null || c.getExamTermId().equals(term.id())).toList();
        Set<UUID> componentIds = components.stream().map(AssessmentComponent::getId).collect(Collectors.toSet());
        List<Assessment> assessments = scheme == null ? List.of() : assessmentRepository.search(term.id(), classId, null, null).stream()
                .filter(a -> componentIds.contains(a.getComponentId())).toList();
        Map<String, Score> scores = new HashMap<>();
        queries.scores(assessments.stream().map(Assessment::getId).toList()).forEach(s -> scores.put(s.assessmentId() + ":" + s.studentId(), s));
        Set<UUID> subjectIds = new LinkedHashSet<>();
        papers.forEach(p -> subjectIds.add(p.subjectId()));
        assessments.forEach(a -> subjectIds.add(a.getSubjectId()));
        Map<UUID, SubjectCategory> categories = new HashMap<>();
        Map<UUID, BigDecimal> passPercents = new HashMap<>();
        if (scheme != null) {
            queries.rows("SELECT subject_id, category, pass_percent FROM assessment_scheme_subject WHERE scheme_id = :s", Map.of("s", scheme.getId()))
                    .forEach(r -> {
                        UUID id = AssessmentQueries.uuid(r[0]);
                        categories.put(id, SubjectCategory.valueOf((String) r[1]));
                        if (r[2] != null) {
                            passPercents.put(id, (BigDecimal) r[2]);
                        }
                    });
        }
        return new Inputs(papers, paperMarks, components, assessments, scores, queries.subjects(subjectIds), categories, passPercents);
    }

    /** The calculator input for one student. */
    List<SubjectInput> subjectsFor(Enrolled e, Inputs in, AssessmentScheme scheme) {
        List<SubjectInput> out = new ArrayList<>();
        for (SubjectInfo subject : in.subjects().values().stream().sorted((a, b) -> a.name().compareToIgnoreCase(b.name())).toList()) {
            List<ExamPaper> papers = in.papers().stream().filter(p -> p.subjectId().equals(subject.id())).toList();
            List<Mark> paperMarks = new ArrayList<>();
            for (ExamPaper p : papers) {
                EffectiveMark m = in.paperMarks().get(p.configId() + ":" + e.studentId());
                if (m != null) {
                    paperMarks.add(new Mark(m.marks(), p.maxMarks(), m.absent(), m.exempt(), p.examDate()));
                }
            }
            List<ComponentInput> components = new ArrayList<>();
            boolean anyMark = !paperMarks.isEmpty();
            if (scheme == null) {
                if (papers.isEmpty()) {
                    continue;
                }
                BigDecimal max = papers.get(papers.size() - 1).maxMarks();
                BigDecimal pass = papers.get(papers.size() - 1).passMarks();
                components.add(new ComponentInput(null, "EXAM", "Examination", AssessmentType.SUMMATIVE, max, ComponentAggregation.LATEST, null,
                        false, true, paperMarks));
                if (!anyMark) {
                    continue;
                }
                out.add(new SubjectInput(subject.id(), subject.name(), subject.code(), SubjectCategory.SCHOLASTIC,
                        pass == null || max == null || max.signum() == 0 ? null : pass.multiply(BigDecimal.valueOf(100)).divide(max, 2, RoundingMode.HALF_UP),
                        components));
                continue;
            }
            for (AssessmentComponent c : in.components()) {
                List<Mark> marks;
                boolean conducted;
                if (c.getSource() == ComponentSource.EXAM_PAPER) {
                    conducted = !papers.isEmpty();
                    marks = paperMarks;
                } else {
                    List<Assessment> own = in.assessments().stream().filter(a -> a.getComponentId().equals(c.getId())
                            && a.getSubjectId().equals(subject.id()) && (a.getSectionId() == null || a.getSectionId().equals(e.sectionId()))).toList();
                    conducted = !own.isEmpty();
                    marks = new ArrayList<>();
                    for (Assessment a : own) {
                        Score s = in.scores().get(a.getId() + ":" + e.studentId());
                        if (s != null) {
                            marks.add(new Mark(s.marks(), a.getMaxMarks(), s.absent(), s.exempt(), a.getAssessmentDate()));
                            anyMark = true;
                        }
                    }
                }
                components.add(new ComponentInput(c.getId(), c.getCode(), c.getName(), c.getAssessmentType(), c.getWeight(), c.getAggregation(),
                        c.getBestOf(), c.isPassRequired(), conducted, marks));
            }
            if (anyMark) {
                out.add(new SubjectInput(subject.id(), subject.name(), subject.code(), in.categories().getOrDefault(subject.id(), SubjectCategory.SCHOLASTIC),
                        in.passPercents().get(subject.id()), components));
            }
        }
        return out;
    }

    private void fill(StudentReportCard card, Outcome outcome, List<GradingScaleTier> tiers, Inputs in, Enrolled e, AssessmentScheme scheme,
                      Attendance attendance, List<TermInfo> earlierTerms, Map<String, BigDecimal> earlier, TermInfo term) {
        List<BigDecimal> points = new ArrayList<>();
        boolean allPoints = true;
        for (SubjectResult s : outcome.subjects()) {
            Optional<GradingScaleTier> tier = s.exempt() ? Optional.empty() : GradingTiers.match(tiers, s.percentage());
            BigDecimal adjustment = in.papers().stream().filter(p -> p.subjectId().equals(s.subjectId()))
                    .map(p -> in.paperMarks().get(p.configId() + ":" + e.studentId())).filter(Objects::nonNull)
                    .map(EffectiveMark::adjustment).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            EffectiveMark single = singlePaperMark(in, s.subjectId(), e.studentId());
            ReportCardSubjectResult r = new ReportCardSubjectResult(card, em.getReference(Subject.class, s.subjectId()), s.name(),
                    s.code() != null ? s.code() : s.name(), s.maxMarks(), s.passMarks(), null, null, s.marks(), s.percentage(), tier.map(GradingScaleTier::getGrade).orElse(s.exempt() ? "EX" : null),
                    tier.map(GradingScaleTier::getGradePoint).orElse(null), s.passing(), s.absent(), s.exempt());
            if (single != null) {
                r.setTheoryMarks(paperPart(single, true));
                r.setPracticalMarks(paperPart(single, false));
            }
            r.setCategory(s.category().name());
            r.setGraceMarks(s.graceMarks());
            r.setAdjustmentMarks(adjustment);
            r.setCountedInTotal(s.countedInTotal());
            r.setComponentBreakdown(breakdown(s));
            card.addSubjectResult(r);
            if (s.countedInTotal()) {
                BigDecimal gp = tier.map(GradingScaleTier::getGradePoint).orElse(null);
                if (gp == null || gp.signum() == 0 && tier.map(GradingScaleTier::isPassing).orElse(false)) {
                    allPoints = false;
                } else {
                    points.add(gp);
                }
            }
        }
        card.setTotalMaxMarks(outcome.totalMax());
        card.setTotalMarksObtained(outcome.totalObtained());
        card.setPercentage(outcome.percentage());
        card.setOverallGrade(outcome.status() == ResultStatus.WITHHELD ? null
                : GradingTiers.match(tiers, outcome.percentage()).map(GradingScaleTier::getGrade).orElse(null));
        card.setCgpa(allPoints && !points.isEmpty() ? points.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(points.size()), 1, RoundingMode.HALF_UP) : null);
        card.setResultStatus(outcome.status());
        card.setGraceApplied(outcome.graceApplied());
        card.setResultNote(outcome.note() == null ? null : outcome.note().length() > 500 ? outcome.note().substring(0, 500) : outcome.note());
        card.setAssessmentSchemeId(scheme == null ? null : scheme.getId());
        int working = attendance == null ? 0 : attendance.working();
        int present = attendance == null ? 0 : attendance.present();
        card.setAttendanceWorkingDays(working);
        card.setAttendancePresentDays(present);
        card.setAttendancePercentage(ResultCalculator.percent(BigDecimal.valueOf(present), BigDecimal.valueOf(working)));
        List<TermScore> history = new ArrayList<>();
        for (TermInfo t : earlierTerms) {
            history.add(new TermScore(earlier.get(t.id() + ":" + e.studentId()), t.weight()));
        }
        history.add(new TermScore(outcome.status() == ResultStatus.WITHHELD ? null : outcome.percentage(), term.weight()));
        TermAggregation aggregation = scheme == null ? TermAggregation.WEIGHTED : scheme.getTermAggregation();
        BigDecimal cumulative = earlierTerms.isEmpty() ? null : ResultCalculator.cumulative(aggregation, history);
        card.setCumulativePercentage(cumulative);
        card.setCumulativeGrade(cumulative == null ? null : GradingTiers.match(tiers, cumulative).map(GradingScaleTier::getGrade).orElse(null));
    }

    private static EffectiveMark singlePaperMark(Inputs in, UUID subjectId, UUID studentId) {
        List<ExamPaper> papers = in.papers().stream().filter(p -> p.subjectId().equals(subjectId)).toList();
        return papers.size() == 1 ? in.paperMarks().get(papers.get(0).configId() + ":" + studentId) : null;
    }

    private BigDecimal paperPart(EffectiveMark m, boolean theory) {
        List<Object[]> r = queries.rows("SELECT theory_marks_obtained, practical_marks_obtained FROM student_mark_entry "
                + "WHERE exam_subject_config_id = :c AND student_id = :s AND deleted_at IS NULL", params("c", m.configId(), "s", m.studentId()));
        return r.isEmpty() ? null : (BigDecimal) r.get(0)[theory ? 0 : 1];
    }

    private static Map<String, Object> breakdown(SubjectResult s) {
        List<Map<String, Object>> comps = new ArrayList<>();
        for (ComponentResult c : s.components()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", c.code());
            m.put("name", c.name());
            m.put("type", c.type() == null ? null : c.type().name());
            m.put("weight", c.weight());
            m.put("included", c.included());
            m.put("percentage", c.percentage());
            m.put("score", c.score() == null ? null : c.score().setScale(2, RoundingMode.HALF_UP));
            m.put("marksCounted", c.marksCounted());
            m.put("note", c.note());
            comps.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("components", comps);
        out.put("rawMarks", s.rawMarks());
        out.put("note", s.note());
        return out;
    }

    private Map<String, BigDecimal> earlierPercentages(List<TermInfo> terms, List<Enrolled> students) {
        Map<String, BigDecimal> out = new HashMap<>();
        if (terms.isEmpty()) {
            return out;
        }
        queries.rows("SELECT exam_term_id, student_id, percentage FROM student_report_card WHERE exam_term_id IN (:t) AND student_id IN (:s) "
                        + "AND deleted_at IS NULL AND status IN ('GENERATED', 'PUBLISHED') AND result_status <> 'WITHHELD'",
                params("t", terms.stream().map(TermInfo::id).toList(), "s", students.stream().map(Enrolled::studentId).toList()))
                .forEach(r -> out.put(r[0] + ":" + r[1], (BigDecimal) r[2]));
        return out;
    }

    /** Section and class ranks by percentage (ties share a rank: 1, 2, 2, 4), withheld results unranked. */
    private void rank(UUID termId, UUID classId, boolean enabled) {
        if (!enabled) {
            queries.update("UPDATE student_report_card SET section_rank = NULL, class_rank = NULL WHERE exam_term_id = :t AND academic_class_id = :c "
                    + "AND deleted_at IS NULL AND status <> 'PUBLISHED'", params("t", termId, "c", classId));
            return;
        }
        queries.update("UPDATE student_report_card r SET class_rank = x.cr, section_rank = x.sr FROM ("
                + "SELECT id, RANK() OVER (ORDER BY percentage DESC) AS cr, RANK() OVER (PARTITION BY section_id ORDER BY percentage DESC) AS sr "
                + "FROM student_report_card WHERE exam_term_id = :t AND academic_class_id = :c AND deleted_at IS NULL "
                + "AND status IN ('GENERATED', 'PUBLISHED') AND result_status <> 'WITHHELD') x "
                + "WHERE r.id = x.id AND r.status <> 'PUBLISHED'", params("t", termId, "c", classId));
    }

    private List<GradingScaleTier> tiers(AssessmentScheme scheme, List<ExamPaper> papers, UUID classId) {
        GradingScale scale = null;
        if (scheme != null && scheme.getScholasticGradingScaleId() != null) {
            scale = gradingScaleRepository.findActiveById(scheme.getScholasticGradingScaleId()).orElse(null);
        }
        if (scale == null) {
            UUID scheduleScale = papers.stream().map(ExamPaper::gradingScaleId).filter(Objects::nonNull).findFirst().orElse(null);
            GradingScale fromSchedule = scheduleScale == null ? null : gradingScaleRepository.findActiveById(scheduleScale).orElse(null);
            scale = gradingConfigService.resolve(fromSchedule, em.find(AcademicClass.class, classId)).scale();
        }
        return scale == null ? List.of() : scale.getTiers();
    }

    // ───────────────────────────── reading, remarks, publishing ─────────────────────────────

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public StudentReportCardResponse getReportCardById(UUID id) {
        StudentReportCard card = card(id);
        requireVisible(card);
        return mapper.toResponse(card);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<StudentReportCardResponse> getReportCardsByStudent(UUID studentId) {
        access.requireStudent(studentId, "see report cards");
        boolean familyOnly = familyOnly(studentId);
        return cardRepository.findByStudentId(studentId).stream()
                .filter(c -> !familyOnly || c.getStatus() == ReportCardStatus.PUBLISHED).map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Page<StudentReportCardResponse> searchReportCards(UUID termId, UUID classId, UUID sectionId, ReportCardStatus status, Pageable pageable) {
        if (classId == null) {
            if (!access.all()) {
                throw new AccessDeniedException("Filter by class");
            }
        } else {
            access.requireClass(classId, sectionId, "see report cards");
        }
        return cardRepository.searchReportCards(termId, classId, sectionId, status, pageable).map(mapper::toResponse);
    }

    @Override
    public ReportCardRemarkResponse addOrUpdateRemark(UUID reportCardId, AddReportCardRemarkRequest request, UUID staffId) {
        StudentReportCard card = card(reportCardId);
        access.requireClass(card.getAcademicClass().getId(), card.getSection() == null ? null : card.getSection().getId(), "write remarks");
        if (card.getStatus() == ReportCardStatus.PUBLISHED) {
            throw new BusinessException("REPORT_CARD_PUBLISHED", "Revoke the report card before changing remarks");
        }
        AuthorRole role = request.getAuthorRole() != null ? request.getAuthorRole() : AuthorRole.CLASS_TEACHER;
        UUID author = staffId != null ? staffId : access.staffId();
        Staff staff = author == null ? null : em.getReference(Staff.class, author);
        ReportCardRemark remark = remarkRepository.findByReportCardIdAndAuthorRole(reportCardId, role).orElse(null);
        if (remark == null) {
            remark = new ReportCardRemark(card, staff, role, request.getRemark().trim(), request.getConductRating());
            card.addRemark(remark);
        } else {
            remark.setRemark(request.getRemark().trim());
            remark.setConductRating(request.getConductRating());
            remark.setAuthorStaff(staff);
        }
        remarkRepository.save(remark);
        return mapper.toRemarkResponse(remark);
    }

    @Override
    public int publishReportCards(PublishReportCardsRequest request) {
        access.requireClass(request.getClassId(), request.getSectionId(), "publish report cards");
        List<StudentReportCard> cards = request.getSectionId() == null ? cardRepository.findByTermAndClass(request.getTermId(), request.getClassId())
                : cardRepository.findByTermAndClassAndSection(request.getTermId(), request.getClassId(), request.getSectionId());
        int n = 0;
        for (StudentReportCard c : cards) {
            if (c.getStatus() == ReportCardStatus.GENERATED) {
                c.publish();
                c.setPublishedAt(clock.instant());
                cardRepository.save(c);
                n++;
            }
        }
        return n;
    }

    @Override
    public StudentReportCardResponse revokeReportCard(UUID id) {
        StudentReportCard card = card(id);
        access.requireClass(card.getAcademicClass().getId(), card.getSection() == null ? null : card.getSection().getId(), "revoke report cards");
        if (card.getStatus() != ReportCardStatus.PUBLISHED) {
            throw new BusinessException("NOT_PUBLISHED", "Only a published report card can be revoked");
        }
        card.revoke();
        return mapper.toResponse(cardRepository.save(card));
    }

    StudentReportCard card(UUID id) {
        return cardRepository.findActiveById(id).orElseThrow(() -> new ResourceNotFoundException("StudentReportCard", id));
    }

    /** Families see published cards only; staff who can see the student see every state. */
    void requireVisible(StudentReportCard card) {
        UUID studentId = card.getStudent().getId();
        access.requireStudent(studentId, "see report cards");
        if (familyOnly(studentId) && card.getStatus() != ReportCardStatus.PUBLISHED) {
            throw new AccessDeniedException("The report card is not published yet");
        }
    }

    private boolean familyOnly(UUID studentId) {
        return !access.all() && access.own().staffIds().isEmpty() && access.familyStudentIds().contains(studentId);
    }
}
