package com.vidya.tenantdata.academic.config.dto;

import com.vidya.tenantdata.academic.config.enums.LessonPlanStatus;
import com.vidya.tenantdata.academic.config.enums.SyllabusStatus;
import com.vidya.tenantdata.academic.config.enums.TopicProgressStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Academic configuration: teacher allocation (class teachers, subject teachers), syllabus and lesson plans. */
public final class AcademicConfigDtos {

    private AcademicConfigDtos() {
    }

    // ── Teacher allocation ───────────────────────────────────────────────────

    /** @param staffId the section's class teacher; null removes the current one */
    public record ClassTeacherRow(@NotNull UUID sectionId, UUID staffId) {
    }

    /**
     * @param effectiveDate day the change takes effect (default today, or the first day of a year that has not started)
     * @param dryRun        validate and preview only
     */
    public record ClassTeachersRequest(@NotNull UUID academicYearId,
                                       @NotEmpty @Size(max = 500) List<@Valid ClassTeacherRow> assignments,
                                       LocalDate effectiveDate, Boolean dryRun) {
    }

    /**
     * @param sectionId null = the teacher takes the subject in every section of the class
     * @param teacherId the (primary) teacher of the subject; null removes the current one
     */
    public record SubjectTeacherRow(@NotNull UUID academicClassId, UUID sectionId, @NotNull UUID subjectId, UUID teacherId) {
    }

    public record SubjectTeachersRequest(@NotNull UUID academicYearId,
                                         @NotEmpty @Size(max = 2000) List<@Valid SubjectTeacherRow> assignments,
                                         LocalDate effectiveDate, Boolean dryRun) {
    }

    /**
     * @param action ASSIGNED, CHANGED, REMOVED or UNCHANGED; null when the row has an error
     * @param error  why the row is invalid; any error means nothing was saved
     */
    public record AllocationRow(int index, String label, String action, String error, List<String> warnings) {
    }

    public record AllocationResult(boolean dryRun, boolean valid, int assigned, int changed, int removed, int unchanged,
                                   List<AllocationRow> rows) {
    }

    // ── Overview ─────────────────────────────────────────────────────────────

    public record TeacherRef(UUID staffId, String staffCode, String name) {
    }

    /**
     * One subject of one section.
     *
     * @param sectionLevel          the class subject exists for this section only (e.g. a stream subject)
     * @param teacherForWholeClass  the teacher is assigned to the class, not to this section specifically
     * @param syllabusTopics        topics in the syllabus (0 when there is none)
     * @param topicsCompleted       topics this section has completed
     */
    public record SubjectSlot(UUID classSubjectId, UUID subjectId, String subjectCode, String subjectName, boolean compulsory,
                              Integer periodsPerWeek, boolean sectionLevel, UUID teacherAssignmentId, TeacherRef teacher,
                              boolean teacherForWholeClass, UUID syllabusId, SyllabusStatus syllabusStatus, int syllabusTopics,
                              int topicsCompleted) {
    }

    public record SectionNode(UUID sectionId, String code, String name, UUID classTeacherAssignmentId, TeacherRef classTeacher,
                              List<SubjectSlot> subjects) {
    }

    public record ClassNode(UUID classId, String code, String name, Integer gradeLevel, List<SectionNode> sections) {
    }

    public record Issue(String level, String code, long count, String message, List<String> examples) {
    }

    /**
     * @param subjectSlots         section × subject combinations that need a teacher
     * @param syllabiExpected      class × subject combinations taught this year
     * @param lessonPlansToReview  submitted plans waiting for approval
     */
    public record ConfigOverview(UUID academicYearId, String academicYear, int sections, int sectionsWithClassTeacher, int subjectSlots,
                                 int subjectSlotsWithTeacher, int syllabiExpected, int syllabi, int syllabiPublished, long lessonPlans,
                                 long lessonPlansToReview, List<ClassNode> classes, List<Issue> issues) {
    }

    /**
     * @param plannedPeriods   periods per week from the class subjects the teacher is assigned to
     * @param timetablePeriods periods per week in the active timetable
     */
    public record TeacherLoad(UUID staffId, String staffCode, String name, String designation, int classTeacherOf, int subjectAssignments,
                              int sections, long plannedPeriods, long timetablePeriods, Integer maxPeriodsPerWeek, boolean overloaded,
                              List<String> teaches) {
    }

    // ── Syllabus ─────────────────────────────────────────────────────────────

    /** @param id existing topic to keep (its coverage is kept); null = new topic */
    public record TopicInput(UUID id, @NotBlank @Size(max = 300) String title, @Min(0) @Max(999) Integer plannedPeriods) {
    }

    /** @param id existing unit to keep; null = new unit. Units and topics are stored in the order sent. */
    public record UnitInput(UUID id, @NotBlank @Size(max = 200) String title, @Size(max = 4000) String description,
                            @Size(max = 4000) String learningOutcomes, UUID examTermId, @Min(0) @Max(999) Integer plannedPeriods,
                            LocalDate plannedStart, LocalDate plannedEnd, List<@Valid TopicInput> topics) {
    }

