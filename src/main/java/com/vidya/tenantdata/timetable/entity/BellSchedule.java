package com.vidya.tenantdata.timetable.entity;

import com.vidya.tenantdata.timetable.enums.BellScheduleStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * School bell schedule / period timing configuration.
 */
@Entity
@Table(name = "bell_schedule")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BellSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "schedule_code", nullable = false, unique = true, length = 50, updatable = false)
    private String scheduleCode;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BellScheduleStatus status = BellScheduleStatus.ACTIVE;

    @OneToMany(mappedBy = "bellSchedule", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("periodSequence ASC")
    private List<BellSchedulePeriod> periods = new ArrayList<>();

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

    public BellSchedule(String scheduleCode, String name, String description, boolean isDefault) {
        this.scheduleCode = scheduleCode;
        this.name = name != null ? name.trim() : null;
        this.description = description != null ? description.trim() : null;
        this.isDefault = isDefault;
        this.status = BellScheduleStatus.ACTIVE;
    }

    public void addPeriod(BellSchedulePeriod period) {
        periods.add(period);
        period.setBellSchedule(this);
    }

    public void removePeriod(BellSchedulePeriod period) {
        periods.remove(period);
        period.setBellSchedule(null);
    }

    public void updateStatus(BellScheduleStatus newStatus) {
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
        if (newStatus == BellScheduleStatus.DELETED) {
            this.deletedAt = Instant.now();
            for (BellSchedulePeriod period : periods) {
                period.softDelete();
            }
        }
    }

    public boolean isDeleted() {
        return deletedAt != null || status == BellScheduleStatus.DELETED;
    }

    public void softDelete() {
        updateStatus(BellScheduleStatus.DELETED);
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
