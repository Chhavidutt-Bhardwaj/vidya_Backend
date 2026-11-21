package com.vidya.tenantdata.assessment.analytics;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.assessment.analytics.AnalyticsDtos.ClassPerformance;
import com.vidya.tenantdata.assessment.analytics.AnalyticsDtos.ItemAnalysis;
import com.vidya.tenantdata.assessment.analytics.AnalyticsDtos.OutcomeAttainment;
import com.vidya.tenantdata.assessment.analytics.AnalyticsDtos.StudentProgress;
import com.vidya.tenantdata.assessment.analytics.AnalyticsDtos.YearComparison;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assessment-analytics")
@RequiredArgsConstructor
@Tag(name = "Assessment analytics", description = "Subject-wise performance, year-over-year comparison, student progress, "
        + "learning-outcome attainment and question item analysis")
public class AssessmentAnalyticsController {

    private final AssessmentAnalyticsService service;

    @GetMapping("/classes/{academicClassId}/terms/{examTermId}")
    @PreAuthorize("hasAuthority('ASSESSMENT_ANALYTICS_READ')")
    @Operation(summary = "Subject-wise performance", description = "From calculated report cards: averages, median, spread, pass rate, "
            + "grade distribution and section comparison per subject")
    public ResponseEntity<ApiResponse<ClassPerformance>> classPerformance(@PathVariable UUID academicClassId, @PathVariable UUID examTermId,
                                                                          @RequestParam(required = false) UUID sectionId) {
        return ResponseEntity.ok(ApiResponse.success("CLASS_PERFORMANCE", "Class performance", service.classPerformance(examTermId, academicClassId, sectionId)));
    }

    @GetMapping("/classes/{academicClassId}/years")
    @PreAuthorize("hasAuthority('ASSESSMENT_ANALYTICS_READ')")
    @Operation(summary = "Year-over-year comparison", description = "Same class across academic years: the named term (e.g. Annual), "
            + "or each year's latest term with results")
    public ResponseEntity<ApiResponse<YearComparison>> years(@PathVariable UUID academicClassId, @RequestParam(required = false) UUID subjectId,
                                                            @RequestParam(required = false) String termName) {
        return ResponseEntity.ok(ApiResponse.success("YEAR_COMPARISON", "Year comparison", service.yearComparison(academicClassId, subjectId, termName)));
    }

    @GetMapping("/classes/{academicClassId}/terms/{examTermId}/outcomes")
    @PreAuthorize("hasAuthority('ASSESSMENT_ANALYTICS_READ')")
    @Operation(summary = "Outcome attainment of a class", description = "Weakest outcomes first, with the number of students below 50%")
    public ResponseEntity<ApiResponse<List<OutcomeAttainment>>> classOutcomes(@PathVariable UUID academicClassId, @PathVariable UUID examTermId,
                                                                             @RequestParam(required = false) UUID sectionId) {
        return ResponseEntity.ok(ApiResponse.success("OUTCOME_ATTAINMENT", "Outcome attainment", service.classOutcomes(examTermId, academicClassId, sectionId)));
    }

    @GetMapping("/students/{studentId}/progress")
    @PreAuthorize("hasAnyAuthority('ASSESSMENT_ANALYTICS_READ', 'REPORT_CARD_READ')")
    @Operation(summary = "Student progress", description = "Every term so far (families: published only), subject trends, strengths, "
            + "subjects needing attention, competency history and outcome attainment")
    public ResponseEntity<ApiResponse<StudentProgress>> progress(@PathVariable UUID studentId) {
        return ResponseEntity.ok(ApiResponse.success("STUDENT_PROGRESS", "Student progress", service.studentProgress(studentId)));
    }

    @GetMapping("/students/{studentId}/outcomes")
    @PreAuthorize("hasAnyAuthority('ASSESSMENT_ANALYTICS_READ', 'REPORT_CARD_READ')")
    public ResponseEntity<ApiResponse<List<OutcomeAttainment>>> studentOutcomes(@PathVariable UUID studentId,
                                                                               @RequestParam(required = false) UUID examTermId) {
        return ResponseEntity.ok(ApiResponse.success("OUTCOME_ATTAINMENT", "Outcome attainment", service.studentOutcomes(studentId, examTermId)));
    }

    @PostMapping("/question-papers/{paperId}/item-analysis")
    @PreAuthorize("hasAuthority('QUESTION_BANK_MANAGE')")
    @Operation(summary = "Item analysis", description = "Facility and discrimination per question (all sets of the paper); "
            + "stored on the questions to guide later papers")
    public ResponseEntity<ApiResponse<ItemAnalysis>> itemAnalysis(@PathVariable UUID paperId) {
        return ResponseEntity.ok(ApiResponse.success("ITEM_ANALYSIS", "Item analysis", service.itemAnalysis(paperId)));
    }
}
