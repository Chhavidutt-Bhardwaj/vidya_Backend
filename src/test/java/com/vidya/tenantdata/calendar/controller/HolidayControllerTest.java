package com.vidya.tenantdata.calendar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.calendar.dto.CreateHolidayRequest;
import com.vidya.tenantdata.calendar.dto.HolidayResponse;
import com.vidya.tenantdata.calendar.dto.UpdateHolidayRequest;
import com.vidya.tenantdata.calendar.enums.HolidayType;
import com.vidya.tenantdata.calendar.service.HolidayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("HolidayController — REST API Status Tests")
class HolidayControllerTest {

    private MockMvc mockMvc;

    @Mock
    private HolidayService holidayService;

    @InjectMocks
    private HolidayController holidayController;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private UUID holidayId;
    private UUID yearId;
    private HolidayResponse sampleHoliday;

    @BeforeEach
    void setUp() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(holidayController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(converter)
                .build();

        holidayId = UUID.randomUUID();
        yearId = UUID.randomUUID();

        sampleHoliday = new HolidayResponse(
                holidayId,
                "HOL-001",
                yearId,
                "2026-2027",
                "Diwali Break",
                HolidayType.VACATION,
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 5),
                "Diwali festival vacation"
        );
    }

    @Test
    @DisplayName("POST /api/v1/holidays — Valid request returns 201 Created")
    void createHoliday_returns201() throws Exception {
        CreateHolidayRequest request = new CreateHolidayRequest(
                yearId, "Diwali Break", HolidayType.VACATION,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5), "Diwali"
        );

        when(holidayService.createHoliday(any(CreateHolidayRequest.class))).thenReturn(sampleHoliday);

        mockMvc.perform(post("/api/v1/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Diwali Break"));
    }

    @Test
    @DisplayName("POST /api/v1/holidays — Invalid body returns 400 Bad Request")
    void createHoliday_validationFailure_returns400() throws Exception {
        CreateHolidayRequest request = new CreateHolidayRequest(
                null, "", null, null, null, null
        );

        mockMvc.perform(post("/api/v1/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/holidays — Overlapping holiday returns 409 Conflict")
    void createHoliday_overlapping_returns409() throws Exception {
        CreateHolidayRequest request = new CreateHolidayRequest(
                yearId, "Diwali Break", HolidayType.VACATION,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5), "Diwali"
        );

        when(holidayService.createHoliday(any(CreateHolidayRequest.class)))
                .thenThrow(new DuplicateResourceException("SchoolHoliday", "dates", "Holiday overlaps with existing holiday"));

        mockMvc.perform(post("/api/v1/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /api/v1/holidays/{id} — Existing holiday returns 200 OK")
    void getHolidayById_returns200() throws Exception {
        when(holidayService.getHolidayById(holidayId)).thenReturn(sampleHoliday);

        mockMvc.perform(get("/api/v1/holidays/{id}", holidayId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(holidayId.toString()))
                .andExpect(jsonPath("$.data.name").value("Diwali Break"));
    }

    @Test
    @DisplayName("GET /api/v1/holidays/{id} — Not found returns 404 Not Found")
    void getHolidayById_notFound_returns404() throws Exception {
        when(holidayService.getHolidayById(holidayId))
                .thenThrow(new ResourceNotFoundException("SchoolHoliday", holidayId));

        mockMvc.perform(get("/api/v1/holidays/{id}", holidayId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/holidays — Paged list returns 200 OK")
    void searchHolidays_returns200() throws Exception {
        when(holidayService.searchHolidays(eq(yearId), eq(HolidayType.VACATION), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleHoliday)));

        mockMvc.perform(get("/api/v1/holidays")
                        .param("academicYearId", yearId.toString())
                        .param("type", "VACATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Diwali Break"));
    }

    @Test
    @DisplayName("PUT /api/v1/holidays/{id} — Valid update returns 200 OK")
    void updateHoliday_returns200() throws Exception {
        UpdateHolidayRequest request = new UpdateHolidayRequest(
                "Updated Diwali Break", HolidayType.VACATION,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 6), "Updated"
        );

        when(holidayService.updateHoliday(eq(holidayId), any(UpdateHolidayRequest.class)))
                .thenReturn(sampleHoliday);

        mockMvc.perform(put("/api/v1/holidays/{id}", holidayId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/v1/holidays/{id} — Soft delete returns 200 OK")
    void deleteHoliday_returns200() throws Exception {
        doNothing().when(holidayService).deleteHoliday(holidayId);

        mockMvc.perform(delete("/api/v1/holidays/{id}", holidayId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
