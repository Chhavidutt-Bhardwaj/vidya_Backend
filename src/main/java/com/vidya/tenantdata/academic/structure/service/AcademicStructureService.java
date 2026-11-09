package com.vidya.tenantdata.academic.structure.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.entity.Stream;
import com.vidya.tenantdata.academic.enums.AcademicClassStatus;
import com.vidya.tenantdata.academic.enums.ClassStage;
import com.vidya.tenantdata.academic.enums.StreamStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.academic.repository.StreamRepository;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.ClassNode;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.ClassStructureRequest;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.CurriculumGridRequest;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.CurriculumGridResult;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.DefaultScale;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.Issue;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.Overview;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.SectionNode;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.SectionStreamsRequest;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.StreamRequest;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.StreamResponse;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.SubjectNode;
import com.vidya.tenantdata.examination.entity.GradingScale;
import com.vidya.tenantdata.examination.grading.GradingConfigService;
import com.vidya.tenantdata.examination.repository.GradingScaleRepository;
import com.vidya.tenantdata.student.repository.StudentAcademicEnrollmentRepository;
import com.vidya.tenantdata.subject.entity.Curriculum;
import com.vidya.tenantdata.subject.entity.CurriculumSubject;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.enums.CurriculumStatus;
import com.vidya.tenantdata.subject.enums.SubjectStatus;
import com.vidya.tenantdata.subject.repository.CurriculumRepository;
import com.vidya.tenantdata.subject.repository.CurriculumSubjectRepository;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Academic structure configuration: streams, class stage / level / grading scale, section
 * streams, the curriculum subject grid and a one-page overview with problems to fix.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcademicStructureService {

    static final UUID NONE = new UUID(0, 0);

    private final StreamRepository streamRepository;
    private final AcademicClassRepository classRepository;
    private final AcademicYearRepository yearRepository;
    private final SectionRepository sectionRepository;
    private final CurriculumRepository curriculumRepository;
    private final CurriculumSubjectRepository curriculumSubjectRepository;
    private final SubjectRepository subjectRepository;
    private final GradingScaleRepository gradingScaleRepository;
    private final GradingConfigService gradingConfigService;
    private final StudentAcademicEnrollmentRepository enrollmentRepository;

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    // ── Streams ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<StreamResponse> streams() {
        Map<UUID, long[]> usage = streamUsage();
        return streamRepository.findAllNotDeleted().stream().map(s -> toResponse(s, usage)).toList();
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public StreamResponse createStream(StreamRequest r) {
        String code = r.code().trim().toUpperCase(Locale.ROOT);
        if (streamRepository.findByCode(code).isPresent()) {
            throw new DuplicateResourceException("Stream", "code", code);
        }
        if (streamRepository.nameTaken(r.name().trim(), NONE)) {
            throw new DuplicateResourceException("Stream", "name", r.name());
        }
        Stream s = streamRepository.save(new Stream(code, r.name().trim(), r.description(), r.sortOrder() != null ? r.sortOrder() : 0));
        return toResponse(s, Map.of());
    }

    /** Code is not changed when the stream is in use (it appears on reports and imports). */
    @Transactional(transactionManager = "tenantTransactionManager")
    public StreamResponse updateStream(UUID id, StreamRequest r) {
        Stream s = stream(id);
        String code = r.code().trim().toUpperCase(Locale.ROOT);
        Map<UUID, long[]> usage = streamUsage();
        if (!s.getCode().equals(code)) {
            if (inUse(usage, id)) {
                throw new BusinessException("Stream " + s.getCode() + " is used by sections or curriculum subjects; its code cannot change");
            }
            streamRepository.findByCode(code).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
                throw new DuplicateResourceException("Stream", "code", code);
            });
            s.setCode(code);
        }
        if (streamRepository.nameTaken(r.name().trim(), id)) {
            throw new DuplicateResourceException("Stream", "name", r.name());
        }
        s.setName(r.name().trim());
        s.setDescription(r.description());
        if (r.sortOrder() != null) {
            s.setSortOrder(r.sortOrder());
        }
        return toResponse(s, usage);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public StreamResponse setStreamStatus(UUID id, StreamStatus status) {
        if (status == StreamStatus.DELETED) {
            throw new BusinessException("Use DELETE to remove a stream");
        }
        Stream s = stream(id);
        s.setStatus(status);
        return toResponse(s, streamUsage());
    }

    /** Only unused streams can be deleted; deactivate a stream that has history. */
    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteStream(UUID id) {
        Stream s = stream(id);
        if (inUse(streamUsage(), id)) {
            throw new BusinessException("Stream " + s.getCode() + " is used by sections or curriculum subjects; set it INACTIVE instead");
        }
        s.setStatus(StreamStatus.DELETED);
        s.setDeletedAt(java.time.Instant.now());
    }

    // ── Class structure ──────────────────────────────────────────────────────

    @Transactional(transactionManager = "tenantTransactionManager")
    public AcademicClass updateClass(UUID classId, ClassStructureRequest r) {
        AcademicClass c = classRepository.findByIdAndNotDeleted(classId).orElseThrow(() -> new ResourceNotFoundException("AcademicClass", classId));
        if (r.gradeLevel() != null) {
            classRepository.findByDeletedAtIsNullOrderBySortOrderAsc().stream()
                    .filter(o -> !o.getId().equals(classId) && r.gradeLevel().equals(o.getGradeLevel()))
                    .findFirst().ifPresent(o -> {
                        throw new BusinessException("Grade level " + r.gradeLevel() + " is already used by class " + o.getName());
                    });
        }
        ClassStage stage = r.stage() != null ? r.stage() : r.gradeLevel() != null ? ClassStage.forGradeLevel(r.gradeLevel()) : null;
        boolean streams = Boolean.TRUE.equals(r.hasStreams());
        if (!streams && c.isHasStreams()) {
            long withStream = ((Number) em.createNativeQuery("""
                            SELECT count(*) FROM section s JOIN academic_year y ON y.id = s.academic_year_id
                            WHERE s.academic_class_id = :c AND s.stream_id IS NOT NULL AND s.deleted_at IS NULL
                              AND y.status IN ('DRAFT', 'ACTIVE')
                            """).setParameter("c", classId).getSingleResult()).longValue();
            if (withStream > 0) {
                throw new BusinessException(withStream + " section(s) of " + c.getName()
                        + " in open years have a stream; clear their streams before turning streams off");
            }
        }
        if (r.gradingScaleId() != null) {
            gradingScaleRepository.findActiveById(r.gradingScaleId())
                    .orElseThrow(() -> new ResourceNotFoundException("GradingScale", r.gradingScaleId()));
        }
        c.setGradeLevel(r.gradeLevel());
        c.setStage(stage);
        c.setHasStreams(streams);
        c.setGradingScaleId(r.gradingScaleId());
        log.info("Class [{}] structure: stage={} level={} streams={} scale={}", c.getCode(), stage, r.gradeLevel(), streams, r.gradingScaleId());
        return c;
    }

    /** Sets (or clears, with streamId null) the stream of many sections at once; all or nothing. */
    @Transactional(transactionManager = "tenantTransactionManager")
    public int setSectionStreams(SectionStreamsRequest r) {
        Set<UUID> seen = new HashSet<>();
        List<String> errors = new ArrayList<>();
        Map<UUID, Section> sections = new LinkedHashMap<>();
        for (SectionStreamsRequest.SectionStream row : r.sections()) {
            if (!seen.add(row.sectionId())) {
                errors.add("Section " + row.sectionId() + " listed twice");
                continue;
            }
            Optional<Section> s = sectionRepository.findByIdAndNotDeleted(row.sectionId());
            if (s.isEmpty()) {
                errors.add("Section " + row.sectionId() + " not found");
                continue;
            }
            Section sec = s.get();
            String label = sec.getAcademicClass().getName() + "-" + sec.getCode();
            if (row.streamId() != null) {
                Optional<Stream> st = streamRepository.findByIdAndNotDeleted(row.streamId());
                if (st.isEmpty()) {
                    errors.add(label + ": stream " + row.streamId() + " not found");
                } else if (st.get().getStatus() != StreamStatus.ACTIVE) {
                    errors.add(label + ": stream " + st.get().getCode() + " is inactive");
                } else if (!sec.getAcademicClass().isHasStreams()) {
                    errors.add(label + ": class " + sec.getAcademicClass().getName() + " does not use streams (turn on hasStreams first)");
                }
            }
            sections.put(row.sectionId(), sec);
        }
        if (!errors.isEmpty()) {
            throw new BusinessException("No section was changed: " + String.join("; ", errors));
        }
        int changed = 0;
        for (SectionStreamsRequest.SectionStream row : r.sections()) {
            Section sec = sections.get(row.sectionId());
            if (!Objects.equals(sec.getStreamId(), row.streamId())) {
                sec.setStreamId(row.streamId());
                changed++;
            }
        }
        return changed;
    }

    // ── Curriculum grid ──────────────────────────────────────────────────────

    /**
     * Replaces the subjects of the listed classes in a curriculum: new rows are added, changed
     * compulsory / order flags updated, rows no longer listed removed (soft delete).
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public CurriculumGridResult curriculumGrid(UUID curriculumId, CurriculumGridRequest r) {
        Curriculum cu = curriculumRepository.findByIdAndNotDeleted(curriculumId)
                .orElseThrow(() -> new ResourceNotFoundException("Curriculum", curriculumId));
        if (cu.getStatus() != CurriculumStatus.DRAFT && cu.getStatus() != CurriculumStatus.ACTIVE) {
            throw new BusinessException("Curriculum " + cu.getCurriculumCode() + " is " + cu.getStatus() + " and cannot be edited");
        }
        Map<UUID, AcademicClass> classes = new HashMap<>();
        List<String> errors = new ArrayList<>();
        for (UUID id : new HashSet<>(r.academicClassIds())) {
            classRepository.findByIdAndNotDeleted(id).ifPresentOrElse(c -> classes.put(id, c), () -> errors.add("Class " + id + " not found"));
        }
        Map<UUID, Subject> subjects = new HashMap<>();
        Map<UUID, Stream> streams = new HashMap<>();
        Set<String> keys = new HashSet<>();
        for (CurriculumGridRequest.Row row : r.subjects()) {
            AcademicClass c = classes.get(row.academicClassId());
            if (c == null) {
                errors.add("Row for class " + row.academicClassId() + ": class is not in academicClassIds");
                continue;
            }
            Subject s = subjects.computeIfAbsent(row.subjectId(), id -> subjectRepository.findByIdAndNotDeleted(id).orElse(null));
            if (s == null || s.getStatus() == SubjectStatus.DELETED) {
                errors.add(c.getName() + ": subject " + row.subjectId() + " not found");
                continue;
            }
            if (row.streamId() != null) {
                Stream st = streams.computeIfAbsent(row.streamId(), id -> streamRepository.findByIdAndNotDeleted(id).orElse(null));
                if (st == null) {
                    errors.add(c.getName() + " " + s.getName() + ": stream " + row.streamId() + " not found");
                    continue;
                }
                if (!c.isHasStreams()) {
                    errors.add(c.getName() + " " + s.getName() + ": class does not use streams");
                    continue;
                }
            }
            if (!keys.add(key(row.academicClassId(), row.subjectId(), row.streamId()))) {
                errors.add(c.getName() + ": " + s.getName() + " listed twice" + (row.streamId() != null ? " for the same stream" : ""));
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException("Curriculum not changed: " + String.join("; ", errors));
        }

        Map<String, CurriculumSubject> existing = new HashMap<>();
        for (CurriculumSubject cs : em.createQuery("""
                        SELECT cs FROM CurriculumSubject cs WHERE cs.curriculum.id = :cu AND cs.deletedAt IS NULL
                          AND cs.academicClass.id IN :classes
                        """, CurriculumSubject.class)
                .setParameter("cu", curriculumId).setParameter("classes", classes.keySet()).getResultList()) {
            existing.put(key(cs.getAcademicClass().getId(), cs.getSubject().getId(), cs.getStreamId()), cs);
        }
        int added = 0;
        int updated = 0;
        int unchanged = 0;
        for (CurriculumGridRequest.Row row : r.subjects()) {
            boolean compulsory = row.compulsory() == null || row.compulsory();
            int order = row.displayOrder() != null ? Math.max(0, row.displayOrder()) : 0;
            CurriculumSubject cs = existing.remove(key(row.academicClassId(), row.subjectId(), row.streamId()));
            if (cs == null) {
                CurriculumSubject n = new CurriculumSubject(cu, subjects.get(row.subjectId()), classes.get(row.academicClassId()), compulsory, order);
                n.setStreamId(row.streamId());
                curriculumSubjectRepository.save(n);
                added++;
            } else if (cs.isCompulsory() != compulsory || !Objects.equals(cs.getDisplayOrder(), order)) {
                cs.setCompulsory(compulsory);
                cs.setDisplayOrder(order);
                updated++;
            } else {
                unchanged++;
            }
        }
        existing.values().forEach(CurriculumSubject::softDelete);
        log.info("Curriculum [{}] grid: +{} ~{} -{} ={}", cu.getCurriculumCode(), added, updated, existing.size(), unchanged);
        return new CurriculumGridResult(added, updated, existing.size(), unchanged);
    }

    // ── Overview ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Overview overview(UUID yearId) {
        AcademicYear year = yearRepository.findByIdAndNotDeleted(yearId).orElseThrow(() -> new ResourceNotFoundException("AcademicYear", yearId));
        List<StreamResponse> streams = streams();
        Map<UUID, StreamResponse> streamById = streams.stream().collect(Collectors.toMap(StreamResponse::id, Function.identity()));
        List<AcademicClass> classes = classRepository.findByDeletedAtIsNullOrderBySortOrderAsc().stream()
                .filter(c -> c.getStatus() == AcademicClassStatus.ACTIVE)
                .sorted(Comparator.comparing((AcademicClass c) -> c.getGradeLevel() == null ? Integer.MAX_VALUE : c.getGradeLevel())
                        .thenComparing(AcademicClass::getSortOrder))
                .toList();
        Map<UUID, List<Section>> sectionsByClass = sectionRepository.findActiveByAcademicYearId(yearId).stream()
                .sorted(Comparator.comparing(Section::getCode))
                .collect(Collectors.groupingBy(s -> s.getAcademicClass().getId(), LinkedHashMap::new, Collectors.toList()));
        Map<UUID, Long> students = new HashMap<>();
        for (Object[] row : enrollmentRepository.countActiveBySection(yearId)) {
            students.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        Map<UUID, List<CurriculumSubject>> subjectsByClass = em.createQuery("""
                        SELECT cs FROM CurriculumSubject cs JOIN FETCH cs.subject JOIN FETCH cs.curriculum cu
                        WHERE cu.academicYear.id = :y AND cu.deletedAt IS NULL AND cs.deletedAt IS NULL
                          AND cu.status IN (com.vidya.tenantdata.subject.enums.CurriculumStatus.DRAFT,
                                            com.vidya.tenantdata.subject.enums.CurriculumStatus.ACTIVE)
                        """, CurriculumSubject.class)
                .setParameter("y", yearId).getResultList().stream()
                .collect(Collectors.groupingBy(cs -> cs.getAcademicClass().getId()));
        GradingScale defaultScale = gradingScaleRepository.findDefaultScale().orElse(null);

        List<ClassNode> nodes = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        for (AcademicClass c : classes) {
            List<Section> secs = sectionsByClass.getOrDefault(c.getId(), List.of());
            List<CurriculumSubject> subs = subjectsByClass.getOrDefault(c.getId(), List.of());
            GradingConfigService.Resolved grading = gradingConfigService.resolve(null, c);

            if (c.getGradeLevel() == null) {
                issues.add(new Issue("WARNING", "NO_GRADE_LEVEL", c.getId(), c.getName() + " has no grade level / stage; set it so promotion order and presets work"));
            }
            if (grading.source() == GradingConfigService.Source.NONE) {
                issues.add(new Issue("ERROR", "NO_GRADING_SCALE", c.getId(), c.getName() + " has no grading scale and there is no school default; marks will get no grade"));
            } else if (c.getGradingScaleId() != null && grading.source() != GradingConfigService.Source.CLASS) {
                issues.add(new Issue("WARNING", "CLASS_SCALE_DELETED", c.getId(), c.getName() + "'s grading scale was deleted; the school default is used"));
            }
            if (!secs.isEmpty() && subs.isEmpty()) {
                issues.add(new Issue("WARNING", "NO_SUBJECTS", c.getId(), c.getName() + " has sections in " + year.getCode() + " but no curriculum subjects"));
            }
            if (c.isHasStreams()) {
                List<String> noStream = secs.stream().filter(s -> s.getStreamId() == null).map(Section::getCode).toList();
                if (!noStream.isEmpty()) {
                    issues.add(new Issue("WARNING", "SECTION_WITHOUT_STREAM", c.getId(), c.getName() + " sections without a stream: " + String.join(", ", noStream)));
                }
                Set<UUID> streamsWithSubjects = subs.stream().map(CurriculumSubject::getStreamId).filter(Objects::nonNull).collect(Collectors.toSet());
                secs.stream().map(Section::getStreamId).filter(Objects::nonNull).distinct()
                        .filter(st -> !streamsWithSubjects.contains(st))
                        .forEach(st -> issues.add(new Issue("WARNING", "STREAM_WITHOUT_SUBJECTS", c.getId(), c.getName() + " "
                                + code(streamById, st) + " sections exist but the curriculum has no " + code(streamById, st) + " subjects")));
                secs.stream().filter(s -> s.getStreamId() != null && streamById.containsKey(s.getStreamId())
                                && streamById.get(s.getStreamId()).status() != StreamStatus.ACTIVE)
                        .forEach(s -> issues.add(new Issue("WARNING", "INACTIVE_STREAM", c.getId(), c.getName() + "-" + s.getCode() + " uses inactive stream "
                                + code(streamById, s.getStreamId()))));
            } else if (secs.stream().anyMatch(s -> s.getStreamId() != null) || subs.stream().anyMatch(s -> s.getStreamId() != null)) {
                issues.add(new Issue("WARNING", "STREAM_ON_UNSTREAMED_CLASS", c.getId(), c.getName() + " does not use streams but some sections or subjects have one"));
            }

            nodes.add(new ClassNode(c.getId(), c.getCode(), c.getName(), c.getStage(), c.getGradeLevel(), c.isHasStreams(),
                    grading.source(), grading.scale() != null ? grading.scale().getId() : null, grading.scale() != null ? grading.scale().getName() : null,
                    secs.stream().map(s -> new SectionNode(s.getId(), s.getCode(), s.getName(), s.getStreamId(), code(streamById, s.getStreamId()),
                            s.getCapacity(), students.getOrDefault(s.getId(), 0L))).toList(),
                    subs.stream().sorted(Comparator.comparing((CurriculumSubject s) -> s.getStreamId() == null ? "" : code(streamById, s.getStreamId()))
                                    .thenComparing(s -> s.getDisplayOrder() == null ? 0 : s.getDisplayOrder()).thenComparing(s -> s.getSubject().getName()))
                            .map(s -> new SubjectNode(s.getId(), s.getCurriculum().getId(), s.getSubject().getId(), s.getSubject().getSubjectCode(),
                                    s.getSubject().getName(), s.getSubject().getSubjectType(), s.isCompulsory(),
                                    s.getDisplayOrder() == null ? 0 : s.getDisplayOrder(), s.getStreamId(), code(streamById, s.getStreamId())))
                            .toList()));
        }
        if (classes.stream().anyMatch(AcademicClass::isHasStreams) && streams.stream().noneMatch(s -> s.status() == StreamStatus.ACTIVE)) {
            issues.add(new Issue("ERROR", "NO_STREAMS", null, "Some classes use streams but no active stream exists"));
        }
        return new Overview(year.getId(), year.getCode(), streams, nodes,
                defaultScale != null ? new DefaultScale(defaultScale.getId(), defaultScale.getName()) : null, issues);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Stream stream(UUID id) {
        return streamRepository.findByIdAndNotDeleted(id).orElseThrow(() -> new ResourceNotFoundException("Stream", id));
    }

    /** stream id → [sections, curriculum subjects] (not deleted). */
    private Map<UUID, long[]> streamUsage() {
        Map<UUID, long[]> out = new HashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT stream_id, 0, count(*) FROM section WHERE stream_id IS NOT NULL AND deleted_at IS NULL GROUP BY stream_id
                        UNION ALL
                        SELECT stream_id, 1, count(*) FROM curriculum_subject WHERE stream_id IS NOT NULL AND deleted_at IS NULL GROUP BY stream_id
                        """).getResultList();
        for (Object[] r : rows) {
            out.computeIfAbsent((UUID) r[0], k -> new long[2])[((Number) r[1]).intValue()] = ((Number) r[2]).longValue();
        }
        return out;
    }

    private static boolean inUse(Map<UUID, long[]> usage, UUID id) {
        long[] u = usage.get(id);
        return u != null && (u[0] > 0 || u[1] > 0);
    }

    private static StreamResponse toResponse(Stream s, Map<UUID, long[]> usage) {
        long[] u = usage.getOrDefault(s.getId(), new long[2]);
        return new StreamResponse(s.getId(), s.getCode(), s.getName(), s.getDescription(), s.getSortOrder(), s.getStatus(), u[0], u[1]);
    }

    private static String code(Map<UUID, StreamResponse> streams, UUID id) {
        return id == null ? null : streams.containsKey(id) ? streams.get(id).code() : id.toString();
    }

    private static String key(UUID cls, UUID subject, UUID stream) {
        return cls + "|" + subject + "|" + (stream == null ? "" : stream);
    }
}
