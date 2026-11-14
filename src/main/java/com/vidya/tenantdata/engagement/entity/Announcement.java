package com.vidya.tenantdata.engagement.entity;

import com.vidya.tenantdata.engagement.enums.AnnouncementAudience;
import com.vidya.tenantdata.engagement.enums.AnnouncementPriority;
import com.vidya.tenantdata.engagement.enums.AnnouncementStatus;
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

/** A notice to the whole school, one class or one section. Families are told when it is published. */
@Entity
@Table(name = "announcement")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "announcement_code", nullable = false, updatable = false, length = 50)
    private String announcementCode;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnnouncementAudience audience;

    @Column(name = "academic_class_id")
    private UUID academicClassId;

    @Column(name = "section_id")
    private UUID sectionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnnouncementPriority priority = AnnouncementPriority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnnouncementStatus status = AnnouncementStatus.DRAFT;

    /** Send notifications when published. */
    @Column(nullable = false)
    private boolean notify = true;

    @Column(name = "publish_on")
    private LocalDate publishOn;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by")
    private UUID publishedBy;

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

    public Announcement(String announcementCode) {
        this.announcementCode = announcementCode;
    }
}
