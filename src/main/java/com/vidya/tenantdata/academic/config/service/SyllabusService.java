package com.vidya.tenantdata.academic.config.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.CopyResult;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.CopySyllabiRequest;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.ProgressRequest;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.ProgressRow;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.SectionCoverage;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.SyllabusRequest;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.SyllabusResponse;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.TopicInput;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.TopicResponse;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.UnitInput;
import com.vidya.tenantdata.academic.config.dto.AcademicConfigDtos.UnitResponse;
import com.vidya.tenantdata.academic.config.entity.Syllabus;
import com.vidya.tenantdata.academic.config.entity.SyllabusTopic;
import com.vidya.tenantdata.academic.config.entity.SyllabusTopicProgress;
import com.vidya.tenantdata.academic.config.entity.SyllabusUnit;
import com.vidya.tenantdata.academic.config.enums.SyllabusStatus;
import com.vidya.tenantdata.academic.config.enums.TopicProgressStatus;
import com.vidya.tenantdata.academic.config.repository.SyllabusRepository;
import com.vidya.tenantdata.academic.config.repository.SyllabusTopicProgressRepository;
import com.vidya.tenantdata.academic.config.repository.SyllabusTopicRepository;
import com.vidya.tenantdata.academic.config.repository.SyllabusUnitRepository;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.enums.SubjectStatus;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Syllabus of a class × subject × year (units → topics) and how far each section has got.
 * The syllabus is saved as one document; coverage is marked per topic and section by the teachers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyllabusService {

    private final SyllabusRepository syllabusRepository;
    private final SyllabusUnitRepository unitRepository;
    private final SyllabusTopicRepository topicRepository;
    private final SyllabusTopicProgressRepository progressRepository;
    private final AcademicYearRepository yearRepository;
    private final AcademicClassRepository classRepository;
    private final SubjectRepository subjectRepository;
    private final TeachingScope scope;

    // ── read ─────────────────────────────────────────────────────────────────

    /** Syllabi of a year with coverage per section; units are left out (see {@link #get}). */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<SyllabusResponse> list(UUID academicYearId, UUID academicClassId, UUID subjectId, SyllabusStatus status) {
        List<Syllabus> syllabi = syllabusRepository.findByAcademicYearIdAndDeletedAtIsNull(academicYearId).stream()
                .filter(s -> academicClassId == null || s.getAcademicClassId().equals(academicClassId))
                .filter(s -> subjectId == null || s.getSubjectId().equals(subjectId))
                .filter(s -> status == null ? s.getStatus() != SyllabusStatus.ARCHIVED : s.getStatus() == status)
                .toList();
        return respond(syllabi, academicYearId, null, false);
    }

    /** One syllabus with units and topics; with {@code sectionId} every topic carries that section's status. */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public SyllabusResponse get(UUID id, UUID sectionId) {
        Syllabus s = syllabus(id);
        return respond(List.of(s), s.getAcademicYearId(), sectionId, true).get(0);
    }

    // ── write ────────────────────────────────────────────────────────────────

    /** Creates or replaces the syllabus of the class × subject × year. Kept units and topics keep their coverage. */
    @Transactional(transactionManager = "tenantTransactionManager")
    public SyllabusResponse save(SyllabusRequest r) {
        AcademicYear year = editableYear(r.academicYearId());
        AcademicClass clazz = classRepository.findByIdAndNotDeleted(r.academicClassId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicClass", r.academicClassId()));
        Subject subject = subjectRepository.findByIdAndNotDeleted(r.subjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Subject", r.subjectId()));
        scope.requireTeaches(year.getId(), clazz.getId(), null, subject.getId(), "edit the syllabus");

        Syllabus s = syllabusRepository.findByAcademicYearIdAndAcademicClassIdAndSubjectIdAndDeletedAtIsNull(year.getId(), clazz.getId(), subject.getId())
                .orElse(null);
        boolean created = s == null;
        if (created) {
            if (subject.getStatus() != SubjectStatus.ACTIVE) {
                throw new BusinessException(subject.getName() + " is not an active subject");
            }
            s = new Syllabus(code(), year.getId(), clazz.getId(), subject.getId(), subject.getName() + " – " + clazz.getName());
            s.setCreatedBy(scope.actorId());
        }
        if (r.title() != null && !r.title().isBlank()) {
            s.setTitle(r.title().trim());
        }
        s.setDescription(r.description());
        s.setReferenceBooks(r.referenceBooks());
        if (r.status() != null) {
            s.setStatus(r.status());
        }
        if (s.getStatus() == SyllabusStatus.PUBLISHED && r.units().isEmpty()) {
            throw new BusinessException("Add at least one unit before publishing the syllabus");
        }
        s.setUpdatedBy(scope.actorId());
        s = syllabusRepository.save(s);

        Map<UUID, SyllabusUnit> oldUnits = created ? new HashMap<>() : byId(unitRepository.findBySyllabusIdOrderByUnitNoAsc(s.getId()), SyllabusUnit::getId);
        Map<UUID, SyllabusTopic> oldTopics = created ? new HashMap<>() : byId(topicRepository.findBySyllabusIdOrderBySortOrderAsc(s.getId()), SyllabusTopic::getId);
        Map<UUID, String> terms = scope.examTerms(year.getId());
        Set<UUID> keptUnits = new HashSet<>();
        Set<UUID> keptTopics = new HashSet<>();
        int unitNo = 0;
        for (UnitInput u : r.units()) {
            unitNo++;
            String where = "Unit " + unitNo + " (" + u.title().trim() + ")";
            SyllabusUnit unit;
            if (u.id() != null) {
                unit = oldUnits.get(u.id());
                if (unit == null || !keptUnits.add(u.id())) {
                    throw new BusinessException(where + ": " + (unit == null ? "id is not a unit of this syllabus" : "listed twice"));
                }
            } else {
                unit = new SyllabusUnit(s.getId());
            }
            if (u.examTermId() != null && !terms.containsKey(u.examTermId())) {
                throw new BusinessException(where + ": the term does not belong to " + year.getCode());
            }
            if (u.plannedStart() != null && u.plannedEnd() != null && u.plannedEnd().isBefore(u.plannedStart())) {
                throw new BusinessException(where + ": planned end is before planned start");
            }
            for (LocalDate d : new LocalDate[]{u.plannedStart(), u.plannedEnd()}) {
                if (d != null && (d.isBefore(year.getStartDate()) || d.isAfter(year.getEndDate()))) {
                    throw new BusinessException(where + ": " + d + " is outside " + year.getCode());
                }
            }
            unit.setUnitNo(unitNo);
            unit.setTitle(u.title().trim());
            unit.setDescription(u.description());
            unit.setLearningOutcomes(u.learningOutcomes());
            unit.setExamTermId(u.examTermId());
            unit.setPlannedPeriods(u.plannedPeriods());
            unit.setPlannedStart(u.plannedStart());
            unit.setPlannedEnd(u.plannedEnd());
            unit = unitRepository.save(unit);

            int order = 0;
            for (TopicInput t : u.topics() == null ? List.<TopicInput>of() : u.topics()) {
                SyllabusTopic topic;
                if (t.id() != null) {
                    topic = oldTopics.get(t.id());
                    if (topic == null || !keptTopics.add(t.id())) {
                        throw new BusinessException(where + ", topic " + t.title().trim() + ": "
                                + (topic == null ? "id is not a topic of this syllabus" : "listed twice"));
                    }
                    topic.setUnitId(unit.getId());
                } else {
                    topic = new SyllabusTopic(s.getId(), unit.getId());
                }
                topic.setTitle(t.title().trim());
                topic.setPlannedPeriods(t.plannedPeriods());
                topic.setSortOrder(++order);
                topicRepository.save(topic);
            }
        }
        // removed topics first, then removed units (their coverage rows go with them)
        topicRepository.deleteAll(oldTopics.values().stream().filter(t -> !keptTopics.contains(t.getId())).toList());
        unitRepository.deleteAll(oldUnits.values().stream().filter(u -> !keptUnits.contains(u.getId())).toList());
        topicRepository.flush();
        log.info("{} syllabus [{}] {} with {} unit(s)", created ? "Created" : "Saved", s.getSyllabusCode(), s.getTitle(), unitNo);
        return respond(List.of(s), year.getId(), null, true).get(0);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public SyllabusResponse setStatus(UUID id, SyllabusStatus status) {
        Syllabus s = syllabus(id);
        scope.requireTeaches(s.getAcademicYearId(), s.getAcademicClassId(), null, s.getSubjectId(), "change the syllabus");
        if (status == SyllabusStatus.PUBLISHED && unitRepository.findBySyllabusIdOrderByUnitNoAsc(id).isEmpty()) {
            throw new BusinessException("Add at least one unit before publishing the syllabus");
        }
        s.setStatus(status);
        s.setUpdatedBy(scope.actorId());
        return respond(List.of(syllabusRepository.save(s)), s.getAcademicYearId(), null, true).get(0);
    }

    /** Removes the syllabus. Lesson plans that pointed at its topics stay and lose the link. */
    @Transactional(transactionManager = "tenantTransactionManager")
    public void delete(UUID id) {
        Syllabus s = syllabus(id);
        editableYear(s.getAcademicYearId());
        s.setDeletedAt(Instant.now());
        s.setUpdatedBy(scope.actorId());
        syllabusRepository.save(s);
    }

    /** Marks topics not started / in progress / completed in one section. Teachers do this for what they teach. */
    @Transactional(transactionManager = "tenantTransactionManager")
    public SyllabusResponse updateProgress(UUID id, ProgressRequest r) {
        Syllabus s = syllabus(id);
        if (s.getStatus() == SyllabusStatus.ARCHIVED) {
            throw new BusinessException("This syllabus is archived");
        }
        requireSectionOfClass(s, r.sectionId());
        scope.requireTeaches(s.getAcademicYearId(), s.getAcademicClassId(), r.sectionId(), s.getSubjectId(), "update syllabus coverage");
        Map<UUID, SyllabusTopic> topics = byId(topicRepository.findBySyllabusIdOrderBySortOrderAsc(id), SyllabusTopic::getId);
        for (ProgressRow row : r.topics()) {
            SyllabusTopic topic = topics.get(row.topicId());
            if (topic == null) {
                throw new BusinessException("Topic " + row.topicId() + " is not a topic of this syllabus");
            }
            if (row.completedOn() != null && row.completedOn().isAfter(LocalDate.now())) {
                throw new BusinessException(topic.getTitle() + ": completed date is in the future");
            }
            mark(topic.getId(), r.sectionId(), row.status(), row.completedOn(), row.remarks());
        }
        return respond(List.of(s), s.getAcademicYearId(), r.sectionId(), true).get(0);
    }

    /** Used when a lesson plan is delivered: the topic is completed in these sections unless it already was. */
    void completeTopic(UUID topicId, Collection<UUID> sectionIds, LocalDate on) {
        for (UUID sectionId : sectionIds) {
            boolean done = progressRepository.findByTopicIdAndSectionId(topicId, sectionId)
                    .map(p -> p.getStatus() == TopicProgressStatus.COMPLETED).orElse(false);
            if (!done) {
                mark(topicId, sectionId, TopicProgressStatus.COMPLETED, on, null);
            }
        }
    }

    /**
     * Copies syllabi (units and topics) into another year as drafts. Class × subject combinations that already
     * have a syllabus there are skipped. Terms and planned dates are not copied: they belong to the old year.
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public CopyResult copy(CopySyllabiRequest r) {
        if (r.fromAcademicYearId().equals(r.toAcademicYearId())) {
            throw new BusinessException("Choose two different academic years");
        }
        yearRepository.findByIdAndNotDeleted(r.fromAcademicYearId()).orElseThrow(() -> new ResourceNotFoundException("AcademicYear", r.fromAcademicYearId()));
        AcademicYear target = editableYear(r.toAcademicYearId());
        Set<UUID> only = r.academicClassIds() == null ? Set.of() : new HashSet<>(r.academicClassIds());
        Set<String> existing = syllabusRepository.findByAcademicYearIdAndDeletedAtIsNull(target.getId()).stream()
                .map(s -> s.getAcademicClassId() + ":" + s.getSubjectId()).collect(Collectors.toSet());
        int copied = 0;
        int skipped = 0;
        for (Syllabus src : syllabusRepository.findByAcademicYearIdAndDeletedAtIsNull(r.fromAcademicYearId())) {
            if (src.getStatus() == SyllabusStatus.ARCHIVED || (!only.isEmpty() && !only.contains(src.getAcademicClassId()))) {
                continue;
            }
            if (existing.contains(src.getAcademicClassId() + ":" + src.getSubjectId())) {
                skipped++;
                continue;
            }
            Syllabus copy = new Syllabus(code(), target.getId(), src.getAcademicClassId(), src.getSubjectId(), src.getTitle());
            copy.setDescription(src.getDescription());
            copy.setReferenceBooks(src.getReferenceBooks());
            copy.setCreatedBy(scope.actorId());
            copy = syllabusRepository.save(copy);
            Map<UUID, List<SyllabusTopic>> topics = topicRepository.findBySyllabusIdOrderBySortOrderAsc(src.getId()).stream()
                    .collect(Collectors.groupingBy(SyllabusTopic::getUnitId));
            for (SyllabusUnit u : unitRepository.findBySyllabusIdOrderByUnitNoAsc(src.getId())) {
                SyllabusUnit nu = new SyllabusUnit(copy.getId());
                nu.setUnitNo(u.getUnitNo());
                nu.setTitle(u.getTitle());
                nu.setDescription(u.getDescription());
                nu.setLearningOutcomes(u.getLearningOutcomes());
                nu.setPlannedPeriods(u.getPlannedPeriods());
                nu = unitRepository.save(nu);
                for (SyllabusTopic t : topics.getOrDefault(u.getId(), List.of())) {
                    SyllabusTopic nt = new SyllabusTopic(copy.getId(), nu.getId());
                    nt.setTitle(t.getTitle());
                    nt.setPlannedPeriods(t.getPlannedPeriods());
                    nt.setSortOrder(t.getSortOrder());
                    topicRepository.save(nt);
                }
            }
            copied++;
        }
        List<String> notes = new ArrayList<>();
        if (copied > 0) {
            notes.add("Copied as drafts without terms and planned dates: review, set this year's terms and publish.");
        }
        if (skipped > 0) {
            notes.add(skipped + " class subject(s) already have a syllabus in " + target.getCode() + " and were left unchanged.");
        }
        log.info("Copied {} syllabus/syllabi into [{}] ({} skipped)", copied, target.getCode(), skipped);
        return new CopyResult(copied, skipped, notes);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void mark(UUID topicId, UUID sectionId, TopicProgressStatus status, LocalDate completedOn, String remarks) {
        SyllabusTopicProgress p = progressRepository.findByTopicIdAndSectionId(topicId, sectionId).orElse(null);
        if (status == TopicProgressStatus.NOT_STARTED) {
            if (p != null) {
                progressRepository.delete(p);
            }
            return;
        }
        if (p == null) {
            p = new SyllabusTopicProgress(topicId, sectionId);
        }
        p.setCompletedOn(status != TopicProgressStatus.COMPLETED ? null
                : completedOn != null ? completedOn : p.getCompletedOn() != null ? p.getCompletedOn() : LocalDate.now());
        p.setStatus(status);
        if (remarks != null) {
            p.setRemarks(remarks.isBlank() ? null : remarks.trim());
        }
        p.setUpdatedBy(scope.actorId());
        progressRepository.save(p);
    }

    private List<SyllabusResponse> respond(List<Syllabus> syllabi, UUID yearId, UUID sectionId, boolean withUnits) {
        if (syllabi.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = syllabi.stream().map(Syllabus::getId).toList();
        Map<UUID, List<SyllabusUnit>> units = unitRepository.findBySyllabusIdInOrderByUnitNoAsc(ids).stream()
                .collect(Collectors.groupingBy(SyllabusUnit::getSyllabusId));
        List<SyllabusTopic> allTopics = topicRepository.findBySyllabusIdInOrderBySortOrderAsc(ids);
        Map<UUID, List<SyllabusTopic>> topics = allTopics.stream().collect(Collectors.groupingBy(SyllabusTopic::getSyllabusId));
        Map<UUID, List<SyllabusTopicProgress>> progress = allTopics.isEmpty() ? Map.of()
                : progressRepository.findByTopicIdIn(allTopics.stream().map(SyllabusTopic::getId).toList()).stream()
                .collect(Collectors.groupingBy(SyllabusTopicProgress::getTopicId));
        Map<UUID, Map<UUID, String>> sections = scope.sectionsOfYear(yearId);
        Map<UUID, String> classNames = scope.classNames(syllabi.stream().map(Syllabus::getAcademicClassId).toList());
        Map<UUID, String> subjectNames = scope.subjectNames(syllabi.stream().map(Syllabus::getSubjectId).toList());
        Map<UUID, String> terms = withUnits ? scope.examTerms(yearId) : Map.of();

        List<SyllabusResponse> out = new ArrayList<>();
        for (Syllabus s : syllabi) {
            List<SyllabusUnit> us = units.getOrDefault(s.getId(), List.of());
            List<SyllabusTopic> ts = topics.getOrDefault(s.getId(), List.of());
            Map<UUID, String> classSections = sections.getOrDefault(s.getAcademicClassId(), Map.of());
            if (sectionId != null && !classSections.containsKey(sectionId)) {
                throw new BusinessException("The section is not a section of this class in the syllabus's academic year");
            }

            // coverage per section
            Map<UUID, int[]> perSection = new LinkedHashMap<>();
            classSections.keySet().forEach(sec -> perSection.put(sec, new int[2]));
            for (SyllabusTopic t : ts) {
                for (SyllabusTopicProgress p : progress.getOrDefault(t.getId(), List.of())) {
                    int[] c = perSection.get(p.getSectionId());
                    if (c != null) {
                        c[p.getStatus() == TopicProgressStatus.COMPLETED ? 0 : 1]++;
                    }
                }
            }
            List<SectionCoverage> coverage = new ArrayList<>();
            int percentSum = 0;
            for (Map.Entry<UUID, int[]> e : perSection.entrySet()) {
                int percent = ts.isEmpty() ? 0 : e.getValue()[0] * 100 / ts.size();
                percentSum += percent;
                coverage.add(new SectionCoverage(e.getKey(), classSections.get(e.getKey()), ts.size(), e.getValue()[0], e.getValue()[1], percent));
            }

            int planned = 0;
            List<UnitResponse> unitResponses = withUnits ? new ArrayList<>() : null;
            for (SyllabusUnit u : us) {
                List<SyllabusTopic> unitTopics = ts.stream().filter(t -> t.getUnitId().equals(u.getId())).toList();
                planned += u.getPlannedPeriods() != null ? u.getPlannedPeriods()
                        : unitTopics.stream().mapToInt(t -> t.getPlannedPeriods() != null ? t.getPlannedPeriods() : 0).sum();
                if (withUnits) {
                    List<TopicResponse> topicResponses = new ArrayList<>();
                    for (SyllabusTopic t : unitTopics) {
                        List<SyllabusTopicProgress> ps = progress.getOrDefault(t.getId(), List.of());
                        SyllabusTopicProgress mine = sectionId == null ? null
                                : ps.stream().filter(p -> p.getSectionId().equals(sectionId)).findFirst().orElse(null);
                        int done = (int) ps.stream().filter(p -> p.getStatus() == TopicProgressStatus.COMPLETED
                                && classSections.containsKey(p.getSectionId())).count();
                        topicResponses.add(new TopicResponse(t.getId(), t.getTitle(), t.getPlannedPeriods(),
                                sectionId == null ? null : mine != null ? mine.getStatus() : TopicProgressStatus.NOT_STARTED,
                                mine != null ? mine.getCompletedOn() : null, mine != null ? mine.getRemarks() : null, done));
                    }
                    unitResponses.add(new UnitResponse(u.getId(), u.getUnitNo(), u.getTitle(), u.getDescription(), u.getLearningOutcomes(),
                            u.getExamTermId(), u.getExamTermId() != null ? terms.get(u.getExamTermId()) : null, u.getPlannedPeriods(),
                            u.getPlannedStart(), u.getPlannedEnd(), topicResponses));
                }
            }
            out.add(new SyllabusResponse(s.getId(), s.getSyllabusCode(), s.getAcademicYearId(), s.getAcademicClassId(),
                    classNames.get(s.getAcademicClassId()), s.getSubjectId(), subjectNames.get(s.getSubjectId()), s.getTitle(),
                    s.getDescription(), s.getReferenceBooks(), s.getStatus(), us.size(), ts.size(), planned,
                    coverage.isEmpty() ? 0 : percentSum / coverage.size(), coverage, unitResponses));
        }
        return out;
    }

    private void requireSectionOfClass(Syllabus s, UUID sectionId) {
        if (!scope.sectionsOfYear(s.getAcademicYearId()).getOrDefault(s.getAcademicClassId(), Map.of()).containsKey(sectionId)) {
            throw new BusinessException("The section is not a section of this class in the syllabus's academic year");
        }
    }

    Syllabus syllabus(UUID id) {
        return syllabusRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("Syllabus", id));
    }

    private AcademicYear editableYear(UUID id) {
        AcademicYear year = yearRepository.findByIdAndNotDeleted(id).orElseThrow(() -> new ResourceNotFoundException("AcademicYear", id));
        if (year.getStatus() == AcademicYearStatus.CLOSED || year.getStatus() == AcademicYearStatus.CANCELLED) {
            throw new BusinessException("Academic year " + year.getCode() + " is " + year.getStatus() + " and cannot be changed");
        }
        return year;
    }

    private String code() {
        for (int i = 0; i < 6; i++) {
            String c = CodeGenerator.syllabusCode();
            if (!syllabusRepository.existsBySyllabusCode(c)) {
                return c;
            }
        }
        throw new BusinessException("Could not generate a unique code; try again");
    }

    private static <T> Map<UUID, T> byId(List<T> list, Function<T, UUID> id) {
        Map<UUID, T> out = new LinkedHashMap<>();
        list.forEach(x -> out.put(id.apply(x), x));
        return out;
    }
}
