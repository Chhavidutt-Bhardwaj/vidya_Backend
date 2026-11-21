package com.vidya.tenantdata.engagement.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.HomeworkRequest;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.HomeworkResponse;
import com.vidya.tenantdata.engagement.service.HomeworkService;
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
@RequestMapping("/api/v1/homework")
@RequiredArgsConstructor
@Tag(name = "Homework", description = "Homework set by subject teachers; shown to families in the daily report and /api/v1/parent")
public class HomeworkController {

    private final HomeworkService service;

    @GetMapping
    @PreAuthorize("hasAuthority('HOMEWORK_READ')")
    @Operation(summary = "Homework list (staff)", description = "Newest first. Without STUDENT scope ALL: homework you set or for classes you teach. "
            + "from/to filter the assigned date")
    public ResponseEntity<ApiResponse<List<HomeworkResponse>>> list(
            @RequestParam(required = false) UUID academicYearId, @RequestParam(required = false) UUID academicClassId,
            @RequestParam(required = false) UUID sectionId, @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) UUID teacherId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.success("HOMEWORK_LIST", "Homework",
                service.list(academicYearId, academicClassId, sectionId, subjectId, teacherId, from, to, limit)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('HOMEWORK_READ')")
    public ResponseEntity<ApiResponse<HomeworkResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("HOMEWORK", "Homework", service.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('HOMEWORK_MANAGE')")
    @Operation(summary = "Set homework", description = "For a subject the teacher teaches in the class (no sectionId = every section)")
    public ResponseEntity<ApiResponse<HomeworkResponse>> create(@Valid @RequestBody HomeworkRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("HOMEWORK_CREATED", "Homework set", service.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('HOMEWORK_MANAGE')")
    public ResponseEntity<ApiResponse<HomeworkResponse>> update(@PathVariable UUID id, @Valid @RequestBody HomeworkRequest request) {
        return ResponseEntity.ok(ApiResponse.success("HOMEWORK_UPDATED", "Homework updated", service.update(id, request)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('HOMEWORK_MANAGE')")
    @Operation(summary = "Cancel homework", description = "Families stop seeing it; the teacher still does")
    public ResponseEntity<ApiResponse<HomeworkResponse>> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("HOMEWORK_CANCELLED", "Homework cancelled", service.cancel(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('HOMEWORK_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("HOMEWORK_DELETED", "Homework deleted"));
    }
}
