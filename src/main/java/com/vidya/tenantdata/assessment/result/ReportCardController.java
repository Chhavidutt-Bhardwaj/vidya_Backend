package com.vidya.tenantdata.assessment.result;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.assessment.AssessmentAccess;
import com.vidya.tenantdata.reportcard.dto.AddReportCardRemarkRequest;
import com.vidya.tenantdata.reportcard.dto.BatchReportCardGenerationResponse;
import com.vidya.tenantdata.reportcard.dto.GenerateReportCardsRequest;
import com.vidya.tenantdata.reportcard.dto.PublishReportCardsRequest;
import com.vidya.tenantdata.reportcard.dto.ReportCardRemarkResponse;
import com.vidya.tenantdata.reportcard.dto.StudentReportCardResponse;
import com.vidya.tenantdata.reportcard.enums.ReportCardStatus;
import com.vidya.tenantdata.reportcard.service.ReportCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/report-cards")
@RequiredArgsConstructor
@Tag(name = "Report cards", description = "Term results calculated with the class's assessment scheme (any board): components, grace, "
        + "compartment, grades, ranks, cumulative result; holistic progress card")
public class ReportCardController {

    private final ReportCardService service;
    private final HolisticCardService holistic;
    private final AssessmentAccess access;

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('REPORT_CARD_GENERATE')")
    @Operation(summary = "Calculate results", description = "For a term and class (optionally a section). Re-running recalculates; "
            + "published cards are left unchanged (revoke first)")
    public ResponseEntity<ApiResponse<BatchReportCardGenerationResponse>> generate(@Valid @RequestBody GenerateReportCardsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("REPORT_CARDS_GENERATED", "Results calculated",
                service.generateReportCards(request, access.staffId())));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_CARD_READ')")
    public ResponseEntity<ApiResponse<Page<StudentReportCardResponse>>> search(@RequestParam(required = false) UUID examTermId,
                                                                              @RequestParam(required = false) UUID academicClassId,
                                                                              @RequestParam(required = false) UUID sectionId,
                                                                              @RequestParam(required = false) ReportCardStatus status,
                                                                              @RequestParam(defaultValue = "0") int page,
                                                                              @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success("REPORT_CARDS", "Report cards", service.searchReportCards(examTermId, academicClassId, sectionId,
                status, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200), Sort.by("percentage").descending()))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('REPORT_CARD_READ')")
    @Operation(summary = "Report card", description = "Families see published cards only")
    public ResponseEntity<ApiResponse<StudentReportCardResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("REPORT_CARD", "Report card", service.getReportCardById(id)));
    }

    @GetMapping("/{id}/holistic")
    @PreAuthorize("hasAuthority('REPORT_CARD_READ')")
    @Operation(summary = "Holistic progress card", description = "Scholastic results with component breakdown, co-scholastic grades, "
            + "competency ratings and learning-outcome attainment")
    public ResponseEntity<ApiResponse<HolisticCardService.HolisticCard>> holistic(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("REPORT_CARD_HOLISTIC", "Holistic progress card", holistic.card(id)));
    }

    @GetMapping("/students/{studentId}")
    @PreAuthorize("hasAuthority('REPORT_CARD_READ')")
    public ResponseEntity<ApiResponse<List<StudentReportCardResponse>>> byStudent(@PathVariable UUID studentId) {
        return ResponseEntity.ok(ApiResponse.success("REPORT_CARDS", "Report cards", service.getReportCardsByStudent(studentId)));
    }

    @PutMapping("/{id}/remarks")
    @PreAuthorize("hasAuthority('REPORT_CARD_REMARK_MANAGE')")
    @Operation(summary = "Class teacher / principal / counselor remark", description = "One per author role; before publishing")
    public ResponseEntity<ApiResponse<ReportCardRemarkResponse>> remark(@PathVariable UUID id, @Valid @RequestBody AddReportCardRemarkRequest request) {
        return ResponseEntity.ok(ApiResponse.success("REPORT_CARD_REMARK_SAVED", "Remark saved", service.addOrUpdateRemark(id, request, null)));
    }

    @PostMapping("/publish")
    @PreAuthorize("hasAuthority('REPORT_CARD_PUBLISH')")
    @Operation(summary = "Publish", description = "GENERATED cards of the term and class (or section) become visible to families; "
            + "the re-evaluation window starts")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> publish(@Valid @RequestBody PublishReportCardsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("REPORT_CARDS_PUBLISHED", "Report cards published",
                Map.of("published", service.publishReportCards(request))));
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAuthority('REPORT_CARD_REVOKE')")
    public ResponseEntity<ApiResponse<StudentReportCardResponse>> revoke(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("REPORT_CARD_REVOKED", "Report card revoked", service.revokeReportCard(id)));
    }
}