    /**
     * The whole syllabus of one class × subject × year. Saving replaces it: units and topics not in the
     * request are removed (with their coverage).
     *
     * @param title  default "<subject> – <class>"
     * @param status default DRAFT for a new syllabus, unchanged for an existing one
     */
    public record SyllabusRequest(@NotNull UUID academicYearId, @NotNull UUID academicClassId, @NotNull UUID subjectId,
                                  @Size(max = 200) String title, @Size(max = 4000) String description,
                                  @Size(max = 1000) String referenceBooks, SyllabusStatus status,
                                  @NotNull @Size(max = 100) List<@Valid UnitInput> units) {
    }

    public record SyllabusStatusRequest(@NotNull SyllabusStatus status) {
    }

    /**
     * @param status            in the requested section; null when no section was asked for
     * @param sectionsCompleted sections of the class that have completed the topic
     */
    public record TopicResponse(UUID id, String title, Integer plannedPeriods, TopicProgressStatus status, LocalDate completedOn,
                                String remarks, int sectionsCompleted) {
    }

    public record UnitResponse(UUID id, int unitNo, String title, String description, String learningOutcomes, UUID examTermId,
                               String examTerm, Integer plannedPeriods, LocalDate plannedStart, LocalDate plannedEnd,
                               List<TopicResponse> topics) {
    }

    public record SectionCoverage(UUID sectionId, String section, int topics, int completed, int inProgress, int percent) {
    }

    /** @param units null in list responses */
    public record SyllabusResponse(UUID id, String code, UUID academicYearId, UUID academicClassId, String className, UUID subjectId,
                                   String subjectName, String title, String description, String referenceBooks, SyllabusStatus status,
                                   int unitCount, int topicCount, int plannedPeriods, int coveragePercent, List<SectionCoverage> coverage,
                                   List<UnitResponse> units) {
    }

    /** @param status NOT_STARTED clears the topic's coverage in the section */
    public record ProgressRow(@NotNull UUID topicId, @NotNull TopicProgressStatus status, LocalDate completedOn,
                              @Size(max = 255) String remarks) {
    }

    public record ProgressRequest(@NotNull UUID sectionId, @NotEmpty @Size(max = 500) List<@Valid ProgressRow> topics) {
    }

    /**
     * Copy syllabi (units and topics, not coverage) into another year.
     *
     * @param academicClassIds only these classes; empty = all
     */
    public record CopySyllabiRequest(@NotNull UUID fromAcademicYearId, @NotNull UUID toAcademicYearId, List<UUID> academicClassIds) {
    }

    public record CopyResult(int copied, int skipped, List<String> notes) {
    }

    // ── Lesson plans ─────────────────────────────────────────────────────────

    /**
     * @param sectionId       null = for every section of the class the teacher teaches
     * @param teacherId       default: the signed-in teacher
     * @param syllabusTopicId topic the lesson covers (its unit is filled in); or just {@code syllabusUnitId}
     * @param submit          send for review straight away
     */
    public record LessonPlanRequest(@NotNull UUID academicYearId, @NotNull UUID academicClassId, UUID sectionId, @NotNull UUID subjectId,
                                    UUID teacherId, UUID syllabusUnitId, UUID syllabusTopicId, @NotBlank @Size(max = 200) String title,
                                    @NotNull LocalDate planDate, @Min(1) @Max(20) Integer periods, @Size(max = 4000) String objectives,
                                    @Size(max = 8000) String teachingMethod, @Size(max = 4000) String resources,
                                    @Size(max = 4000) String homework, @Size(max = 4000) String assessment, Boolean submit) {
    }

    public enum ReviewDecision { APPROVE, RETURN }

    /** @param remarks required when returning a plan */
    public record ReviewRequest(@NotNull ReviewDecision decision, @Size(max = 500) String remarks) {
    }

    /**
     * @param deliveredOn        default today
     * @param markTopicCompleted mark the plan's syllabus topic completed in its section(s); default true
     */
    public record DeliverRequest(LocalDate deliveredOn, @Size(max = 4000) String reflection, Boolean markTopicCompleted) {
    }

    public record LessonPlanResponse(UUID id, String code, UUID academicYearId, UUID academicClassId, String className, UUID sectionId,
                                     String section, UUID subjectId, String subjectName, UUID teacherId, String teacherName,
                                     UUID syllabusUnitId, String syllabusUnit, UUID syllabusTopicId, String syllabusTopic, String title,
                                     LocalDate planDate, int periods, String objectives, String teachingMethod, String resources,
                                     String homework, String assessment, LessonPlanStatus status, Instant submittedAt, Instant reviewedAt,
                                     String reviewRemarks, LocalDate deliveredOn, String reflection, Instant updatedAt) {
    }
}
