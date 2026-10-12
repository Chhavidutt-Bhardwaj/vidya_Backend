package com.vidya.tenantdata.calendar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.calendar.dto.DayStatusResponse;
import com.vidya.tenantdata.calendar.dto.WorkingDayConfigRequest;
import com.vidya.tenantdata.calendar.dto.WorkingDayConfigResponse;
import com.vidya.tenantdata.calendar.enums.DayStatus;
import com.vidya.tenantdata.calendar.enums.SaturdayPattern;
import com.vidya.tenantdata.calendar.service.CalendarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("CalendarController — REST API Status Tests")
class CalendarControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CalendarService calendarService;

    @InjectMocks
    private CalendarController calendarController;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UUID yearId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(calendarController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        yearId = UUID.randomUUID();
    }

    @Test
    @DisplayName("GET /api/v1/calendar/config — Returns 200 OK with config")
    void getWorkingDayConfig_returns200() throws Exception {
        WorkingDayConfigResponse response = new WorkingDayConfigResponse(
                UUID.randomUUID(), yearId, "2026-2027",
                true, true, true, true, true, true, false, SaturdayPattern.ALL, false
        );

        when(calendarService.getWorkingDayConfig(yearId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/calendar/config")
                        .param("academicYearId", yearId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saturdayPattern").value("ALL"));
    }

    @Test
    @DisplayName("GET /api/v1/calendar/config — Unknown academic year returns 404 Not Found")
    void getWorkingDayConfig_notFound_returns404() throws Exception {
        when(calendarService.getWorkingDayConfig(yearId))
                .thenThrow(new ResourceNotFoundException("AcademicYear", yearId));

        mockMvc.perform(get("/api/v1/calendar/config")
                        .param("academicYearId", yearId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/v1/calendar/config — Valid request returns 200 OK")
    void updateWorkingDayConfig_returns200() throws Exception {
        WorkingDayConfigRequest request = new WorkingDayConfigRequest(
                true, true, true, true, true, true, false, SaturdayPattern.SECOND_FOURTH
        );
        WorkingDayConfigResponse response = new WorkingDayConfigResponse(
                UUID.randomUUID(), yearId, "2026-2027",
                true, true, true, true, true, true, false, SaturdayPattern.SECOND_FOURTH, false
        );

        when(calendarService.updateWorkingDayConfig(eq(yearId), any(WorkingDayConfigRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/calendar/config")
                        .param("academicYearId", yearId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.saturdayPattern").value("SECOND_FOURTH"));
    }

    @Test
    @DisplayName("PUT /api/v1/calendar/config — Missing field returns 400 Bad Request")
    void updateWorkingDayConfig_missingField_returns400() throws Exception {
        WorkingDayConfigRequest request = new WorkingDayConfigRequest(
                null, true, true, true, true, true, false, null
        );

        mockMvc.perform(put("/api/v1/calendar/config")
                        .param("academicYearId", yearId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/calendar/days — Returns 200 OK with date status list")
    void getDaysStatus_returns200() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 2);

        List<DayStatusResponse> dayStatuses = List.of(
                new DayStatusResponse(from, DayOfWeek.SATURDAY, DayStatus.WORKING),
                new DayStatusResponse(to, DayOfWeek.SUNDAY, DayStatus.WEEKLY_OFF)
        );

        when(calendarService.getDaysStatus(from, to)).thenReturn(dayStatuses);

        mockMvc.perform(get("/api/v1/calendar/days")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("WORKING"))
                .andExpect(jsonPath("$.data[1].status").value("WEEKLY_OFF"));
    }

    @Test
    @DisplayName("GET /api/v1/calendar/days — Invalid range returns 422 Unprocessable Content")
    void getDaysStatus_invalidRange_returns422() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 10);
        LocalDate to = LocalDate.of(2026, 8, 1);

        when(calendarService.getDaysStatus(from, to))
                .thenThrow(new BusinessException("from date cannot be after to date"));

        mockMvc.perform(get("/api/v1/calendar/days")
                        .param("from", "2026-08-10")
                        .param("to", "2026-08-01"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("GET /api/v1/calendar/working-days/count — Returns 200 OK with count")
    void countWorkingDays_returns200() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 7);

        when(calendarService.countWorkingDays(from, to)).thenReturn(6L);

        mockMvc.perform(get("/api/v1/calendar/working-days/count")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workingDaysCount").value(6));
    }
}
