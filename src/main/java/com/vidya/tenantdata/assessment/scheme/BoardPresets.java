package com.vidya.tenantdata.assessment.scheme;

import com.vidya.tenantdata.academic.enums.ClassStage;
import com.vidya.tenantdata.assessment.enums.AreaGroup;
import com.vidya.tenantdata.assessment.enums.AssessmentType;
import com.vidya.tenantdata.assessment.enums.Board;
import com.vidya.tenantdata.assessment.enums.ComponentAggregation;
import com.vidya.tenantdata.assessment.enums.ComponentSource;
import com.vidya.tenantdata.assessment.enums.MarkRounding;
import com.vidya.tenantdata.assessment.enums.TermAggregation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Starting points for each board and stage. A preset only fills a new scheme; every number stays editable, because
 * boards revise weightages, pass and compartment rules by circular and schools add their own components.
 *
 * <p>Weights are marks out of 100 per subject per term. Components with source EXAM_PAPER take the term's exam paper
 * for the subject (exam schedule → subject config); the others are filled by assessments teachers record.</p>
 */
public final class BoardPresets {

    public record Component(String code, String name, AssessmentType type, int weight, ComponentAggregation aggregation,
                            Integer bestOf, ComponentSource source, boolean passRequired) {
    }

    public record Area(String code, String name, AreaGroup group) {
    }

    public record Preset(String key, Board board, String name, String description, Set<ClassStage> stages, String gradingPresetKey,
                         BigDecimal subjectPassPercent, boolean separateComponentPass, MarkRounding rounding, int roundingScale,
                         Integer bestOfSubjects, TermAggregation termAggregation, int compartmentMaxSubjects, boolean rankEnabled,
                         String coScholasticGrades, String competencyLevels, List<Component> components, List<Area> areas) {
    }

    private static final String LEVELS_4 = "BEGINNING,DEVELOPING,PROFICIENT,ADVANCED";

    private static final List<Area> CBSE_AREAS = List.of(
            new Area("WORK_ED", "Work Education", AreaGroup.CO_SCHOLASTIC),
            new Area("ART_ED", "Art Education", AreaGroup.CO_SCHOLASTIC),
            new Area("HPE", "Health & Physical Education", AreaGroup.CO_SCHOLASTIC),
            new Area("DISCIPLINE", "Discipline", AreaGroup.DISCIPLINE));

    /** CBSE VI–VIII (and III–V in most schools): PT 10 + notebook 5 + subject enrichment 5 + term exam 80. */
    private static final List<Component> CBSE_TERM = List.of(
            c("PT", "Periodic Test", AssessmentType.FORMATIVE, 10, ComponentAggregation.BEST_OF, 2, ComponentSource.ASSESSMENTS, false),
            c("NB", "Notebook Submission", AssessmentType.FORMATIVE, 5, ComponentAggregation.AVERAGE, null, ComponentSource.ASSESSMENTS, false),
            c("SE", "Subject Enrichment", AssessmentType.FORMATIVE, 5, ComponentAggregation.AVERAGE, null, ComponentSource.ASSESSMENTS, false),
            c("TERM", "Term Examination", AssessmentType.SUMMATIVE, 80, ComponentAggregation.LATEST, null, ComponentSource.EXAM_PAPER, false));

