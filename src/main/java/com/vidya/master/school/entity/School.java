package com.vidya.master.school.entity;

import com.vidya.common.audit.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Master business entity representing a School on the Vidya SaaS platform.
 *
 * <p>The {@code tenantId} field is initially null during onboarding and is
 * populated once the dedicated tenant database is provisioned.
 */
@Entity
@Table(name = "school")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class School extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "school_code", nullable = false, unique = true, length = 50, updatable = false)
    private String schoolCode;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "school_name", nullable = false, length = 300)
    private String schoolName;

    @Column(name = "legal_name", length = 300)
    private String legalName;

    @Column(name = "registration_number", length = 100)
    private String registrationNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "board", length = 30)
    private SchoolBoard board;

    @Enumerated(EnumType.STRING)
    @Column(name = "school_type", length = 30)
    private SchoolType schoolType;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "website", length = 500)
    private String website;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SchoolStatus status = SchoolStatus.DRAFT;

    @OneToMany(mappedBy = "school", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<SchoolBranch> branches = new HashSet<>();

    public School(String schoolCode,
                  String schoolName,
                  String legalName,
                  String registrationNumber,
                  SchoolBoard board,
                  SchoolType schoolType,
                  String email,
                  String phone,
                  String website,
                  SchoolStatus status) {
        this.schoolCode = schoolCode;
        this.schoolName = schoolName;
        this.legalName = legalName;
        this.registrationNumber = registrationNumber;
        this.board = board;
        this.schoolType = schoolType;
        this.email = email;
        this.phone = phone;
        this.website = website;
        this.status = status != null ? status : SchoolStatus.DRAFT;
    }

    public void addBranch(SchoolBranch branch) {
        branches.add(branch);
        branch.setSchool(this);
    }

    public void removeBranch(SchoolBranch branch) {
        branches.remove(branch);
        branch.setSchool(null);
    }
}
