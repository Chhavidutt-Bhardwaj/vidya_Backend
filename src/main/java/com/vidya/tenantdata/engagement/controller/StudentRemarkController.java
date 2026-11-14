package com.vidya.tenantdata.engagement.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.RemarkResponse;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.RemarkUpdateRequest;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.RemarksRequest;
import com.vidya.tenantdata.engagement.service.StudentRemarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student-remarks")
@RequiredArgsConstructor
@Tag(name = "Student Remarks", description = "Teachers' daily remarks about students; visible ones go into the daily report")
public class StudentRemarkController {

    private final StudentRemarkService service;

    @GetMapping
    @PreAuthorize("hasAuthority('STUDENT_REMARK_READ') and @dataAccess.canQueryStudents(authentication, #studentId, #sectionId, 'STUDENT')")
    @Operation(summary = "Remarks of a student or a section", description = "Staff-only remarks only for users who can write remarks")
    public ResponseEntity<ApiResponse<List<RemarkResponse>>> list(
            @RequestParam(required = false) UUID studentId, @RequestParam(required = false) UUID sectionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.success("STUDENT_REMARKS", "Remarks", service.list(studentId, sectionId, from, to, limit)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('STUDENT_REMARK_MANAGE')")
    @Operation(summary = "Write remarks for one or many students", description = "Only students you teach (STUDENT scope ALL: any). All or nothing")
    public ResponseEntity<ApiResponse<List<RemarkResponse>>> create(@Valid @RequestBody RemarksRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("STUDENT_REMARKS_CREATED", "Remarks saved", service.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('STUDENT_REMARK_MANAGE')")
    public ResponseEntity<ApiResponse<RemarkResponse>> update(@PathVariable UUID id, @Valid @RequestBody RemarkUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("STUDENT_REMARK_UPDATED", "Remark updated", service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('STUDENT_REMARK_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("STUDENT_REMARK_DELETED", "Remark deleted"));
    }
}
