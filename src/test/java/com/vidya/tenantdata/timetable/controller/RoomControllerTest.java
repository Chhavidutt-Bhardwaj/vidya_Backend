package com.vidya.tenantdata.timetable.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.timetable.dto.CreateRoomRequest;
import com.vidya.tenantdata.timetable.dto.RoomResponse;
import com.vidya.tenantdata.timetable.dto.UpdateRoomRequest;
import com.vidya.tenantdata.timetable.dto.UpdateRoomStatusRequest;
import com.vidya.tenantdata.timetable.enums.RoomStatus;
import com.vidya.tenantdata.timetable.enums.RoomType;
import com.vidya.tenantdata.timetable.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomController — REST API Unit Tests")
class RoomControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RoomService roomService;

    @InjectMocks
    private RoomController roomController;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private UUID roomId;
    private RoomResponse roomResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(roomController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        roomId = UUID.randomUUID();
        roomResponse = new RoomResponse(
                roomId,
                "RM-2026-0001",
                "Science Lab 1",
                RoomType.SCIENCE_LAB,
                40,
                "Science Block",
                "2nd Floor",
                RoomStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
                0L
        );
    }

    @Test
    @DisplayName("POST /api/v1/rooms — 201 Created")
    void createRoom_success_returns201() throws Exception {
        CreateRoomRequest request = new CreateRoomRequest("Science Lab 1", RoomType.SCIENCE_LAB, 40, "Science Block", "2nd Floor");
        when(roomService.createRoom(any(CreateRoomRequest.class))).thenReturn(roomResponse);

        mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(roomId.toString()))
                .andExpect(jsonPath("$.data.name").value("Science Lab 1"))
                .andExpect(jsonPath("$.data.roomCode").value("RM-2026-0001"));
    }

    @Test
    @DisplayName("POST /api/v1/rooms — 400 Bad Request on invalid input")
    void createRoom_validationError_returns400() throws Exception {
        // Name blank and capacity negative
        CreateRoomRequest request = new CreateRoomRequest("", RoomType.SCIENCE_LAB, -5, "Science Block", "2nd Floor");

        mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/rooms — 409 Conflict on duplicate room name")
    void createRoom_duplicateName_returns409() throws Exception {
        CreateRoomRequest request = new CreateRoomRequest("Science Lab 1", RoomType.SCIENCE_LAB, 40, "Science Block", "2nd Floor");
        when(roomService.createRoom(any(CreateRoomRequest.class)))
                .thenThrow(new DuplicateResourceException("Room", "name", "Science Lab 1"));

        mockMvc.perform(post("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("GET /api/v1/rooms/{id} — 200 OK")
    void getRoomById_success_returns200() throws Exception {
        when(roomService.getRoomById(roomId)).thenReturn(roomResponse);

        mockMvc.perform(get("/api/v1/rooms/{id}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(roomId.toString()))
                .andExpect(jsonPath("$.data.name").value("Science Lab 1"));
    }

    @Test
    @DisplayName("GET /api/v1/rooms/{id} — 404 Not Found")
    void getRoomById_notFound_returns404() throws Exception {
        when(roomService.getRoomById(roomId))
                .thenThrow(new ResourceNotFoundException("Room", roomId));

        mockMvc.perform(get("/api/v1/rooms/{id}", roomId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/rooms — 200 OK with Paged Response")
    void searchRooms_success_returns200Paged() throws Exception {
        when(roomService.searchRooms(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(roomResponse)));

        mockMvc.perform(get("/api/v1/rooms")
                        .param("search", "Science")
                        .param("roomType", "SCIENCE_LAB")
                        .param("status", "ACTIVE")
                        .param("building", "Science Block")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(roomId.toString()))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @DisplayName("PUT /api/v1/rooms/{id} — 200 OK")
    void updateRoom_success_returns200() throws Exception {
        UpdateRoomRequest request = new UpdateRoomRequest("Updated Lab 1", RoomType.SCIENCE_LAB, 45, "Main Block", "1st Floor", RoomStatus.ACTIVE);
        when(roomService.updateRoom(eq(roomId), any(UpdateRoomRequest.class))).thenReturn(roomResponse);

        mockMvc.perform(put("/api/v1/rooms/{id}", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(roomId.toString()));
    }

    @Test
    @DisplayName("PUT /api/v1/rooms/{id} — 400 Bad Request on invalid input")
    void updateRoom_invalidInput_returns400() throws Exception {
        UpdateRoomRequest request = new UpdateRoomRequest("", null, -1, null, null, null);

        mockMvc.perform(put("/api/v1/rooms/{id}", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("PATCH /api/v1/rooms/{id}/status — 200 OK")
    void updateRoomStatus_success_returns200() throws Exception {
        UpdateRoomStatusRequest request = new UpdateRoomStatusRequest(RoomStatus.MAINTENANCE);
        when(roomService.updateRoomStatus(eq(roomId), any(UpdateRoomStatusRequest.class))).thenReturn(roomResponse);

        mockMvc.perform(patch("/api/v1/rooms/{id}/status", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /api/v1/rooms/{id} — 200 OK")
    void deleteRoom_success_returns200() throws Exception {
        doNothing().when(roomService).deleteRoom(roomId);

        mockMvc.perform(delete("/api/v1/rooms/{id}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("ROOM_DELETED"));
    }

    @Test
    @DisplayName("DELETE /api/v1/rooms/{id} — 404 Not Found")
    void deleteRoom_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("ROOM_NOT_FOUND", "Room not found with ID: " + roomId))
                .when(roomService).deleteRoom(roomId);

        mockMvc.perform(delete("/api/v1/rooms/{id}", roomId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
