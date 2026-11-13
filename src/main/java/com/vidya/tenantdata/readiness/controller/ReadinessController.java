package com.vidya.tenantdata.readiness.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.readiness.dto.ReadinessDtos.ReadinessReport;
import com.vidya.tenantdata.readiness.service.ReadinessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/academic-years/{id}")
@RequiredArgsConstructor
@Tag(name = "Validation & Readiness", description = "Configuration checks, conflicts, missing data and pending approvals for an academic year")
public class ReadinessController {

    private final ReadinessService readinessService;

    @GetMapping("/readiness")
    @PreAuthorize("hasAuthority('ACADEMIC_YEAR_READ')")
    @Operation(summary = "Is the year ready to go live?",
            description = "Verdict (READY / READY_WITH_WARNINGS / BLOCKED) with checks grouped into configuration, conflicts, missing data and "
                    + "pending approvals. Each check has a severity, a count, up to five examples and the endpoint that fixes it. "
                    + "Areas the user may not read are listed in notChecked.")
    public ResponseEntity<ApiResponse<ReadinessReport>> readiness(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("ACADEMIC_YEAR_READINESS", "Academic year readiness", readinessService.check(id)));
    }
}
