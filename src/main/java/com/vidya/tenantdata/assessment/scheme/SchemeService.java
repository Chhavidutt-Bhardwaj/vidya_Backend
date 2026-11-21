package com.vidya.tenantdata.assessment.scheme;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.assessment.AssessmentAccess;
import com.vidya.tenantdata.assessment.repository.AssessmentQueries;
import com.vidya.tenantdata.assessment.repository.AssessmentQueries.TermInfo;
import com.vidya.tenantdata.assessment.entity.AssessmentComponent;
import com.vidya.tenantdata.assessment.entity.AssessmentScheme;
import com.vidya.tenantdata.assessment.entity.CoScholasticArea;
import com.vidya.tenantdata.assessment.enums.AreaGroup;
import com.vidya.tenantdata.assessment.enums.ComponentAggregation;
import com.vidya.tenantdata.assessment.enums.ComponentSource;
import com.vidya.tenantdata.assessment.enums.SchemeStatus;
import com.vidya.tenantdata.assessment.enums.SubjectCategory;
import com.vidya.tenantdata.assessment.repository.AssessmentComponentRepository;
import com.vidya.tenantdata.assessment.repository.AssessmentRepository;
import com.vidya.tenantdata.assessment.repository.AssessmentSchemeRepository;
import com.vidya.tenantdata.assessment.repository.CoScholasticAreaRepository;
import com.vidya.tenantdata.assessment.scheme.SchemeDtos.AreaRequest;
import com.vidya.tenantdata.assessment.scheme.SchemeDtos.AreaResponse;
import com.vidya.tenantdata.assessment.scheme.SchemeDtos.ComponentRequest;
import com.vidya.tenantdata.assessment.scheme.SchemeDtos.ComponentResponse;
import com.vidya.tenantdata.assessment.scheme.SchemeDtos.CopySchemeRequest;
import com.vidya.tenantdata.assessment.scheme.SchemeDtos.CreateSchemeRequest;
import com.vidya.tenantdata.assessment.scheme.SchemeDtos.NamedRef;
import com.vidya.tenantdata.assessment.scheme.SchemeDtos.SchemeResponse;
import com.vidya.tenantdata.assessment.scheme.SchemeDtos.SubjectRuleRequest;
import com.vidya.tenantdata.assessment.scheme.SchemeDtos.SubjectRuleResponse;
import com.vidya.tenantdata.assessment.scheme.SchemeDtos.UpdateSchemeRequest;
import com.vidya.tenantdata.examination.grading.GradingConfigService;
import com.vidya.tenantdata.examination.grading.GradingPresets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.vidya.tenantdata.assessment.repository.AssessmentQueries.params;
import static com.vidya.tenantdata.assessment.repository.AssessmentQueries.uuid;

/**
 * Assessment schemes: one per group of classes per academic year, so a school can run CBSE in some classes and IGCSE or
 * a state board in others. Results, co-scholastic grades and competency levels of a class follow its ACTIVE scheme.
 */
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "tenantTransactionManager")
public class SchemeService {

    private final AssessmentSchemeRepository schemeRepository;
    private final AssessmentComponentRepository componentRepository;
    private final CoScholasticAreaRepository areaRepository;
    private final AssessmentRepository assessmentRepository;
    private final GradingConfigService gradingConfigService;
    private final AssessmentQueries queries;
    private final AssessmentAccess access;

