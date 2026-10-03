package com.vidya.master.workflow.service;

import com.vidya.master.workflow.admin.dto.InitialSchoolAdminRequest;
import com.vidya.master.workflow.admin.dto.InitialSchoolAdminResponse;
import com.vidya.master.workflow.dto.PlanSelectionRequest;
import com.vidya.master.workflow.dto.PlanSelectionResponse;
import com.vidya.master.workflow.dto.SchoolOnboardingProgressResponse;
import com.vidya.master.workflow.dto.WorkflowActivationResponse;
import com.vidya.master.workflow.dto.WorkflowActivateRequest;
import com.vidya.master.workflow.dto.WorkflowApprovalResponse;
import com.vidya.master.workflow.dto.WorkflowApproveRequest;
import com.vidya.master.workflow.dto.WorkflowPaymentInitiateRequest;
import com.vidya.master.workflow.dto.WorkflowPaymentInitiateResponse;
import com.vidya.master.workflow.dto.WorkflowProvisionRequest;
import com.vidya.master.workflow.dto.WorkflowProvisionResponse;
import com.vidya.master.workflow.dto.WorkflowRetryRequest;
import com.vidya.master.workflow.event.PaymentCompletedEvent;

import java.util.UUID;

/**
 * Core application orchestration service coordinating the end-to-end School Onboarding Pipeline:
 * Onboarding -> Verification -> Plan Selection -> Subscription Snapshot -> Payment -> Approval ->
 * Tenant DB Provisioning -> Flyway Migration -> Tenant Initialization -> School Admin -> School Activation.
 */
public interface SchoolOnboardingWorkflowService {

    PlanSelectionResponse selectPlan(UUID onboardingId, PlanSelectionRequest request);

    WorkflowPaymentInitiateResponse initiatePayment(UUID onboardingId, WorkflowPaymentInitiateRequest request);

    void handlePaymentCompleted(PaymentCompletedEvent event);

    WorkflowApprovalResponse approveOnboarding(UUID onboardingId, WorkflowApproveRequest request);

    WorkflowProvisionResponse provisionTenant(UUID onboardingId, WorkflowProvisionRequest request);

    InitialSchoolAdminResponse createInitialAdmin(UUID onboardingId, InitialSchoolAdminRequest request);

    WorkflowActivationResponse activateSchool(UUID onboardingId, WorkflowActivateRequest request);

    WorkflowActivationResponse retryWorkflow(UUID onboardingId, WorkflowRetryRequest request);

    SchoolOnboardingProgressResponse getWorkflowProgress(UUID onboardingId);
}
