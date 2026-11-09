package com.vidya.tenantdata.academic.structure.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Stream;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.enums.ClassStage;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.StreamRepository;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.TemplateRequest;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.TemplateResult;
import com.vidya.tenantdata.examination.entity.GradingScale;
import com.vidya.tenantdata.examination.grading.GradingConfigService;
import com.vidya.tenantdata.examination.grading.GradingPresets;
import com.vidya.tenantdata.examination.repository.GradingScaleRepository;
import com.vidya.tenantdata.subject.entity.Curriculum;
import com.vidya.tenantdata.subject.entity.CurriculumSubject;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.enums.SubjectType;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Standard Indian K–12 structure in one call: classes with stage and level, XI–XII streams,
 * grading scale per stage, a common subject catalogue and (optionally) a curriculum for a year.
 *
 * <p>Idempotent: anything that already exists (matched by code, name or grade level) is kept as it
 * is; only missing stage / level / scale values are filled in. Everything is a starting point the
 * school edits to match its board.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StructureTemplateService {

    /** grade level → [code, name] */
    static final Map<Integer, String[]> CLASSES = new LinkedHashMap<>();
    static final Map<String, String[]> STREAMS = new LinkedHashMap<>();
    /** subject name → type */
    static final Map<String, SubjectType> SUBJECTS = new LinkedHashMap<>();

    static {
        CLASSES.put(-3, new String[]{"PRE_NURSERY", "Pre-Nursery"});
        CLASSES.put(-2, new String[]{"NURSERY", "Nursery"});
        CLASSES.put(-1, new String[]{"LKG", "LKG"});
        CLASSES.put(0, new String[]{"UKG", "UKG"});
        String[] roman = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"};
        for (int i = 1; i <= 12; i++) {
            CLASSES.put(i, new String[]{"CLASS_" + i, "Class " + roman[i - 1]});
        }
        STREAMS.put("SCIENCE", new String[]{"Science", "Physics, Chemistry with Mathematics and/or Biology"});
        STREAMS.put("COMMERCE", new String[]{"Commerce", "Accountancy, Business Studies, Economics"});
        STREAMS.put("HUMANITIES", new String[]{"Humanities", "History, Political Science, Geography, Economics, Psychology"});

        SUBJECTS.put("English", SubjectType.LANGUAGE);
        SUBJECTS.put("Hindi", SubjectType.LANGUAGE);
        SUBJECTS.put("Sanskrit", SubjectType.LANGUAGE);
        SUBJECTS.put("Mathematics", SubjectType.CORE);
        SUBJECTS.put("Environmental Studies", SubjectType.CORE);
        SUBJECTS.put("Science", SubjectType.CORE);
        SUBJECTS.put("Social Science", SubjectType.CORE);
        SUBJECTS.put("Computer Science", SubjectType.ELECTIVE);
        SUBJECTS.put("Information Technology", SubjectType.VOCATIONAL);
        SUBJECTS.put("Physics", SubjectType.CORE);
        SUBJECTS.put("Chemistry", SubjectType.CORE);
        SUBJECTS.put("Biology", SubjectType.CORE);
        SUBJECTS.put("Accountancy", SubjectType.CORE);
        SUBJECTS.put("Business Studies", SubjectType.CORE);
        SUBJECTS.put("Economics", SubjectType.CORE);
        SUBJECTS.put("History", SubjectType.CORE);
        SUBJECTS.put("Political Science", SubjectType.CORE);
        SUBJECTS.put("Geography", SubjectType.CORE);
        SUBJECTS.put("Psychology", SubjectType.ELECTIVE);
        SUBJECTS.put("Art Education", SubjectType.CO_CURRICULAR);
        SUBJECTS.put("Health & Physical Education", SubjectType.CO_CURRICULAR);
    }

    /** One curriculum line: subject, compulsory?, stream code (null = whole class). */
    record Line(String subject, boolean compulsory, String stream) {
    }

    /** Subjects per stage (and per stream for senior secondary). */
    static List<Line> linesFor(int level, ClassStage stage, List<String> streams) {
        List<Line> l = new ArrayList<>();
        switch (stage) {
            case PRE_PRIMARY -> {
                l.add(new Line("English", true, null));
                l.add(new Line("Hindi", true, null));
                l.add(new Line("Mathematics", true, null));
                l.add(new Line("Environmental Studies", true, null));
                l.add(new Line("Art Education", true, null));
            }
            case PRIMARY -> {
                l.add(new Line("English", true, null));
                l.add(new Line("Hindi", true, null));
                l.add(new Line("Mathematics", true, null));
                if (level >= 3) {
                    l.add(new Line("Environmental Studies", true, null));
                }
                l.add(new Line("Computer Science", false, null));
                l.add(new Line("Art Education", true, null));
                l.add(new Line("Health & Physical Education", true, null));
            }
            case MIDDLE -> {
                l.add(new Line("English", true, null));
                l.add(new Line("Hindi", true, null));
                l.add(new Line("Sanskrit", false, null));
                l.add(new Line("Mathematics", true, null));
                l.add(new Line("Science", true, null));
                l.add(new Line("Social Science", true, null));
                l.add(new Line("Computer Science", false, null));
                l.add(new Line("Art Education", true, null));
                l.add(new Line("Health & Physical Education", true, null));
            }
            case SECONDARY -> {
                l.add(new Line("English", true, null));
                l.add(new Line("Hindi", true, null));
                l.add(new Line("Mathematics", true, null));
                l.add(new Line("Science", true, null));
                l.add(new Line("Social Science", true, null));
                l.add(new Line("Information Technology", false, null));
                l.add(new Line("Health & Physical Education", true, null));
            }
            case SENIOR_SECONDARY -> {
                l.add(new Line("English", true, null));
                l.add(new Line("Health & Physical Education", false, null));
                if (streams.contains("SCIENCE")) {
                    l.add(new Line("Physics", true, "SCIENCE"));
                    l.add(new Line("Chemistry", true, "SCIENCE"));
                    l.add(new Line("Mathematics", false, "SCIENCE"));
                    l.add(new Line("Biology", false, "SCIENCE"));
                    l.add(new Line("Computer Science", false, "SCIENCE"));
                }
                if (streams.contains("COMMERCE")) {
                    l.add(new Line("Accountancy", true, "COMMERCE"));
                    l.add(new Line("Business Studies", true, "COMMERCE"));
                    l.add(new Line("Economics", true, "COMMERCE"));
                    l.add(new Line("Mathematics", false, "COMMERCE"));
                    l.add(new Line("Information Technology", false, "COMMERCE"));
                }
                if (streams.contains("HUMANITIES")) {
                    l.add(new Line("History", true, "HUMANITIES"));
                    l.add(new Line("Political Science", true, "HUMANITIES"));
                    l.add(new Line("Geography", false, "HUMANITIES"));
                    l.add(new Line("Economics", false, "HUMANITIES"));
                    l.add(new Line("Psychology", false, "HUMANITIES"));
                }
                if (streams.isEmpty()) {
                    l.add(new Line("Mathematics", false, null));
                }
            }
        }
        return l;
    }

    private final AcademicClassRepository classRepository;
    private final AcademicYearRepository yearRepository;
    private final StreamRepository streamRepository;
    private final SubjectRepository subjectRepository;
    private final CurriculumRepository curriculumRepository;
    private final CurriculumSubjectRepository curriculumSubjectRepository;
    private final GradingScaleRepository gradingScaleRepository;
    private final GradingConfigService gradingConfigService;

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    @Transactional(transactionManager = "tenantTransactionManager")
    public TemplateResult apply(TemplateRequest r) {
        int from = r.fromLevel() != null ? r.fromLevel() : -2;
        int to = r.toLevel() != null ? r.toLevel() : 12;
        if (from > to) {
            throw new BusinessException("fromLevel " + from + " is after toLevel " + to);
        }
        List<String> streamCodes = (r.streams() != null ? r.streams() : List.copyOf(STREAMS.keySet())).stream()
                .map(s -> s.trim().toUpperCase(Locale.ROOT)).distinct().toList();
        for (String s : streamCodes) {
            if (!STREAMS.containsKey(s)) {
                throw new BusinessException("Unknown template stream " + s + "; use " + STREAMS.keySet() + " and add others via /streams");
            }
        }
        boolean withScales = r.gradingScales() == null || r.gradingScales();
        boolean withSubjects = r.subjects() == null || r.subjects();
        boolean dry = Boolean.TRUE.equals(r.dryRun());
        AcademicYear year = null;
        if (r.academicYearId() != null) {
            year = yearRepository.findByIdAndNotDeleted(r.academicYearId())
                    .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", r.academicYearId()));
            if (year.getStatus() == AcademicYearStatus.CLOSED || year.getStatus() == AcademicYearStatus.CANCELLED) {
                throw new BusinessException("Academic year " + year.getCode() + " is " + year.getStatus());
            }
            if (!withSubjects && subjectRepository.count() == 0) {
                throw new BusinessException("A curriculum needs subjects; set subjects=true or create subjects first");
            }
        }
        boolean seniorIncluded = to >= 11;
        List<String> notes = new ArrayList<>();
        List<String> classesCreated = new ArrayList<>();
        List<String> classesUpdated = new ArrayList<>();
        List<String> streamsCreated = new ArrayList<>();
        List<String> scalesCreated = new ArrayList<>();
        List<String> subjectsCreated = new ArrayList<>();

        // 1. Grading scales per stage
        Map<ClassStage, GradingScale> scaleByStage = new HashMap<>();
        if (withScales) {
            boolean hasDefault = gradingScaleRepository.findDefaultScale().isPresent();
            for (ClassStage stage : ClassStage.values()) {
                if (!overlaps(stage, from, to)) {
                    continue;
                }
                GradingPresets.Preset p = GradingPresets.forStage(stage);
                Optional<GradingScale> scale = scaleNamed(p.name());
                if (scale.isEmpty() && !scalesCreated.contains(p.name())) {
                    scalesCreated.add(p.name());
                    if (!dry) {
                        boolean makeDefault = !hasDefault && (stage == ClassStage.SECONDARY || stage == ClassStage.SENIOR_SECONDARY);
                        scale = Optional.of(gradingConfigService.create(p.name(), p.description(), p.tiers(), makeDefault));
                        hasDefault |= makeDefault;
                    }
                }
                scale.ifPresent(sc -> scaleByStage.put(stage, sc));
            }
            if (!hasDefault && !dry) {
                notes.add("No default grading scale: set one with PATCH /exams/grading-scales/{id}/default");
            }
        }

        // 2. Streams
        Map<String, Stream> streams = new HashMap<>();
        if (seniorIncluded) {
            int order = 1;
            for (String code : streamCodes) {
                Optional<Stream> s = streamRepository.findByCode(code);
                if (s.isEmpty()) {
                    streamsCreated.add(code);
                    if (!dry) {
                        s = Optional.of(streamRepository.save(new Stream(code, STREAMS.get(code)[0], STREAMS.get(code)[1], order)));
                    }
                }
                s.ifPresent(st -> streams.put(code, st));
                order++;
            }
        }

        // 3. Classes
        List<AcademicClass> all = classRepository.findByDeletedAtIsNullOrderBySortOrderAsc();
        Map<Integer, AcademicClass> byLevel = new LinkedHashMap<>();
        for (int level = from; level <= to; level++) {
            String[] def = CLASSES.get(level);
            ClassStage stage = ClassStage.forGradeLevel(level);
            final int lv = level;
            AcademicClass c = all.stream().filter(x -> Integer.valueOf(lv).equals(x.getGradeLevel())).findFirst()
                    .or(() -> all.stream().filter(x -> x.getCode().equalsIgnoreCase(def[0]) || matchesName(x.getName(), lv)).findFirst())
                    .orElse(null);
            boolean streamsHere = lv >= 11 && !streamCodes.isEmpty();
            if (c == null) {
                classesCreated.add(def[1]);
                if (dry) {
                    continue;
                }
                if (classRepository.existsByCode(def[0])) {
                    throw new BusinessException("Class code " + def[0] + " exists (deleted); restore it or rename before applying the template");
                }
                c = new AcademicClass(def[0], def[1], def[1], (level + 4) * 10);
                c.setGradeLevel(level);
                c.setStage(stage);
                c.setHasStreams(streamsHere);
                GradingScale sc = scaleByStage.get(stage);
                c.setGradingScaleId(sc != null ? sc.getId() : null);
                c = classRepository.save(c);
            } else {
                List<String> changed = new ArrayList<>();
                if (c.getGradeLevel() == null) {
                    changed.add("level " + level);
                    if (!dry) {
                        c.setGradeLevel(level);
                    }
                }
                if (c.getStage() == null) {
                    changed.add("stage " + stage);
                    if (!dry) {
                        c.setStage(stage);
                    }
                }
                if (streamsHere && !c.isHasStreams()) {
                    changed.add("streams on");
                    if (!dry) {
                        c.setHasStreams(true);
                    }
                }
                GradingScale sc = scaleByStage.get(stage);
                if (c.getGradingScaleId() == null && (sc != null || (dry && withScales))) {
                    changed.add("grading scale");
                    if (!dry && sc != null) {
                        c.setGradingScaleId(sc.getId());
                    }
                }
                if (!changed.isEmpty()) {
                    classesUpdated.add(c.getName() + " (" + String.join(", ", changed) + ")");
                }
            }
            byLevel.put(level, c);
        }

        // 4. Subjects
        Map<String, Subject> subjects = new HashMap<>();
        if (withSubjects) {
            int order = 1;
            for (Map.Entry<String, SubjectType> e : SUBJECTS.entrySet()) {
                Optional<Subject> s = subjectNamed(e.getKey());
                if (s.isEmpty()) {
                    subjectsCreated.add(e.getKey());
                    if (!dry) {
                        s = Optional.of(subjectRepository.save(new Subject(CodeGenerator.subjectCode(), e.getKey(), null, null, e.getValue(), order)));
                    }
                }
                s.ifPresent(x -> subjects.put(e.getKey(), x));
                order++;
            }
        } else {
            SUBJECTS.keySet().forEach(n -> subjectNamed(n).ifPresent(s -> subjects.put(n, s)));
        }

        // 5. Curriculum for a year
        String curriculumName = null;
        int curriculumLines = 0;
        if (year != null) {
            curriculumName = "Standard curriculum " + year.getCode();
            final String cn = curriculumName;
            boolean exists = !em.createQuery("SELECT c.id FROM Curriculum c WHERE c.academicYear.id = :y AND lower(c.name) = lower(:n) AND c.deletedAt IS NULL")
                    .setParameter("y", year.getId()).setParameter("n", cn).getResultList().isEmpty();
            if (exists) {
                notes.add("Curriculum '" + cn + "' already exists for " + year.getCode() + "; not changed (edit it with the curriculum grid)");
                curriculumName = null;
            } else {
                Curriculum cu = dry ? null : curriculumRepository.save(new Curriculum(CodeGenerator.curriculumCode(), year, cn,
                        "Created from the Indian K-12 template; edit subjects to match your board."));
                for (int level = from; level <= to; level++) {
                    AcademicClass c = byLevel.get(level);
                    int order = 1;
                    for (Line line : linesFor(level, ClassStage.forGradeLevel(level), level >= 11 ? streamCodes : List.of())) {
                        Subject s = subjects.get(line.subject());
                        if (dry) {
                            curriculumLines++;
                            continue;
                        }
                        if (s == null || c == null) {
                            continue;
                        }
                        CurriculumSubject cs = new CurriculumSubject(cu, s, c, line.compulsory(), order++);
                        if (line.stream() != null && streams.containsKey(line.stream())) {
                            cs.setStreamId(streams.get(line.stream()).getId());
                        }
                        curriculumSubjectRepository.save(cs);
                        curriculumLines++;
                    }
                }
            }
        }

        if (seniorIncluded && streamCodes.isEmpty()) {
            notes.add("No streams: classes XI–XII are set up without streams");
        }
        notes.add("Review subjects and compulsory / elective flags against your board's scheme of studies");
        if (withScales) {
            notes.add("8-point scale marks E1/E2 as not passing; change it if your school follows no-detention for I–VIII");
        }
        log.info("K-12 template {}: classes +{} ~{}, streams +{}, scales +{}, subjects +{}, curriculum lines {}",
                dry ? "(dry run)" : "applied", classesCreated.size(), classesUpdated.size(), streamsCreated.size(), scalesCreated.size(),
                subjectsCreated.size(), curriculumLines);
        return new TemplateResult(dry, classesCreated, classesUpdated, streamsCreated, scalesCreated, subjectsCreated, curriculumName,
                curriculumLines, notes);
    }

    private static boolean overlaps(ClassStage stage, int from, int to) {
        for (int l = from; l <= to; l++) {
            if (ClassStage.forGradeLevel(l) == stage) {
                return true;
            }
        }
        return false;
    }

    /** "Class 5", "Class V", "5", "Grade 5", "5th", "Std 5" for level 5; "Nursery", "LKG" … for pre-primary. */
    static boolean matchesName(String name, int level) {
        String n = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        String[] def = CLASSES.get(level);
        if (level <= 0) {
            return n.equals(def[1].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""))
                    || (level == -1 && n.equals("lowerkg")) || (level == 0 && n.equals("upperkg"))
                    || (level == -1 && n.equals("kg1")) || (level == 0 && n.equals("kg2"));
        }
        String roman = def[1].substring("Class ".length()).toLowerCase(Locale.ROOT);
        String num = String.valueOf(level);
        for (String prefix : List.of("class", "grade", "std", "standard", "")) {
            if (n.equals(prefix + num) || n.equals(prefix + roman)
                    || n.equals(prefix + num + "st") || n.equals(prefix + num + "nd") || n.equals(prefix + num + "rd") || n.equals(prefix + num + "th")) {
                return true;
            }
        }
        return false;
    }

    private Optional<GradingScale> scaleNamed(String name) {
        return em.createQuery("SELECT g FROM GradingScale g WHERE lower(g.name) = lower(:n) AND g.deletedAt IS NULL", GradingScale.class)
                .setParameter("n", name).getResultList().stream().findFirst();
    }

    private Optional<Subject> subjectNamed(String name) {
        return em.createQuery("SELECT s FROM Subject s WHERE lower(s.name) = lower(:n) AND s.deletedAt IS NULL", Subject.class)
                .setParameter("n", name).getResultList().stream().findFirst();
    }
}
