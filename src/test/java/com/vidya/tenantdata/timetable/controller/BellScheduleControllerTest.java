package com.vidya.tenantdata.timetable.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.timetable.dto.BellScheduleResponse;
import com.vidya.tenantdata.timetable.dto.CreateBellScheduleRequest;
import com.vidya.tenantdata.timetable.dto.CreatePeriodRequest;
import com.vidya.tenantdata.timetable.dto.PeriodResponse;
import com.vidya.tenantdata.timetable.dto.UpdateBellScheduleRequest;
import com.vidya.tenantdata.timetable.dto.UpdateBellScheduleStatusRequest;
import com.vidya.tenantdata.timetable.dto.UpdatePeriodRequest;
import com.vidya.tenantdata.timetable.enums.BellScheduleStatus;
import com.vidya.tenantdata.timetable.enums.PeriodType;
import com.vidya.tenantdata.timetable.service.BellScheduleService;
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
import java.time.LocalTime;
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
@DisplayName("BellScheduleController — REST API Unit Tests")
class BellScheduleControllerTest {

    private MockMvc mockMvc;

    @Mock
    private BellScheduleService bellScheduleService;

    @InjectMocks
    private BellScheduleController bellScheduleController;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private BellScheduleResponse sampleResponse;
    private PeriodResponse periodResponse;
    private UUID scheduleId;
    private UUID periodId;

