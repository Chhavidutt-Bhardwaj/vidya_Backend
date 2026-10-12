package com.vidya.tenantdata.calendar.entity;

import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.calendar.enums.SaturdayPattern;
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
import java.util.UUID;

@Entity
@Table(name = "working_day_config")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkingDayConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @Column(name = "monday", nullable = false)
    private boolean monday = true;

    @Column(name = "tuesday", nullable = false)
    private boolean tuesday = true;

    @Column(name = "wednesday", nullable = false)
    private boolean wednesday = true;

    @Column(name = "thursday", nullable = false)
    private boolean thursday = true;

    @Column(name = "friday", nullable = false)
    private boolean friday = true;

    @Column(name = "saturday", nullable = false)
    private boolean saturday = true;

    @Column(name = "sunday", nullable = false)
    private boolean sunday = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "saturday_pattern", nullable = false, length = 30)
    private SaturdayPattern saturdayPattern = SaturdayPattern.ALL;

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

    public WorkingDayConfig(
            AcademicYear academicYear,
            boolean monday,
            boolean tuesday,
            boolean wednesday,
            boolean thursday,
            boolean friday,
            boolean saturday,
            boolean sunday,
            SaturdayPattern saturdayPattern) {
        this.academicYear = academicYear;
        this.monday = monday;
        this.tuesday = tuesday;
        this.wednesday = wednesday;
        this.thursday = thursday;
        this.friday = friday;
        this.saturday = saturday;
        this.sunday = sunday;
        this.saturdayPattern = saturdayPattern != null ? saturdayPattern : SaturdayPattern.ALL;
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
