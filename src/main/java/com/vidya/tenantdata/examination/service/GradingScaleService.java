package com.vidya.tenantdata.examination.service;

import com.vidya.tenantdata.examination.dto.CreateGradingScaleRequest;
import com.vidya.tenantdata.examination.dto.GradingScaleResponse;
import com.vidya.tenantdata.examination.entity.GradingScaleTier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface GradingScaleService {

    GradingScaleResponse createGradingScale(CreateGradingScaleRequest request);

    GradingScaleResponse getGradingScaleById(UUID id);

    GradingScaleResponse getDefaultGradingScale();

    Page<GradingScaleResponse> listGradingScales(Pageable pageable);

    void setDefaultGradingScale(UUID id);

    void deleteGradingScale(UUID id);

    Optional<GradingScaleTier> calculateGrade(UUID scaleId, BigDecimal percentage);
}
