package com.vidya.tenantdata.timetable.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.tenantdata.timetable.dto.BellScheduleResponse;
import com.vidya.tenantdata.timetable.dto.CreateBellScheduleRequest;
import com.vidya.tenantdata.timetable.dto.CreatePeriodRequest;
import com.vidya.tenantdata.timetable.dto.PeriodResponse;
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
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("BellScheduleController — Unit Tests")
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
    private UUID scheduleId;

    @BeforeEach
    void setUp() {
        scheduleId = UUID.randomUUID();
        PeriodResponse period = new PeriodResponse(
                UUID.randomUUID(), "PRD-1", "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45), PeriodType.TEACHING, Instant.now(), Instant.now(), 0L
        );
        sampleResponse = new BellScheduleResponse(
                scheduleId, "BSC-12345678", "Regular", "Standard", true, BellScheduleStatus.ACTIVE, List.of(period), Instant.now(), Instant.now(), 0L
        );

        mockMvc = MockMvcBuilders.standaloneSetup(bellScheduleController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/tenant/bell-schedules creates schedule")
    void createSchedule_success() throws Exception {
        CreateBellScheduleRequest request = new CreateBellScheduleRequest(
                "Regular", "Standard", true,
                List.of(new CreatePeriodRequest("Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45), PeriodType.TEACHING))
        );
        when(bellScheduleService.createBellSchedule(any(CreateBellScheduleRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/tenant/bell-schedules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("BELL_SCHEDULE_CREATED"))
                .andExpect(jsonPath("$.data.name").value("Regular"));
    }

    @Test
    @DisplayName("GET /api/v1/tenant/bell-schedules/default returns default schedule")
    void getDefaultSchedule_success() throws Exception {
        when(bellScheduleService.getDefaultSchedule()).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/tenant/bell-schedules/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BELL_SCHEDULE_RETRIEVED"))
                .andExpect(jsonPath("$.data.isDefault").value(true));
    }
}
