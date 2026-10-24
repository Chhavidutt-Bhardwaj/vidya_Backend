package com.vidya.tenantdata.examination.mapper;

import com.vidya.tenantdata.examination.dto.GradingScaleResponse;
import com.vidya.tenantdata.examination.dto.GradingScaleTierDto;
import com.vidya.tenantdata.examination.entity.GradingScale;
import com.vidya.tenantdata.examination.entity.GradingScaleTier;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class GradingScaleMapper {

    public GradingScaleResponse toResponse(GradingScale scale) {
        if (scale == null) return null;

        List<GradingScaleTierDto> tierDtos = scale.getTiers() != null
                ? scale.getTiers().stream().map(this::toTierDto).toList()
                : Collections.emptyList();

        return GradingScaleResponse.builder()
                .id(scale.getId())
                .scaleCode(scale.getScaleCode())
                .name(scale.getName())
                .description(scale.getDescription())
                .isDefault(scale.isDefault())
                .tiers(tierDtos)
                .createdAt(scale.getCreatedAt())
                .updatedAt(scale.getUpdatedAt())
                .build();
    }

    public GradingScaleTierDto toTierDto(GradingScaleTier tier) {
        if (tier == null) return null;

        return GradingScaleTierDto.builder()
                .id(tier.getId())
                .grade(tier.getGrade())
                .minPercentage(tier.getMinPercentage())
                .maxPercentage(tier.getMaxPercentage())
                .gradePoint(tier.getGradePoint())
                .description(tier.getDescription())
                .isPassing(tier.isPassing())
                .sortOrder(tier.getSortOrder())
                .build();
    }
}
