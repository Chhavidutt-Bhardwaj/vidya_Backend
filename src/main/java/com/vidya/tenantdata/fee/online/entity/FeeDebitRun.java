package com.vidya.tenantdata.fee.online.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One run of the auto-debit job in a school (plan, invites, notices, submissions) and what it did. */
@Entity
@Table(name = "fee_debit_run")
@Getter
@Setter
@NoArgsConstructor
public class FeeDebitRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "run_date", nullable = false, updatable = false)
    private LocalDate runDate;

    @Column(name = "run_trigger", nullable = false, updatable = false)
    private String runTrigger;

    @Column(nullable = false)
    private String status = "RUNNING";

    @Column(nullable = false)
    private int planned;

    @Column(nullable = false)
    private int invites;

    @Column(nullable = false)
    private int notices;

    @Column(nullable = false)
    private int submitted;

    @Column(nullable = false)
    private int skipped;

    @Column(nullable = false)
    private int errors;

    @Column
    private String detail;

    @Column(name = "triggered_by", updatable = false)
    private UUID triggeredBy;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
