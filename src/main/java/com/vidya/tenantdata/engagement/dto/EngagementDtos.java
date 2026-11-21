package com.vidya.tenantdata.engagement.dto;

import com.vidya.tenantdata.calendar.enums.DayStatus;
import com.vidya.tenantdata.engagement.enums.ActivityType;
import com.vidya.tenantdata.engagement.enums.AnnouncementAudience;
import com.vidya.tenantdata.engagement.enums.AnnouncementPriority;
import com.vidya.tenantdata.engagement.enums.AnnouncementStatus;
import com.vidya.tenantdata.engagement.enums.HomeworkStatus;
import com.vidya.tenantdata.engagement.enums.OutboxStatus;
import com.vidya.tenantdata.engagement.enums.RemarkCategory;
import com.vidya.tenantdata.engagement.enums.RemarkSentiment;
import com.vidya.tenantdata.engagement.enums.ReportRunStatus;
import com.vidya.tenantdata.engagement.enums.ReportRunTrigger;
import com.vidya.tenantdata.operations.enums.NotificationChannel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Parent engagement: homework, class activities, announcements, teacher remarks, the daily report and the inbox. */
public final class EngagementDtos {

    private EngagementDtos() {
    }

    // ── Homework ─────────────────────────────────────────────────────────────

    /**
     * @param sectionId  null = every section of the class
     * @param teacherId  default: the signed-in teacher
     * @param assignedOn default: today
     */
    public record HomeworkRequest(@NotNull UUID academicYearId, @NotNull UUID academicClassId, UUID sectionId, @NotNull UUID subjectId,
                                  UUID teacherId, @NotBlank @Size(max = 200) String title, @Size(max = 5000) String description,
                                  LocalDate assignedOn, @NotNull LocalDate dueOn) {
    }

    public record HomeworkResponse(UUID id, String homeworkCode, UUID academicYearId, UUID academicClassId, String className,
                                   UUID sectionId, String sectionName, UUID subjectId, String subjectName, UUID teacherId,
                                   String teacherName, String title, String description, LocalDate assignedOn, LocalDate dueOn,
                                   HomeworkStatus status, Instant updatedAt) {
    }

    // ── Class activities ─────────────────────────────────────────────────────

    /** @param sectionId null = every section of the class */
    public record ClassActivityRequest(@NotNull UUID academicYearId, @NotNull UUID academicClassId, UUID sectionId, UUID subjectId,
                                       @NotNull LocalDate activityDate, @NotNull ActivityType activityType,
                                       @NotBlank @Size(max = 200) String title, @Size(max = 5000) String description) {
    }

    public record ClassActivityResponse(UUID id, String activityCode, UUID academicYearId, UUID academicClassId, String className,
                                        UUID sectionId, String sectionName, UUID subjectId, String subjectName, UUID teacherId,
                                        String teacherName, LocalDate activityDate, ActivityType activityType, String title,
                                        String description, Instant updatedAt) {
    }

    // ── Announcements ────────────────────────────────────────────────────────

