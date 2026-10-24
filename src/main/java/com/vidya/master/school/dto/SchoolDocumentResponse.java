package com.vidya.master.school.dto;

import com.vidya.master.school.entity.DocumentVerificationStatus;
import com.vidya.master.school.entity.SchoolDocument;
import com.vidya.master.school.entity.SchoolDocumentType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SchoolDocumentResponse(
        UUID id,
        UUID schoolId,
        SchoolDocumentType documentType,
        String documentName,
        String documentNumber,
        String fileUrl,
        Long fileSizeBytes,
        String mimeType,
        String issuedBy,
        LocalDate issuedDate,
        LocalDate expiryDate,
        DocumentVerificationStatus verificationStatus,
        UUID verifiedByUserId,
        Instant verifiedAt,
        String rejectionReason
) {
    public static SchoolDocumentResponse fromEntity(SchoolDocument entity) {
        return new SchoolDocumentResponse(
                entity.getId(),
                entity.getSchool().getId(),
                entity.getDocumentType(),
                entity.getDocumentName(),
                entity.getDocumentNumber(),
                entity.getFileUrl(),
                entity.getFileSizeBytes(),
                entity.getMimeType(),
                entity.getIssuedBy(),
                entity.getIssuedDate(),
                entity.getExpiryDate(),
                entity.getVerificationStatus(),
                entity.getVerifiedByUserId(),
                entity.getVerifiedAt(),
                entity.getRejectionReason()
        );
    }
}
