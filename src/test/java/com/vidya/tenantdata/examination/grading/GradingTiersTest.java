package com.vidya.tenantdata.examination.grading;

import com.vidya.common.exception.BusinessException;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.enums.ClassStage;
import com.vidya.tenantdata.examination.dto.GradingScaleTierDto;
import com.vidya.tenantdata.examination.entity.GradingScale;
import com.vidya.tenantdata.examination.entity.GradingScaleTier;
import com.vidya.tenantdata.examination.mapper.GradingScaleMapper;
import com.vidya.tenantdata.examination.repository.ExamScheduleRepository;
import com.vidya.tenantdata.examination.repository.GradingScaleRepository;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Grading structure — tier rules, presets, grade lookup, scale resolution")
class GradingTiersTest {

    @Test
    @DisplayName("every preset is a valid scale")
    void presetsValid() {
        assertThat(GradingPresets.all()).hasSize(6);
        GradingPresets.all().forEach(p -> assertThatCode(() -> GradingTiers.validate(p.tiers())).as(p.key()).doesNotThrowAnyException());
        assertThat(GradingPresets.forStage(ClassStage.SECONDARY).key()).isEqualTo("CBSE_9_POINT");
        assertThat(GradingPresets.forStage(ClassStage.MIDDLE).key()).isEqualTo("CBSE_8_POINT");
        assertThat(GradingPresets.forStage(ClassStage.PRE_PRIMARY).key()).isEqualTo("PRE_PRIMARY_DESCRIPTIVE");
    }

    @Test
    @DisplayName("rejects overlaps, gaps over 1 point, missing 0 / 100, min above max and duplicate grades")
    void invalid() {
        assertThatThrownBy(() -> GradingTiers.validate(List.of(t("A", 50, 100), t("B", 0, 60)))).hasMessageContaining("overlaps");
        assertThatThrownBy(() -> GradingTiers.validate(List.of(t("A", 60, 100), t("B", 0, 50)))).hasMessageContaining("Gap");
        assertThatThrownBy(() -> GradingTiers.validate(List.of(t("A", 51, 100), t("B", 10, 50)))).hasMessageContaining("start at 0");
        assertThatThrownBy(() -> GradingTiers.validate(List.of(t("A", 51, 99), t("B", 0, 50)))).hasMessageContaining("end at 100");
        assertThatThrownBy(() -> GradingTiers.validate(List.of(t("A", 100, 51), t("B", 0, 50)))).hasMessageContaining("above max");
        assertThatThrownBy(() -> GradingTiers.validate(List.of(t("A", 51, 100), t("a", 0, 50)))).hasMessageContaining("twice");
        assertThatThrownBy(() -> GradingTiers.validate(List.of())).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("lookup: inside a band, in a gap gets the lower band (90.5 → A2)")
    void match() {
        GradingScale scale = new GradingScale("GSC-1", "s", null, false);
        List<GradingScaleTier> tiers = GradingPresets.find("CBSE_9_POINT").orElseThrow().tiers().stream()
                .map(d -> new GradingScaleTier(scale, d.getGrade(), d.getMinPercentage(), d.getMaxPercentage(), d.getGradePoint(),
                        d.getDescription(), d.isPassing(), d.getSortOrder())).toList();

        assertThat(GradingTiers.match(tiers, new BigDecimal("95")).orElseThrow().getGrade()).isEqualTo("A1");
        assertThat(GradingTiers.match(tiers, new BigDecimal("90.5")).orElseThrow().getGrade()).isEqualTo("A2");
        assertThat(GradingTiers.match(tiers, new BigDecimal("32.99")).orElseThrow().getGrade()).isEqualTo("E");
        assertThat(GradingTiers.match(tiers, new BigDecimal("0")).orElseThrow().getGrade()).isEqualTo("E");
        assertThat(GradingTiers.match(tiers, null)).isEmpty();
    }

    @Test
    @DisplayName("scale resolution: exam schedule → class → school default; deleted scales skipped")
    void resolution() {
        GradingScaleRepository repo = mock(GradingScaleRepository.class);
        GradingConfigService svc = new GradingConfigService(repo, mock(AcademicClassRepository.class), mock(ExamScheduleRepository.class),
                mock(GradingScaleMapper.class));
        GradingScale schedule = scale("Exam");
        GradingScale cls = scale("Class");
        GradingScale def = scale("Default");
        AcademicClass c = new AcademicClass("CLASS_9", "Class IX", null, 9);
        c.setGradingScaleId(cls.getId());
        when(repo.findActiveById(cls.getId())).thenReturn(Optional.of(cls));
        when(repo.findDefaultScale()).thenReturn(Optional.of(def));

        assertThat(svc.resolve(schedule, c).source()).isEqualTo(GradingConfigService.Source.EXAM_SCHEDULE);
        assertThat(svc.resolve(null, c).scale()).isSameAs(cls);
        schedule.setDeletedAt(Instant.now());
        assertThat(svc.resolve(schedule, c).source()).isEqualTo(GradingConfigService.Source.CLASS);
        c.setGradingScaleId(UUID.randomUUID());
        assertThat(svc.resolve(null, c).source()).isEqualTo(GradingConfigService.Source.SCHOOL_DEFAULT);
        when(repo.findDefaultScale()).thenReturn(Optional.empty());
        assertThat(svc.resolve(null, null).source()).isEqualTo(GradingConfigService.Source.NONE);
    }

    @Test
    @DisplayName("class stage from grade level")
    void stages() {
        assertThat(ClassStage.forGradeLevel(-2)).isEqualTo(ClassStage.PRE_PRIMARY);
        assertThat(ClassStage.forGradeLevel(0)).isEqualTo(ClassStage.PRE_PRIMARY);
        assertThat(ClassStage.forGradeLevel(5)).isEqualTo(ClassStage.PRIMARY);
        assertThat(ClassStage.forGradeLevel(8)).isEqualTo(ClassStage.MIDDLE);
        assertThat(ClassStage.forGradeLevel(10)).isEqualTo(ClassStage.SECONDARY);
        assertThat(ClassStage.forGradeLevel(12)).isEqualTo(ClassStage.SENIOR_SECONDARY);
    }

    private static GradingScale scale(String name) {
        GradingScale s = new GradingScale("GSC-" + name, name, null, false);
        s.setId(UUID.randomUUID());
        return s;
    }

    private static GradingScaleTierDto t(String g, int min, int max) {
        return GradingScaleTierDto.builder().grade(g).minPercentage(BigDecimal.valueOf(min)).maxPercentage(BigDecimal.valueOf(max)).build();
    }
}
