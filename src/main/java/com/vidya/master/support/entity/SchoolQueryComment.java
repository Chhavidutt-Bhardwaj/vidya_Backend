package com.vidya.master.support.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Threaded comment / message on a School Support Query.
 * Can be public (visible to School) or internal note (only visible to RM, Hub Manager, and Platform team).
 */
@Entity
@Table(name = "school_query_comment")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolQueryComment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "query_id", nullable = false)
    private SchoolSupportQuery query;

    @Column(name = "author_user_id")
    private UUID authorUserId;

    @Column(name = "author_name", nullable = false, length = 200)
    private String authorName;

    @Column(name = "author_role", nullable = false, length = 50)
    private String authorRole;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "is_internal_note", nullable = false)
    private boolean internalNote = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public SchoolQueryComment(SchoolSupportQuery query,
                              UUID authorUserId,
                              String authorName,
                              String authorRole,
                              String message,
                              boolean internalNote) {
        this.query = query;
        this.authorUserId = authorUserId;
        this.authorName = authorName;
        this.authorRole = authorRole;
        this.message = message;
        this.internalNote = internalNote;
        this.createdAt = Instant.now();
    }
}
