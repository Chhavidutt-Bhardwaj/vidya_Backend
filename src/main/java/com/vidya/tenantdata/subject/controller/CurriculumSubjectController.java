package com.vidya.tenantdata.subject.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.tenantdata.subject.dto.CreateCurriculumSubjectRequest;
import com.vidya.tenantdata.subject.dto.CurriculumSubjectResponse;
import com.vidya.tenantdata.subject.dto.UpdateCurriculumSubjectRequest;
import com.vidya.tenantdata.subject.enums.SubjectType;
import com.vidya.tenantdata.subject.service.CurriculumSubjectService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping({"/api/v1/tenant/curricula/{curriculumId}/subjects", "/api/v1/curricula/{curriculumId}/subjects"})
@RequiredArgsConstructor
@Tag(name = "Curriculum Subjects", description = "Curriculum subject mapping operations for the active tenant")
public class CurriculumSubjectController {

    private final CurriculumSubjectService curriculumSubjectService;

    @PostMapping
    @PreAuthorize("hasAuthority('CURRICULUM_SUBJECT_CREATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Add subject to curriculum", description = "Maps an academic subject to a curriculum for a specific class")
    public ResponseEntity<ApiResponse<CurriculumSubjectResponse>> addSubjectToCurriculum(
            @Parameter(description = "Curriculum UUID") @PathVariable UUID curriculumId,
            @Valid @RequestBody CreateCurriculumSubjectRequest request) {
        log.info("REST: Add subject [{}] to curriculum [{}] for class [{}]",
                request.getSubjectId(), curriculumId, request.getAcademicClassId());
        CurriculumSubjectResponse response = curriculumSubjectService.addSubjectToCurriculum(curriculumId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("CURRICULUM_SUBJECT_ADDED", "Subject added to curriculum successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CURRICULUM_SUBJECT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "List curriculum subjects", description = "Lists subjects mapped to a curriculum with optional class/type filters")
    public ResponseEntity<PagedApiResponse<CurriculumSubjectResponse>> listCurriculumSubjects(
            @Parameter(description = "Curriculum UUID") @PathVariable UUID curriculumId,
            @RequestParam(required = false) UUID academicClassId,
            @RequestParam(required = false) SubjectType subjectType,
            @RequestParam(required = false) Boolean isCompulsory,
            @PageableDefault(size = 50, sort = "displayOrder", direction = Sort.Direction.ASC) Pageable pageable) {
        log.info("REST: List subjects for curriculum id: [{}]", curriculumId);
        Page<CurriculumSubjectResponse> page = curriculumSubjectService.listCurriculumSubjects(
                curriculumId, academicClassId, subjectType, isCompulsory, pageable);
        return ResponseEntity.ok(PagedApiResponse.of("CURRICULUM_SUBJECTS_RETRIEVED", "Curriculum subjects retrieved successfully", page.getContent(), page));
    }

    @GetMapping("/{curriculumSubjectId}")
    @PreAuthorize("hasAuthority('CURRICULUM_SUBJECT_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get curriculum subject mapping", description = "Retrieves a single curriculum subject assignment")
    public ResponseEntity<ApiResponse<CurriculumSubjectResponse>> getCurriculumSubject(
            @Parameter(description = "Curriculum UUID") @PathVariable UUID curriculumId,
            @Parameter(description = "CurriculumSubject assignment UUID") @PathVariable UUID curriculumSubjectId) {
        log.info("REST: Get curriculum subject id: [{}] for curriculum: [{}]", curriculumSubjectId, curriculumId);
        CurriculumSubjectResponse response = curriculumSubjectService.getCurriculumSubject(curriculumId, curriculumSubjectId);
        return ResponseEntity.ok(ApiResponse.success("CURRICULUM_SUBJECT_RETRIEVED", "Curriculum subject retrieved successfully", response));
    }

    @PutMapping("/{curriculumSubjectId}")
    @PreAuthorize("hasAuthority('CURRICULUM_SUBJECT_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update curriculum subject", description = "Updates compulsory status or display order of a curriculum subject")
    public ResponseEntity<ApiResponse<CurriculumSubjectResponse>> updateCurriculumSubject(
            @Parameter(description = "Curriculum UUID") @PathVariable UUID curriculumId,
            @Parameter(description = "CurriculumSubject assignment UUID") @PathVariable UUID curriculumSubjectId,
            @Valid @RequestBody UpdateCurriculumSubjectRequest request) {
        log.info("REST: Update curriculum subject id: [{}] for curriculum: [{}]", curriculumSubjectId, curriculumId);
        CurriculumSubjectResponse response = curriculumSubjectService.updateCurriculumSubject(curriculumId, curriculumSubjectId, request);
        return ResponseEntity.ok(ApiResponse.success("CURRICULUM_SUBJECT_UPDATED", "Curriculum subject updated successfully", response));
    }

    @DeleteMapping("/{curriculumSubjectId}")
    @PreAuthorize("hasAuthority('CURRICULUM_SUBJECT_DELETE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Remove subject from curriculum", description = "Soft-deletes a subject assignment from a curriculum")
    public ResponseEntity<ApiResponse<Void>> removeSubjectFromCurriculum(
            @Parameter(description = "Curriculum UUID") @PathVariable UUID curriculumId,
            @Parameter(description = "CurriculumSubject assignment UUID") @PathVariable UUID curriculumSubjectId) {
        log.info("REST: Remove curriculum subject id: [{}] from curriculum: [{}]", curriculumSubjectId, curriculumId);
        curriculumSubjectService.removeSubjectFromCurriculum(curriculumId, curriculumSubjectId);
        return ResponseEntity.ok(ApiResponse.success("CURRICULUM_SUBJECT_REMOVED", "Subject removed from curriculum successfully", null));
    }
}