    private static final List<Preset> ALL = List.of(
            new Preset("CBSE_FOUNDATIONAL", Board.CBSE, "CBSE foundational stage (Balvatika–II)",
                    "Competency-based holistic progress card (NEP 2020): observation and activities, no pass/fail. "
                            + "Rate competencies Stream / Mountain / Sky.",
                    Set.of(ClassStage.PRE_PRIMARY), "PRE_PRIMARY_DESCRIPTIVE", BigDecimal.ZERO, false, MarkRounding.HALF_UP, 0, null,
                    TermAggregation.AVERAGE, 0, false, "A,B,C", "STREAM,MOUNTAIN,SKY",
                    List.of(c("OBS", "Observation & Activities", AssessmentType.FORMATIVE, 100, ComponentAggregation.AVERAGE, null,
                            ComponentSource.ASSESSMENTS, false)),
                    List.of(new Area("PHYSICAL", "Physical Development", AreaGroup.CO_SCHOLASTIC),
                            new Area("SOCIO_EMO", "Socio-emotional Development", AreaGroup.LIFE_SKILLS),
                            new Area("AESTHETIC", "Aesthetic & Cultural Development", AreaGroup.CO_SCHOLASTIC),
                            new Area("HABITS", "Positive Learning Habits", AreaGroup.VALUES))),
            new Preset("CBSE_PREPARATORY", Board.CBSE, "CBSE preparatory stage (III–V)",
                    "Term results from periodic tests, notebooks, subject enrichment and the term exam; competencies rated "
                            + "Beginner / Proficient / Advanced for the holistic progress card. No ranks.",
                    Set.of(ClassStage.PRIMARY), "CBSE_8_POINT", BigDecimal.valueOf(33), false, MarkRounding.HALF_UP, 0, null,
                    TermAggregation.AVERAGE, 0, false, "A,B,C", "BEGINNER,PROFICIENT,ADVANCED", CBSE_TERM, CBSE_AREAS),
            new Preset("CBSE_MIDDLE", Board.CBSE, "CBSE middle stage (VI–VIII)",
                    "PT 10 (best two) + notebook 5 + subject enrichment 5 + term exam 80 per term; 8-point grades; co-scholastic A–C.",
                    Set.of(ClassStage.MIDDLE), "CBSE_8_POINT", BigDecimal.valueOf(33), false, MarkRounding.HALF_UP, 0, null,
                    TermAggregation.WEIGHTED, 0, true, "A,B,C", LEVELS_4, CBSE_TERM, CBSE_AREAS),
            new Preset("CBSE_SECONDARY", Board.CBSE, "CBSE secondary (IX–X)",
                    "Internal assessment 20 (periodic tests best two of three 5, multiple assessment 5, portfolio 5, subject "
                            + "enrichment 5) + exam 80; 33% per subject; best five subjects; up to two compartment subjects; "
                            + "co-scholastic A–E.",
                    Set.of(ClassStage.SECONDARY), "CBSE_9_POINT", BigDecimal.valueOf(33), false, MarkRounding.UP, 0, 5,
                    TermAggregation.WEIGHTED, 2, true, "A,B,C,D,E", LEVELS_4,
                    List.of(c("PT", "Periodic Tests", AssessmentType.FORMATIVE, 5, ComponentAggregation.BEST_OF, 2, ComponentSource.ASSESSMENTS, false),
                            c("MA", "Multiple Assessment", AssessmentType.FORMATIVE, 5, ComponentAggregation.AVERAGE, null, ComponentSource.ASSESSMENTS, false),
                            c("PF", "Portfolio", AssessmentType.FORMATIVE, 5, ComponentAggregation.AVERAGE, null, ComponentSource.ASSESSMENTS, false),
                            c("SE", "Subject Enrichment", AssessmentType.FORMATIVE, 5, ComponentAggregation.AVERAGE, null, ComponentSource.ASSESSMENTS, false),
                            c("EXAM", "Examination", AssessmentType.SUMMATIVE, 80, ComponentAggregation.LATEST, null, ComponentSource.EXAM_PAPER, false)),
                    CBSE_AREAS),
            new Preset("CBSE_SENIOR", Board.CBSE, "CBSE senior secondary (XI–XII)",
                    "Theory 70 + practical / project / internal 30, each passed separately at 33%; best five subjects; "
                            + "one compartment subject. Subjects with 80/20 or 100 theory: change the weights.",
                    Set.of(ClassStage.SENIOR_SECONDARY), "CBSE_9_POINT", BigDecimal.valueOf(33), true, MarkRounding.UP, 0, 5,
                    TermAggregation.WEIGHTED, 1, true, "A,B,C,D,E", LEVELS_4,
                    List.of(c("THEORY", "Theory Examination", AssessmentType.SUMMATIVE, 70, ComponentAggregation.LATEST, null, ComponentSource.EXAM_PAPER, true),
                            c("PRACTICAL", "Practical / Project / Internal", AssessmentType.FORMATIVE, 30, ComponentAggregation.SUM, null, ComponentSource.ASSESSMENTS, true)),
                    List.of(new Area("WORK_ED", "Work Experience", AreaGroup.CO_SCHOLASTIC),
                            new Area("GS", "General Studies", AreaGroup.CO_SCHOLASTIC),
                            new Area("HPE", "Health & Physical Education", AreaGroup.CO_SCHOLASTIC),
                            new Area("DISCIPLINE", "Discipline", AreaGroup.DISCIPLINE))),
            new Preset("ICSE_JUNIOR", Board.ICSE, "ICSE (I–VIII)",
                    "Class tests and projects 20 + term exam 80; 33% per subject; percentage divisions.",
                    Set.of(ClassStage.PRIMARY, ClassStage.MIDDLE), "PERCENTAGE_DIVISION", BigDecimal.valueOf(33), false, MarkRounding.HALF_UP, 0,
                    null, TermAggregation.WEIGHTED, 0, true, "A,B,C,D,E", LEVELS_4,
                    List.of(c("CT", "Class Tests & Projects", AssessmentType.FORMATIVE, 20, ComponentAggregation.AVERAGE, null, ComponentSource.ASSESSMENTS, false),
                            c("TERM", "Term Examination", AssessmentType.SUMMATIVE, 80, ComponentAggregation.LATEST, null, ComponentSource.EXAM_PAPER, false)),
                    List.of(new Area("SUPW", "SUPW & Community Service", AreaGroup.CO_SCHOLASTIC),
                            new Area("PE", "Physical Education", AreaGroup.CO_SCHOLASTIC),
                            new Area("ART", "Art & Craft", AreaGroup.CO_SCHOLASTIC),
                            new Area("CONDUCT", "Conduct", AreaGroup.DISCIPLINE))),
            new Preset("ICSE_SECONDARY", Board.ICSE, "ICSE (IX–X)",
                    "Internal assessment 20 + external exam 80; 33% per subject; best five subjects (English is compulsory: "
                            + "keep it SCHOLASTIC and mark the sixth subject ADDITIONAL); SUPW graded A–E.",
                    Set.of(ClassStage.SECONDARY), "PERCENTAGE_DIVISION", BigDecimal.valueOf(33), false, MarkRounding.HALF_UP, 0, 5,
                    TermAggregation.WEIGHTED, 0, true, "A,B,C,D,E", LEVELS_4,
                    List.of(c("IA", "Internal Assessment", AssessmentType.FORMATIVE, 20, ComponentAggregation.SUM, null, ComponentSource.ASSESSMENTS, false),
                            c("EXAM", "Examination", AssessmentType.SUMMATIVE, 80, ComponentAggregation.LATEST, null, ComponentSource.EXAM_PAPER, false)),
                    List.of(new Area("SUPW", "SUPW & Community Service", AreaGroup.CO_SCHOLASTIC),
                            new Area("PE", "Physical Education", AreaGroup.CO_SCHOLASTIC),
                            new Area("CONDUCT", "Conduct", AreaGroup.DISCIPLINE))),
            new Preset("ISC_SENIOR", Board.ISC, "ISC (XI–XII)",
                    "Theory 70 + practical / project 30, each passed separately; 35% per subject; best four subjects; "
                            + "up to two supplementary subjects. Check the current CISCE circular.",
                    Set.of(ClassStage.SENIOR_SECONDARY), "PERCENTAGE_DIVISION", BigDecimal.valueOf(35), true, MarkRounding.HALF_UP, 0, 4,
                    TermAggregation.WEIGHTED, 2, true, "A,B,C,D,E", LEVELS_4,
                    List.of(c("THEORY", "Theory Examination", AssessmentType.SUMMATIVE, 70, ComponentAggregation.LATEST, null, ComponentSource.EXAM_PAPER, true),
                            c("PRACTICAL", "Practical / Project", AssessmentType.FORMATIVE, 30, ComponentAggregation.SUM, null, ComponentSource.ASSESSMENTS, true)),
                    List.of(new Area("SUPW", "SUPW & Community Service", AreaGroup.CO_SCHOLASTIC),
                            new Area("PE", "Physical Education", AreaGroup.CO_SCHOLASTIC))),
            new Preset("STATE_CCE", Board.STATE, "State board (CCE pattern)",
                    "Formative assessments 20 (FA1–FA4, averaged) + summative 80; 35% per subject; co-scholastic life skills, "
                            + "attitudes & values, co-curricular and health. Adjust to your state's pattern.",
                    Set.of(ClassStage.PRIMARY, ClassStage.MIDDLE, ClassStage.SECONDARY), "PERCENTAGE_DIVISION", BigDecimal.valueOf(35), false,
                    MarkRounding.HALF_UP, 0, null, TermAggregation.WEIGHTED, 0, true, "A,B,C,D,E", LEVELS_4,
                    List.of(c("FA", "Formative Assessments", AssessmentType.FORMATIVE, 20, ComponentAggregation.AVERAGE, null, ComponentSource.ASSESSMENTS, false),
                            c("SA", "Summative Assessment", AssessmentType.SUMMATIVE, 80, ComponentAggregation.LATEST, null, ComponentSource.EXAM_PAPER, false)),
                    List.of(new Area("LIFE_SKILLS", "Life Skills", AreaGroup.LIFE_SKILLS),
                            new Area("VALUES", "Attitudes & Values", AreaGroup.VALUES),
                            new Area("CO_CURRICULAR", "Co-curricular Activities", AreaGroup.CO_SCHOLASTIC),
                            new Area("HPE", "Health & Physical Education", AreaGroup.CO_SCHOLASTIC))),
            new Preset("IB_MYP", Board.IB, "IB Middle Years Programme",
                    "Criteria A–D (each 0–8, recorded as assessments out of 8; the latest best-fit judgement counts) converted to "
                            + "the 1–7 scale; no subject pass mark, no ranks; ATL skills rated Novice / Learner / Practitioner / Expert.",
                    Set.of(ClassStage.MIDDLE, ClassStage.SECONDARY), "IB_1_7", BigDecimal.ZERO, false, MarkRounding.HALF_UP, 0, null,
                    TermAggregation.LATEST, 0, false, "N,L,P,E", "BEGINNING,DEVELOPING,PROFICIENT,ADVANCED",
                    List.of(c("CRIT_A", "Criterion A", AssessmentType.SUMMATIVE, 25, ComponentAggregation.LATEST, null, ComponentSource.ASSESSMENTS, false),
                            c("CRIT_B", "Criterion B", AssessmentType.SUMMATIVE, 25, ComponentAggregation.LATEST, null, ComponentSource.ASSESSMENTS, false),
                            c("CRIT_C", "Criterion C", AssessmentType.SUMMATIVE, 25, ComponentAggregation.LATEST, null, ComponentSource.ASSESSMENTS, false),
                            c("CRIT_D", "Criterion D", AssessmentType.SUMMATIVE, 25, ComponentAggregation.LATEST, null, ComponentSource.ASSESSMENTS, false)),
                    List.of(new Area("ATL_COMM", "ATL: Communication", AreaGroup.LIFE_SKILLS),
                            new Area("ATL_SOCIAL", "ATL: Social", AreaGroup.LIFE_SKILLS),
                            new Area("ATL_SELF", "ATL: Self-management", AreaGroup.LIFE_SKILLS),
                            new Area("ATL_RESEARCH", "ATL: Research", AreaGroup.LIFE_SKILLS),
                            new Area("ATL_THINKING", "ATL: Thinking", AreaGroup.LIFE_SKILLS))),
            new Preset("IB_DP", Board.IB, "IB Diploma Programme",
                    "School-level prediction: external-style exams 75 + internal assessment 25, 1–7 scale. CAS / TOK / EE as "
                            + "co-scholastic areas.",
                    Set.of(ClassStage.SENIOR_SECONDARY), "IB_1_7", BigDecimal.ZERO, false, MarkRounding.HALF_UP, 0, null,
                    TermAggregation.LATEST, 0, false, "A,B,C,D,E", LEVELS_4,
                    List.of(c("EXAM", "Examination Papers", AssessmentType.SUMMATIVE, 75, ComponentAggregation.LATEST, null, ComponentSource.EXAM_PAPER, false),
                            c("IA", "Internal Assessment", AssessmentType.FORMATIVE, 25, ComponentAggregation.LATEST, null, ComponentSource.ASSESSMENTS, false)),
                    List.of(new Area("CAS", "Creativity, Activity, Service", AreaGroup.CO_SCHOLASTIC),
                            new Area("TOK", "Theory of Knowledge", AreaGroup.OTHER),
                            new Area("EE", "Extended Essay", AreaGroup.OTHER))),
            new Preset("IGCSE", Board.IGCSE, "Cambridge IGCSE",
                    "Written papers 80 + coursework / practical 20; A*–G grades (indicative boundaries); grade G or better passes.",
                    Set.of(ClassStage.SECONDARY), "IGCSE_A_STAR_G", BigDecimal.valueOf(20), false, MarkRounding.HALF_UP, 0, null,
                    TermAggregation.LATEST, 0, false, "A,B,C,D,E", LEVELS_4,
                    List.of(c("PAPERS", "Written Papers", AssessmentType.SUMMATIVE, 80, ComponentAggregation.LATEST, null, ComponentSource.EXAM_PAPER, false),
                            c("COURSEWORK", "Coursework / Practical", AssessmentType.FORMATIVE, 20, ComponentAggregation.SUM, null, ComponentSource.ASSESSMENTS, false)),
                    List.of()),
            new Preset("CUSTOM", Board.CUSTOM, "Custom", "Exam paper only (100); add your own components, rules and areas.",
                    Set.of(), "PERCENTAGE_DIVISION", BigDecimal.valueOf(33), false, MarkRounding.NONE, 2, null, TermAggregation.WEIGHTED, 0, true,
                    "A,B,C", LEVELS_4,
                    List.of(c("EXAM", "Examination", AssessmentType.SUMMATIVE, 100, ComponentAggregation.LATEST, null, ComponentSource.EXAM_PAPER, false)),
                    List.of()));

    private BoardPresets() {
    }

    public static List<Preset> all() {
        return ALL;
    }

    public static Optional<Preset> find(String key) {
        return ALL.stream().filter(p -> p.key().equalsIgnoreCase(key)).findFirst();
    }

    private static Component c(String code, String name, AssessmentType type, int weight, ComponentAggregation aggregation, Integer bestOf,
                               ComponentSource source, boolean passRequired) {
        return new Component(code, name, type, weight, aggregation, bestOf, source, passRequired);
    }
}
