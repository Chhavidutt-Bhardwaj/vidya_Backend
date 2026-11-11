package com.vidya.tenantdata.operations.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The school's lending rules (one row). */
@Entity
@Table(name = "library_settings")
@Getter
@Setter
@NoArgsConstructor
public class LibrarySettings {

    /** Always 1: the school has one row. */
    @Id
    private Short id = 1;

    @Column(name = "student_max_books", nullable = false)
    private int studentMaxBooks = 2;

    @Column(name = "student_loan_days", nullable = false)
    private int studentLoanDays = 14;

    @Column(name = "staff_max_books", nullable = false)
    private int staffMaxBooks = 5;

    @Column(name = "staff_loan_days", nullable = false)
    private int staffLoanDays = 30;

    @Column(name = "max_renewals", nullable = false)
    private int maxRenewals = 1;

    @Column(name = "fine_per_day", nullable = false, precision = 8, scale = 2)
    private BigDecimal finePerDay = BigDecimal.ZERO;

    @Column(name = "fine_grace_days", nullable = false)
    private int fineGraceDays;

    @Column(name = "max_fine", precision = 10, scale = 2)
    private BigDecimal maxFine;

    @Column(name = "lost_book_charge_percent", nullable = false, precision = 6, scale = 2)
    private BigDecimal lostBookChargePercent = new BigDecimal("100");

    @Column(name = "allow_reservation", nullable = false)
    private boolean allowReservation = true;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;
}
