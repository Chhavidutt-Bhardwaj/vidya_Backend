package com.vidya.tenantdata.operations.entity;

import com.vidya.tenantdata.operations.enums.FeeFrequency;
import com.vidya.tenantdata.operations.enums.LateFineType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Fee rules of one academic year: how often fees are collected, the due day, the late fine and receipts. */
@Entity
@Table(name = "fee_settings")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeeSettings {

    @Id
    @Column(name = "academic_year_id", nullable = false, updatable = false)
    private UUID academicYearId;

    @Enumerated(EnumType.STRING)
    @Column(name = "collection_frequency", nullable = false, length = 20)
    private FeeFrequency collectionFrequency = FeeFrequency.QUARTERLY;

    @Column(name = "due_day", nullable = false)
    private int dueDay = 10;

    @Column(name = "grace_days", nullable = false)
    private int graceDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "late_fine_type", nullable = false, length = 20)
    private LateFineType lateFineType = LateFineType.NONE;

    @Column(name = "late_fine_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal lateFineValue = BigDecimal.ZERO;

    @Column(name = "late_fine_cap", precision = 10, scale = 2)
    private BigDecimal lateFineCap;

    @Column(name = "allow_partial_payment", nullable = false)
    private boolean allowPartialPayment = true;

    @Column(name = "allow_online_payment", nullable = false)
    private boolean allowOnlinePayment;

    @Column(name = "receipt_prefix", nullable = false, length = 10)
    private String receiptPrefix = "RCP";

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "updated_by")
    private UUID updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public FeeSettings(UUID academicYearId) {
        this.academicYearId = academicYearId;
    }
}
