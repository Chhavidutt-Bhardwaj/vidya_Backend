package com.vidya.tenantdata.engagement.service;

import com.vidya.tenantdata.engagement.repository.DailyReportQueries;
import com.vidya.tenantdata.calendar.dto.DayStatusResponse;
import com.vidya.tenantdata.calendar.service.CalendarService;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.ActivityItem;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.AnnouncementItem;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.AttendanceInfo;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.ChildResponse;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.DailyActivityReport;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.DailyReportSettingsRequest;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.DailyReportSettingsResponse;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.FamilyDailyReport;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.HomeworkItem;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.LessonItem;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.RemarkItem;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.ReportSummary;
import com.vidya.tenantdata.engagement.entity.DailyReportSettings;
import com.vidya.tenantdata.engagement.enums.RemarkSentiment;
import com.vidya.tenantdata.engagement.repository.DailyReportSettingsRepository;
import com.vidya.tenantdata.engagement.repository.DailyReportQueries.EnrolledStudent;
import com.vidya.tenantdata.engagement.repository.DailyReportQueries.StudentRef;
import com.vidya.tenantdata.security.DataAccessService;
import com.vidya.tenantdata.security.OwnRecords;
import com.vidya.tenantdata.user.account.SchoolContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Student Daily Activity Report: attendance, homework set today and due tomorrow, lessons delivered,
 * class activities, announcements and teacher remarks for one student and one day.
 *
 * <p>Who may see which student is decided by the controller ({@code @dataAccess}); this service decides only
 * whether staff-only remarks are included (users who can write remarks see them, families never do).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyActivityReportService {

    static final String STAFF_REMARKS_AUTHORITY = "STUDENT_REMARK_MANAGE";

    private final DailyReportQueries queries;
    private final DailyReportSettingsRepository settingsRepository;
    private final CalendarService calendarService;
    private final DataAccessService dataAccess;
    private final SchoolContext context;
    private final Clock clock;

    // ── reports ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public DailyActivityReport student(UUID studentId, LocalDate date) {
        return build(List.of(studentId), null, day(date), seesStaffRemarks(), settings0()).get(0);
    }

    /** Every enrolled student of a section (teacher preview of what families will get). */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<DailyActivityReport> section(UUID sectionId, LocalDate date) {
        return build(null, sectionId, day(date), seesStaffRemarks(), settings0());
    }

    /** All children of the signed-in parent (and the signed-in student's own report). */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public FamilyDailyReport family(LocalDate date) {
        LocalDate d = day(date);
        Set<UUID> children = familyStudentIds();
        return new FamilyDailyReport(d, children.isEmpty() ? List.of() : build(children, null, d, seesStaffRemarks(), settings0()));
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<ChildResponse> children() {
        Set<UUID> ids = familyStudentIds();
        if (ids.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(clock);
        Map<UUID, EnrolledStudent> enrolled = new HashMap<>();
        queries.enrolledStudents(today, ids, null, null).forEach(e -> enrolled.put(e.studentId(), e));
        List<ChildResponse> out = new ArrayList<>();
        for (StudentRef s : queries.students(ids).values()) {
            EnrolledStudent e = enrolled.get(s.studentId());
            out.add(e == null ? new ChildResponse(s.studentId(), s.studentCode(), s.name(), null, null, null, null, null)
                    : new ChildResponse(s.studentId(), s.studentCode(), s.name(), e.academicClassId(), e.className(), e.sectionId(),
                    e.sectionName(), e.rollNumber()));
        }
        out.sort(Comparator.comparing(ChildResponse::name, Comparator.nullsLast(String::compareToIgnoreCase)));
        return out;
    }

    /**
     * Builds reports. Content the school turned off in the settings is left empty.
     *
     * @param studentIds       these students (a student without an enrollment on the date gets {@code enrolled=false}); null = all
     * @param sectionId        with {@code studentIds} null: only this section
     * @param includeStaffOnly include remarks not visible to guardians
     */
    public List<DailyActivityReport> build(Collection<UUID> studentIds, UUID sectionId, LocalDate date, boolean includeStaffOnly,
                                           DailyReportSettings s) {
        DayStatusResponse day = dayStatus(date);
        List<EnrolledStudent> enrolled = queries.enrolledStudents(date, studentIds, null, studentIds == null ? sectionId : null);
        List<UUID> ids = enrolled.stream().map(EnrolledStudent::studentId).toList();
        Map<UUID, AttendanceInfo> attendance = s.isIncludeAttendance() ? queries.attendance(ids, date) : Map.of();
        Map<UUID, List<RemarkItem>> remarks = s.isIncludeRemarks() ? queries.remarks(ids, date, includeStaffOnly) : Map.of();
        Map<String, SectionContent> perSection = new HashMap<>();

        List<DailyActivityReport> out = new ArrayList<>();
        for (EnrolledStudent e : enrolled) {
            SectionContent c = perSection.computeIfAbsent(e.academicYearId() + "|" + e.academicClassId() + "|" + e.sectionId(),
                    k -> load(e, date, s));
            AttendanceInfo att = s.isIncludeAttendance() ? attendance.getOrDefault(e.studentId(), new AttendanceInfo("NOT_MARKED", null, null)) : null;
            List<RemarkItem> rem = remarks.getOrDefault(e.studentId(), List.of());
            List<HomeworkItem> assigned = c.homework.stream().filter(h -> date.equals(h.assignedOn())).toList();
            List<HomeworkItem> dueTomorrow = c.homework.stream()
                    .filter(h -> date.plusDays(1).equals(h.dueOn()) && !date.equals(h.assignedOn())).toList();
            ReportSummary summary = new ReportSummary(att == null ? null : att.status(), assigned.size(), dueTomorrow.size(),
                    c.lessons.size(), c.activities.size(), c.announcements.size(), rem.size(),
                    (int) rem.stream().filter(r -> r.sentiment() == RemarkSentiment.POSITIVE).count(),
                    (int) rem.stream().filter(r -> r.sentiment() == RemarkSentiment.CONCERN).count());
            out.add(new DailyActivityReport(e.studentId(), e.studentCode(), e.name(), true, e.academicClassId(), e.className(), e.sectionId(),
                    e.sectionName(), e.rollNumber(), date, day == null ? null : day.status(), day == null ? null : day.holidayName(), att,
                    assigned, dueTomorrow, c.lessons, c.activities, c.announcements, rem, summary));
        }

        if (studentIds != null) {
            Set<UUID> missing = new LinkedHashSet<>(studentIds);
            ids.forEach(missing::remove);
            for (StudentRef ref : queries.students(missing).values()) {
                out.add(new DailyActivityReport(ref.studentId(), ref.studentCode(), ref.name(), false, null, null, null, null, null, date,
                        day == null ? null : day.status(), day == null ? null : day.holidayName(), null, List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), new ReportSummary(null, 0, 0, 0, 0, 0, 0, 0, 0)));
            }
            if (out.isEmpty()) {
                throw new com.vidya.common.exception.ResourceNotFoundException("Student", studentIds.iterator().next());
            }
        }
        out.sort(Comparator.comparing(DailyActivityReport::studentName, Comparator.nullsLast(String::compareToIgnoreCase)));
        return out;
    }

    private record SectionContent(List<HomeworkItem> homework, List<LessonItem> lessons, List<ActivityItem> activities,
                                  List<AnnouncementItem> announcements) {
    }

    private SectionContent load(EnrolledStudent e, LocalDate date, DailyReportSettings s) {
        return new SectionContent(
                s.isIncludeHomework() ? queries.homework(e.academicYearId(), e.academicClassId(), e.sectionId(), date) : List.of(),
                s.isIncludeLessons() ? queries.lessonsDelivered(e.academicYearId(), e.academicClassId(), e.sectionId(), date) : List.of(),
                s.isIncludeClassActivities() ? queries.activities(e.academicYearId(), e.academicClassId(), e.sectionId(), date) : List.of(),
                s.isIncludeAnnouncements() ? queries.announcements(e.academicClassId(), e.sectionId(), date) : List.of());
    }

    // ── settings ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public DailyReportSettingsResponse settings() {
        return settingsRepository.findById(DailyReportSettings.ID).map(s -> response(s, true))
                .orElseGet(() -> response(new DailyReportSettings(), false));
    }

    /** Fields left null keep their current value. */
    @Transactional(transactionManager = "tenantTransactionManager")
    public DailyReportSettingsResponse saveSettings(DailyReportSettingsRequest r) {
        DailyReportSettings s = settingsRepository.findById(DailyReportSettings.ID).orElseGet(DailyReportSettings::new);
        if (r.enabled() != null) {
            s.setEnabled(r.enabled());
        }
        if (r.sendTime() != null) {
            s.setSendTime(r.sendTime().withSecond(0).withNano(0));
        }
        if (r.skipNonWorkingDays() != null) {
            s.setSkipNonWorkingDays(r.skipNonWorkingDays());
        }
        if (r.includeAttendance() != null) {
            s.setIncludeAttendance(r.includeAttendance());
        }
        if (r.includeHomework() != null) {
            s.setIncludeHomework(r.includeHomework());
        }
        if (r.includeClassActivities() != null) {
            s.setIncludeClassActivities(r.includeClassActivities());
        }
        if (r.includeLessons() != null) {
            s.setIncludeLessons(r.includeLessons());
        }
        if (r.includeAnnouncements() != null) {
            s.setIncludeAnnouncements(r.includeAnnouncements());
        }
        if (r.includeRemarks() != null) {
            s.setIncludeRemarks(r.includeRemarks());
        }
        s.setUpdatedBy(context.actorId());
        return response(settingsRepository.save(s), true);
    }

    /** Stored settings or the defaults. */
    public DailyReportSettings settings0() {
        return settingsRepository.findById(DailyReportSettings.ID).orElseGet(DailyReportSettings::new);
    }

    private static DailyReportSettingsResponse response(DailyReportSettings s, boolean saved) {
        return new DailyReportSettingsResponse(s.isEnabled(), s.getSendTime(), s.isSkipNonWorkingDays(), s.isIncludeAttendance(),
                s.isIncludeHomework(), s.isIncludeClassActivities(), s.isIncludeLessons(), s.isIncludeAnnouncements(), s.isIncludeRemarks(), saved);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Calendar status of the day; null when the calendar is not set up. */
    public DayStatusResponse dayStatus(LocalDate date) {
        try {
            return calendarService.getDayStatus(date);
        } catch (RuntimeException e) {
            log.debug("No day status for {}: {}", date, e.getMessage());
            return null;
        }
    }

    private LocalDate day(LocalDate date) {
        return date != null ? date : LocalDate.now(clock);
    }

    private Set<UUID> familyStudentIds() {
        OwnRecords own = dataAccess.ownRecords(SecurityContextHolder.getContext().getAuthentication());
        Set<UUID> ids = new LinkedHashSet<>(own.childStudentIds());
        ids.addAll(own.selfStudentIds());
        return ids;
    }

    private static boolean seesStaffRemarks() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(STAFF_REMARKS_AUTHORITY::equals);
    }
}
