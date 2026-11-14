package com.vidya.tenantdata.engagement.entity;

import com.vidya.tenantdata.engagement.enums.ReportRunStatus;
import com.vidya.tenantdata.engagement.enums.ReportRunTrigger;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One day's daily report run. The row is claimed with {@code INSERT ... ON CONFLICT DO NOTHING}
 * (see {@code DailyReportRunRepository#claim}), so only one server sends a given day.
 */
@Entity
@Table(name = "daily_report_run")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyReportRun {

    @Id
    private UUID id;

    @Column(name = "report_date", nullable = false, updatable = false)
    private LocalDate reportDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_trigger", nullable = false, length = 20)
    private ReportRunTrigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportRunStatus status;

    @Column(name = "skip_reason")
    private String skipReason;

    @Column(nullable = false)
    private int students;

    @Column(nullable = false)
    private int recipients;

    @Column(nullable = false)
    private int messages;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(columnDefinition = "text")
    private String error;

    @Column(name = "started_by")
    private UUID startedBy;
}