    public List<BoardPresets.Preset> presets() {
        return BoardPresets.all();
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<SchemeResponse> list(UUID academicYearId) {
        return schemeRepository.findByAcademicYearIdAndDeletedAtIsNullOrderByName(academicYearId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public SchemeResponse get(UUID id) {
        return toResponse(find(id));
    }

    /** The ACTIVE scheme of a class in a year, if any. */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Optional<SchemeResponse> forClass(UUID academicYearId, UUID classId) {
        return schemeRepository.findActiveFor(academicYearId, classId).map(this::toResponse);
    }

    public SchemeResponse create(CreateSchemeRequest req) {
        requireYear(req.academicYearId());
        BoardPresets.Preset preset = null;
        if (req.presetKey() != null && !req.presetKey().isBlank()) {
            preset = BoardPresets.find(req.presetKey()).orElseThrow(() -> new BusinessException("UNKNOWN_PRESET",
                    "Unknown preset " + req.presetKey() + "; see GET /api/v1/assessment-schemes/presets"));
        }
        if (preset == null && req.board() == null) {
            throw new BusinessException("BOARD_REQUIRED", "Give a presetKey or a board");
        }
        nameFree(req.academicYearId(), req.name(), null);
        AssessmentScheme s = new AssessmentScheme();
        s.setSchemeCode(CodeGenerator.assessmentSchemeCode());
        s.setName(req.name().trim());
        s.setAcademicYearId(req.academicYearId());
        s.setDescription(req.description());
        s.setBoard(req.board() != null ? req.board() : preset.board());
        s.setCreatedBy(access.actorId());
        if (preset != null) {
            s.setPresetKey(preset.key());
            s.setSubjectPassPercent(preset.subjectPassPercent());
            s.setSeparateComponentPass(preset.separateComponentPass());
            s.setRoundingMode(preset.rounding());
            s.setRoundingScale(preset.roundingScale());
            s.setBestOfSubjects(preset.bestOfSubjects());
            s.setTermAggregation(preset.termAggregation());
            s.setCompartmentMaxSubjects(preset.compartmentMaxSubjects());
            s.setRankEnabled(preset.rankEnabled());
            s.setCoScholasticGrades(preset.coScholasticGrades());
            s.setCompetencyLevels(preset.competencyLevels());
            if (s.getDescription() == null) {
                s.setDescription(preset.description());
            }
        }
        s.setScholasticGradingScaleId(req.scholasticGradingScaleId() != null ? requireScale(req.scholasticGradingScaleId())
                : preset != null ? ensureScale(preset.gradingPresetKey()) : null);
        s = schemeRepository.save(s);
        if (preset != null) {
            int order = 0;
            for (BoardPresets.Component c : preset.components()) {
                AssessmentComponent comp = new AssessmentComponent();
                comp.setSchemeId(s.getId());
                comp.setCode(c.code());
                comp.setName(c.name());
                comp.setAssessmentType(c.type());
                comp.setWeight(BigDecimal.valueOf(c.weight()));
                comp.setAggregation(c.aggregation());
                comp.setBestOf(c.bestOf());
                comp.setSource(c.source());
                comp.setPassRequired(c.passRequired());
                comp.setSortOrder(++order);
                componentRepository.save(comp);
            }
            order = 0;
            for (BoardPresets.Area a : preset.areas()) {
                CoScholasticArea area = new CoScholasticArea();
                area.setSchemeId(s.getId());
                area.setCode(a.code());
                area.setName(a.name());
                area.setAreaGroup(a.group());
                area.setSortOrder(++order);
                areaRepository.save(area);
            }
        }
        if (req.classIds() != null && !req.classIds().isEmpty()) {
            assignClasses(s, req.classIds());
        }
        return toResponse(s);
    }

    public SchemeResponse update(UUID id, UpdateSchemeRequest r) {
        AssessmentScheme s = find(id);
        if (r.name() != null) {
            nameFree(s.getAcademicYearId(), r.name(), s.getId());
            s.setName(r.name().trim());
        }
        if (r.description() != null) s.setDescription(r.description());
        if (r.scholasticGradingScaleId() != null) s.setScholasticGradingScaleId(requireScale(r.scholasticGradingScaleId()));
        if (r.coScholasticGrades() != null) s.setCoScholasticGrades(r.coScholasticGrades().toUpperCase(Locale.ROOT));
        if (r.competencyLevels() != null) s.setCompetencyLevels(r.competencyLevels());
        if (r.subjectPassPercent() != null) s.setSubjectPassPercent(r.subjectPassPercent());
        if (r.overallPassPercent() != null) s.setOverallPassPercent(r.overallPassPercent());
        if (Boolean.TRUE.equals(r.clearOverallPassPercent())) s.setOverallPassPercent(null);
        if (r.separateComponentPass() != null) s.setSeparateComponentPass(r.separateComponentPass());
        if (r.roundingMode() != null) s.setRoundingMode(r.roundingMode());
        if (r.roundingScale() != null) s.setRoundingScale(r.roundingScale());
        if (r.bestOfSubjects() != null) s.setBestOfSubjects(r.bestOfSubjects() == 0 ? null : r.bestOfSubjects());
        if (r.termAggregation() != null) s.setTermAggregation(r.termAggregation());
        if (r.graceMaxPerSubject() != null) s.setGraceMaxPerSubject(r.graceMaxPerSubject());
        if (r.graceMaxTotal() != null) s.setGraceMaxTotal(r.graceMaxTotal());
        if (r.graceMaxSubjects() != null) s.setGraceMaxSubjects(r.graceMaxSubjects());
        if (r.compartmentMaxSubjects() != null) s.setCompartmentMaxSubjects(r.compartmentMaxSubjects());
        if (r.reevaluationWindowDays() != null) s.setReevaluationWindowDays(r.reevaluationWindowDays());
        if (r.rankEnabled() != null) s.setRankEnabled(r.rankEnabled());
        return toResponse(schemeRepository.save(s));
    }

    /** Replaces the component list; components are matched by code so recorded assessments keep their component. */
    public SchemeResponse replaceComponents(UUID id, List<ComponentRequest> requested) {
        AssessmentScheme s = find(id);
        Set<UUID> yearTerms = queries.termsOfYear(s.getAcademicYearId()).stream().map(TermInfo::id).collect(Collectors.toSet());
        List<String> errors = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        List<UUID> examPaperTerms = new ArrayList<>();
        for (ComponentRequest c : requested) {
            if (!codes.add(c.code().toUpperCase(Locale.ROOT))) {
                errors.add("code " + c.code() + " appears twice");
            }
            if ((c.aggregation() == ComponentAggregation.BEST_OF) != (c.bestOf() != null)) {
                errors.add(c.code() + ": bestOf is set exactly when aggregation is BEST_OF");
            }
            if (c.examTermId() != null && !yearTerms.contains(c.examTermId())) {
                errors.add(c.code() + ": the exam term is not a term of the scheme's year");
            }
            if (c.source() == ComponentSource.EXAM_PAPER) {
                examPaperTerms.add(c.examTermId());
            }
        }
        if (examPaperTerms.size() > 1 && (examPaperTerms.contains(null) || new HashSet<>(examPaperTerms).size() != examPaperTerms.size())) {
            errors.add("only one EXAM_PAPER component may apply to a term (the term's exam paper would count twice)");
        }
        Map<String, AssessmentComponent> existing = componentRepository.findBySchemeIdOrderBySortOrderAscCodeAsc(id).stream()
                .collect(Collectors.toMap(c -> c.getCode().toUpperCase(Locale.ROOT), Function.identity()));
        for (Map.Entry<String, AssessmentComponent> e : existing.entrySet()) {
            if (!codes.contains(e.getKey()) && assessmentRepository.existsByComponentIdAndDeletedAtIsNull(e.getValue().getId())) {
                errors.add("component " + e.getValue().getCode() + " has recorded assessments and cannot be removed");
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException("INVALID_COMPONENTS", String.join("; ", errors));
        }
        for (Map.Entry<String, AssessmentComponent> e : existing.entrySet()) {
            if (!codes.contains(e.getKey())) {
                componentRepository.delete(e.getValue());
            }
        }
        for (ComponentRequest c : requested) {
            AssessmentComponent comp = existing.getOrDefault(c.code().toUpperCase(Locale.ROOT), new AssessmentComponent());
            comp.setSchemeId(id);
            comp.setCode(c.code());
            comp.setName(c.name());
            comp.setAssessmentType(c.assessmentType());
            comp.setExamTermId(c.examTermId());
            comp.setWeight(c.weight());
            comp.setAggregation(c.aggregation());
            comp.setBestOf(c.bestOf());
            comp.setSource(c.source());
            comp.setPassRequired(c.passRequired());
            comp.setSortOrder(c.sortOrder());
            componentRepository.save(comp);
        }
        componentRepository.flush();
        return toResponse(s);
    }

    public SchemeResponse replaceAreas(UUID id, List<AreaRequest> requested) {
        AssessmentScheme s = find(id);
        Set<String> codes = new HashSet<>();
        for (AreaRequest a : requested) {
            if (!codes.add(a.code().toUpperCase(Locale.ROOT))) {
                throw new BusinessException("INVALID_AREAS", "code " + a.code() + " appears twice");
            }
        }
        Map<String, CoScholasticArea> existing = areaRepository.findBySchemeIdOrderBySortOrderAscCodeAsc(id).stream()
                .collect(Collectors.toMap(a -> a.getCode().toUpperCase(Locale.ROOT), Function.identity()));
        for (Map.Entry<String, CoScholasticArea> e : existing.entrySet()) {
            if (!codes.contains(e.getKey())) {
                long graded = ((Number) queries.rows("SELECT count(*) FROM co_scholastic_grade WHERE area_id = :a",
                        Map.of("a", e.getValue().getId())).get(0)[0]).longValue();
                if (graded > 0) {
                    throw new BusinessException("AREA_IN_USE", e.getValue().getName() + " has " + graded + " grade(s) and cannot be removed");
                }
                areaRepository.delete(e.getValue());
            }
        }
        for (AreaRequest a : requested) {
            CoScholasticArea area = existing.getOrDefault(a.code().toUpperCase(Locale.ROOT), new CoScholasticArea());
            area.setSchemeId(id);
            area.setCode(a.code());
            area.setName(a.name());
            area.setAreaGroup(a.areaGroup() != null ? a.areaGroup() : AreaGroup.CO_SCHOLASTIC);
            area.setDescription(a.description());
            area.setSortOrder(a.sortOrder());
            areaRepository.save(area);
        }
        areaRepository.flush();
        return toResponse(s);
    }

    /** Subjects that are not plain main subjects; subjects not listed are SCHOLASTIC. */
    public SchemeResponse replaceSubjectRules(UUID id, List<SubjectRuleRequest> rules) {
        AssessmentScheme s = find(id);
        Set<UUID> seen = new HashSet<>();
        for (SubjectRuleRequest r : rules) {
            if (!seen.add(r.subjectId())) {
                throw new BusinessException("INVALID_SUBJECTS", "A subject is listed twice");
            }
        }
        if (!seen.isEmpty() && queries.subjects(seen).size() != seen.size()) {
            throw new BusinessException("INVALID_SUBJECTS", "Unknown subject in the list");
        }
        queries.update("DELETE FROM assessment_scheme_subject WHERE scheme_id = :s", Map.of("s", id));
        for (SubjectRuleRequest r : rules) {
            queries.update("INSERT INTO assessment_scheme_subject (scheme_id, subject_id, category, pass_percent) VALUES (:s, :sub, :c, :p)",
                    params("s", id, "sub", r.subjectId(), "c", r.category().name(), "p", r.passPercent()));
        }
        return toResponse(s);
    }

    public SchemeResponse replaceClasses(UUID id, List<UUID> classIds) {
        AssessmentScheme s = find(id);
        queries.update("DELETE FROM assessment_scheme_class WHERE scheme_id = :s", Map.of("s", id));
        assignClasses(s, classIds);
        return toResponse(s);
    }

    public SchemeResponse activate(UUID id) {
        AssessmentScheme s = find(id);
        List<String> missing = new ArrayList<>();
        if (classIds(id).isEmpty()) missing.add("assign at least one class");
        if (componentRepository.findBySchemeIdOrderBySortOrderAscCodeAsc(id).isEmpty()) missing.add("add at least one component");
        if (s.getScholasticGradingScaleId() == null) missing.add("choose a grading scale");
        if (!missing.isEmpty()) {
            throw new BusinessException("SCHEME_INCOMPLETE", "Before activating: " + String.join(", ", missing));
        }
        s.setStatus(SchemeStatus.ACTIVE);
        return toResponse(schemeRepository.save(s));
    }

    public SchemeResponse archive(UUID id) {
        AssessmentScheme s = find(id);
        s.setStatus(SchemeStatus.ARCHIVED);
        return toResponse(schemeRepository.save(s));
    }

    public void delete(UUID id) {
        AssessmentScheme s = find(id);
        if (s.getStatus() != SchemeStatus.DRAFT) {
            throw new BusinessException("SCHEME_NOT_DRAFT", "Only a DRAFT scheme can be deleted; archive it instead");
        }
        boolean used = componentRepository.findBySchemeIdOrderBySortOrderAscCodeAsc(id).stream()
                .anyMatch(c -> assessmentRepository.existsByComponentIdAndDeletedAtIsNull(c.getId()));
        if (used) {
            throw new BusinessException("SCHEME_IN_USE", "Assessments are recorded under this scheme; archive it instead");
        }
        queries.update("DELETE FROM assessment_scheme_class WHERE scheme_id = :s", Map.of("s", id));
        s.setDeletedAt(Instant.now());
        schemeRepository.save(s);
    }

    /** Copies a scheme to another year (e.g. the next one). Term-specific components follow terms of the same name. */
    public SchemeResponse copy(UUID id, CopySchemeRequest req) {
        AssessmentScheme from = find(id);
        requireYear(req.academicYearId());
        String name = req.name() != null && !req.name().isBlank() ? req.name().trim() : from.getName();
        nameFree(req.academicYearId(), name, null);
        AssessmentScheme to = new AssessmentScheme();
        to.setSchemeCode(CodeGenerator.assessmentSchemeCode());
        to.setName(name);
        to.setAcademicYearId(req.academicYearId());
        to.setBoard(from.getBoard());
        to.setDescription(from.getDescription());
        to.setPresetKey(from.getPresetKey());
        to.setScholasticGradingScaleId(from.getScholasticGradingScaleId());
        to.setCoScholasticGrades(from.getCoScholasticGrades());
        to.setCompetencyLevels(from.getCompetencyLevels());
        to.setSubjectPassPercent(from.getSubjectPassPercent());
        to.setOverallPassPercent(from.getOverallPassPercent());
        to.setSeparateComponentPass(from.isSeparateComponentPass());
        to.setRoundingMode(from.getRoundingMode());
        to.setRoundingScale(from.getRoundingScale());
        to.setBestOfSubjects(from.getBestOfSubjects());
        to.setTermAggregation(from.getTermAggregation());
        to.setGraceMaxPerSubject(from.getGraceMaxPerSubject());
        to.setGraceMaxTotal(from.getGraceMaxTotal());
        to.setGraceMaxSubjects(from.getGraceMaxSubjects());
        to.setCompartmentMaxSubjects(from.getCompartmentMaxSubjects());
        to.setReevaluationWindowDays(from.getReevaluationWindowDays());
        to.setRankEnabled(from.isRankEnabled());
        to = schemeRepository.save(to);
        Map<UUID, String> oldTerms = new HashMap<>();
        queries.termsOfYear(from.getAcademicYearId()).forEach(t -> oldTerms.put(t.id(), t.name().toLowerCase(Locale.ROOT)));
        Map<String, UUID> newTerms = new HashMap<>();
        queries.termsOfYear(req.academicYearId()).forEach(t -> newTerms.putIfAbsent(t.name().toLowerCase(Locale.ROOT), t.id()));
        for (AssessmentComponent c : componentRepository.findBySchemeIdOrderBySortOrderAscCodeAsc(id)) {
            AssessmentComponent n = new AssessmentComponent();
            n.setSchemeId(to.getId());
            n.setCode(c.getCode());
            n.setName(c.getName());
            n.setAssessmentType(c.getAssessmentType());
            n.setExamTermId(c.getExamTermId() == null ? null : newTerms.get(oldTerms.get(c.getExamTermId())));
            n.setWeight(c.getWeight());
            n.setAggregation(c.getAggregation());
            n.setBestOf(c.getBestOf());
            n.setSource(c.getSource());
            n.setPassRequired(c.isPassRequired());
            n.setSortOrder(c.getSortOrder());
            componentRepository.save(n);
        }
        for (CoScholasticArea a : areaRepository.findBySchemeIdOrderBySortOrderAscCodeAsc(id)) {
            CoScholasticArea n = new CoScholasticArea();
            n.setSchemeId(to.getId());
            n.setCode(a.getCode());
            n.setName(a.getName());
            n.setAreaGroup(a.getAreaGroup());
            n.setDescription(a.getDescription());
            n.setSortOrder(a.getSortOrder());
            areaRepository.save(n);
        }
        queries.update("INSERT INTO assessment_scheme_subject (scheme_id, subject_id, category, pass_percent) "
                + "SELECT :to, subject_id, category, pass_percent FROM assessment_scheme_subject WHERE scheme_id = :from",
                params("to", to.getId(), "from", id));
        if (req.copyClasses()) {
            queries.update("INSERT INTO assessment_scheme_class (scheme_id, academic_year_id, academic_class_id) "
                    + "SELECT :to, :y, c.academic_class_id FROM assessment_scheme_class c WHERE c.scheme_id = :from "
                    + "ON CONFLICT DO NOTHING", params("to", to.getId(), "y", req.academicYearId(), "from", id));
        }
        return toResponse(to);
    }

    // ───────────────────────────── helpers ─────────────────────────────

    public AssessmentScheme find(UUID id) {
        return schemeRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("AssessmentScheme", id));
    }

    private void assignClasses(AssessmentScheme s, List<UUID> classIds) {
        Set<UUID> ids = new LinkedHashSet<>(classIds);
        if (ids.isEmpty()) {
            return;
        }
        Map<UUID, String> names = names("SELECT id, name FROM academic_class WHERE id IN (:ids) AND deleted_at IS NULL", ids);
        if (names.size() != ids.size()) {
            throw new BusinessException("INVALID_CLASSES", "Unknown class in the list");
        }
        List<String> taken = new ArrayList<>();
        queries.rows("SELECT c.name, s.name FROM assessment_scheme_class x JOIN assessment_scheme s ON s.id = x.scheme_id "
                + "JOIN academic_class c ON c.id = x.academic_class_id WHERE x.academic_year_id = :y AND x.academic_class_id IN (:ids) "
                + "AND x.scheme_id <> :s", params("y", s.getAcademicYearId(), "ids", ids, "s", s.getId()))
                .forEach(r -> taken.add(r[0] + " (in " + r[1] + ")"));
        if (!taken.isEmpty()) {
            throw new BusinessException("CLASS_HAS_SCHEME", "Already in another scheme this year: " + String.join(", ", taken));
        }
        for (UUID c : ids) {
            queries.update("INSERT INTO assessment_scheme_class (scheme_id, academic_year_id, academic_class_id) VALUES (:s, :y, :c) "
                    + "ON CONFLICT DO NOTHING", params("s", s.getId(), "y", s.getAcademicYearId(), "c", c));
        }
    }

    private List<UUID> classIds(UUID schemeId) {
        return queries.rows("SELECT academic_class_id FROM assessment_scheme_class WHERE scheme_id = :s", Map.of("s", schemeId))
                .stream().map(r -> uuid(r[0])).toList();
    }

    private void nameFree(UUID yearId, String name, UUID self) {
        boolean taken = schemeRepository.findByAcademicYearIdAndDeletedAtIsNullOrderByName(yearId).stream()
                .anyMatch(x -> !x.getId().equals(self) && x.getName().equalsIgnoreCase(name.trim()));
        if (taken) {
            throw new BusinessException("SCHEME_NAME_TAKEN", "A scheme named " + name.trim() + " already exists in that year");
        }
    }

    private void requireYear(UUID yearId) {
        if (queries.rows("SELECT id FROM academic_year WHERE id = :y AND deleted_at IS NULL", Map.of("y", yearId)).isEmpty()) {
            throw new ResourceNotFoundException("AcademicYear", yearId);
        }
    }

    private UUID requireScale(UUID scaleId) {
        if (queries.rows("SELECT id FROM grading_scale WHERE id = :g AND deleted_at IS NULL", Map.of("g", scaleId)).isEmpty()) {
            throw new ResourceNotFoundException("GradingScale", scaleId);
        }
        return scaleId;
    }

    /** The school's scale for a grading preset, created from the preset the first time. */
    UUID ensureScale(String gradingPresetKey) {
        if (gradingPresetKey == null) {
            return null;
        }
        GradingPresets.Preset p = GradingPresets.find(gradingPresetKey).orElse(null);
        if (p == null) {
            return null;
        }
        List<Object[]> found = queries.rows("SELECT id FROM grading_scale WHERE lower(name) = lower(:n) AND deleted_at IS NULL LIMIT 1",
                Map.of("n", p.name()));
        if (!found.isEmpty()) {
            return uuid(found.get(0)[0]);
        }
        return gradingConfigService.create(p.name(), p.description(), p.tiers(), false).getId();
    }

    private Map<UUID, String> names(String sql, Set<UUID> ids) {
        Map<UUID, String> out = new HashMap<>();
        if (!ids.isEmpty()) {
            queries.rows(sql, Map.of("ids", ids)).forEach(r -> out.put(uuid(r[0]), (String) r[1]));
        }
        return out;
    }

    SchemeResponse toResponse(AssessmentScheme s) {
        Map<UUID, String> termNames = new HashMap<>();
        List<TermInfo> terms = queries.termsOfYear(s.getAcademicYearId());
        terms.forEach(t -> termNames.put(t.id(), t.name()));
        List<AssessmentComponent> components = componentRepository.findBySchemeIdOrderBySortOrderAscCodeAsc(s.getId());
        List<ComponentResponse> comps = components.stream().map(c -> new ComponentResponse(c.getId(), c.getCode(), c.getName(),
                c.getAssessmentType(), c.getExamTermId(), termNames.get(c.getExamTermId()), c.getWeight(), c.getAggregation(), c.getBestOf(),
                c.getSource(), c.isPassRequired(), c.getSortOrder())).toList();
        List<AreaResponse> areas = areaRepository.findBySchemeIdOrderBySortOrderAscCodeAsc(s.getId()).stream()
                .map(a -> new AreaResponse(a.getId(), a.getCode(), a.getName(), a.getAreaGroup(), a.getDescription(), a.getSortOrder())).toList();
        List<NamedRef> classes = queries.rows("SELECT c.id, c.name FROM assessment_scheme_class x JOIN academic_class c ON c.id = x.academic_class_id "
                + "WHERE x.scheme_id = :s ORDER BY c.sort_order, c.name", Map.of("s", s.getId())).stream()
                .map(r -> new NamedRef(uuid(r[0]), (String) r[1])).toList();
        List<SubjectRuleResponse> subjects = queries.rows("SELECT x.subject_id, sub.name, x.category, x.pass_percent FROM assessment_scheme_subject x "
                + "JOIN subject sub ON sub.id = x.subject_id WHERE x.scheme_id = :s ORDER BY sub.name", Map.of("s", s.getId())).stream()
                .map(r -> new SubjectRuleResponse(uuid(r[0]), (String) r[1], SubjectCategory.valueOf((String) r[2]), (BigDecimal) r[3])).toList();
        String scaleName = s.getScholasticGradingScaleId() == null ? null
                : queries.rows("SELECT name FROM grading_scale WHERE id = :g", Map.of("g", s.getScholasticGradingScaleId())).stream()
                .map(r -> (String) r[0]).findFirst().orElse(null);
        return new SchemeResponse(s.getId(), s.getSchemeCode(), s.getName(), s.getBoard(), s.getAcademicYearId(), s.getDescription(),
                s.getPresetKey(), s.getScholasticGradingScaleId(), scaleName, list(s.getCoScholasticGrades()), list(s.getCompetencyLevels()),
                s.getSubjectPassPercent(), s.getOverallPassPercent(), s.isSeparateComponentPass(), s.getRoundingMode(), s.getRoundingScale(),
                s.getBestOfSubjects(), s.getTermAggregation(), s.getGraceMaxPerSubject(), s.getGraceMaxTotal(), s.getGraceMaxSubjects(),
                s.getCompartmentMaxSubjects(), s.getReevaluationWindowDays(), s.isRankEnabled(), s.getStatus(), classes, comps, areas, subjects,
                warnings(components, terms), s.getUpdatedAt());
    }

    /** Terms whose components do not add up to 100 (allowed, but usually a mistake). */
    static List<String> warnings(List<AssessmentComponent> components, List<TermInfo> terms) {
        List<String> out = new ArrayList<>();
        if (components.isEmpty()) {
            out.add("No components yet: results cannot be calculated");
            return out;
        }
        List<TermInfo> check = terms.isEmpty() ? List.of(new TermInfo(null, null, "every term", null, null, null)) : terms;
        for (TermInfo t : check) {
            BigDecimal sum = components.stream().filter(c -> c.getExamTermId() == null || Objects.equals(c.getExamTermId(), t.id()))
                    .map(AssessmentComponent::getWeight).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (sum.compareTo(BigDecimal.valueOf(100)) != 0) {
                out.add("Components of " + t.name() + " add up to " + sum.stripTrailingZeros().toPlainString() + ", not 100");
            }
        }
        return out;
    }

    public static List<String> list(String csv) {
        return csv == null || csv.isBlank() ? List.of() : Arrays.stream(csv.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }
}
