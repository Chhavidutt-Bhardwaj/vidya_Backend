package com.vidya.tenantdata.assessment.scheme;

import com.vidya.tenantdata.assessment.enums.AreaGroup;
import com.vidya.tenantdata.assessment.enums.AssessmentType;
import com.vidya.tenantdata.assessment.enums.Board;
import com.vidya.tenantdata.assessment.enums.ComponentAggregation;
import com.vidya.tenantdata.assessment.enums.ComponentSource;
import com.vidya.tenantdata.assessment.enums.MarkRounding;
import com.vidya.tenantdata.assessment.enums.SchemeStatus;
import com.vidya.tenantdata.assessment.enums.SubjectCategory;
import com.vidya.tenantdata.assessment.enums.TermAggregation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SchemeDtos {

    private SchemeDtos() {
    }

    public record CreateSchemeRequest(
            @NotNull UUID academicYearId,
            @Size(max = 50) String presetKey,
            @NotBlank @Size(max = 150) String name,
            Board board,
            @Size(max = 2000) String description,
            List<UUID> classIds,
            UUID scholasticGradingScaleId) {
    }

    /** Partial update: only the fields sent change. */
    public record UpdateSchemeRequest(
            @Size(max = 150) String name,
            @Size(max = 2000) String description,
            UUID scholasticGradingScaleId,
            @Pattern(regexp = "^[A-Za-z0-9+*\\-]{1,10}(,[A-Za-z0-9+*\\-]{1,10})*$", message = "comma-separated grades of up to 10 characters")
            String coScholasticGrades,
            @Pattern(regexp = "^[A-Z_]{1,30}(,[A-Z_]{1,30})*$", message = "comma-separated levels (A-Z and _)")
            String competencyLevels,
            @DecimalMin("0") @DecimalMax("100") BigDecimal subjectPassPercent,
            @DecimalMin("0") @DecimalMax("100") BigDecimal overallPassPercent,
            Boolean clearOverallPassPercent,
            Boolean separateComponentPass,
            MarkRounding roundingMode,
            @Min(0) @Max(2) Integer roundingScale,
            @Min(0) @Max(20) Integer bestOfSubjects,
            TermAggregation termAggregation,
            @DecimalMin("0") @DecimalMax("100") BigDecimal graceMaxPerSubject,
            @DecimalMin("0") @DecimalMax("500") BigDecimal graceMaxTotal,
            @Min(0) @Max(20) Integer graceMaxSubjects,
            @Min(0) @Max(20) Integer compartmentMaxSubjects,
            @Min(0) @Max(90) Integer reevaluationWindowDays,
            Boolean rankEnabled) {
    }

    public record ComponentRequest(
            @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9_\\-]+$") String code,
            @NotBlank @Size(max = 100) String name,
            @NotNull AssessmentType assessmentType,
            UUID examTermId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000") BigDecimal weight,
            @NotNull ComponentAggregation aggregation,
            @Min(1) @Max(20) Integer bestOf,
            @NotNull ComponentSource source,
            boolean passRequired,
            int sortOrder) {
    }

    public record ComponentsRequest(@NotEmpty @Valid List<ComponentRequest> components) {
    }

    public record AreaRequest(
            @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9_\\-]+$") String code,
            @NotBlank @Size(max = 150) String name,
            AreaGroup areaGroup,
            @Size(max = 500) String description,
            int sortOrder) {
    }

    public record AreasRequest(@NotNull @Valid List<AreaRequest> areas) {
    }

    public record SubjectRuleRequest(@NotNull UUID subjectId, @NotNull SubjectCategory category,
                                     @DecimalMin("0") @DecimalMax("100") BigDecimal passPercent) {
    }

    public record SubjectRulesRequest(@NotNull @Valid List<SubjectRuleRequest> subjects) {
    }

    public record ClassesRequest(@NotNull List<UUID> classIds) {
    }

    public record CopySchemeRequest(@NotNull UUID academicYearId, @Size(max = 150) String name, boolean copyClasses) {
    }

    public record NamedRef(UUID id, String name) {
    }

    public record ComponentResponse(UUID id, String code, String name, AssessmentType assessmentType, UUID examTermId, String examTermName,
                                    BigDecimal weight, ComponentAggregation aggregation, Integer bestOf, ComponentSource source,
                                    boolean passRequired, int sortOrder) {
    }

    public record AreaResponse(UUID id, String code, String name, AreaGroup areaGroup, String description, int sortOrder) {
    }

    public record SubjectRuleResponse(UUID subjectId, String subjectName, SubjectCategory category, BigDecimal passPercent) {
    }

    public record SchemeResponse(
            UUID id, String schemeCode, String name, Board board, UUID academicYearId, String description, String presetKey,
            UUID scholasticGradingScaleId, String scholasticGradingScaleName, List<String> coScholasticGrades, List<String> competencyLevels,
            BigDecimal subjectPassPercent, BigDecimal overallPassPercent, boolean separateComponentPass, MarkRounding roundingMode,
            int roundingScale, Integer bestOfSubjects, TermAggregation termAggregation, BigDecimal graceMaxPerSubject,
            BigDecimal graceMaxTotal, int graceMaxSubjects, int compartmentMaxSubjects, int reevaluationWindowDays, boolean rankEnabled,
            SchemeStatus status, List<NamedRef> classes, List<ComponentResponse> components, List<AreaResponse> areas,
            List<SubjectRuleResponse> subjects, List<String> warnings, Instant updatedAt) {
    }
}
