package com.vidya.tenantdata.academic.structure.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.academic.dto.AcademicClassResponse;
import com.vidya.tenantdata.academic.mapper.AcademicMapper;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.ClassStructureRequest;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.CurriculumGridRequest;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.CurriculumGridResult;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.Overview;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.SectionStreamsRequest;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.StreamRequest;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.StreamResponse;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.StreamStatusRequest;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.TemplateRequest;
import com.vidya.tenantdata.academic.structure.dto.StructureDtos.TemplateResult;
import com.vidya.tenantdata.academic.structure.service.AcademicStructureService;
import com.vidya.tenantdata.academic.structure.service.StructureTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Academic structure configuration: streams, class settings, section streams, curriculum grid, overview, template. */
@RestController
@RequestMapping("/api/v1/academic-structure")
@RequiredArgsConstructor
@Tag(name = "Academic Structure", description = "Classes, sections, streams, subjects, curriculum and grading in one place")
public class AcademicStructureController {

    private final AcademicStructureService structureService;
    private final StructureTemplateService templateService;

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('ACADEMIC_CLASS_READ') and hasAuthority('CURRICULUM_READ')")
    @Operation(summary = "Whole structure of a year",
            description = "Classes (stage, level, streams, effective grading scale) → sections (stream, students) → curriculum subjects, plus problems to fix")
    public ResponseEntity<ApiResponse<Overview>> overview(@RequestParam UUID academicYearId) {
        return ResponseEntity.ok(ApiResponse.success("ACADEMIC_STRUCTURE", "Academic structure", structureService.overview(academicYearId)));
    }

    @PostMapping("/templates/indian-k12")
    @PreAuthorize("hasAuthority('ACADEMIC_CLASS_CREATE') and hasAuthority('SUBJECT_CREATE') and hasAuthority('GRADING_SCALE_MANAGE')"
            + " and (#request.academicYearId() == null or hasAuthority('CURRICULUM_CREATE'))")
    @Operation(summary = "Apply the standard Indian K–12 structure",
            description = "Creates missing classes (Nursery–XII with stage and level), XI–XII streams, grading scales per stage, common subjects "
                    + "and optionally a curriculum for a year. Existing items are kept. Use dryRun to preview.")
    public ResponseEntity<ApiResponse<TemplateResult>> template(@Valid @RequestBody TemplateRequest request) {
        TemplateResult r = templateService.apply(request);
        return ResponseEntity.status(r.dryRun() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ApiResponse.success("ACADEMIC_STRUCTURE_TEMPLATE", r.dryRun() ? "Template preview" : "Template applied", r));
    }

    @PutMapping("/classes/{classId}")
    @PreAuthorize("hasAuthority('ACADEMIC_CLASS_UPDATE')")
    @Operation(summary = "Set a class's stage, grade level, streams and grading scale",
            description = "Full replacement; null clears a value. stage defaults from gradeLevel.")
    public ResponseEntity<ApiResponse<AcademicClassResponse>> updateClass(@PathVariable UUID classId, @Valid @RequestBody ClassStructureRequest request) {
        return ResponseEntity.ok(ApiResponse.success("ACADEMIC_CLASS_STRUCTURE_UPDATED", "Class structure updated",
                AcademicMapper.toResponse(structureService.updateClass(classId, request))));
    }

    @PutMapping("/sections/streams")
    @PreAuthorize("hasAuthority('SECTION_UPDATE')")
    @Operation(summary = "Set the stream of sections", description = "streamId null clears it. All or nothing.")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> sectionStreams(@Valid @RequestBody SectionStreamsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("SECTION_STREAMS_UPDATED", "Section streams updated",
                Map.of("changed", structureService.setSectionStreams(request))));
    }

    @PutMapping("/curricula/{curriculumId}/subjects")
    @PreAuthorize("hasAuthority('CURRICULUM_SUBJECT_CREATE') and hasAuthority('CURRICULUM_SUBJECT_UPDATE') and hasAuthority('CURRICULUM_SUBJECT_DELETE')")
    @Operation(summary = "Save the curriculum subject grid for some classes",
            description = "For every class in academicClassIds the subject list becomes exactly the rows given (stream-specific rows allowed for classes with streams)")
    public ResponseEntity<ApiResponse<CurriculumGridResult>> curriculumGrid(@PathVariable UUID curriculumId,
                                                                            @Valid @RequestBody CurriculumGridRequest request) {
        return ResponseEntity.ok(ApiResponse.success("CURRICULUM_GRID_SAVED", "Curriculum subjects saved",
                structureService.curriculumGrid(curriculumId, request)));
    }

    // ── Streams ──────────────────────────────────────────────────────────────

    @GetMapping("/streams")
    @PreAuthorize("hasAuthority('ACADEMIC_CLASS_READ')")
    @Operation(summary = "List streams with usage")
    public ResponseEntity<ApiResponse<List<StreamResponse>>> streams() {
        return ResponseEntity.ok(ApiResponse.success("STREAMS_FETCHED", "Streams", structureService.streams()));
    }

    @PostMapping("/streams")
    @PreAuthorize("hasAuthority('ACADEMIC_CLASS_CREATE')")
    @Operation(summary = "Create a stream")
    public ResponseEntity<ApiResponse<StreamResponse>> createStream(@Valid @RequestBody StreamRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("STREAM_CREATED", "Stream created", structureService.createStream(request)));
    }

    @PutMapping("/streams/{id}")
    @PreAuthorize("hasAuthority('ACADEMIC_CLASS_UPDATE')")
    @Operation(summary = "Update a stream", description = "The code cannot change once sections or subjects use the stream")
    public ResponseEntity<ApiResponse<StreamResponse>> updateStream(@PathVariable UUID id, @Valid @RequestBody StreamRequest request) {
        return ResponseEntity.ok(ApiResponse.success("STREAM_UPDATED", "Stream updated", structureService.updateStream(id, request)));
    }

    @PatchMapping("/streams/{id}/status")
    @PreAuthorize("hasAuthority('ACADEMIC_CLASS_UPDATE')")
    @Operation(summary = "Activate or deactivate a stream")
    public ResponseEntity<ApiResponse<StreamResponse>> streamStatus(@PathVariable UUID id, @Valid @RequestBody StreamStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("STREAM_STATUS_UPDATED", "Stream status updated", structureService.setStreamStatus(id, request.status())));
    }

    @DeleteMapping("/streams/{id}")
    @PreAuthorize("hasAuthority('ACADEMIC_CLASS_DELETE')")
    @Operation(summary = "Delete an unused stream")
    public ResponseEntity<ApiResponse<Void>> deleteStream(@PathVariable UUID id) {
        structureService.deleteStream(id);
        return ResponseEntity.ok(ApiResponse.success("STREAM_DELETED", "Stream deleted"));
    }
}
