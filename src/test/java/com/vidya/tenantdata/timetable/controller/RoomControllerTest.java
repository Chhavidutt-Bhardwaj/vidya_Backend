package com.vidya.tenantdata.timetable.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.timetable.dto.CreateRoomRequest;
import com.vidya.tenantdata.timetable.dto.RoomResponse;
import com.vidya.tenantdata.timetable.dto.UpdateRoomRequest;
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
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomController — Unit Tests")
class RoomControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RoomService roomService;

    @InjectMocks
    private RoomController roomController;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private RoomResponse sampleResponse;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        roomId = UUID.randomUUID();
        sampleResponse = new RoomResponse(
                roomId, "ROM-12345678", "Room 101", RoomType.CLASSROOM, 40, "Main Wing", "1st Floor", RoomStatus.ACTIVE, Instant.now(), Instant.now(), 0L
        );

        mockMvc = MockMvcBuilders.standaloneSetup(roomController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/tenant/rooms creates room")
    void createRoom_success() throws Exception {
        CreateRoomRequest request = new CreateRoomRequest("Room 101", RoomType.CLASSROOM, 40, "Main Wing", "1st Floor");
        when(roomService.createRoom(any(CreateRoomRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/tenant/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("ROOM_CREATED"))
                .andExpect(jsonPath("$.data.name").value("Room 101"))
                .andExpect(jsonPath("$.data.capacity").value(40));
    }

    @Test
    @DisplayName("GET /api/v1/tenant/rooms searches rooms")
    void searchRooms_success() throws Exception {
        when(roomService.searchRooms(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse)));

        mockMvc.perform(get("/api/v1/tenant/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ROOMS_RETRIEVED"))
                .andExpect(jsonPath("$.data[0].roomCode").value("ROM-12345678"));
    }

    @Test
    @DisplayName("GET /api/v1/tenant/rooms/{id} returns room")
    void getRoomById_success() throws Exception {
        when(roomService.getRoomById(roomId)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/tenant/rooms/{id}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ROOM_RETRIEVED"))
                .andExpect(jsonPath("$.data.id").value(roomId.toString()));
    }

    @Test
    @DisplayName("PUT /api/v1/tenant/rooms/{id} updates room")
    void updateRoom_success() throws Exception {
        UpdateRoomRequest request = new UpdateRoomRequest("Room 101", RoomType.LAB, 50, "Main", "1st", RoomStatus.ACTIVE);
        when(roomService.updateRoom(eq(roomId), any(UpdateRoomRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(put("/api/v1/tenant/rooms/{id}", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ROOM_UPDATED"));
    }

    @Test
    @DisplayName("DELETE /api/v1/tenant/rooms/{id} deletes room")
    void deleteRoom_success() throws Exception {
        doNothing().when(roomService).deleteRoom(roomId);

        mockMvc.perform(delete("/api/v1/tenant/rooms/{id}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ROOM_DELETED"));
    }
}
