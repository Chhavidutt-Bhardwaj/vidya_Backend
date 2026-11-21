package com.vidya.tenantdata.engagement.entity;

import com.vidya.tenantdata.engagement.enums.ActivityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Something a class or section did on a day (lab, sports, trip, event ...), shown in the daily report. */
@Entity
@Table(name = "class_activity")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClassActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "activity_code", nullable = false, updatable = false, length = 50)
    private String activityCode;

    @Column(name = "academic_year_id", nullable = false)
    private UUID academicYearId;

    @Column(name = "academic_class_id", nullable = false)
    private UUID academicClassId;

    /** Null = every section of the class. */
    @Column(name = "section_id")
    private UUID sectionId;

    @Column(name = "subject_id")
    private UUID subjectId;

    @Column(name = "teacher_id")
    private UUID teacherId;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 30)
    private ActivityType activityType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private Long version = 0L;

    public ClassActivity(String activityCode) {
        this.activityCode = activityCode;
    }
}
