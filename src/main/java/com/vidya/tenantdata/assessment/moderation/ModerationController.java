package com.vidya.tenantdata.assessment.moderation;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.assessment.enums.AdjustmentStatus;
import com.vidya.tenantdata.assessment.enums.ReevaluationStatus;
import com.vidya.tenantdata.assessment.moderation.ModerationDtos.AdjustmentResponse;
import com.vidya.tenantdata.assessment.moderation.ModerationDtos.AssignReviewerRequest;
import com.vidya.tenantdata.assessment.moderation.ModerationDtos.ModerationBatchResponse;
import com.vidya.tenantdata.assessment.moderation.ModerationDtos.ModerationPreview;
import com.vidya.tenantdata.assessment.moderation.ModerationDtos.ModerationRequest;
import com.vidya.tenantdata.assessment.moderation.ModerationDtos.ProposeAdjustmentRequest;
import com.vidya.tenantdata.assessment.moderation.ModerationDtos.ReevaluationCreateRequest;
import com.vidya.tenantdata.assessment.moderation.ModerationDtos.ReevaluationDecision;
import com.vidya.tenantdata.assessment.moderation.ModerationDtos.ReevaluationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Grace, moderation & re-evaluation", description = "Every change to a mark after entry, kept beside the raw mark; "
        + "only approved changes count in results")
public class ModerationController {

    private final ModerationService service;

    @GetMapping("/mark-adjustments")
    @PreAuthorize("hasAnyAuthority('MARKS_MODERATE', 'MARKS_ENTRY', 'ASSESSMENT_MANAGE')")
    @Operation(summary = "Mark changes", description = "Of an exam paper or assessment; without either (exam cell): the queue of a status, default PROPOSED")
    public ResponseEntity<ApiResponse<List<AdjustmentResponse>>> adjustments(@RequestParam(required = false) UUID examSubjectConfigId,
                                                                            @RequestParam(required = false) UUID assessmentId,
                                                                            @RequestParam(required = false) AdjustmentStatus status) {
        return ResponseEntity.ok(ApiResponse.success("MARK_ADJUSTMENTS", "Mark changes", service.list(examSubjectConfigId, assessmentId, status)));
    }