    /**
     * @param academicClassId required for CLASS and SECTION
     * @param sectionId       required for SECTION
     * @param notifyFamilies  send notifications when published (default true)
     * @param publish         publish at once instead of saving a draft
     */
    public record AnnouncementRequest(@NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 10000) String body,
                                      @NotNull AnnouncementAudience audience, UUID academicClassId, UUID sectionId,
                                      AnnouncementPriority priority, Boolean notifyFamilies, LocalDate expiresOn, Boolean publish) {
    }

    /** @param notificationsQueued messages queued by this call (publish only) */
    public record AnnouncementResponse(UUID id, String announcementCode, String title, String body, AnnouncementAudience audience,
                                       UUID academicClassId, String className, UUID sectionId, String sectionName,
                                       AnnouncementPriority priority, AnnouncementStatus status, boolean notifyFamilies, LocalDate publishOn,
                                       LocalDate expiresOn, Instant publishedAt, Integer notificationsQueued, Instant updatedAt) {
    }

    // ── Teacher remarks ──────────────────────────────────────────────────────

    /** @param visibleToGuardians false = staff-only note (default true) */
    public record RemarkRow(@NotNull UUID studentId, UUID subjectId, RemarkCategory category, RemarkSentiment sentiment,
                            @NotBlank @Size(max = 1000) String remark, Boolean visibleToGuardians) {
    }

    /** @param remarkDate default: today */
    public record RemarksRequest(LocalDate remarkDate, @NotEmpty @Size(max = 200) List<@Valid RemarkRow> remarks) {
    }

    public record RemarkUpdateRequest(UUID subjectId, RemarkCategory category, RemarkSentiment sentiment,
                                      @NotBlank @Size(max = 1000) String remark, Boolean visibleToGuardians) {
    }

    public record RemarkResponse(UUID id, UUID studentId, String studentName, LocalDate remarkDate, UUID teacherId, String teacherName,
                                 UUID subjectId, String subjectName, RemarkCategory category, RemarkSentiment sentiment, String remark,
                                 boolean visibleToGuardians, Instant createdAt) {
    }

    // ── Daily activity report ────────────────────────────────────────────────

    /** @param status PRESENT, ABSENT, LATE, HALF_DAY, EXCUSED, or NOT_MARKED */
    public record AttendanceInfo(String status, String remarks, Instant markedAt) {
    }

    public record HomeworkItem(UUID id, String subjectName, String title, String description, LocalDate assignedOn, LocalDate dueOn,
                               String teacherName) {
    }

    /** A lesson the teacher marked delivered that day (from lesson plans). */
    public record LessonItem(String subjectName, String title, String topic, String teacherName) {
    }

    public record ActivityItem(UUID id, ActivityType activityType, String subjectName, String title, String description,
                               String teacherName) {
    }

    public record AnnouncementItem(UUID id, String title, String body, AnnouncementPriority priority, AnnouncementAudience audience) {
    }

    public record RemarkItem(UUID id, RemarkCategory category, RemarkSentiment sentiment, String remark, String subjectName,
                             String teacherName, boolean visibleToGuardians) {
    }

    public record ReportSummary(String attendanceStatus, int homeworkAssigned, int homeworkDueTomorrow, int lessons, int activities,
                                int announcements, int remarks, int positiveRemarks, int concerns) {
    }

    /**
     * One student's day. Sections the school turned off in the report settings are empty lists
     * ({@code attendance} null).
     *
     * @param enrolled false when the student has no active enrollment for the date (everything else is empty)
     */
    public record DailyActivityReport(UUID studentId, String studentCode, String studentName, boolean enrolled, UUID academicClassId,
                                      String className, UUID sectionId, String sectionName, String rollNumber, LocalDate date,
                                      DayStatus dayStatus, String holidayName, AttendanceInfo attendance,
                                      List<HomeworkItem> homeworkAssigned, List<HomeworkItem> homeworkDueTomorrow,
                                      List<LessonItem> lessons, List<ActivityItem> activities, List<AnnouncementItem> announcements,
                                      List<RemarkItem> remarks, ReportSummary summary) {
    }

    /** Every child of the signed-in parent (or the signed-in student) for one day. */
    public record FamilyDailyReport(LocalDate date, List<DailyActivityReport> children) {
    }

    public record ChildResponse(UUID studentId, String studentCode, String name, UUID academicClassId, String className,
                                UUID sectionId, String sectionName, String rollNumber) {
    }

    public record DailyReportSettingsRequest(Boolean enabled, LocalTime sendTime, Boolean skipNonWorkingDays, Boolean includeAttendance,
                                             Boolean includeHomework, Boolean includeClassActivities, Boolean includeLessons,
                                             Boolean includeAnnouncements, Boolean includeRemarks) {
    }

    /** @param saved false = defaults; the school has not saved settings yet */
    public record DailyReportSettingsResponse(boolean enabled, LocalTime sendTime, boolean skipNonWorkingDays, boolean includeAttendance,
                                              boolean includeHomework, boolean includeClassActivities, boolean includeLessons,
                                              boolean includeAnnouncements, boolean includeRemarks, boolean saved) {
    }

    /** @param date default: today */
    public record RunRequest(LocalDate date) {
    }

    public record ReportRunResponse(LocalDate reportDate, ReportRunTrigger trigger, ReportRunStatus status, String skipReason,
                                    int students, int recipients, int messages, Instant startedAt, Instant finishedAt, String error) {
    }

    // ── Notifications ────────────────────────────────────────────────────────

    public record InboxItem(UUID id, String eventCode, String subject, String body, AnnouncementPriority priority, String refType,
                            UUID refId, LocalDate refDate, Instant createdAt, Instant readAt) {
    }

    public record Inbox(long unread, List<InboxItem> items) {
    }

    public record OutboxItem(UUID id, String eventCode, NotificationChannel channel, String recipientName, String address,
                             String subject, AnnouncementPriority priority, OutboxStatus status, int attempts, Instant nextAttemptAt,
                             Instant sentAt, String lastError, Instant createdAt) {
    }
}
