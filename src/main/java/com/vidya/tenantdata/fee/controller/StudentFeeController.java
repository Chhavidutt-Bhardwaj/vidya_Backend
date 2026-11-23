package com.vidya.tenantdata.fee.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.fee.dto.FeeDtos.LedgerDetail;
import com.vidya.tenantdata.fee.service.FeeLedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** A student's fees, for the student profile and for parents (who see their own children only). */
@RestController
@RequiredArgsConstructor
@Tag(name = "Fees")
public class StudentFeeController {

    private final FeeLedgerService ledgers;

    @GetMapping("/api/v1/students/{studentId}/fees")
    @PreAuthorize("hasAuthority('FEE_READ') and @dataAccess.canAccessStudent(authentication, #studentId, 'FEE')")
    @Operation(summary = "A student's ledger", description = "Lines, payments and concessions of the year (default current); 404 until billed")
    public ResponseEntity<ApiResponse<LedgerDetail>> fees(@PathVariable UUID studentId, @RequestParam(required = false) UUID academicYearId) {
        return ResponseEntity.ok(ApiResponse.success("STUDENT_FEES", "Student fees", ledgers.forStudent(studentId, academicYearId)));
    }
}
