package com.vidya.tenantdata.timetable.entity;

import com.vidya.tenantdata.timetable.enums.PeriodType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Period/time slot definition within a {@link BellSchedule}.
 */
@Entity
@Table(name = "bell_schedule_period")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BellSchedulePeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "period_code", nullable = false, unique = true, length = 50, updatable = false)
    private String periodCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bell_schedule_id", nullable = false)
    private BellSchedule bellSchedule;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "period_sequence", nullable = false)
    private int periodSequence;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 30)
    private PeriodType periodType = PeriodType.TEACHING;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public BellSchedulePeriod(String periodCode,
                              BellSchedule bellSchedule,
                              String name,
                              int periodSequence,
                              LocalTime startTime,
                              LocalTime endTime,
                              PeriodType periodType) {
        this.periodCode = periodCode;
        this.bellSchedule = bellSchedule;
        this.name = name != null ? name.trim() : null;
        this.periodSequence = periodSequence;
        this.startTime = startTime;
        this.endTime = endTime;
        this.periodType = periodType != null ? periodType : PeriodType.TEACHING;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