    @PostMapping("/mark-adjustments")
    @PreAuthorize("hasAnyAuthority('MARKS_MODERATE', 'MARKS_ENTRY', 'ASSESSMENT_MANAGE')")
    @Operation(summary = "Propose grace or a correction", description = "For one student on an exam paper or assessment; someone else approves")
    public ResponseEntity<ApiResponse<AdjustmentResponse>> propose(@Valid @RequestBody ProposeAdjustmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("MARK_ADJUSTMENT_PROPOSED", "Change proposed", service.propose(request)));
    }

    @PostMapping("/mark-adjustments/{id}/approve")
    @PreAuthorize("hasAuthority('MARKS_MODERATE')")
    public ResponseEntity<ApiResponse<AdjustmentResponse>> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("MARK_ADJUSTMENT_APPROVED", "Change approved", service.approve(id)));
    }

    @PostMapping("/mark-adjustments/{id}/reject")
    @PreAuthorize("hasAuthority('MARKS_MODERATE')")
    public ResponseEntity<ApiResponse<AdjustmentResponse>> reject(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("MARK_ADJUSTMENT_REJECTED", "Change rejected", service.reject(id)));
    }

    @PostMapping("/mark-adjustments/{id}/revert")
    @PreAuthorize("hasAuthority('MARKS_MODERATE')")
    public ResponseEntity<ApiResponse<AdjustmentResponse>> revert(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("MARK_ADJUSTMENT_REVERTED", "Change reverted", service.revert(id)));
    }

    @PostMapping("/moderation/preview")
    @PreAuthorize("hasAuthority('MARKS_MODERATE')")
    @Operation(summary = "Preview moderation", description = "Before / after per student, average and pass count; nothing is saved")
    public ResponseEntity<ApiResponse<ModerationPreview>> preview(@Valid @RequestBody ModerationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("MODERATION_PREVIEW", "Moderation preview", service.preview(request)));
    }

    @PostMapping("/moderation")
    @PreAuthorize("hasAuthority('MARKS_MODERATE')")
    @Operation(summary = "Moderate an exam paper", description = "ADD_FLAT, ADD_PERCENT_OF_MAX, SCALE_FACTOR or RAISE_TO_PASS_WITHIN; "
            + "onlyBelow limits it to students below that mark. Marks never exceed the maximum")
    public ResponseEntity<ApiResponse<ModerationBatchResponse>> moderate(@Valid @RequestBody ModerationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("MODERATION_APPLIED", "Moderation applied", service.apply(request)));
    }

    @GetMapping("/moderation")
    @PreAuthorize("hasAuthority('MARKS_MODERATE')")
    public ResponseEntity<ApiResponse<List<ModerationBatchResponse>>> batches(@RequestParam UUID examSubjectConfigId) {
        return ResponseEntity.ok(ApiResponse.success("MODERATION_BATCHES", "Moderation batches", service.batches(examSubjectConfigId)));
    }

    @PostMapping("/moderation/{id}/revert")
    @PreAuthorize("hasAuthority('MARKS_MODERATE')")
    public ResponseEntity<ApiResponse<ModerationBatchResponse>> revertBatch(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("MODERATION_REVERTED", "Moderation reverted", service.revertBatch(id)));
    }

    @PostMapping("/reevaluation-requests")
    @PreAuthorize("hasAuthority('REEVALUATION_REQUEST')")
    @Operation(summary = "Ask for re-evaluation", description = "Parents: own children; within the scheme's window after results are published")
    public ResponseEntity<ApiResponse<ReevaluationResponse>> request(@Valid @RequestBody ReevaluationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("REEVALUATION_REQUESTED", "Re-evaluation requested",
                service.request(request)));
    }

    @GetMapping("/reevaluation-requests/mine")
    @PreAuthorize("hasAuthority('REEVALUATION_REQUEST')")
    @Operation(summary = "My / my children's requests")
    public ResponseEntity<ApiResponse<List<ReevaluationResponse>>> mine() {
        return ResponseEntity.ok(ApiResponse.success("REEVALUATION_REQUESTS", "Re-evaluation requests", service.mine()));
    }

    @PostMapping("/reevaluation-requests/{id}/withdraw")
    @PreAuthorize("hasAuthority('REEVALUATION_REQUEST')")
    public ResponseEntity<ApiResponse<ReevaluationResponse>> withdraw(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("REEVALUATION_WITHDRAWN", "Request withdrawn", service.withdraw(id)));
    }

    @GetMapping("/reevaluation-requests")
    @PreAuthorize("hasAuthority('REEVALUATION_MANAGE')")
    public ResponseEntity<ApiResponse<List<ReevaluationResponse>>> requests(@RequestParam(required = false) ReevaluationStatus status,
                                                                           @RequestParam(required = false) UUID examSubjectConfigId,
                                                                           @RequestParam(required = false) UUID studentId) {
        return ResponseEntity.ok(ApiResponse.success("REEVALUATION_REQUESTS", "Re-evaluation requests",
                service.requests(status, examSubjectConfigId, studentId)));
    }

    @PostMapping("/reevaluation-requests/{id}/assign")
    @PreAuthorize("hasAuthority('REEVALUATION_MANAGE')")
    @Operation(summary = "Assign a reviewer", description = "Default: yourself. Moves the request to UNDER_REVIEW")
    public ResponseEntity<ApiResponse<ReevaluationResponse>> assign(@PathVariable UUID id, @RequestBody(required = false) AssignReviewerRequest request) {
        return ResponseEntity.ok(ApiResponse.success("REEVALUATION_ASSIGNED", "Reviewer assigned",
                service.assign(id, request == null ? null : request.reviewerStaffId())));
    }

    @PostMapping("/reevaluation-requests/{id}/decide")
    @PreAuthorize("hasAuthority('REEVALUATION_MANAGE')")
    @Operation(summary = "Decide", description = "REVISED (with revisedMarks, recorded as an approved change), UNCHANGED or REJECTED")
    public ResponseEntity<ApiResponse<ReevaluationResponse>> decide(@PathVariable UUID id, @Valid @RequestBody ReevaluationDecision request) {
        return ResponseEntity.ok(ApiResponse.success("REEVALUATION_DECIDED", "Re-evaluation decided", service.decide(id, request)));
    }
}
