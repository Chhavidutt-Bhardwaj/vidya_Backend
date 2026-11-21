package com.vidya.tenantdata.operations.entity;

import com.vidya.tenantdata.operations.enums.ConfigStatus;
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

/** A shelf / subject grouping of library books. */
@Entity
@Table(name = "library_category")
@Getter
@Setter
@NoArgsConstructor
public class LibraryCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 255)
    private String description;

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
