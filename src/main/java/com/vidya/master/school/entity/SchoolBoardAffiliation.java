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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Educational board affiliation (e.g. CBSE, HBSE, ICSE, IB) allowing multi-curriculum institutional structures.
 */
@Entity
@Table(name = "school_board_affiliation")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolBoardAffiliation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Enumerated(EnumType.STRING)
    @Column(name = "board_type", nullable = false, length = 50)
    private BoardAffiliationType boardType;

    @Column(name = "board_name", length = 150)
    private String boardName;

    @Column(name = "affiliation_number", nullable = false, length = 100)
    private String affiliationNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "affiliation_level", nullable = false, length = 50)
    private BoardAffiliationLevel affiliationLevel = BoardAffiliationLevel.ALL_LEVELS;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "is_primary_board", nullable = false)
    private boolean primaryBoard = false;

    @Column(name = "affiliation_letter_url", length = 500)
    private String affiliationLetterUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BoardAffiliationStatus status = BoardAffiliationStatus.ACTIVE;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public SchoolBoardAffiliation(School school,
                                  BoardAffiliationType boardType,
                                  String boardName,
                                  String affiliationNumber,
                                  BoardAffiliationLevel affiliationLevel,
                                  LocalDate validFrom,
                                  LocalDate validTo,
                                  boolean primaryBoard,
                                  String affiliationLetterUrl,
                                  BoardAffiliationStatus status) {
        this.school = school;
        this.boardType = boardType;
        this.boardName = boardName;
        this.affiliationNumber = affiliationNumber;
        this.affiliationLevel = affiliationLevel != null ? affiliationLevel : BoardAffiliationLevel.ALL_LEVELS;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.primaryBoard = primaryBoard;
        this.affiliationLetterUrl = affiliationLetterUrl;
        this.status = status != null ? status : BoardAffiliationStatus.ACTIVE;
    }
}
