package com.vidya.tenantdata.reportcard.entity;

import com.vidya.tenantdata.reportcard.enums.AuthorRole;
import com.vidya.tenantdata.reportcard.enums.ConductRating;
import com.vidya.tenantdata.staff.entity.Staff;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "report_card_remark")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportCardRemark {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_card_id", nullable = false)
    private StudentReportCard reportCard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_staff_id")
    private Staff authorStaff;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_role", nullable = false, length = 50)
    private AuthorRole authorRole = AuthorRole.CLASS_TEACHER;

    @Column(name = "remark", nullable = false, columnDefinition = "TEXT")
    private String remark;

    @Enumerated(EnumType.STRING)
    @Column(name = "conduct_rating", length = 50)
    private ConductRating conductRating;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ReportCardRemark(StudentReportCard reportCard, Staff authorStaff, AuthorRole authorRole,
                            String remark, ConductRating conductRating) {
        this.reportCard = reportCard;
        this.authorStaff = authorStaff;
        this.authorRole = authorRole != null ? authorRole : AuthorRole.CLASS_TEACHER;
        this.remark = remark;
        this.conductRating = conductRating;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
