package com.vidya.tenantdata.assessment.competency;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.assessment.competency.CompetencyDtos.CoScholasticRequest;
import com.vidya.tenantdata.assessment.competency.CompetencyDtos.CoScholasticResponse;
import com.vidya.tenantdata.assessment.competency.CompetencyDtos.CompetencyRequest;
import com.vidya.tenantdata.assessment.competency.CompetencyDtos.CompetencyResponse;
import com.vidya.tenantdata.assessment.competency.CompetencyDtos.OutcomeRequest;
import com.vidya.tenantdata.assessment.competency.CompetencyDtos.OutcomeResponse;
import com.vidya.tenantdata.assessment.competency.CompetencyDtos.OutcomesRequest;
import com.vidya.tenantdata.assessment.competency.CompetencyDtos.RatingResponse;
import com.vidya.tenantdata.assessment.competency.CompetencyDtos.RatingsRequest;
import com.vidya.tenantdata.assessment.competency.CompetencyDtos.SaveResult;
import com.vidya.tenantdata.assessment.competency.CompetencyDtos.TopicsRequest;
import com.vidya.tenantdata.assessment.enums.CompetencyCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Competencies & outcomes", description = "Competency framework, learning outcomes mapped to syllabus topics, "
        + "competency / outcome ratings and co-scholastic grades per term")
public class CompetencyController {

    private final CompetencyService service;

    @GetMapping("/competencies")
    @PreAuthorize("hasAuthority('COMPETENCY_READ')")
    public ResponseEntity<ApiResponse<List<CompetencyResponse>>> competencies(@RequestParam(required = false) UUID subjectId,
                                                                             @RequestParam(required = false) CompetencyCategory category) {
        return ResponseEntity.ok(ApiResponse.success("COMPETENCIES", "Competencies", service.competencies(subjectId, category)));
    }

