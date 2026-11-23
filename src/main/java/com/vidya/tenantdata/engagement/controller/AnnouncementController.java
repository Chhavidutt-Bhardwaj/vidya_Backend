package com.vidya.tenantdata.engagement.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.AnnouncementRequest;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.AnnouncementResponse;
import com.vidya.tenantdata.engagement.enums.AnnouncementAudience;
import com.vidya.tenantdata.engagement.enums.AnnouncementStatus;
import com.vidya.tenantdata.engagement.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
@Tag(name = "Announcements", description = "Notices to the school, a class or a section. Publishing notifies guardians; EMERGENCY ignores quiet hours")
public class AnnouncementController {

    private final AnnouncementService service;

    @GetMapping
    @PreAuthorize("hasAuthority('ANNOUNCEMENT_READ')")
    @Operation(summary = "Announcements (staff)", description = "Without STUDENT scope ALL: your drafts and published ones for the school or your classes. "
            + "Families: /api/v1/parent/announcements")
    public ResponseEntity<ApiResponse<List<AnnouncementResponse>>> list(@RequestParam(required = false) AnnouncementStatus status,
                                                                        @RequestParam(required = false) AnnouncementAudience audience,
                                                                        @RequestParam(required = false) UUID academicClassId,
                                                                        @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.success("ANNOUNCEMENTS", "Announcements", service.list(status, audience, academicClassId, limit)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
    @Operation(summary = "Write an announcement", description = "Saved as a draft; publish=true publishes and notifies at once. "
            + "SCHOOL audience needs STUDENT scope ALL; otherwise only classes / sections you teach")
    public ResponseEntity<ApiResponse<AnnouncementResponse>> create(@Valid @RequestBody AnnouncementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("ANNOUNCEMENT_CREATED", "Announcement saved", service.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
    @Operation(summary = "Change a draft")
    public ResponseEntity<ApiResponse<AnnouncementResponse>> update(@PathVariable UUID id, @Valid @RequestBody AnnouncementRequest request) {
        return ResponseEntity.ok(ApiResponse.success("ANNOUNCEMENT_UPDATED", "Announcement updated", service.update(id, request)));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
    @Operation(summary = "Publish a draft", description = "Queues messages to the guardians of every student enrolled in the audience "
            + "(notificationsQueued in the response)")
    public ResponseEntity<ApiResponse<AnnouncementResponse>> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("ANNOUNCEMENT_PUBLISHED", "Announcement published", service.publish(id)));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
    public ResponseEntity<ApiResponse<AnnouncementResponse>> withdraw(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("ANNOUNCEMENT_WITHDRAWN", "Announcement withdrawn", service.withdraw(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
    @Operation(summary = "Delete a draft or withdrawn announcement")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("ANNOUNCEMENT_DELETED", "Announcement deleted"));
    }
}
