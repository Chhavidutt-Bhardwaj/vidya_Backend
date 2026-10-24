package com.vidya.tenantdata.examination.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Grading scale response")
public class GradingScaleResponse {

    private UUID id;
    private String scaleCode;
    private String name;
    private String description;
    private boolean isDefault;
    private List<GradingScaleTierDto> tiers;
    private Instant createdAt;
    private Instant updatedAt;
}
