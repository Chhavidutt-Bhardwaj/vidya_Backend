package com.vidya.tenantdata.engagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/** The school's daily report setup (single row, id = 1). Defaults apply until the school saves it. */
@Entity
@Table(name = "daily_report_settings")
@Getter
@Setter
@NoArgsConstructor
public class DailyReportSettings {

    public static final short ID = 1;

    @Id
    private Short id = ID;

    /** Send automatically every day. The report can always be viewed in the app. */
    @Column(nullable = false)
    private boolean enabled = false;

    /** School-local time after which the day's report is sent. */
    @Column(name = "send_time", nullable = false)
    private LocalTime sendTime = LocalTime.of(18, 0);

    @Column(name = "skip_non_working_days", nullable = false)
    private boolean skipNonWorkingDays = true;

    @Column(name = "include_attendance", nullable = false)
    private boolean includeAttendance = true;

    @Column(name = "include_homework", nullable = false)
    private boolean includeHomework = true;

    @Column(name = "include_class_activities", nullable = false)
    private boolean includeClassActivities = true;

    @Column(name = "include_lessons", nullable = false)
    private boolean includeLessons = true;

    @Column(name = "include_announcements", nullable = false)
    private boolean includeAnnouncements = true;

    @Column(name = "include_remarks", nullable = false)
    private boolean includeRemarks = true;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private Long version = 0L;
}
