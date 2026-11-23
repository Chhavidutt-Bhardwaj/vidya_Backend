package com.vidya.tenantdata.assessment.omr;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.assessment.omr.OmrService.BatchView;
import com.vidya.tenantdata.assessment.omr.OmrService.ImportRequest;
import com.vidya.tenantdata.assessment.omr.OmrService.PostView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/omr")
@RequiredArgsConstructor
@Tag(name = "OMR evaluation", description = "Scanner exports scored against the answer key of each set, reviewed, then posted as marks")
public class OmrController {

    private static final long MAX_FILE = 2_000_000;

    private final OmrService service;

    @PostMapping(value = "/batches", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('OMR_MANAGE')")
    @Operation(summary = "Evaluate sheets (JSON)", description = "csv text or sheets rows; nothing is posted yet")
    public ResponseEntity<ApiResponse<BatchView>> evaluate(@Valid @RequestBody ImportRequest request) {
        return ResponseEntity.ok(ApiResponse.success("OMR_EVALUATED", "Sheets evaluated", service.evaluate(request)));
    }

    @PostMapping(value = "/batches", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('OMR_MANAGE')")
    @Operation(summary = "Evaluate sheets (CSV upload)", description = "Header: student_code | admission_number | roll_number, optional set, "
            + "answers or q1..qN")
    public ResponseEntity<ApiResponse<BatchView>> upload(@RequestPart("file") MultipartFile file, @RequestParam UUID questionPaperId,
                                                         @RequestParam(required = false) UUID sectionId,
                                                         @RequestParam(required = false) UUID examSubjectConfigId,
                                                         @RequestParam(required = false) UUID assessmentId,
                                                         @RequestParam(defaultValue = "false") boolean negativeMarking) throws IOException {
        if (file.getSize() > MAX_FILE) {
            throw new com.vidya.common.exception.BusinessException("OMR_TOO_LARGE", "The file is larger than 2 MB");
        }
        String csv = new String(file.getBytes(), StandardCharsets.UTF_8);
        return ResponseEntity.ok(ApiResponse.success("OMR_EVALUATED", "Sheets evaluated", service.evaluate(new ImportRequest(questionPaperId, sectionId,
                examSubjectConfigId, assessmentId, negativeMarking, file.getOriginalFilename(), csv, null))));
    }

    @GetMapping("/batches")
    @PreAuthorize("hasAuthority('OMR_MANAGE')")
    public ResponseEntity<ApiResponse<List<BatchView>>> batches(@RequestParam UUID questionPaperId) {
        return ResponseEntity.ok(ApiResponse.success("OMR_BATCHES", "OMR batches", service.batches(questionPaperId)));
    }

    @GetMapping("/batches/{id}")
    @PreAuthorize("hasAuthority('OMR_MANAGE')")
    public ResponseEntity<ApiResponse<BatchView>> batch(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("OMR_BATCH", "OMR batch", service.batch(id)));
    }

    @PostMapping("/batches/{id}/post")
    @PreAuthorize("hasAuthority('OMR_MANAGE') and hasAuthority('MARKS_ENTRY')")
    @Operation(summary = "Post marks", description = "Matched sheets become marks (scaled to the exam paper / assessment maximum); "
            + "enrolled students without a sheet are marked absent")
    public ResponseEntity<ApiResponse<PostView>> post(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("OMR_POSTED", "Marks posted", service.post(id)));
    }
}
