package com.vidya.master.community.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Personal information details for a community.
 */
@Entity
@Table(name = "community_personal_detail")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityPersonalDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "community_id", nullable = false, unique = true)
    private Community community;

    @Column(name = "contact_person_name", length = 200)
    private String contactPersonName;

    @Column(name = "designation", length = 100)
    private String designation;

    @Column(name = "alternate_email", length = 255)
    private String alternateEmail;

    @Column(name = "alternate_phone", length = 20)
    private String alternatePhone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    @Column(name = "pan_number", length = 20)
    private String panNumber;

    @Column(name = "aadhaar_number", length = 20)
    private String aadhaarNumber;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by")
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public CommunityPersonalDetail(Community community,
                                    String contactPersonName,
                                    String designation,
                                    String alternateEmail,
                                    String alternatePhone,
                                    LocalDate dateOfBirth,
                                    Gender gender,
                                    String panNumber,
                                    String aadhaarNumber) {
        this.community = community;
        this.contactPersonName = contactPersonName;
        this.designation = designation;
        this.alternateEmail = alternateEmail;
        this.alternatePhone = alternatePhone;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.panNumber = panNumber;
        this.aadhaarNumber = aadhaarNumber;
    }
}
