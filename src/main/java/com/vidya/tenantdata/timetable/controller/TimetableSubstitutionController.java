package com.vidya.tenantdata.timetable.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.tenantdata.timetable.dto.CreateSubstitutionRequest;
import com.vidya.tenantdata.timetable.dto.SubstitutionResponse;
import com.vidya.tenantdata.timetable.dto.UpdateSubstitutionRequest;
import com.vidya.tenantdata.timetable.enums.SubstitutionStatus;
import com.vidya.tenantdata.timetable.service.TimetableSubstitutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/substitutions")
@RequiredArgsConstructor
@Tag(name = "Timetable Substitutions", description = "Daily teacher substitution, room change, and class cancellation management")
public class TimetableSubstitutionController {

    private final TimetableSubstitutionService substitutionService;

    @PostMapping
    @PreAuthorize("hasAuthority('SUBSTITUTION_MANAGE')")
    @Operation(summary = "Create substitution", description = "Creates a daily teacher substitution, room change, or class cancellation")
    public ResponseEntity<ApiResponse<SubstitutionResponse>> createSubstitution(
            @Valid @RequestBody CreateSubstitutionRequest request
    ) {
        log.info("REST: Create substitution for timetable [{}] on date [{}]", request.timetableId(), request.substitutionDate());
        SubstitutionResponse response = substitutionService.createSubstitution(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("SUBSTITUTION_CREATED", "Substitution created successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SUBSTITUTION_READ')")
    @Operation(summary = "Get substitution by ID", description = "Retrieves details of a substitution")
    public ResponseEntity<ApiResponse<SubstitutionResponse>> getSubstitutionById(
            @Parameter(description = "Substitution ID") @PathVariable UUID id
    ) {
        SubstitutionResponse response = substitutionService.getSubstitutionById(id);
        return ResponseEntity.ok(ApiResponse.success("SUBSTITUTION_FETCHED", "Substitution retrieved successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SUBSTITUTION_READ')")
    @Operation(summary = "Search substitutions", description = "Search and filter substitutions by date range, section, teacher, or status")
    public ResponseEntity<PagedApiResponse<SubstitutionResponse>> searchSubstitutions(
            @Parameter(description = "Start date filter (YYYY-MM-DD)") @RequestParam(required = false) LocalDate dateFrom,
            @Parameter(description = "End date filter (YYYY-MM-DD)") @RequestParam(required = false) LocalDate dateTo,
            @Parameter(description = "Section ID filter") @RequestParam(required = false) UUID sectionId,
            @Parameter(description = "Teacher ID filter") @RequestParam(required = false) UUID teacherId,
            @Parameter(description = "Status filter") @RequestParam(required = false) SubstitutionStatus status,
            @PageableDefault(size = 20, sort = "substitutionDate", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<SubstitutionResponse> page = substitutionService.searchSubstitutions(dateFrom, dateTo, sectionId, teacherId, status, pageable);
        return ResponseEntity.ok(PagedApiResponse.of(
                "SUBSTITUTIONS_FETCHED",
                "Substitutions retrieved successfully",
                page.getContent(),
                page
        ));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SUBSTITUTION_MANAGE')")
    @Operation(summary = "Update substitution", description = "Updates substitute teacher, room, or reason for an ACTIVE substitution")
    public ResponseEntity<ApiResponse<SubstitutionResponse>> updateSubstitution(
            @Parameter(description = "Substitution ID") @PathVariable UUID id,
            @Valid @RequestBody UpdateSubstitutionRequest request
    ) {
        SubstitutionResponse response = substitutionService.updateSubstitution(id, request);
        return ResponseEntity.ok(ApiResponse.success("SUBSTITUTION_UPDATED", "Substitution updated successfully", response));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SUBSTITUTION_MANAGE')")
    @Operation(summary = "Cancel substitution", description = "Cancels an ACTIVE substitution")
    public ResponseEntity<ApiResponse<SubstitutionResponse>> cancelSubstitution(
            @Parameter(description = "Substitution ID") @PathVariable UUID id
    ) {
        SubstitutionResponse response = substitutionService.cancelSubstitution(id);
        return ResponseEntity.ok(ApiResponse.success("SUBSTITUTION_CANCELLED", "Substitution cancelled successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SUBSTITUTION_MANAGE')")
    @Operation(summary = "Delete substitution", description = "Soft deletes a substitution record")
    public ResponseEntity<ApiResponse<Void>> deleteSubstitution(
            @Parameter(description = "Substitution ID") @PathVariable UUID id
    ) {
        substitutionService.deleteSubstitution(id);
        return ResponseEntity.ok(ApiResponse.success("SUBSTITUTION_DELETED", "Substitution deleted successfully", null));
    }
}
