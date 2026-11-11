package com.vidya.tenantdata.operations.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** Amount of one fee head in a fee structure, per occurrence of the head's frequency. */
@Entity
@Table(name = "fee_structure_item")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeeStructureItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "fee_structure_id", nullable = false, updatable = false)
    private UUID feeStructureId;

    @Column(name = "fee_head_id", nullable = false, updatable = false)
    private UUID feeHeadId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    public FeeStructureItem(UUID feeStructureId, UUID feeHeadId) {
        this.feeStructureId = feeStructureId;
        this.feeHeadId = feeHeadId;
    }
}
