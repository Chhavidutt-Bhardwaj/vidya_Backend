package com.vidya.tenantdata.engagement.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.ClassActivityRequest;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.ClassActivityResponse;
import com.vidya.tenantdata.engagement.service.ClassActivityService;
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
@RequestMapping("/api/v1/class-activities")
@RequiredArgsConstructor
@Tag(name = "Class Activities", description = "What a class or section did on a day (lab, sports, trip, event ...), for the daily report")
public class ClassActivityController {

    private final ClassActivityService service;

    @GetMapping
    @PreAuthorize("hasAuthority('CLASS_ACTIVITY_READ')")
    @Operation(summary = "Class activities (staff)", description = "Without STUDENT scope ALL: yours and those of classes you teach")
    public ResponseEntity<ApiResponse<List<ClassActivityResponse>>> list(
            @RequestParam(required = false) UUID academicYearId, @RequestParam(required = false) UUID academicClassId,
            @RequestParam(required = false) UUID sectionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.success("CLASS_ACTIVITIES", "Class activities",
                service.list(academicYearId, academicClassId, sectionId, from, to, limit)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CLASS_ACTIVITY_MANAGE')")
    @Operation(summary = "Record a class activity", description = "For a section you teach, or a class you teach in (no sectionId = whole class)")
    public ResponseEntity<ApiResponse<ClassActivityResponse>> create(@Valid @RequestBody ClassActivityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("CLASS_ACTIVITY_CREATED", "Class activity recorded",
                service.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CLASS_ACTIVITY_MANAGE')")
    public ResponseEntity<ApiResponse<ClassActivityResponse>> update(@PathVariable UUID id, @Valid @RequestBody ClassActivityRequest request) {
        return ResponseEntity.ok(ApiResponse.success("CLASS_ACTIVITY_UPDATED", "Class activity updated", service.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CLASS_ACTIVITY_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("CLASS_ACTIVITY_DELETED", "Class activity deleted"));
    }
}
