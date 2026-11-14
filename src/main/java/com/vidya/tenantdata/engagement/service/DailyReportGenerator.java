package com.vidya.tenantdata.engagement.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.tenantdata.calendar.dto.DayStatusResponse;
import com.vidya.tenantdata.calendar.enums.DayStatus;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.DailyActivityReport;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.ReportRunResponse;
import com.vidya.tenantdata.engagement.entity.DailyReportRun;
import com.vidya.tenantdata.engagement.entity.DailyReportSettings;
import com.vidya.tenantdata.engagement.enums.ReportRunStatus;
import com.vidya.tenantdata.engagement.enums.ReportRunTrigger;
import com.vidya.tenantdata.engagement.notification.NotificationOutboxService;
import com.vidya.tenantdata.engagement.notification.NotificationTemplates;
import com.vidya.tenantdata.engagement.notification.NotificationTemplates.Resolved;
import com.vidya.tenantdata.engagement.repository.DailyReportRunRepository;
import com.vidya.tenantdata.operations.entity.CommunicationSettings;
import com.vidya.tenantdata.operations.enums.NotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Sends one day's report to every family: claims the day (one server only), builds every enrolled student's
 * report, groups children per guardian contact and queues the messages in the outbox. The dispatcher delivers.
 *
 * <p>Runs in three short transactions (claim, work, outcome) so a failure is recorded as FAILED rather than
 * rolled back with the work.
 */
@Slf4j
@Service
public class DailyReportGenerator {

    private final DailyActivityReportService reportService;
    private final DailyReportQueries queries;
    private final DailyReportRunRepository runRepository;
    private final NotificationTemplates templates;
    private final NotificationOutboxService outbox;
    private final TransactionTemplate tx;
    private final Clock clock;

    public DailyReportGenerator(DailyActivityReportService reportService, DailyReportQueries queries, DailyReportRunRepository runRepository,
                                NotificationTemplates templates, NotificationOutboxService outbox,
                                @Qualifier("tenantTransactionManager") PlatformTransactionManager transactionManager, Clock clock) {
        this.reportService = reportService;
        this.queries = queries;
        this.runRepository = runRepository;
        this.templates = templates;
        this.outbox = outbox;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Scheduler entry point for the current school: sends today's report once the send time has passed. */
    public ReportRunResponse runIfDue() {
        LocalDateTime now = LocalDateTime.now(clock);
        Boolean due = tx.execute(st -> {
            DailyReportSettings s = reportService.settings0();
            return s.isEnabled() && !now.toLocalTime().isBefore(s.getSendTime())
                    && runRepository.findByReportDate(now.toLocalDate()).isEmpty();
        });
        return Boolean.TRUE.equals(due) ? generate(now.toLocalDate(), ReportRunTrigger.SCHEDULED, null) : null;
    }

    /**
     * Sends the report for {@code date}. SCHEDULED runs once per day; MANUAL may run a day again — messages
     * already queued for that day are not sent twice (outbox keys).
     *
     * @return the run, or null when a scheduled run found the day already taken
     */
    public ReportRunResponse generate(LocalDate date, ReportRunTrigger trigger, UUID actor) {
        if (date.isAfter(LocalDate.now(clock))) {
            throw new BusinessException("A daily report cannot be sent for a future date");
        }
        Boolean claimed = tx.execute(st -> runRepository.claim(date, trigger.name(), actor) == 1
                || (trigger == ReportRunTrigger.MANUAL && runRepository.reclaim(date, trigger.name(), actor) == 1));
        if (!Boolean.TRUE.equals(claimed)) {
            if (trigger == ReportRunTrigger.MANUAL) {
                throw new BusinessException("REPORT_RUN_IN_PROGRESS", "The report for " + date + " is being sent right now");
            }
            return null;
        }
        try {
            Outcome outcome = tx.execute(st -> work(date, trigger));
            return finish(date, outcome, null);
        } catch (RuntimeException e) {
            log.error("Daily report for {} failed", date, e);
            return finish(date, null, e);
        }
    }

    private Outcome work(LocalDate date, ReportRunTrigger trigger) {
        DailyReportSettings s = reportService.settings0();
        if (trigger == ReportRunTrigger.SCHEDULED && s.isSkipNonWorkingDays()) {
            DayStatusResponse day = reportService.dayStatus(date);
            if (day != null && day.status() != DayStatus.WORKING) {
                return Outcome.skipped("Not a working day (" + (day.holidayName() != null ? day.holidayName() : day.status()) + ")");
            }
        }
        Resolved template = templates.resolve(NotificationEvent.DAILY_ACTIVITY_REPORT);
        if (!template.enabled()) {
            return Outcome.skipped("The Daily activity report message is turned off in the communication templates");
        }
        if (template.channels().isEmpty()) {
            return Outcome.skipped("None of the template's channels is switched on in the communication settings");
        }
        List<DailyActivityReport> reports = reportService.build(null, null, date, false, s);
        List<UUID> withContent = reports.stream().filter(DailyReportFormatter::hasContent).map(DailyActivityReport::studentId).toList();
        CommunicationSettings cs = templates.settings();
        DailyReportComposer.Composition c = DailyReportComposer.compose(reports, queries.guardians(withContent), template,
                templates.schoolName(cs), date);
        int queued = outbox.enqueue(c.drafts());
        log.info("Daily report {}: {} students, {} with news, {} recipients, {} messages queued", date, reports.size(), withContent.size(),
                c.recipients(), queued);
        return Outcome.completed(withContent.size(), c.recipients(), queued);
    }

    private ReportRunResponse finish(LocalDate date, Outcome outcome, RuntimeException error) {
        return tx.execute(st -> {
            DailyReportRun run = runRepository.findByReportDate(date).orElseThrow();
            if (error != null) {
                run.setStatus(ReportRunStatus.FAILED);
                run.setError(error.getClass().getSimpleName() + ": " + error.getMessage());
            } else {
                run.setStatus(outcome.status());
                run.setSkipReason(outcome.skipReason());
                run.setStudents(outcome.students());
                run.setRecipients(outcome.recipients());
                run.setMessages(outcome.messages());
            }
            run.setFinishedAt(clock.instant());
            return response(runRepository.save(run));
        });
    }

    /** What a run did. */
    record Outcome(ReportRunStatus status, String skipReason, int students, int recipients, int messages) {
        static Outcome skipped(String reason) {
            return new Outcome(ReportRunStatus.SKIPPED, reason, 0, 0, 0);
        }

        static Outcome completed(int students, int recipients, int messages) {
            return new Outcome(ReportRunStatus.COMPLETED, null, students, recipients, messages);
        }
    }

    public List<ReportRunResponse> runs(Integer limit) {
        int max = limit == null ? 30 : Math.max(1, Math.min(limit, 365));
        return tx.execute(st -> runRepository.findAllByOrderByReportDateDesc(PageRequest.of(0, max)).stream()
                .map(DailyReportGenerator::response).toList());
    }

    static ReportRunResponse response(DailyReportRun r) {
        return new ReportRunResponse(r.getReportDate(), r.getTrigger(), r.getStatus(), r.getSkipReason(), r.getStudents(), r.getRecipients(),
                r.getMessages(), r.getStartedAt(), r.getFinishedAt(), r.getError());
    }
}
