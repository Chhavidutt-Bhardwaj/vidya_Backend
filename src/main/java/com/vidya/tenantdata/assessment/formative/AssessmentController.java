package com.vidya.tenantdata.assessment.formative;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.assessment.formative.AssessmentDtos.AssessmentResponse;
import com.vidya.tenantdata.assessment.formative.AssessmentDtos.CreateAssessmentRequest;
import com.vidya.tenantdata.assessment.formative.AssessmentDtos.ScoresRequest;
import com.vidya.tenantdata.assessment.formative.AssessmentDtos.UpdateAssessmentRequest;
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
@RequestMapping("/api/v1/assessments")
@RequiredArgsConstructor
@Tag(name = "Formative & summative assessments", description = "Class tests, projects, quizzes, orals, practicals ... recorded under a component "
        + "of the class's assessment scheme; they feed term results")
public class AssessmentController {

    private final AssessmentService service;

    @GetMapping
    @PreAuthorize("hasAuthority('ASSESSMENT_READ')")
    public ResponseEntity<ApiResponse<List<AssessmentResponse>>> list(@RequestParam UUID examTermId, @RequestParam UUID academicClassId,
                                                                     @RequestParam(required = false) UUID sectionId,
                                                                     @RequestParam(required = false) UUID subjectId) {
        return ResponseEntity.ok(ApiResponse.success("ASSESSMENTS", "Assessments", service.list(examTermId, academicClassId, sectionId, subjectId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ASSESSMENT_READ')")
    @Operation(summary = "Assessment with scores", description = "adjustedMarks include approved grace / moderation")
    public ResponseEntity<ApiResponse<AssessmentResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("ASSESSMENT", "Assessment", service.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ASSESSMENT_MANAGE')")
    @Operation(summary = "Record an assessment", description = "Under an ASSESSMENTS component of the class's active scheme, for a subject you teach")
    public ResponseEntity<ApiResponse<AssessmentResponse>> create(@Valid @RequestBody CreateAssessmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("ASSESSMENT_CREATED", "Assessment created", service.create(request)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ASSESSMENT_MANAGE')")
    public ResponseEntity<ApiResponse<AssessmentResponse>> update(@PathVariable UUID id, @Valid @RequestBody UpdateAssessmentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("ASSESSMENT_UPDATED", "Assessment updated", service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ASSESSMENT_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("ASSESSMENT_DELETED", "Assessment deleted", null));
    }

    @PutMapping("/{id}/scores")
    @PreAuthorize("hasAuthority('ASSESSMENT_MANAGE')")
    @Operation(summary = "Enter scores", description = "Upserts; marks, absent or exempt per student; all or nothing")
    public ResponseEntity<ApiResponse<AssessmentResponse>> scores(@PathVariable UUID id, @Valid @RequestBody ScoresRequest request) {
        return ResponseEntity.ok(ApiResponse.success("ASSESSMENT_SCORES_SAVED", "Scores saved", service.saveScores(id, request.scores())));
    }

    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAuthority('ASSESSMENT_MANAGE')")
    public ResponseEntity<ApiResponse<AssessmentResponse>> lock(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("ASSESSMENT_LOCKED", "Assessment locked", service.lock(id)));
    }

    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasAuthority('MARKS_MODERATE')")
    public ResponseEntity<ApiResponse<AssessmentResponse>> unlock(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("ASSESSMENT_UNLOCKED", "Assessment unlocked", service.unlock(id)));
    }
}
