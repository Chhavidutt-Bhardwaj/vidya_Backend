package com.vidya.tenantdata.timetable.controller;

import com.vidya.common.api.ApiResponse;
import com.vidya.common.api.PagedApiResponse;
import com.vidya.tenantdata.timetable.dto.CreateRoomRequest;
import com.vidya.tenantdata.timetable.dto.RoomResponse;
import com.vidya.tenantdata.timetable.dto.UpdateRoomRequest;
import com.vidya.tenantdata.timetable.enums.RoomStatus;
import com.vidya.tenantdata.timetable.enums.RoomType;
import com.vidya.tenantdata.timetable.service.RoomService;
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
@RequestMapping({"/api/v1/tenant/rooms", "/api/v1/rooms"})
@RequiredArgsConstructor
@Tag(name = "Room Management", description = "Physical classrooms and facility management for the active school tenant")
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    @PreAuthorize("hasAuthority('ROOM_CREATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Create room", description = "Creates a new physical room or facility in the school")
    public ResponseEntity<ApiResponse<RoomResponse>> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        log.info("REST: Create room [{}]", request.name());
        RoomResponse response = roomService.createRoom(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("ROOM_CREATED", "Room created successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROOM_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Search rooms", description = "Searches and filters physical rooms with pagination")
    public ResponseEntity<PagedApiResponse<RoomResponse>> searchRooms(
            @Parameter(description = "Search term by room name or code")
            @RequestParam(required = false) String search,
            @Parameter(description = "Filter by room type")
            @RequestParam(required = false) RoomType roomType,
            @Parameter(description = "Filter by room status")
            @RequestParam(required = false) RoomStatus status,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<RoomResponse> page = roomService.searchRooms(search, roomType, status, pageable);
        return ResponseEntity.ok(PagedApiResponse.of("ROOMS_RETRIEVED", "Rooms retrieved successfully", page.getContent(), page));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROOM_READ') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Get room by ID", description = "Fetches a room by its unique ID")
    public ResponseEntity<ApiResponse<RoomResponse>> getRoomById(@PathVariable UUID id) {
        RoomResponse response = roomService.getRoomById(id);
        return ResponseEntity.ok(ApiResponse.success("ROOM_RETRIEVED", "Room retrieved successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROOM_UPDATE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Update room", description = "Updates an existing room's details and status")
    public ResponseEntity<ApiResponse<RoomResponse>> updateRoom(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoomRequest request
    ) {
        log.info("REST: Update room [{}]", id);
        RoomResponse response = roomService.updateRoom(id, request);
        return ResponseEntity.ok(ApiResponse.success("ROOM_UPDATED", "Room updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROOM_DELETE') or hasRole('SCHOOL_ADMIN') or hasRole('SCHOOL_OWNER') or hasRole('PLATFORM_ADMIN') or hasRole('PLATFORM_SUPER_ADMIN')")
    @Operation(summary = "Delete room", description = "Soft deletes a physical room")
    public ResponseEntity<ApiResponse<Void>> deleteRoom(@PathVariable UUID id) {
        log.info("REST: Delete room [{}]", id);
        roomService.deleteRoom(id);
        return ResponseEntity.ok(ApiResponse.success("ROOM_DELETED", "Room deleted successfully", null));
    }
}