    @PostMapping("/competencies")
    @PreAuthorize("hasAuthority('COMPETENCY_MANAGE')")
    public ResponseEntity<ApiResponse<CompetencyResponse>> createCompetency(@Valid @RequestBody CompetencyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("COMPETENCY_CREATED", "Competency created", service.createCompetency(request)));
    }

    @PutMapping("/competencies/{id}")
    @PreAuthorize("hasAuthority('COMPETENCY_MANAGE')")
    public ResponseEntity<ApiResponse<CompetencyResponse>> updateCompetency(@PathVariable UUID id, @Valid @RequestBody CompetencyRequest request) {
        return ResponseEntity.ok(ApiResponse.success("COMPETENCY_UPDATED", "Competency updated", service.updateCompetency(id, request)));
    }

    @DeleteMapping("/competencies/{id}")
    @PreAuthorize("hasAuthority('COMPETENCY_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deleteCompetency(@PathVariable UUID id) {
        service.deleteCompetency(id);
        return ResponseEntity.ok(ApiResponse.success("COMPETENCY_DELETED", "Competency deleted", null));
    }

    @GetMapping("/learning-outcomes")
    @PreAuthorize("hasAuthority('COMPETENCY_READ')")
    @Operation(summary = "Learning outcomes", description = "academicClassId also returns outcomes that apply to every class")
    public ResponseEntity<ApiResponse<List<OutcomeResponse>>> outcomes(@RequestParam(required = false) UUID subjectId,
                                                                      @RequestParam(required = false) UUID academicClassId,
                                                                      @RequestParam(required = false) UUID competencyId) {
        return ResponseEntity.ok(ApiResponse.success("LEARNING_OUTCOMES", "Learning outcomes", service.outcomes(subjectId, academicClassId, competencyId)));
    }

    @GetMapping("/learning-outcomes/{id}")
    @PreAuthorize("hasAuthority('COMPETENCY_READ')")
    public ResponseEntity<ApiResponse<OutcomeResponse>> outcome(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("LEARNING_OUTCOME", "Learning outcome", service.outcome(id)));
    }

    @PostMapping("/learning-outcomes")
    @PreAuthorize("hasAuthority('COMPETENCY_MANAGE')")
    @Operation(summary = "Add learning outcomes", description = "Up to 500 at once, all or nothing (e.g. a board's outcomes for a class)")
    public ResponseEntity<ApiResponse<List<OutcomeResponse>>> createOutcomes(@Valid @RequestBody OutcomesRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("LEARNING_OUTCOMES_CREATED", "Outcomes created",
                service.createOutcomes(request.outcomes())));
    }

    @PutMapping("/learning-outcomes/{id}")
    @PreAuthorize("hasAuthority('COMPETENCY_MANAGE')")
    public ResponseEntity<ApiResponse<OutcomeResponse>> updateOutcome(@PathVariable UUID id, @Valid @RequestBody OutcomeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("LEARNING_OUTCOME_UPDATED", "Outcome updated", service.updateOutcome(id, request)));
    }

    @PutMapping("/learning-outcomes/{id}/syllabus-topics")
    @PreAuthorize("hasAuthority('COMPETENCY_MANAGE')")
    @Operation(summary = "Map to syllabus topics", description = "Replaces the mapping; topics must belong to the outcome's subject")
    public ResponseEntity<ApiResponse<OutcomeResponse>> topics(@PathVariable UUID id, @Valid @RequestBody TopicsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("LEARNING_OUTCOME_UPDATED", "Topics mapped", service.setTopics(id, request.syllabusTopicIds())));
    }

    @DeleteMapping("/learning-outcomes/{id}")
    @PreAuthorize("hasAuthority('COMPETENCY_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deleteOutcome(@PathVariable UUID id) {
        service.deleteOutcome(id);
        return ResponseEntity.ok(ApiResponse.success("LEARNING_OUTCOME_DELETED", "Outcome deleted", null));
    }

    @PostMapping("/competency-ratings")
    @PreAuthorize("hasAuthority('ASSESSMENT_MANAGE')")
    @Operation(summary = "Rate students", description = "Competency or learning-outcome levels for a term; levels come from the scheme of each student's class. "
            + "Upserts, all or nothing")
    public ResponseEntity<ApiResponse<SaveResult>> rate(@Valid @RequestBody RatingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("COMPETENCY_RATINGS_SAVED", "Ratings saved", service.saveRatings(request)));
    }

    @GetMapping("/competency-ratings")
    @PreAuthorize("hasAuthority('ASSESSMENT_READ')")
    public ResponseEntity<ApiResponse<List<RatingResponse>>> ratings(@RequestParam UUID examTermId, @RequestParam(required = false) UUID studentId,
                                                                    @RequestParam(required = false) UUID academicClassId,
                                                                    @RequestParam(required = false) UUID sectionId) {
        return ResponseEntity.ok(ApiResponse.success("COMPETENCY_RATINGS", "Ratings", service.ratings(examTermId, studentId, academicClassId, sectionId)));
    }

    @PostMapping("/co-scholastic-grades")
    @PreAuthorize("hasAuthority('ASSESSMENT_MANAGE')")
    @Operation(summary = "Grade co-scholastic areas", description = "Areas and grades of the scheme of each student's class. Upserts, all or nothing")
    public ResponseEntity<ApiResponse<SaveResult>> grade(@Valid @RequestBody CoScholasticRequest request) {
        return ResponseEntity.ok(ApiResponse.success("CO_SCHOLASTIC_SAVED", "Grades saved", service.saveCoScholastic(request)));
    }

    @GetMapping("/co-scholastic-grades")
    @PreAuthorize("hasAuthority('ASSESSMENT_READ')")
    public ResponseEntity<ApiResponse<List<CoScholasticResponse>>> coScholastic(@RequestParam UUID examTermId, @RequestParam(required = false) UUID studentId,
                                                                               @RequestParam(required = false) UUID academicClassId,
                                                                               @RequestParam(required = false) UUID sectionId) {
        return ResponseEntity.ok(ApiResponse.success("CO_SCHOLASTIC_GRADES", "Co-scholastic grades",
                service.coScholastic(examTermId, studentId, academicClassId, sectionId)));
    }
}
