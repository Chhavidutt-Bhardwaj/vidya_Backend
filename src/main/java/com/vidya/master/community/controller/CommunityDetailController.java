package com.vidya.master.community.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.ApiResponseCode;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.master.community.dto.CommissionLedgerResponse;
import com.vidya.master.community.dto.CommunityPersonalDetailRequest;
import com.vidya.master.community.dto.CommunityPersonalDetailResponse;
import com.vidya.master.community.entity.CommunityAddress;
import com.vidya.master.community.entity.CommunityBankDetail;
import com.vidya.master.community.entity.CommunityCommission;
import com.vidya.master.community.entity.CommunityLoginHistory;
import com.vidya.master.community.entity.CommunityPayroll;
import com.vidya.master.community.entity.CommunityProfessionalDetail;
import com.vidya.master.community.service.CommunityDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for managing Community Detail tables.
 * Handles personal, professional, address, bank, payroll, commission, and login history.
 */
@RestController
@RequestMapping("/api/v1/communities/{communityId}/details")
@RequiredArgsConstructor
@Tag(name = "Community Details Management", description = "APIs for managing community personal, professional, address, bank, payroll, commission, and login history")
public class CommunityDetailController {

    private final CommunityDetailService communityDetailService;

    // ==================== Personal Details ====================

