package com.vidya.tenantdata.assessment.scheme;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.assessment.scheme.SchemeDtos.AreasRequest;
import com.vidya.tenantdata.assessment.scheme.SchemeDtos.ClassesRequest;
import com.vidya.tenantdata.assessment.scheme.SchemeDtos.ComponentsRequest;
import com.vidya.tenantdata.assessment.scheme.SchemeDtos.CopySchemeRequest;
import com.vidya.tenantdata.assessment.scheme.SchemeDtos.CreateSchemeRequest;
import com.vidya.tenantdata.assessment.scheme.SchemeDtos.SchemeResponse;
import com.vidya.tenantdata.assessment.scheme.SchemeDtos.SubjectRulesRequest;
import com.vidya.tenantdata.assessment.scheme.SchemeDtos.UpdateSchemeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assessment-schemes")
@RequiredArgsConstructor
@Tag(name = "Assessment schemes", description = "Board-wise evaluation schemes (CBSE, ICSE/ISC, state, IB, IGCSE, custom) mapped to classes per year: "
        + "formative / summative components and weights, scholastic and co-scholastic areas, pass, grace, compartment and grading rules")
public class SchemeController {

    private final SchemeService service;

    @GetMapping("/presets")
    @PreAuthorize("hasAuthority('ASSESSMENT_SCHEME_READ')")
    @Operation(summary = "Board presets", description = "Starting points per board and stage; every value stays editable after creation")
    public ResponseEntity<ApiResponse<List<BoardPresets.Preset>>> presets() {
        return ResponseEntity.ok(ApiResponse.success("ASSESSMENT_SCHEME_PRESETS", "Presets", service.presets()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ASSESSMENT_SCHEME_READ')")
    public ResponseEntity<ApiResponse<List<SchemeResponse>>> list(@RequestParam UUID academicYearId) {
        return ResponseEntity.ok(ApiResponse.success("ASSESSMENT_SCHEMES", "Assessment schemes", service.list(academicYearId)));
    }

    @GetMapping("/for-class")
    @PreAuthorize("hasAuthority('ASSESSMENT_SCHEME_READ')")
    @Operation(summary = "Active scheme of a class", description = "Null data when the class has no active scheme (results then use exam papers only)")
    public ResponseEntity<ApiResponse<SchemeResponse>> forClass(@RequestParam UUID academicYearId, @RequestParam UUID academicClassId) {
        return ResponseEntity.ok(ApiResponse.success("ASSESSMENT_SCHEME", "Scheme of the class",
                service.forClass(academicYearId, academicClassId).orElse(null)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ASSESSMENT_SCHEME_READ')")
    public ResponseEntity<ApiResponse<SchemeResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("ASSESSMENT_SCHEME", "Assessment scheme", service.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ASSESSMENT_SCHEME_MANAGE')")
    @Operation(summary = "Create a scheme", description = "From a preset (presetKey) or empty (board). Created as DRAFT")
    public ResponseEntity<ApiResponse<SchemeResponse>> create(@Valid @RequestBody CreateSchemeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("ASSESSMENT_SCHEME_CREATED", "Scheme created", service.create(request)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ASSESSMENT_SCHEME_MANAGE')")
    @Operation(summary = "Change rules", description = "Pass, grace, compartment, rounding, best-of, term aggregation, grades. Only the fields sent change")
    public ResponseEntity<ApiResponse<SchemeResponse>> update(@PathVariable UUID id, @Valid @RequestBody UpdateSchemeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("ASSESSMENT_SCHEME_UPDATED", "Scheme updated", service.update(id, request)));
    }

    @PutMapping("/{id}/components")
    @PreAuthorize("hasAuthority('ASSESSMENT_SCHEME_MANAGE')")
    @Operation(summary = "Replace components", description = "Matched by code; a component with recorded assessments cannot be removed")
    public ResponseEntity<ApiResponse<SchemeResponse>> components(@PathVariable UUID id, @Valid @RequestBody ComponentsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("ASSESSMENT_SCHEME_UPDATED", "Components saved", service.replaceComponents(id, request.components())));
    }

    @PutMapping("/{id}/co-scholastic-areas")
    @PreAuthorize("hasAuthority('ASSESSMENT_SCHEME_MANAGE')")
    public ResponseEntity<ApiResponse<SchemeResponse>> areas(@PathVariable UUID id, @Valid @RequestBody AreasRequest request) {
        return ResponseEntity.ok(ApiResponse.success("ASSESSMENT_SCHEME_UPDATED", "Co-scholastic areas saved", service.replaceAreas(id, request.areas())));
    }

    @PutMapping("/{id}/subjects")
    @PreAuthorize("hasAuthority('ASSESSMENT_SCHEME_MANAGE')")
    @Operation(summary = "Subject categories", description = "ADDITIONAL / GRADE_ONLY / EXCLUDED subjects and subject pass percentages; unlisted subjects are SCHOLASTIC")
    public ResponseEntity<ApiResponse<SchemeResponse>> subjects(@PathVariable UUID id, @Valid @RequestBody SubjectRulesRequest request) {
        return ResponseEntity.ok(ApiResponse.success("ASSESSMENT_SCHEME_UPDATED", "Subject rules saved", service.replaceSubjectRules(id, request.subjects())));
    }

    @PutMapping("/{id}/classes")
    @PreAuthorize("hasAuthority('ASSESSMENT_SCHEME_MANAGE')")
    @Operation(summary = "Classes under the scheme", description = "A class can be in one scheme per year")
    public ResponseEntity<ApiResponse<SchemeResponse>> classes(@PathVariable UUID id, @Valid @RequestBody ClassesRequest request) {
        return ResponseEntity.ok(ApiResponse.success("ASSESSMENT_SCHEME_UPDATED", "Classes saved", service.replaceClasses(id, request.classIds())));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('ASSESSMENT_SCHEME_MANAGE')")
    public ResponseEntity<ApiResponse<SchemeResponse>> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("ASSESSMENT_SCHEME_ACTIVATED", "Scheme active", service.activate(id)));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('ASSESSMENT_SCHEME_MANAGE')")
    public ResponseEntity<ApiResponse<SchemeResponse>> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("ASSESSMENT_SCHEME_ARCHIVED", "Scheme archived", service.archive(id)));
    }

    @PostMapping("/{id}/copy")
    @PreAuthorize("hasAuthority('ASSESSMENT_SCHEME_MANAGE')")
    @Operation(summary = "Copy to another year", description = "Components, areas and subject rules; classes too when copyClasses is true")
    public ResponseEntity<ApiResponse<SchemeResponse>> copy(@PathVariable UUID id, @Valid @RequestBody CopySchemeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("ASSESSMENT_SCHEME_CREATED", "Scheme copied", service.copy(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ASSESSMENT_SCHEME_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("ASSESSMENT_SCHEME_DELETED", "Scheme deleted", null));
    }
}
