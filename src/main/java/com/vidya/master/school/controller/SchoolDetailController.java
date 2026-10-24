package com.vidya.master.school.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.ApiResponseCode;
import com.vidya.master.school.dto.CompleteSchoolProfileRequest;
import com.vidya.master.school.dto.CompleteSchoolProfileResponse;
import com.vidya.master.school.dto.SchoolAddressRequest;
import com.vidya.master.school.dto.SchoolAddressResponse;
import com.vidya.master.school.dto.SchoolBoardAffiliationRequest;
import com.vidya.master.school.dto.SchoolBoardAffiliationResponse;
import com.vidya.master.school.dto.SchoolContactRequest;
import com.vidya.master.school.dto.SchoolContactResponse;
import com.vidya.master.school.dto.SchoolDocumentRequest;
import com.vidya.master.school.dto.SchoolDocumentResponse;
import com.vidya.master.school.service.SchoolDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/schools/{id}")
@RequiredArgsConstructor
@Tag(name = "School Sub-Details & Onboarding Profile", description = "Endpoints for normalized school contacts, addresses, boards, and certificates")
public class SchoolDetailController {

    private final SchoolDetailService schoolDetailService;

    // ================= CONTACTS =================

    @PostMapping("/contacts")
    @Operation(summary = "Add designated contact to school (Principal, Admin in-charge, Billing, etc.)")
    public ResponseEntity<ApiResponse<SchoolContactResponse>> addContact(
            @PathVariable UUID id,
            @Valid @RequestBody SchoolContactRequest request) {
        SchoolContactResponse response = schoolDetailService.addContact(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ApiResponseCode.SUCCESS, "Contact added successfully", response));
    }

    @GetMapping("/contacts")
    @Operation(summary = "List all contacts of a school")
    public ResponseEntity<ApiResponse<List<SchoolContactResponse>>> getContacts(@PathVariable UUID id) {
        List<SchoolContactResponse> response = schoolDetailService.getContacts(id);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, "Contacts retrieved successfully", response));
    }

    @PutMapping("/contacts/{contactId}")
    @Operation(summary = "Update designated contact")
    public ResponseEntity<ApiResponse<SchoolContactResponse>> updateContact(
            @PathVariable UUID id,
            @PathVariable UUID contactId,
            @Valid @RequestBody SchoolContactRequest request) {
        SchoolContactResponse response = schoolDetailService.updateContact(id, contactId, request);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, "Contact updated successfully", response));
    }

    @DeleteMapping("/contacts/{contactId}")
    @Operation(summary = "Delete designated contact")
    public ResponseEntity<ApiResponse<Void>> deleteContact(
            @PathVariable UUID id,
            @PathVariable UUID contactId) {
        schoolDetailService.deleteContact(id, contactId);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, "Contact deleted successfully"));
    }

    // ================= ADDRESSES =================

    @PostMapping("/addresses")
    @Operation(summary = "Add physical campus address or registered office")
    public ResponseEntity<ApiResponse<SchoolAddressResponse>> addAddress(
            @PathVariable UUID id,
            @Valid @RequestBody SchoolAddressRequest request) {
        SchoolAddressResponse response = schoolDetailService.addAddress(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ApiResponseCode.SUCCESS, "Address added successfully", response));
    }

    @GetMapping("/addresses")
    @Operation(summary = "List all addresses of a school")
    public ResponseEntity<ApiResponse<List<SchoolAddressResponse>>> getAddresses(@PathVariable UUID id) {
        List<SchoolAddressResponse> response = schoolDetailService.getAddresses(id);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, "Addresses retrieved successfully", response));
    }

    @PutMapping("/addresses/{addressId}")
    @Operation(summary = "Update address")
    public ResponseEntity<ApiResponse<SchoolAddressResponse>> updateAddress(
            @PathVariable UUID id,
            @PathVariable UUID addressId,
            @Valid @RequestBody SchoolAddressRequest request) {
        SchoolAddressResponse response = schoolDetailService.updateAddress(id, addressId, request);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, "Address updated successfully", response));
    }

    @DeleteMapping("/addresses/{addressId}")
    @Operation(summary = "Delete address")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @PathVariable UUID id,
            @PathVariable UUID addressId) {
        schoolDetailService.deleteAddress(id, addressId);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, "Address deleted successfully"));
    }

    // ================= BOARD AFFILIATIONS (CBSE, HBSE, etc.) =================

    @PostMapping("/boards")
    @Operation(summary = "Add educational board affiliation (CBSE, HBSE, ICSE, IB, etc.)")
    public ResponseEntity<ApiResponse<SchoolBoardAffiliationResponse>> addBoardAffiliation(
            @PathVariable UUID id,
            @Valid @RequestBody SchoolBoardAffiliationRequest request) {
        SchoolBoardAffiliationResponse response = schoolDetailService.addBoardAffiliation(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ApiResponseCode.SUCCESS, "Board affiliation added successfully", response));
    }

    @GetMapping("/boards")
    @Operation(summary = "List all educational board affiliations")
    public ResponseEntity<ApiResponse<List<SchoolBoardAffiliationResponse>>> getBoardAffiliations(@PathVariable UUID id) {
        List<SchoolBoardAffiliationResponse> response = schoolDetailService.getBoardAffiliations(id);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, "Board affiliations retrieved successfully", response));
    }

    @PutMapping("/boards/{affiliationId}")
    @Operation(summary = "Update board affiliation")
    public ResponseEntity<ApiResponse<SchoolBoardAffiliationResponse>> updateBoardAffiliation(
            @PathVariable UUID id,
            @PathVariable UUID affiliationId,
            @Valid @RequestBody SchoolBoardAffiliationRequest request) {
        SchoolBoardAffiliationResponse response = schoolDetailService.updateBoardAffiliation(id, affiliationId, request);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, "Board affiliation updated successfully", response));
    }

    @DeleteMapping("/boards/{affiliationId}")
    @Operation(summary = "Remove board affiliation")
    public ResponseEntity<ApiResponse<Void>> deleteBoardAffiliation(
            @PathVariable UUID id,
            @PathVariable UUID affiliationId) {
        schoolDetailService.deleteBoardAffiliation(id, affiliationId);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, "Board affiliation deleted successfully"));
    }

    // ================= COMPLIANCE DOCUMENTS & CERTIFICATES =================

    @PostMapping("/documents")
    @Operation(summary = "Upload / record statutory compliance document (Affiliation letter, Fire NOC, Trust Deed, etc.)")
    public ResponseEntity<ApiResponse<SchoolDocumentResponse>> addDocument(
            @PathVariable UUID id,
            @Valid @RequestBody SchoolDocumentRequest request) {
        SchoolDocumentResponse response = schoolDetailService.addDocument(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ApiResponseCode.SUCCESS, "Document added successfully", response));
    }

    @GetMapping("/documents")
    @Operation(summary = "List all documents and certificates for a school")
    public ResponseEntity<ApiResponse<List<SchoolDocumentResponse>>> getDocuments(@PathVariable UUID id) {
        List<SchoolDocumentResponse> response = schoolDetailService.getDocuments(id);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, "Documents retrieved successfully", response));
    }

    @PostMapping("/documents/{documentId}/verify")
    @Operation(summary = "Verify document / certificate")
    public ResponseEntity<ApiResponse<SchoolDocumentResponse>> verifyDocument(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            @RequestParam UUID verifiedByUserId) {
        SchoolDocumentResponse response = schoolDetailService.verifyDocument(id, documentId, verifiedByUserId);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, "Document verified successfully", response));
    }

    @PostMapping("/documents/{documentId}/reject")
    @Operation(summary = "Reject document with reason")
    public ResponseEntity<ApiResponse<SchoolDocumentResponse>> rejectDocument(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            @RequestParam UUID rejectedByUserId,
            @RequestParam String reason) {
        SchoolDocumentResponse response = schoolDetailService.rejectDocument(id, documentId, rejectedByUserId, reason);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, "Document marked as rejected", response));
    }

    @DeleteMapping("/documents/{documentId}")
    @Operation(summary = "Delete compliance document")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            @PathVariable UUID id,
            @PathVariable UUID documentId) {
        schoolDetailService.deleteDocument(id, documentId);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, "Document deleted successfully"));
    }

    // ================= AGGREGATED COMPLETE 360 PROFILE =================

    @GetMapping("/complete-profile")
    @Operation(summary = "Get complete 360 aggregated profile of school (core, branches, contacts, addresses, boards, documents, RM relationship)")
    public ResponseEntity<ApiResponse<CompleteSchoolProfileResponse>> getCompleteSchoolProfile(@PathVariable UUID id) {
        CompleteSchoolProfileResponse response = schoolDetailService.getCompleteSchoolProfile(id);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, "Complete school profile retrieved successfully", response));
    }

    @PutMapping("/complete-profile")
    @Operation(summary = "Submit complete post-approval school profile in one multi-facet payload")
    public ResponseEntity<ApiResponse<CompleteSchoolProfileResponse>> saveCompleteSchoolProfile(
            @PathVariable UUID id,
            @Valid @RequestBody CompleteSchoolProfileRequest request) {
        CompleteSchoolProfileResponse response = schoolDetailService.saveCompleteSchoolProfile(id, request);
        return ResponseEntity.ok(ApiResponse.success(ApiResponseCode.SUCCESS, "Complete school profile saved successfully", response));
    }
}
