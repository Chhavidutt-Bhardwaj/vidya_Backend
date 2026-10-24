package com.vidya.tenantdata.examination.service;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.examination.dto.CreateGradingScaleRequest;
import com.vidya.tenantdata.examination.dto.GradingScaleResponse;
import com.vidya.tenantdata.examination.dto.GradingScaleTierDto;
import com.vidya.tenantdata.examination.entity.GradingScale;
import com.vidya.tenantdata.examination.entity.GradingScaleTier;
import com.vidya.tenantdata.examination.mapper.GradingScaleMapper;
import com.vidya.tenantdata.examination.repository.GradingScaleRepository;
import com.vidya.tenantdata.examination.repository.GradingScaleTierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GradingScaleServiceImpl implements GradingScaleService {

    private final GradingScaleRepository gradingScaleRepository;
    private final GradingScaleTierRepository gradingScaleTierRepository;
    private final GradingScaleMapper gradingScaleMapper;

    @Override
    @Transactional("tenantTransactionManager")
    public GradingScaleResponse createGradingScale(CreateGradingScaleRequest request) {
        log.info("Creating grading scale with name: [{}]", request.getName());

        if (gradingScaleRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DuplicateResourceException("Grading scale with name '" + request.getName() + "' already exists");
        }

        GradingScale scale = new GradingScale(
                CodeGenerator.gradingScaleCode(),
                request.getName(),
                request.getDescription(),
                request.isDefault()
        );

        if (request.getTiers() != null) {
            for (GradingScaleTierDto tierDto : request.getTiers()) {
                GradingScaleTier tier = new GradingScaleTier(
                        scale,
                        tierDto.getGrade(),
                        tierDto.getMinPercentage(),
                        tierDto.getMaxPercentage(),
                        tierDto.getGradePoint(),
                        tierDto.getDescription(),
                        tierDto.isPassing(),
                        tierDto.getSortOrder()
                );
                scale.addTier(tier);
            }
        }

        GradingScale saved = gradingScaleRepository.save(scale);
        return gradingScaleMapper.toResponse(saved);
    }

    @Override
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public GradingScaleResponse getGradingScaleById(UUID id) {
        GradingScale scale = gradingScaleRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("GradingScale", id));
        return gradingScaleMapper.toResponse(scale);
    }

    @Override
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public GradingScaleResponse getDefaultGradingScale() {
        GradingScale scale = gradingScaleRepository.findDefaultScale()
                .orElseThrow(() -> new ResourceNotFoundException("GradingScale", "DEFAULT"));
        return gradingScaleMapper.toResponse(scale);
    }

    @Override
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public Page<GradingScaleResponse> listGradingScales(Pageable pageable) {
        return gradingScaleRepository.findAllActive(pageable)
                .map(gradingScaleMapper::toResponse);
    }

    @Override
    @Transactional("tenantTransactionManager")
    public void setDefaultGradingScale(UUID id) {
        log.info("Setting grading scale [{}] as default", id);
        GradingScale scale = gradingScaleRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("GradingScale", id));

        gradingScaleRepository.findDefaultScale().ifPresent(curr -> {
            curr.setDefault(false);
            gradingScaleRepository.save(curr);
        });

        scale.setDefault(true);
        gradingScaleRepository.save(scale);
    }

    @Override
    @Transactional("tenantTransactionManager")
    public void deleteGradingScale(UUID id) {
        log.info("Soft deleting grading scale [{}]", id);
        GradingScale scale = gradingScaleRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("GradingScale", id));
        scale.setDeletedAt(Instant.now());
        gradingScaleRepository.save(scale);
    }

    @Override
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public Optional<GradingScaleTier> calculateGrade(UUID scaleId, BigDecimal percentage) {
        if (scaleId == null || percentage == null) {
            return Optional.empty();
        }
        return gradingScaleTierRepository.findMatchingTier(scaleId, percentage);
    }
}
