package com.vidya.tenantdata.examination.grading;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.examination.dto.CreateGradingScaleRequest;
import com.vidya.tenantdata.examination.dto.GradingScaleResponse;
import com.vidya.tenantdata.examination.dto.GradingScaleTierDto;
import com.vidya.tenantdata.examination.entity.ExamSchedule;
import com.vidya.tenantdata.examination.entity.GradingScale;
import com.vidya.tenantdata.examination.entity.GradingScaleTier;
import com.vidya.tenantdata.examination.mapper.GradingScaleMapper;
import com.vidya.tenantdata.examination.repository.ExamScheduleRepository;
import com.vidya.tenantdata.examination.repository.GradingScaleRepository;
import com.vidya.tenantdata.examination.grading.GradingPresets.Preset;
import com.vidya.common.util.CodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Grading structure: presets, editing a scale's tiers, and which scale grades a class.
 *
 * <p>Resolution order used for marks: exam schedule's scale → class's scale → school default.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GradingConfigService {

    public enum Source { EXAM_SCHEDULE, CLASS, SCHOOL_DEFAULT, NONE }

    public record Resolved(Source source, GradingScale scale) {
    }

    public record EffectiveScale(Source source, GradingScaleResponse scale) {
    }

    private final GradingScaleRepository gradingScaleRepository;
    private final AcademicClassRepository academicClassRepository;
    private final ExamScheduleRepository examScheduleRepository;
    private final GradingScaleMapper gradingScaleMapper;

    public List<Preset> presets() {
        return GradingPresets.all();
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public GradingScaleResponse createFromPreset(String key, String name, boolean makeDefault) {
        Preset p = GradingPresets.find(key).orElseThrow(() -> new ResourceNotFoundException("GradingPreset", key));
        String scaleName = name != null && !name.isBlank() ? name.trim() : p.name();
        if (gradingScaleRepository.existsByNameIgnoreCase(scaleName)) {
            throw new DuplicateResourceException("Grading scale with name '" + scaleName + "' already exists");
        }
        return gradingScaleMapper.toResponse(create(scaleName, p.description(), p.tiers(), makeDefault));
    }

    /** Creates a validated scale; when default, the previous default stops being one. */
    @Transactional(transactionManager = "tenantTransactionManager")
    public GradingScale create(String name, String description, List<GradingScaleTierDto> tiers, boolean makeDefault) {
        GradingTiers.validate(tiers);
        if (makeDefault) {
            clearDefault();
        }
        GradingScale scale = new GradingScale(CodeGenerator.gradingScaleCode(), name, description, makeDefault);
        tiers.forEach(t -> scale.addTier(tier(scale, t)));
        return gradingScaleRepository.save(scale);
    }

    /**
     * Replaces name, description and tiers. Marks already entered keep the grade they were given;
     * re-save marks to regrade them.
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public GradingScaleResponse update(UUID id, CreateGradingScaleRequest request) {
        GradingScale scale = gradingScaleRepository.findActiveById(id).orElseThrow(() -> new ResourceNotFoundException("GradingScale", id));
        GradingTiers.validate(request.getTiers());
        if (!scale.getName().equalsIgnoreCase(request.getName().trim()) && gradingScaleRepository.existsByNameIgnoreCase(request.getName().trim())) {
            throw new DuplicateResourceException("Grading scale with name '" + request.getName() + "' already exists");
        }
        scale.setName(request.getName().trim());
        scale.setDescription(request.getDescription());
        scale.getTiers().clear();
        gradingScaleRepository.saveAndFlush(scale);
        request.getTiers().forEach(t -> scale.addTier(tier(scale, t)));
        if (request.isDefault() && !scale.isDefault()) {
            clearDefault();
            scale.setDefault(true);
        }
        log.info("Grading scale [{}] updated with {} tiers", scale.getScaleCode(), request.getTiers().size());
        return gradingScaleMapper.toResponse(gradingScaleRepository.save(scale));
    }

    /** Scale that grades marks of an exam schedule (or of a class when no schedule is given). */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public EffectiveScale effective(UUID academicClassId, UUID examScheduleId) {
        Resolved r;
        if (examScheduleId != null) {
            ExamSchedule s = examScheduleRepository.findActiveById(examScheduleId)
                    .orElseThrow(() -> new ResourceNotFoundException("ExamSchedule", examScheduleId));
            r = resolve(s.getGradingScale(), s.getAcademicClass());
        } else if (academicClassId != null) {
            AcademicClass c = academicClassRepository.findByIdAndNotDeleted(academicClassId)
                    .orElseThrow(() -> new ResourceNotFoundException("AcademicClass", academicClassId));
            r = resolve(null, c);
        } else {
            r = resolve(null, null);
        }
        return new EffectiveScale(r.source(), r.scale() != null ? gradingScaleMapper.toResponse(r.scale()) : null);
    }

    /** Resolution used by mark entry; a deleted scale is skipped. */
    public Resolved resolve(GradingScale scheduleScale, AcademicClass academicClass) {
        if (scheduleScale != null && scheduleScale.getDeletedAt() == null) {
            return new Resolved(Source.EXAM_SCHEDULE, scheduleScale);
        }
        if (academicClass != null && academicClass.getGradingScaleId() != null) {
            Optional<GradingScale> c = gradingScaleRepository.findActiveById(academicClass.getGradingScaleId());
            if (c.isPresent()) {
                return new Resolved(Source.CLASS, c.get());
            }
        }
        return gradingScaleRepository.findDefaultScale().map(s -> new Resolved(Source.SCHOOL_DEFAULT, s))
                .orElse(new Resolved(Source.NONE, null));
    }

    private void clearDefault() {
        gradingScaleRepository.findDefaultScale().ifPresent(d -> {
            d.setDefault(false);
            gradingScaleRepository.saveAndFlush(d);
        });
    }

    private static GradingScaleTier tier(GradingScale scale, GradingScaleTierDto t) {
        if (t.getGrade() == null || t.getGrade().isBlank()) {
            throw new BusinessException("Every tier needs a grade label");
        }
        return new GradingScaleTier(scale, t.getGrade().trim(), t.getMinPercentage(), t.getMaxPercentage(),
                t.getGradePoint() != null ? t.getGradePoint() : java.math.BigDecimal.ZERO, t.getDescription(), t.isPassing(), t.getSortOrder());
    }
}
