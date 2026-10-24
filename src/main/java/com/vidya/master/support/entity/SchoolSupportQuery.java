package com.vidya.master.support.entity;

import com.vidya.common.audit.AuditableEntity;
import com.vidya.master.community.entity.Community;
import com.vidya.master.hub.entity.Hub;
import com.vidya.master.school.entity.School;
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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Primary ticket entity representing an issue, software problem, or payment query
 * raised by a School, escalated through the 3-tier chain:
 * Community RM &rarr; Hub Manager &rarr; Platform Admin.
 */
@Entity
@Table(name = "school_support_query")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolSupportQuery extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "ticket_number", nullable = false, unique = true, length = 50, updatable = false)
    private String ticketNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_community_id")
    private Community assignedCommunity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hub_id")
    private Hub hub;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private QueryCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 30)
    private QueryPriority priority = QueryPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private QueryStatus status = QueryStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_level", nullable = false, length = 30)
    private EscalationLevel currentLevel = EscalationLevel.COMMUNITY_RM;

    @Column(name = "subject", nullable = false, length = 300)
    private String subject;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "raised_by_user_id")
    private UUID raisedByUserId;

    @Column(name = "raised_by_name", length = 200)
    private String raisedByName;

    @Column(name = "raised_by_role", length = 50)
    private String raisedByRole;

    @Column(name = "assigned_rm_member_id")
    private UUID assignedRmMemberId;

    @Column(name = "escalated_to_hub_manager_id")
    private UUID escalatedToHubManagerId;

    @Column(name = "escalated_to_platform_user_id")
    private UUID escalatedToPlatformUserId;

    @Column(name = "resolution_summary", columnDefinition = "TEXT")
    private String resolutionSummary;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @OneToMany(mappedBy = "query", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SchoolQueryEscalationLog> escalationLogs = new ArrayList<>();

    @OneToMany(mappedBy = "query", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SchoolQueryComment> comments = new ArrayList<>();

    public SchoolSupportQuery(String ticketNumber,
                              School school,
                              Community assignedCommunity,
                              Hub hub,
                              QueryCategory category,
                              QueryPriority priority,
                              String subject,
                              String description,
                              UUID raisedByUserId,
                              String raisedByName,
                              String raisedByRole,
                              UUID assignedRmMemberId) {
        this.ticketNumber = ticketNumber;
        this.school = school;
        this.assignedCommunity = assignedCommunity;
        this.hub = hub;
        this.category = category;
        this.priority = priority != null ? priority : QueryPriority.MEDIUM;
        this.subject = subject;
        this.description = description;
        this.raisedByUserId = raisedByUserId;
        this.raisedByName = raisedByName;
        this.raisedByRole = raisedByRole;
        this.assignedRmMemberId = assignedRmMemberId;
        this.status = QueryStatus.OPEN;
        this.currentLevel = EscalationLevel.COMMUNITY_RM;
    }

    public void addEscalationLog(SchoolQueryEscalationLog log) {
        escalationLogs.add(log);
        log.setQuery(this);
    }

    public void addComment(SchoolQueryComment comment) {
        comments.add(comment);
        comment.setQuery(this);
    }
}
