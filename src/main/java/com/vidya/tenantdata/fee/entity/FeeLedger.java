package com.vidya.tenantdata.fee.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** A student's fee account (invoice) for one academic year. Its lines say what is owed; payments settle them. */
@Entity
@Table(name = "fee_ledger")
@Getter
@Setter
@NoArgsConstructor
public class FeeLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_number", nullable = false, updatable = false, length = 40)
    private String invoiceNumber;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "academic_year_id", nullable = false, updatable = false)
    private UUID academicYearId;

    @Column(name = "academic_class_id", nullable = false)
    private UUID academicClassId;

    @Column(name = "section_id")
    private UUID sectionId;

    @Column(name = "fee_structure_id")
    private UUID feeStructureId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Version
    @Column(nullable = false)
    private Long version;
}
