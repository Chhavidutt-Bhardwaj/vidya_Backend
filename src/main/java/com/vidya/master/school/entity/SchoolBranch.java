package com.vidya.master.school.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Physical branch/campus belonging to a School.
 */
@Entity
@Table(name = "school_branch")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolBranch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Column(name = "branch_code", nullable = false, unique = true, length = 50, updatable = false)
    private String branchCode;

    @Column(name = "branch_name", nullable = false, length = 300)
    private String branchName;

    @Column(name = "is_main_branch", nullable = false)
    private boolean mainBranch = false;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "pincode", length = 20)
    private String pincode;

    @Column(name = "country", nullable = false, length = 100)
    private String country = "India";

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BranchStatus status = BranchStatus.ACTIVE;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public SchoolBranch(School school,
                        String branchCode,
                        String branchName,
                        boolean mainBranch,
                        String addressLine1,
                        String addressLine2,
                        String city,
                        String state,
                        String pincode,
                        String country,
                        String contactEmail,
                        String contactPhone) {
        this.school = school;
        this.branchCode = branchCode;
        this.branchName = branchName;
        this.mainBranch = mainBranch;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.state = state;
        this.pincode = pincode;
        this.country = country != null ? country : "India";
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.status = BranchStatus.ACTIVE;
    }
}
