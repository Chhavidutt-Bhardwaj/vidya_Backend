package com.vidya.tenantdata.engagement.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.Inbox;
import com.vidya.tenantdata.engagement.notification.NotificationOutboxService;
import com.vidya.tenantdata.security.DataAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** The signed-in user's in-app notifications in this school. */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification Inbox", description = "In-app notifications of the signed-in user (daily reports, announcements, alerts)")
public class NotificationInboxController {

    private final NotificationOutboxService outbox;
    private final DataAccessService dataAccess;

    @GetMapping
    @PreAuthorize("hasAuthority('NOTIFICATION_INBOX_READ')")
    @Operation(summary = "My notifications", description = "Newest first, with the unread count. refType/refId/refDate point at the source "
            + "(DAILY_REPORT → /api/v1/parent/daily-report?date=, ANNOUNCEMENT → the announcement)")
    public ResponseEntity<ApiResponse<Inbox>> inbox(Authentication authentication, @RequestParam(required = false) Boolean unreadOnly,
                                                    @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.success("NOTIFICATIONS", "Notifications",
                outbox.inbox(dataAccess.ownRecords(authentication).tenantUserIds(), Boolean.TRUE.equals(unreadOnly), limit)));
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("hasAuthority('NOTIFICATION_INBOX_READ')")
    public ResponseEntity<ApiResponse<Void>> read(Authentication authentication, @PathVariable UUID id) {
        outbox.markRead(dataAccess.ownRecords(authentication).tenantUserIds(), id);
        return ResponseEntity.ok(ApiResponse.success("NOTIFICATION_READ", "Marked as read"));
    }

    @PostMapping("/read-all")
    @PreAuthorize("hasAuthority('NOTIFICATION_INBOX_READ')")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> readAll(Authentication authentication) {
        int n = outbox.markAllRead(dataAccess.ownRecords(authentication).tenantUserIds());
        return ResponseEntity.ok(ApiResponse.success("NOTIFICATIONS_READ", "All marked as read", Map.of("marked", n)));
    }
}
