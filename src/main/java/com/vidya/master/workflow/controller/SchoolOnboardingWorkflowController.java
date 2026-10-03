package com.vidya.master.workflow.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.ApiResponseCode;
import com.vidya.master.workflow.admin.dto.InitialSchoolAdminRequest;
import com.vidya.master.workflow.admin.dto.InitialSchoolAdminResponse;
import com.vidya.master.workflow.dto.PlanSelectionRequest;
import com.vidya.master.workflow.dto.PlanSelectionResponse;
import com.vidya.master.workflow.dto.SchoolOnboardingProgressResponse;
import com.vidya.master.workflow.dto.WorkflowActivateRequest;
import com.vidya.master.workflow.dto.WorkflowActivationResponse;
import com.vidya.master.workflow.dto.WorkflowApprovalResponse;
import com.vidya.master.workflow.dto.WorkflowApproveRequest;
import com.vidya.master.workflow.dto.WorkflowPaymentInitiateRequest;
import com.vidya.master.workflow.dto.WorkflowPaymentInitiateResponse;
import com.vidya.master.workflow.dto.WorkflowProvisionRequest;
import com.vidya.master.workflow.dto.WorkflowProvisionResponse;
import com.vidya.master.workflow.dto.WorkflowRetryRequest;
import com.vidya.master.workflow.service.SchoolOnboardingWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/onboarding/{id}")
@RequiredArgsConstructor
@Tag(name = "School Onboarding Workflow Orchestrator", description = "End-to-end pipeline coordinating verification, plan snapshots, payment gateways, tenant DB provisioning, admin accounts, and school activation")
public class SchoolOnboardingWorkflowController {

    private final SchoolOnboardingWorkflowService workflowService;

    @PostMapping("/select-plan")
    @Operation(summary = "Select subscription plan and lock immutable billing snapshot")
    public ResponseEntity<ApiResponse<PlanSelectionResponse>> selectPlan(
            @PathVariable UUID id,
            @Valid @RequestBody PlanSelectionRequest request) {
        PlanSelectionResponse response = workflowService.selectPlan(id, request);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.SUCCESS,
                "Subscription plan snapshot created and locked successfully",
                response
        ));
    }

    @PostMapping("/payment/initiate")
    @Operation(summary = "Initiate payment gateway order for onboarding subscription fee")
    public ResponseEntity<ApiResponse<WorkflowPaymentInitiateResponse>> initiatePayment(
            @PathVariable UUID id,
            @Valid @RequestBody WorkflowPaymentInitiateRequest request) {
        WorkflowPaymentInitiateResponse response = workflowService.initiatePayment(id, request);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.PAYMENT_INITIATED,
                "Payment order initiated successfully",
                response
        ));
    }

    @PostMapping("/provision")
    @Operation(summary = "Trigger tenant database creation, Flyway migrations, and initial seed data")
    public ResponseEntity<ApiResponse<WorkflowProvisionResponse>> provisionTenant(
            @PathVariable UUID id,
            @RequestBody(required = false) WorkflowProvisionRequest request) {
        WorkflowProvisionResponse response = workflowService.provisionTenant(id, request);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.SUCCESS,
                "Tenant database provisioned and initialized successfully",
                response
        ));
    }

    @PostMapping("/admin")
    @Operation(summary = "Provision initial school administrator account")
    public ResponseEntity<ApiResponse<InitialSchoolAdminResponse>> createInitialAdmin(
            @PathVariable UUID id,
            @RequestBody(required = false) InitialSchoolAdminRequest request) {
        InitialSchoolAdminResponse response = workflowService.createInitialAdmin(id, request);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.USER_CREATED,
                "Initial school administrator created successfully",
                response
        ));
    }

    @PostMapping("/retry")
    @Operation(summary = "Retry failed workflow step (e.g. provisioning recovery) without double-charging")
    public ResponseEntity<ApiResponse<WorkflowActivationResponse>> retryWorkflow(
            @PathVariable UUID id,
            @RequestBody(required = false) WorkflowRetryRequest request) {
        WorkflowActivationResponse response = workflowService.retryWorkflow(id, request);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.ONBOARDING_APPROVED,
                "Workflow step recovered and pipeline completed successfully",
                response
        ));
    }

    @GetMapping("/workflow-status")
    @Operation(summary = "Get full multi-domain pipeline status and consistency report")
    public ResponseEntity<ApiResponse<SchoolOnboardingProgressResponse>> getWorkflowProgress(
            @PathVariable UUID id) {
        SchoolOnboardingProgressResponse response = workflowService.getWorkflowProgress(id);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.ONBOARDING_FOUND,
                "Workflow progress retrieved successfully",
                response
        ));
    }
}
