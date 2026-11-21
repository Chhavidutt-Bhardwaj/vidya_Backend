package com.vidya.tenantdata.operations.entity;

import com.vidya.tenantdata.operations.enums.LateCountsAs;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/** The school's attendance marking rules (one row). */
@Entity
@Table(name = "attendance_settings")
@Getter
@Setter
@NoArgsConstructor
public class AttendanceSettings {

    /** Always 1: the school has one row. */
    @Id
    private Short id = 1;

    @Column(name = "marking_deadline")
    private LocalTime markingDeadline;

    @Column(name = "late_after")
    private LocalTime lateAfter;

    @Column(name = "backdate_days")
    private Integer backdateDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "late_counts_as", nullable = false, length = 20)
    private LateCountsAs lateCountsAs = LateCountsAs.PRESENT;

    @Column(name = "minimum_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal minimumPercent = new BigDecimal("75");

    @Column(name = "notify_guardian_on_absence", nullable = false)
    private boolean notifyGuardianOnAbsence;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;
}
