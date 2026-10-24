package com.vidya.master.community.entity;

import com.vidya.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Payroll configuration for a community.
 */
@Entity
@Table(name = "community_payroll")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityPayroll extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @Enumerated(EnumType.STRING)
    @Column(name = "payroll_frequency", nullable = false, length = 30)
    private PayrollFrequency payrollFrequency;

    @Column(name = "payroll_day")
    private Integer payrollDay;

    @Column(name = "payroll_cycle_start_day")
    private Integer payrollCycleStartDay;

    @Column(name = "payroll_cycle_end_day")
    private Integer payrollCycleEndDay;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "base_salary_structure", columnDefinition = "JSONB")
    private Map<String, Object> baseSalaryStructure;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "deduction_config", columnDefinition = "JSONB")
    private Map<String, Object> deductionConfig;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowance_config", columnDefinition = "JSONB")
    private Map<String, Object> allowanceConfig;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tax_config", columnDefinition = "JSONB")
    private Map<String, Object> taxConfig;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    public CommunityPayroll(Community community,
                             PayrollFrequency payrollFrequency,
                             Integer payrollDay,
                             Integer payrollCycleStartDay,
                             Integer payrollCycleEndDay,
                             Map<String, Object> baseSalaryStructure,
                             Map<String, Object> deductionConfig,
                             Map<String, Object> allowanceConfig,
                             Map<String, Object> taxConfig,
                             LocalDate effectiveFrom,
                             LocalDate effectiveTo) {
        this.community = community;
        this.payrollFrequency = payrollFrequency;
        this.payrollDay = payrollDay;
        this.payrollCycleStartDay = payrollCycleStartDay;
        this.payrollCycleEndDay = payrollCycleEndDay;
        this.baseSalaryStructure = baseSalaryStructure;
        this.deductionConfig = deductionConfig;
        this.allowanceConfig = allowanceConfig;
        this.taxConfig = taxConfig;
        this.isActive = true;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }
}
