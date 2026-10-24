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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Normalized physical address for a school campus, registered office, or administrative headquarters.
 */
@Entity
@Table(name = "school_address")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolAddress {

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
    @Column(name = "address_type", nullable = false, length = 50)
    private SchoolAddressType addressType = SchoolAddressType.CAMPUS_PRIMARY;

    @Column(name = "address_line1", nullable = false)
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    @Column(name = "pincode", nullable = false, length = 20)
    private String pincode;

    @Column(name = "country", nullable = false, length = 100)
    private String country = "India";

    @Column(name = "landmark", length = 200)
    private String landmark;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryAddress = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public SchoolAddress(School school,
                         SchoolBranch branch,
                         SchoolAddressType addressType,
                         String addressLine1,
                         String addressLine2,
                         String city,
                         String state,
                         String pincode,
                         String country,
                         String landmark,
                         BigDecimal latitude,
                         BigDecimal longitude,
                         boolean primaryAddress) {
        this.school = school;
        this.branch = branch;
        this.addressType = addressType != null ? addressType : SchoolAddressType.CAMPUS_PRIMARY;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.state = state;
        this.pincode = pincode;
        this.country = country != null ? country : "India";
        this.landmark = landmark;
        this.latitude = latitude;
        this.longitude = longitude;
        this.primaryAddress = primaryAddress;
    }
}
