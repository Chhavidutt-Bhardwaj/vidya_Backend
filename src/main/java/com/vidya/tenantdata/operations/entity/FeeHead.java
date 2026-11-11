package com.vidya.tenantdata.operations.entity;

import com.vidya.tenantdata.operations.enums.ConfigStatus;
import com.vidya.tenantdata.operations.enums.FeeCategory;
import com.vidya.tenantdata.operations.enums.FeeFrequency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** Something the school charges for (tuition, admission, exam …) and how often it is charged. */
@Entity
@Table(name = "fee_head")
@Getter
@Setter
@NoArgsConstructor
public class FeeHead {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FeeCategory category = FeeCategory.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeeFrequency frequency = FeeFrequency.ANNUAL;

    @Column(name = "is_optional", nullable = false)
    private boolean optional;

    @Column(name = "is_refundable", nullable = false)
    private boolean refundable;

    @Column(length = 255)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConfigStatus status = ConfigStatus.ACTIVE;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version = 0L;
}
