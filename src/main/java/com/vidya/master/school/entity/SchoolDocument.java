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
 * Compliance document, statutory certificate, or accreditation record for a school.
 */
@Entity
@Table(name = "school_document")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchoolDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 60)
    private SchoolDocumentType documentType;

    @Column(name = "document_name", nullable = false, length = 200)
    private String documentName;

    @Column(name = "document_number", length = 100)
    private String documentNumber;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "issued_by", length = 200)
    private String issuedBy;

    @Column(name = "issued_date")
    private LocalDate issuedDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 30)
    private DocumentVerificationStatus verificationStatus = DocumentVerificationStatus.PENDING;

    @Column(name = "verified_by_user_id")
    private UUID verifiedByUserId;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public SchoolDocument(School school,
                          SchoolDocumentType documentType,
                          String documentName,
                          String documentNumber,
                          String fileUrl,
                          Long fileSizeBytes,
                          String mimeType,
                          String issuedBy,
                          LocalDate issuedDate,
                          LocalDate expiryDate) {
        this.school = school;
        this.documentType = documentType;
        this.documentName = documentName;
        this.documentNumber = documentNumber;
        this.fileUrl = fileUrl;
        this.fileSizeBytes = fileSizeBytes;
        this.mimeType = mimeType;
        this.issuedBy = issuedBy;
        this.issuedDate = issuedDate;
        this.expiryDate = expiryDate;
        this.verificationStatus = DocumentVerificationStatus.PENDING;
    }

    public void verify(UUID verifiedByUserId) {
        this.verificationStatus = DocumentVerificationStatus.VERIFIED;
        this.verifiedByUserId = verifiedByUserId;
        this.verifiedAt = Instant.now();
        this.rejectionReason = null;
    }

    public void reject(UUID rejectedByUserId, String reason) {
        this.verificationStatus = DocumentVerificationStatus.REJECTED;
        this.verifiedByUserId = rejectedByUserId;
        this.verifiedAt = Instant.now();
        this.rejectionReason = reason;
    }
}
