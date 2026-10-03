package com.vidya.tenantdata.timetable.entity;

import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.timetable.enums.TimetableStatus;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Master Timetable for an {@link AcademicYear} using a {@link BellSchedule}.
 */
@Entity
@Table(name = "timetable")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Timetable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "timetable_code", nullable = false, unique = true, length = 50, updatable = false)
    private String timetableCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bell_schedule_id", nullable = false)
    private BellSchedule bellSchedule;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TimetableStatus status = TimetableStatus.DRAFT;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @OneToMany(mappedBy = "timetable", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TimetableEntry> entries = new ArrayList<>();

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

    public Timetable(String timetableCode,
                     AcademicYear academicYear,
                     BellSchedule bellSchedule,
                     String name,
                     String description,
                     LocalDate effectiveFrom,
                     LocalDate effectiveTo) {
        this.timetableCode = timetableCode;
        this.academicYear = academicYear;
        this.bellSchedule = bellSchedule;
        this.name = name != null ? name.trim() : null;
        this.description = description != null ? description.trim() : null;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.status = TimetableStatus.DRAFT;
    }

    public void updateStatus(TimetableStatus newStatus) {
        this.status.validateTransitionTo(newStatus);
        this.status = newStatus;
        if (newStatus == TimetableStatus.DELETED) {
            this.deletedAt = Instant.now();
            for (TimetableEntry entry : entries) {
                entry.softDelete();
            }
        }
    }

    public boolean isDeleted() {
        return deletedAt != null || status == TimetableStatus.DELETED;
    }

    public void softDelete() {
        updateStatus(TimetableStatus.DELETED);
    }

    public void addEntry(TimetableEntry entry) {
        entries.add(entry);
        entry.setTimetable(this);
    }

    public void removeEntry(TimetableEntry entry) {
        entries.remove(entry);
        entry.setTimetable(null);
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
