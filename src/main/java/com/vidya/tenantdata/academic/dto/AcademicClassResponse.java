package com.vidya.tenantdata.academic.dto;

import com.vidya.tenantdata.academic.enums.AcademicClassStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Representation of an academic class")
public class AcademicClassResponse {

    @Schema(description = "Academic class UUID")
    private UUID id;

    @Schema(description = "Class code", example = "CLASS_5")
    private String code;

    @Schema(description = "Class name", example = "Class 5")
    private String name;

    @Schema(description = "Display name", example = "Grade 5 (Primary)")
    private String displayName;

    @Schema(description = "Sort order", example = "5")
    private int sortOrder;

    @Schema(description = "Class status", example = "ACTIVE")
    private AcademicClassStatus status;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;

    @Schema(description = "Optimistic locking version")
    private Long version;
}