    @PostMapping("/personal")
    @Operation(summary = "Create personal details", description = "Create personal information details for a community")
    public ResponseEntity<ApiResponse<CommunityPersonalDetailResponse>> createPersonalDetail(
            @Parameter(description = "Community ID")
            @PathVariable UUID communityId,
            @Valid @RequestBody CommunityPersonalDetailRequest request) {
        CommunityPersonalDetailResponse response = communityDetailService.createPersonalDetail(communityId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "PERSONAL_DETAIL_CREATED",
                        "Personal details created successfully",
                        response
                ));
    }

    @PutMapping("/personal")
    @Operation(summary = "Update personal details", description = "Update personal information details for a community")
    public ResponseEntity<ApiResponse<CommunityPersonalDetailResponse>> updatePersonalDetail(
            @Parameter(description = "Community ID")
            @PathVariable UUID communityId,
            @Valid @RequestBody CommunityPersonalDetailRequest request) {
        CommunityPersonalDetailResponse response = communityDetailService.updatePersonalDetail(communityId, request);
        return ResponseEntity.ok(ApiResponse.success(
                ApiResponseCode.COMMUNITY_SECTION_UPDATED,
                "Personal details updated successfully",
                response
        ));
    }

    @GetMapping("/personal")
    @Operation(summary = "Get personal details", description = "Retrieve personal information details for a community")
    public ResponseEntity<ApiResponse<CommunityPersonalDetailResponse>> getPersonalDetail(
            @Parameter(description = "Community ID")
            @PathVariable UUID communityId) {
        CommunityPersonalDetailResponse response = communityDetailService.getPersonalDetail(communityId);
        return ResponseEntity.ok(ApiResponse.success(
                "RESOURCE_FOUND",
                "Personal details retrieved successfully",
                response
        ));
    }

    // ==================== Professional Details ====================

    @PostMapping("/professional")
    @Operation(summary = "Create professional details", description = "Create professional/business details for a community")
    public ResponseEntity<ApiResponse<CommunityProfessionalDetail>> createProfessionalDetail(
            @Parameter(description = "Community ID")
            @PathVariable UUID communityId,
            @RequestBody Map<String, Object> request) {
        CommunityProfessionalDetail response = communityDetailService.createProfessionalDetail(communityId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "RESOURCE_CREATED",
                        "Professional details created successfully",
                        response
                ));
    }

    @GetMapping("/professional")
    @Operation(summary = "Get professional details", description = "Retrieve professional/business details for a community")
    public ResponseEntity<ApiResponse<CommunityProfessionalDetail>> getProfessionalDetail(
            @Parameter(description = "Community ID")
            @PathVariable UUID communityId) {
        CommunityProfessionalDetail response = communityDetailService.getProfessionalDetail(communityId);
        return ResponseEntity.ok(ApiResponse.success(
                "RESOURCE_FOUND",
                "Professional details retrieved successfully",
                response
        ));
    }

    // ==================== Address Management ====================

    @PostMapping("/addresses")
    @Operation(summary = "Add address", description = "Add a new address for a community")
    public ResponseEntity<ApiResponse<CommunityAddress>> addAddress(
            @Parameter(description = "Community ID")
            @PathVariable UUID communityId,
            @RequestBody Map<String, Object> request) {
        CommunityAddress response = communityDetailService.addAddress(communityId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "RESOURCE_CREATED",
                        "Address added successfully",
                        response
                ));
    }

    @GetMapping("/addresses")
    @Operation(summary = "Get addresses", description = "Retrieve all addresses for a community")
    public ResponseEntity<ApiResponse<List<CommunityAddress>>> getAddresses(
            @Parameter(description = "Community ID")
            @PathVariable UUID communityId) {
        List<CommunityAddress> response = communityDetailService.getAddresses(communityId);
        return ResponseEntity.ok(ApiResponse.success(
                "RESOURCE_FOUND",
                "Addresses retrieved successfully",
                response
        ));
    }

    // ==================== Bank Details ====================

    @PostMapping("/bank-details")
    @Operation(summary = "Add bank detail", description = "Add a new bank account for a community")
    public ResponseEntity<ApiResponse<CommunityBankDetail>> addBankDetail(
            @Parameter(description = "Community ID")
            @PathVariable UUID communityId,
            @RequestBody Map<String, Object> request) {
        CommunityBankDetail response = communityDetailService.addBankDetail(communityId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "RESOURCE_CREATED",
                        "Bank detail added successfully",
                        response
                ));
    }

    @GetMapping("/bank-details")
    @Operation(summary = "Get bank details", description = "Retrieve all bank accounts for a community")
    public ResponseEntity<ApiResponse<List<CommunityBankDetail>>> getBankDetails(
            @Parameter(description = "Community ID")
            @PathVariable UUID communityId) {
        List<CommunityBankDetail> response = communityDetailService.getBankDetails(communityId);
        return ResponseEntity.ok(ApiResponse.success(
                "RESOURCE_FOUND",
                "Bank details retrieved successfully",
                response
        ));
    }

    // ==================== Payroll Management ====================

    @PostMapping("/payroll")
    @Operation(summary = "Create payroll", description = "Create payroll configuration for a community")
    public ResponseEntity<ApiResponse<CommunityPayroll>> createPayroll(
            @Parameter(description = "Community ID")
            @PathVariable UUID communityId,
            @RequestBody Map<String, Object> request) {
        CommunityPayroll response = communityDetailService.createPayroll(communityId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "RESOURCE_CREATED",
                        "Payroll created successfully",
                        response
                ));
    }

    @GetMapping("/payroll")
    @Operation(summary = "Get payrolls", description = "Retrieve all payroll configurations for a community")
    public ResponseEntity<ApiResponse<List<CommunityPayroll>>> getPayrolls(
            @Parameter(description = "Community ID")
            @PathVariable UUID communityId) {
        List<CommunityPayroll> response = communityDetailService.getPayrolls(communityId);
        return ResponseEntity.ok(ApiResponse.success(
                "RESOURCE_FOUND",
                "Payrolls retrieved successfully",
                response
        ));
    }

    // ==================== Commission Management ====================

    @PostMapping("/commissions")
    @Operation(summary = "Create commission", description = "Create commission structure for a community")
    public ResponseEntity<ApiResponse<CommunityCommission>> createCommission(
            @Parameter(description = "Community ID")
            @PathVariable UUID communityId,
            @RequestBody Map<String, Object> request) {
        CommunityCommission response = communityDetailService.createCommission(communityId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "RESOURCE_CREATED",
                        "Commission created successfully",
                        response
                ));
    }

    @GetMapping("/commissions")
    @Operation(summary = "Get commissions", description = "Retrieve all commission structures for a community")
    public ResponseEntity<ApiResponse<List<CommunityCommission>>> getCommissions(
            @Parameter(description = "Community ID")
            @PathVariable UUID communityId) {
        List<CommunityCommission> response = communityDetailService.getCommissions(communityId);
        return ResponseEntity.ok(ApiResponse.success(
                "RESOURCE_FOUND",
                "Commissions retrieved successfully",
                response
        ));
    }

    // ==================== Login History ====================

    @PostMapping("/login-history")
    @Operation(summary = "Record login", description = "Record a login event for a community member")
    public ResponseEntity<ApiResponse<CommunityLoginHistory>> recordLogin(
            @Parameter(description = "Community ID")
            @PathVariable UUID communityId,
            @RequestParam UUID platformUserId,
            @RequestBody Map<String, Object> metadata) {
        CommunityLoginHistory response = communityDetailService.recordLogin(communityId, platformUserId, metadata);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "RESOURCE_CREATED",
                        "Login recorded successfully",
                        response
                ));
    }

    @PostMapping("/login-history/logout")
    @Operation(summary = "Record logout", description = "Record a logout event for a community member")
    public ResponseEntity<Void> recordLogout(
            @Parameter(description = "Community ID")
            @PathVariable UUID communityId,
            @RequestParam UUID platformUserId,
            @RequestParam String sessionId) {
        communityDetailService.recordLogout(communityId, platformUserId, sessionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/login-history")
    @Operation(summary = "Get login history", description = "Retrieve login history for a community")
    public ResponseEntity<ApiResponse<List<CommunityLoginHistory>>> getLoginHistory(
            @Parameter(description = "Community ID")
            @PathVariable UUID communityId) {
        List<CommunityLoginHistory> response = communityDetailService.getLoginHistory(communityId);
        return ResponseEntity.ok(ApiResponse.success(
                "RESOURCE_FOUND",
                "Login history retrieved successfully",
                response
        ));
    }

    // ==================== Commission Ledger ====================

    @GetMapping("/commission-ledger")
    @Operation(summary = "Get commission ledger", description = "Retrieve earned commissions (both Onboarding bounty and Query resolution fees) for a community")
    public ResponseEntity<PagedApiResponse<CommissionLedgerResponse>> getCommissionLedger(
            @Parameter(description = "Community ID")
            @PathVariable UUID communityId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<CommissionLedgerResponse> response = communityDetailService.getCommissionLedger(communityId, pageable);
        return ResponseEntity.ok(PagedApiResponse.of(
                "COMMISSION_LEDGER_FOUND",
                "Commission ledger entries retrieved successfully",
                response
        ));
    }
}
