package com.vidya.tenantdata.timetable.dto;

import com.vidya.tenantdata.timetable.enums.RoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request payload for updating a room's operational status")
public record UpdateRoomStatusRequest(
        @NotNull(message = "Room status is required")
        @Schema(description = "New operational status (ACTIVE, INACTIVE, MAINTENANCE, DELETED)", example = "MAINTENANCE")
        RoomStatus status
) {}