    @BeforeEach
    void setUp() {
        scheduleId = UUID.randomUUID();
        periodId = UUID.randomUUID();

        periodResponse = new PeriodResponse(
                periodId, "PRD-1", "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45), PeriodType.TEACHING, Instant.now(), Instant.now(), 0L
        );
        sampleResponse = new BellScheduleResponse(
                scheduleId, "BSC-12345678", "Regular Schedule", "Standard", true, BellScheduleStatus.ACTIVE, List.of(periodResponse), Instant.now(), Instant.now(), 0L
        );

        mockMvc = MockMvcBuilders.standaloneSetup(bellScheduleController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/bell-schedules — 201 Created")
    void createSchedule_success_returns201() throws Exception {
        CreateBellScheduleRequest request = new CreateBellScheduleRequest(
                "Regular Schedule", "Standard", true,
                List.of(new CreatePeriodRequest("Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45), PeriodType.TEACHING))
        );
        when(bellScheduleService.createBellSchedule(any(CreateBellScheduleRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/bell-schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("BELL_SCHEDULE_CREATED"))
                .andExpect(jsonPath("$.data.name").value("Regular Schedule"));
    }

    @Test
    @DisplayName("POST /api/v1/bell-schedules — 400 Bad Request on invalid input")
    void createSchedule_validationError_returns400() throws Exception {
        CreateBellScheduleRequest request = new CreateBellScheduleRequest("", null, false, null);

        mockMvc.perform(post("/api/v1/bell-schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/bell-schedules — 409 Conflict on duplicate name")
    void createSchedule_duplicateName_returns409() throws Exception {
        CreateBellScheduleRequest request = new CreateBellScheduleRequest(
                "Regular Schedule", "Standard", false,
                List.of(new CreatePeriodRequest("Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45), PeriodType.TEACHING))
        );
        when(bellScheduleService.createBellSchedule(any(CreateBellScheduleRequest.class)))
                .thenThrow(new DuplicateResourceException("BellSchedule", "name", "Regular Schedule"));

        mockMvc.perform(post("/api/v1/bell-schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("GET /api/v1/bell-schedules/{id} — 200 OK")
    void getScheduleById_success_returns200() throws Exception {
        when(bellScheduleService.getBellScheduleById(scheduleId)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/bell-schedules/{id}", scheduleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BELL_SCHEDULE_RETRIEVED"))
                .andExpect(jsonPath("$.data.id").value(scheduleId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/bell-schedules/{id} — 404 Not Found")
    void getScheduleById_notFound_returns404() throws Exception {
        when(bellScheduleService.getBellScheduleById(scheduleId))
                .thenThrow(new ResourceNotFoundException("BellSchedule", scheduleId));

        mockMvc.perform(get("/api/v1/bell-schedules/{id}", scheduleId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/bell-schedules — 200 OK with Paged Response")
    void searchSchedules_success_returns200Paged() throws Exception {
        when(bellScheduleService.searchBellSchedules(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse)));

        mockMvc.perform(get("/api/v1/bell-schedules")
                        .param("search", "Regular")
                        .param("status", "ACTIVE")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BELL_SCHEDULES_RETRIEVED"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/bell-schedules/default — 200 OK")
    void getDefaultSchedule_success_returns200() throws Exception {
        when(bellScheduleService.getDefaultSchedule()).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/bell-schedules/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BELL_SCHEDULE_RETRIEVED"))
                .andExpect(jsonPath("$.data.isDefault").value(true));
    }

    @Test
    @DisplayName("PUT /api/v1/bell-schedules/{id} — 200 OK")
    void updateSchedule_success_returns200() throws Exception {
        UpdateBellScheduleRequest request = new UpdateBellScheduleRequest("Updated Schedule", "Desc", false, BellScheduleStatus.ACTIVE, null);
        when(bellScheduleService.updateBellSchedule(eq(scheduleId), any(UpdateBellScheduleRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(put("/api/v1/bell-schedules/{id}", scheduleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BELL_SCHEDULE_UPDATED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/bell-schedules/{id}/status — 200 OK")
    void updateScheduleStatus_success_returns200() throws Exception {
        UpdateBellScheduleStatusRequest request = new UpdateBellScheduleStatusRequest(BellScheduleStatus.INACTIVE);
        when(bellScheduleService.updateBellScheduleStatus(eq(scheduleId), any(UpdateBellScheduleStatusRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(patch("/api/v1/bell-schedules/{id}/status", scheduleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BELL_SCHEDULE_STATUS_UPDATED"));
    }

    @Test
    @DisplayName("POST /api/v1/bell-schedules/{id}/set-default — 200 OK")
    void setDefaultSchedule_success_returns200() throws Exception {
        when(bellScheduleService.setDefaultSchedule(scheduleId)).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/bell-schedules/{id}/set-default", scheduleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BELL_SCHEDULE_SET_DEFAULT"));
    }

    @Test
    @DisplayName("POST /api/v1/bell-schedules/{id}/clone — 201 Created")
    void cloneSchedule_success_returns201() throws Exception {
        when(bellScheduleService.cloneSchedule(scheduleId)).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/bell-schedules/{id}/clone", scheduleId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("BELL_SCHEDULE_CLONED"));
    }

    @Test
    @DisplayName("DELETE /api/v1/bell-schedules/{id} — 200 OK")
    void deleteSchedule_success_returns200() throws Exception {
        doNothing().when(bellScheduleService).deleteBellSchedule(scheduleId);

        mockMvc.perform(delete("/api/v1/bell-schedules/{id}", scheduleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BELL_SCHEDULE_DELETED"));
    }

    @Test
    @DisplayName("POST /api/v1/bell-schedules/{id}/periods — 201 Created")
    void addPeriod_success_returns201() throws Exception {
        CreatePeriodRequest request = new CreatePeriodRequest("Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45), PeriodType.TEACHING);
        when(bellScheduleService.addPeriod(eq(scheduleId), any(CreatePeriodRequest.class))).thenReturn(periodResponse);

        mockMvc.perform(post("/api/v1/bell-schedules/{id}/periods", scheduleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("PERIOD_ADDED"))
                .andExpect(jsonPath("$.data.id").value(periodId.toString()));
    }

    @Test
    @DisplayName("PUT /api/v1/bell-schedules/{id}/periods/{periodId} — 200 OK")
    void updatePeriod_success_returns200() throws Exception {
        UpdatePeriodRequest request = new UpdatePeriodRequest("Updated Period", 1, LocalTime.of(8, 0), LocalTime.of(8, 45), PeriodType.TEACHING);
        when(bellScheduleService.updatePeriod(eq(scheduleId), eq(periodId), any(UpdatePeriodRequest.class))).thenReturn(periodResponse);

        mockMvc.perform(put("/api/v1/bell-schedules/{id}/periods/{periodId}", scheduleId, periodId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERIOD_UPDATED"));
    }

    @Test
    @DisplayName("DELETE /api/v1/bell-schedules/{id}/periods/{periodId} — 200 OK")
    void deletePeriod_success_returns200() throws Exception {
        doNothing().when(bellScheduleService).deletePeriod(scheduleId, periodId);

        mockMvc.perform(delete("/api/v1/bell-schedules/{id}/periods/{periodId}", scheduleId, periodId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PERIOD_DELETED"));
    }
}
