package com.vidya.master.school.dto;

import com.vidya.master.school.entity.SchoolDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SchoolDocumentRequest(
        @NotNull(message = "Document type is required")
        SchoolDocumentType documentType,

        @NotBlank(message = "Document name is required")
        @Size(max = 200, message = "Document name must not exceed 200 characters")
        String documentName,

        @Size(max = 100, message = "Document number must not exceed 100 characters")
        String documentNumber,

        @NotBlank(message = "File URL is required")
        @Size(max = 500, message = "File URL must not exceed 500 characters")
        String fileUrl,

        Long fileSizeBytes,

        @Size(max = 100, message = "MIME type must not exceed 100 characters")
        String mimeType,

        @Size(max = 200, message = "Issued by must not exceed 200 characters")
        String issuedBy,

        LocalDate issuedDate,

        LocalDate expiryDate
) {}
