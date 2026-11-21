package com.vidya.tenantdata.engagement;

import com.vidya.common.exception.BusinessException;
import com.vidya.tenantdata.calendar.dto.DayStatusResponse;
import com.vidya.tenantdata.calendar.enums.DayStatus;
import com.vidya.tenantdata.calendar.enums.HolidayType;
import com.vidya.tenantdata.calendar.service.CalendarService;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.ActivityItem;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.AnnouncementItem;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.AttendanceInfo;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.DailyActivityReport;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.HomeworkItem;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.RemarkItem;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.ReportRunResponse;
import com.vidya.tenantdata.engagement.entity.DailyReportRun;
import com.vidya.tenantdata.engagement.entity.DailyReportSettings;
import com.vidya.tenantdata.engagement.enums.ActivityType;
import com.vidya.tenantdata.engagement.enums.AnnouncementAudience;
import com.vidya.tenantdata.engagement.enums.AnnouncementPriority;
import com.vidya.tenantdata.engagement.enums.RemarkCategory;
import com.vidya.tenantdata.engagement.enums.RemarkSentiment;
import com.vidya.tenantdata.engagement.enums.ReportRunStatus;
import com.vidya.tenantdata.engagement.enums.ReportRunTrigger;
import com.vidya.tenantdata.engagement.notification.NotificationOutboxService;
import com.vidya.tenantdata.engagement.notification.NotificationTemplates;
import com.vidya.tenantdata.engagement.notification.NotificationTemplates.Resolved;
import com.vidya.tenantdata.engagement.notification.OutboxDraft;
import com.vidya.tenantdata.engagement.repository.DailyReportRunRepository;
import com.vidya.tenantdata.engagement.repository.DailyReportSettingsRepository;
import com.vidya.tenantdata.engagement.service.DailyActivityReportService;
import com.vidya.tenantdata.engagement.service.DailyReportComposer;
import com.vidya.tenantdata.engagement.service.DailyReportComposer.Composition;
import com.vidya.tenantdata.engagement.service.DailyReportGenerator;
import com.vidya.tenantdata.engagement.repository.DailyReportQueries;
import com.vidya.tenantdata.engagement.repository.DailyReportQueries.EnrolledStudent;
import com.vidya.tenantdata.engagement.repository.DailyReportQueries.GuardianContact;
import com.vidya.tenantdata.engagement.repository.DailyReportQueries.StudentRef;
import com.vidya.tenantdata.operations.entity.CommunicationSettings;
import com.vidya.tenantdata.operations.enums.NotificationChannel;
import com.vidya.tenantdata.operations.enums.NotificationEvent;
import com.vidya.tenantdata.security.DataAccessService;
import com.vidya.tenantdata.user.account.SchoolContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Student daily activity report")
class DailyActivityReportTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 13);

    @Mock private DailyReportQueries queries;
    @Mock private DailyReportSettingsRepository settingsRepository;
    @Mock private CalendarService calendarService;
    @Mock private DataAccessService dataAccess;
    @Mock private SchoolContext context;

    private final Clock clock = Clock.fixed(ZonedDateTime.of(2026, 11, 13, 19, 0, 0, 0, IST).toInstant(), IST);
    private DailyActivityReportService service;

    private final UUID year = UUID.randomUUID();
    private final UUID classV = UUID.randomUUID();
    private final UUID sectionA = UUID.randomUUID();
    private final UUID aarav = UUID.randomUUID();
    private final UUID meera = UUID.randomUUID();
    private final UUID rohan = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DailyActivityReportService(queries, settingsRepository, calendarService, dataAccess, context, clock);
        when(calendarService.getDayStatus(any())).thenAnswer(inv -> new DayStatusResponse(inv.getArgument(0), DayOfWeek.FRIDAY, DayStatus.WORKING));
        when(queries.enrolledStudents(eq(DAY), any(), isNull(), any())).thenReturn(List.of(
                enrolled(aarav, "Aarav Sharma"), enrolled(meera, "Meera Sharma")));
        when(queries.attendance(anyCollection(), eq(DAY))).thenReturn(Map.of(aarav, new AttendanceInfo("PRESENT", null, Instant.now())));
        when(queries.homework(year, classV, sectionA, DAY)).thenReturn(List.of(
                new HomeworkItem(UUID.randomUUID(), "Mathematics", "Exercise 4.2", null, DAY, DAY.plusDays(2), "Asha Rao"),
                new HomeworkItem(UUID.randomUUID(), "English", "Essay on trees", null, DAY.minusDays(3), DAY.plusDays(1), "Ravi Iyer")));
        when(queries.activities(year, classV, sectionA, DAY)).thenReturn(List.of(
                new ActivityItem(UUID.randomUUID(), ActivityType.SPORTS, null, "Inter-house relay", null, "Asha Rao")));
        when(queries.lessonsDelivered(year, classV, sectionA, DAY)).thenReturn(List.of());
        when(queries.announcements(classV, sectionA, DAY)).thenReturn(List.of(
                new AnnouncementItem(UUID.randomUUID(), "Annual day on 20 Nov", "…", AnnouncementPriority.IMPORTANT, AnnouncementAudience.SCHOOL)));
        when(queries.remarks(anyCollection(), eq(DAY), anyBoolean())).thenReturn(Map.of(meera, List.of(
                new RemarkItem(UUID.randomUUID(), RemarkCategory.PARTICIPATION, RemarkSentiment.POSITIVE, "Great answers in class", "Science", "Asha Rao", true))));
    }

    private EnrolledStudent enrolled(UUID id, String name) {
        return new EnrolledStudent(id, "STU-" + name.charAt(0), name, year, classV, "Class V", sectionA, "A", "1");
    }

    @Nested
    @DisplayName("Building")
    class Building {

        @Test
        @DisplayName("Splits homework set today from homework due tomorrow, defaults attendance to NOT_MARKED")
        void contents() {
            List<DailyActivityReport> r = service.build(List.of(aarav, meera), null, DAY, false, new DailyReportSettings());
            assertThat(r).extracting(DailyActivityReport::studentName).containsExactly("Aarav Sharma", "Meera Sharma");
            DailyActivityReport a = r.get(0);
            assertThat(a.attendance().status()).isEqualTo("PRESENT");
            assertThat(a.homeworkAssigned()).extracting(HomeworkItem::title).containsExactly("Exercise 4.2");
            assertThat(a.homeworkDueTomorrow()).extracting(HomeworkItem::title).containsExactly("Essay on trees");
            assertThat(a.activities()).hasSize(1);
            assertThat(a.announcements()).hasSize(1);
            assertThat(a.remarks()).isEmpty();
            assertThat(a.dayStatus()).isEqualTo(DayStatus.WORKING);

            DailyActivityReport m = r.get(1);
            assertThat(m.attendance().status()).isEqualTo("NOT_MARKED");
            assertThat(m.summary().positiveRemarks()).isEqualTo(1);
            assertThat(m.summary().homeworkAssigned()).isEqualTo(1);
            verify(queries).homework(year, classV, sectionA, DAY); // once for the whole section
        }

        @Test
        @DisplayName("Content the school turned off is not loaded")
        void settingsExclude() {
            DailyReportSettings s = new DailyReportSettings();
            s.setIncludeHomework(false);
            s.setIncludeAttendance(false);
            s.setIncludeRemarks(false);
            DailyActivityReport a = service.build(List.of(aarav), null, DAY, false, s).get(0);
            assertThat(a.homeworkAssigned()).isEmpty();
            assertThat(a.attendance()).isNull();
            verify(queries, never()).homework(any(), any(), any(), any());
            verify(queries, never()).attendance(anyCollection(), any());
            verify(queries, never()).remarks(anyCollection(), any(), anyBoolean());
        }

        @Test
        @DisplayName("Families never get staff-only remarks; staff with remark rights do")
        void staffOnlyRemarks() {
            service.build(List.of(aarav), null, DAY, false, new DailyReportSettings());
            verify(queries).remarks(anyCollection(), eq(DAY), eq(false));
            service.build(List.of(aarav), null, DAY, true, new DailyReportSettings());
            verify(queries).remarks(anyCollection(), eq(DAY), eq(true));
        }

        @Test
        @DisplayName("A child without an enrollment on the date gets an empty, not-enrolled report")
        void notEnrolled() {
            when(queries.students(anyCollection())).thenReturn(Map.of(rohan, new StudentRef(rohan, "STU-R", "Rohan Sharma")));
            List<DailyActivityReport> r = service.build(List.of(aarav, meera, rohan), null, DAY, false, new DailyReportSettings());
            DailyActivityReport x = r.stream().filter(d -> d.studentId().equals(rohan)).findFirst().orElseThrow();
            assertThat(x.enrolled()).isFalse();
            assertThat(x.homeworkAssigned()).isEmpty();
        }

        @Test
        @DisplayName("Holiday is shown on the report")
        void holiday() {
            when(calendarService.getDayStatus(DAY)).thenReturn(new DayStatusResponse(DAY, DayOfWeek.FRIDAY, DayStatus.HOLIDAY, "Diwali", HolidayType.values()[0]));
            DailyActivityReport a = service.build(List.of(aarav), null, DAY, false, new DailyReportSettings()).get(0);
            assertThat(a.dayStatus()).isEqualTo(DayStatus.HOLIDAY);
            assertThat(a.holidayName()).isEqualTo("Diwali");
        }
    }

    @Nested
    @DisplayName("Composing messages")
    class Composing {

        private final UUID parentLogin = UUID.randomUUID();

        private Resolved template(NotificationChannel... channels) {
            return new Resolved(NotificationEvent.DAILY_ACTIVITY_REPORT, true, EnumSet.copyOf(List.of(channels)),
                    NotificationEvent.DAILY_ACTIVITY_REPORT.defaultSubject(), NotificationEvent.DAILY_ACTIVITY_REPORT.defaultBody());
        }

        private List<DailyActivityReport> reports() {
            return service.build(List.of(aarav, meera), null, DAY, false, new DailyReportSettings());
        }

        @Test
        @DisplayName("One parent with two children gets one message per channel covering both")
        void oneMessagePerParent() {
            List<GuardianContact> guardians = List.of(
                    new GuardianContact(aarav, UUID.randomUUID(), "Priya Sharma", "Priya@Example.com ", "+91 98765 43210", parentLogin, true),
                    new GuardianContact(meera, UUID.randomUUID(), "Priya Sharma", "priya@example.com", "+919876543210", parentLogin, true));
            Composition c = DailyReportComposer.compose(reports(), guardians,
                    template(NotificationChannel.IN_APP, NotificationChannel.EMAIL, NotificationChannel.SMS), "Green Valley School", DAY);

            assertThat(c.recipients()).isEqualTo(1);
            assertThat(c.drafts()).hasSize(3);
            OutboxDraft email = c.drafts().stream().filter(d -> d.channel() == NotificationChannel.EMAIL).findFirst().orElseThrow();
            assertThat(email.address()).isEqualTo("priya@example.com");
            assertThat(email.subject()).isEqualTo("Daily report for 13 Nov 2026: Aarav, Meera");
            assertThat(email.body()).contains("Aarav Sharma (Class V A)", "Meera Sharma (Class V A)", "• Attendance: Present",
                    "Mathematics – Exercise 4.2", "Due tomorrow: English – Essay on trees", "\"Great answers in class\"", "Green Valley School");
            assertThat(email.dedupeKey()).isEqualTo("DAILY_REPORT:2026-11-13:EMAIL:priya@example.com");

            OutboxDraft sms = c.drafts().stream().filter(d -> d.channel() == NotificationChannel.SMS).findFirst().orElseThrow();
            assertThat(sms.address()).isEqualTo("+919876543210");
            assertThat(sms.body()).contains("Aarav: Present, HW 1, 1 due tmrw", "Meera: Not marked yet").doesNotContain("•");

            OutboxDraft inApp = c.drafts().stream().filter(d -> d.channel() == NotificationChannel.IN_APP).findFirst().orElseThrow();
            assertThat(inApp.recipientUserId()).isEqualTo(parentLogin);
            assertThat(inApp.address()).isNull();
            assertThat(inApp.refDate()).isEqualTo(DAY);
        }

        @Test
        @DisplayName("Guardians without a contact for a channel are left out of that channel")
        void missingContacts() {
            List<GuardianContact> guardians = List.of(
                    new GuardianContact(aarav, UUID.randomUUID(), "Father", null, "12", null, false),
                    new GuardianContact(aarav, UUID.randomUUID(), "Mother", "mother@example.com", null, null, true));
            Composition c = DailyReportComposer.compose(reports(), guardians,
                    template(NotificationChannel.IN_APP, NotificationChannel.EMAIL, NotificationChannel.SMS), "School", DAY);
            assertThat(c.drafts()).extracting(OutboxDraft::channel).containsExactly(NotificationChannel.EMAIL);
            assertThat(c.recipients()).isEqualTo(1);
        }

        @Test
        @DisplayName("Students with nothing to report are not messaged")
        void nothingToReport() {
            when(queries.homework(any(), any(), any(), any())).thenReturn(List.of());
            when(queries.activities(any(), any(), any(), any())).thenReturn(List.of());
            when(queries.announcements(any(), any(), any())).thenReturn(List.of());
            when(queries.attendance(anyCollection(), any())).thenReturn(Map.of());
            when(queries.remarks(anyCollection(), any(), anyBoolean())).thenReturn(Map.of());
            Composition c = DailyReportComposer.compose(reports(),
                    List.of(new GuardianContact(aarav, UUID.randomUUID(), "P", "p@example.com", null, null, true)),
                    template(NotificationChannel.EMAIL), "School", DAY);
            assertThat(c.drafts()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Sending a day")
    class Generating {

        @Mock private DailyReportRunRepository runRepository;
        @Mock private NotificationTemplates templates;
        @Mock private NotificationOutboxService outbox;
        @Mock private PlatformTransactionManager txManager;

        private DailyReportGenerator generator;
        private final AtomicReference<DailyReportRun> run = new AtomicReference<>();
        private final DailyReportSettings settings = new DailyReportSettings();

        @BeforeEach
        void setUpGenerator() throws Exception {
            when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
            generator = new DailyReportGenerator(service, queries, runRepository, templates, outbox, txManager, clock);
            settings.setEnabled(true);
            when(settingsRepository.findById(DailyReportSettings.ID)).thenReturn(Optional.of(settings));
            when(templates.settings()).thenReturn(new CommunicationSettings());
            when(templates.schoolName(any())).thenReturn("Green Valley School");
            when(templates.resolve(NotificationEvent.DAILY_ACTIVITY_REPORT)).thenReturn(new Resolved(NotificationEvent.DAILY_ACTIVITY_REPORT, true,
                    EnumSet.of(NotificationChannel.EMAIL), "S {{date}}", "B {{summary}}"));
            when(queries.enrolledStudents(eq(DAY), isNull(), isNull(), isNull())).thenReturn(List.of(enrolled(aarav, "Aarav Sharma"),
                    enrolled(meera, "Meera Sharma")));
            when(queries.guardians(anyCollection())).thenReturn(List.of(
                    new GuardianContact(aarav, UUID.randomUUID(), "Priya", "priya@example.com", null, null, true),
                    new GuardianContact(meera, UUID.randomUUID(), "Priya", "priya@example.com", null, null, true)));
            when(outbox.enqueue(anyCollection())).thenAnswer(inv -> ((java.util.Collection<?>) inv.getArgument(0)).size());

            Constructor<DailyReportRun> ctor = DailyReportRun.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            DailyReportRun r = ctor.newInstance();
            r.setReportDate(DAY);
            r.setTrigger(ReportRunTrigger.SCHEDULED);
            r.setStatus(ReportRunStatus.RUNNING);
            run.set(r);
            when(runRepository.claim(eq(DAY), any(), any())).thenReturn(1);
            when(runRepository.findByReportDate(DAY)).thenReturn(Optional.of(r));
            when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("Queues one message per parent and records the counts")
        void completes() {
            ReportRunResponse r = generator.generate(DAY, ReportRunTrigger.SCHEDULED, null);
            assertThat(r.status()).isEqualTo(ReportRunStatus.COMPLETED);
            assertThat(r.students()).isEqualTo(2);
            assertThat(r.recipients()).isEqualTo(1);
            assertThat(r.messages()).isEqualTo(1);
        }

        @Test
        @DisplayName("Scheduled runs skip holidays; manual runs do not")
        void holidaySkipped() {
            when(calendarService.getDayStatus(DAY)).thenReturn(new DayStatusResponse(DAY, DayOfWeek.FRIDAY, DayStatus.HOLIDAY, "Diwali", null));
            ReportRunResponse r = generator.generate(DAY, ReportRunTrigger.SCHEDULED, null);
            assertThat(r.status()).isEqualTo(ReportRunStatus.SKIPPED);
            assertThat(r.skipReason()).contains("Diwali");

            ReportRunResponse manual = generator.generate(DAY, ReportRunTrigger.MANUAL, UUID.randomUUID());
            assertThat(manual.status()).isEqualTo(ReportRunStatus.COMPLETED);
        }

        @Test
        @DisplayName("A turned-off template is recorded as skipped")
        void templateOff() {
            when(templates.resolve(NotificationEvent.DAILY_ACTIVITY_REPORT)).thenReturn(new Resolved(NotificationEvent.DAILY_ACTIVITY_REPORT, false,
                    EnumSet.of(NotificationChannel.EMAIL), "s", "b"));
            assertThat(generator.generate(DAY, ReportRunTrigger.SCHEDULED, null).status()).isEqualTo(ReportRunStatus.SKIPPED);
        }

        @Test
        @DisplayName("A day another server already claimed is left alone (scheduled) or refused (manual, while running)")
        void alreadyClaimed() {
            when(runRepository.claim(eq(DAY), any(), any())).thenReturn(0);
            when(runRepository.reclaim(eq(DAY), any(), any())).thenReturn(0);
            assertThat(generator.generate(DAY, ReportRunTrigger.SCHEDULED, null)).isNull();
            assertThatThrownBy(() -> generator.generate(DAY, ReportRunTrigger.MANUAL, null)).isInstanceOf(BusinessException.class);
            verify(outbox, never()).enqueue(anyCollection());
        }

        @Test
        @DisplayName("A failure is recorded as FAILED")
        void failure() {
            when(queries.guardians(anyCollection())).thenThrow(new IllegalStateException("db gone"));
            ReportRunResponse r = generator.generate(DAY, ReportRunTrigger.SCHEDULED, null);
            assertThat(r.status()).isEqualTo(ReportRunStatus.FAILED);
            assertThat(r.error()).contains("db gone");
        }

        @Test
        @DisplayName("runIfDue waits for the send time and for the report to be switched on")
        void runIfDue() {
            settings.setSendTime(java.time.LocalTime.of(20, 0));
            assertThat(generator.runIfDue()).isNull();
            settings.setSendTime(java.time.LocalTime.of(18, 0));
            settings.setEnabled(false);
            assertThat(generator.runIfDue()).isNull();
            settings.setEnabled(true);
            when(runRepository.findByReportDate(DAY)).thenReturn(Optional.empty());
            List<Object> claimed = new ArrayList<>();
            when(runRepository.claim(eq(DAY), eq("SCHEDULED"), any())).thenAnswer(inv -> {
                claimed.add(inv.getArgument(0));
                return 0;
            });
            generator.runIfDue();
            assertThat(claimed).containsExactly(DAY);
        }

        @Test
        void futureDateRefused() {
            assertThatThrownBy(() -> generator.generate(DAY.plusDays(1), ReportRunTrigger.MANUAL, null)).isInstanceOf(BusinessException.class);
        }
    }
}
