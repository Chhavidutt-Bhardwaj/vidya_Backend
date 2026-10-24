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
 * Normalized contact directory entry for a school (Principal, Admin in-charge, Billing, etc.).
 */
@Entity
@Table(name = "school_contact_detail")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolContactDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private SchoolBranch branch;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", nullable = false, length = 50)
    private SchoolContactType contactType;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "designation", length = 100)
    private String designation;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "alternate_phone", length = 20)
    private String alternatePhone;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryContact = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public SchoolContactDetail(School school,
                               SchoolBranch branch,
                               SchoolContactType contactType,
                               String fullName,
                               String designation,
                               String email,
                               String phone,
                               String alternatePhone,
                               boolean primaryContact) {
        this.school = school;
        this.branch = branch;
        this.contactType = contactType;
        this.fullName = fullName;
        this.designation = designation;
        this.email = email;
        this.phone = phone;
        this.alternatePhone = alternatePhone;
        this.primaryContact = primaryContact;
    }
}
