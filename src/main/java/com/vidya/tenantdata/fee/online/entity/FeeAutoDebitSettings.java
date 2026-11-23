package com.vidya.tenantdata.fee.online.entity;

import com.vidya.tenantdata.fee.online.enums.GatewayProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** The school's one row of online-fee settings; missing = the defaults below (auto-debit off). */
@Entity
@Table(name = "fee_autodebit_settings")
@Getter
@Setter
@NoArgsConstructor
public class FeeAutoDebitSettings {

    public static final short SINGLETON = 1;

    @Id
    private Short id = SINGLETON;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "mandate_provider", length = 20)
    private GatewayProvider mandateProvider;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_provider", length = 20)
    private GatewayProvider paymentProvider;

    @Column(name = "debit_day", nullable = false)
    private short debitDay = 10;

    @Column(name = "notice_days", nullable = false, length = 50)
    private String noticeDays = "3,1";

    @Column(name = "invite_days_before", nullable = false)
    private short inviteDaysBefore = 7;

    @Column(name = "mandate_setup_url", length = 500)
    private String mandateSetupUrl;

    @Column(name = "sibling_gap_days", nullable = false)
    private short siblingGapDays = 1;

    @Column(name = "max_attempts", nullable = false)
    private short maxAttempts = 2;

    @Column(name = "retry_after_days", nullable = false)
    private short retryAfterDays = 3;

    @Column(name = "bounce_charge_paise", nullable = false)
    private long bounceChargePaise = 50_000;

    @Column(name = "bounce_gst_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal bounceGstPercent = new BigDecimal("18.00");

    @Column(name = "mandate_max_amount_paise", nullable = false)
    private long mandateMaxAmountPaise = 10_000_000;

    @Column(name = "mandate_validity_years", nullable = false)
    private short mandateValidityYears = 10;

    @Column(name = "link_expiry_days", nullable = false)
    private short linkExpiryDays = 7;

    @Column(name = "return_url", length = 500)
    private String returnUrl;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    /** Days before a debit on which the payer is told, largest first, without duplicates or non-positive values. */
    public List<Integer> noticeDayList() {
        return parseDays(noticeDays);
    }

    public static List<Integer> parseDays(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Integer::valueOf).filter(d -> d > 0)
                .distinct().sorted((a, b) -> Integer.compare(b, a)).toList();
    }
}
