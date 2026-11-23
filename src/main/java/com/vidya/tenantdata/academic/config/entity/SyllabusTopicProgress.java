package com.vidya.tenantdata.academic.config.entity;

import com.vidya.tenantdata.academic.config.enums.TopicProgressStatus;
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

/** Coverage of one topic in one section. A topic without a row has not been started there. */
@Entity
@Table(name = "syllabus_topic_progress")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SyllabusTopicProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "topic_id", nullable = false, updatable = false)
    private UUID topicId;

    @Column(name = "section_id", nullable = false, updatable = false)
    private UUID sectionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TopicProgressStatus status;

    @Column(name = "completed_on")
    private LocalDate completedOn;

    @Column(length = 255)
    private String remarks;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version = 0L;

    public SyllabusTopicProgress(UUID topicId, UUID sectionId) {
        this.topicId = topicId;
        this.sectionId = sectionId;
    }
}
