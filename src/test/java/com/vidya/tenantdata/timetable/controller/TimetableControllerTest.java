package com.vidya.tenantdata.timetable.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.tenantdata.timetable.dto.CreateTimetableEntryRequest;
import com.vidya.tenantdata.timetable.dto.CreateTimetableRequest;
import com.vidya.tenantdata.timetable.dto.ScheduleValidationResponse;
import com.vidya.tenantdata.timetable.dto.TimetableDetailResponse;
import com.vidya.tenantdata.timetable.dto.TimetableEntryResponse;
import com.vidya.tenantdata.timetable.dto.TimetableGridResponse;
import com.vidya.tenantdata.timetable.dto.TimetableResponse;
import com.vidya.tenantdata.timetable.enums.PeriodType;
import com.vidya.tenantdata.timetable.enums.TimetableEntryType;
import com.vidya.tenantdata.timetable.enums.TimetableStatus;
import com.vidya.tenantdata.timetable.service.TimetableService;
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

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("TimetableController — Unit Tests")
class TimetableControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TimetableService timetableService;

    @InjectMocks
    private TimetableController timetableController;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private TimetableResponse sampleTimetable;
    private TimetableEntryResponse sampleEntry;
    private UUID timetableId;
    private UUID entryId;

    @BeforeEach
    void setUp() {
        timetableId = UUID.randomUUID();
        entryId = UUID.randomUUID();

        sampleTimetable = new TimetableResponse(
                timetableId, "TTB-12345678", UUID.randomUUID(), "AY-2026", "2026-2027",
                UUID.randomUUID(), "BSC-1", "Standard", "Master Timetable 2026",
                "Main", TimetableStatus.DRAFT, null, null, 0,
                Instant.now(), Instant.now(), 0L
        );

        sampleEntry = new TimetableEntryResponse(
                entryId, "TTE-12345678", timetableId, UUID.randomUUID(),
                UUID.randomUUID(), "PRD-1", "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45), PeriodType.TEACHING,
                DayOfWeek.MONDAY, UUID.randomUUID(), "CLS-8", "Class 8", UUID.randomUUID(), "SEC-A", "Section A",
                UUID.randomUUID(), "SUB-MTH", "Mathematics", UUID.randomUUID(), UUID.randomUUID(), "STF-1", "Teacher A",
                null, UUID.randomUUID(), "ROM-101", "Room 101", TimetableEntryType.REGULAR,
                Instant.now(), Instant.now(), 0L
        );

        mockMvc = MockMvcBuilders.standaloneSetup(timetableController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/tenant/timetables creates timetable")
    void createTimetable_success() throws Exception {
        CreateTimetableRequest request = new CreateTimetableRequest(UUID.randomUUID(), UUID.randomUUID(), "Master Timetable 2026", "Main", null, null);
        when(timetableService.createTimetable(any(CreateTimetableRequest.class))).thenReturn(sampleTimetable);

        mockMvc.perform(post("/api/v1/tenant/timetables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("TIMETABLE_CREATED"))
                .andExpect(jsonPath("$.data.name").value("Master Timetable 2026"));
    }

    @Test
    @DisplayName("POST /api/v1/tenant/timetables/{id}/publish publishes timetable")
    void publishTimetable_success() throws Exception {
        TimetableResponse publishedResponse = new TimetableResponse(
                timetableId, "TTB-12345678", UUID.randomUUID(), "AY-2026", "2026-2027",
                UUID.randomUUID(), "BSC-1", "Standard", "Master Timetable 2026",
                "Main", TimetableStatus.PUBLISHED, null, null, 0,
                Instant.now(), Instant.now(), 1L
        );
        when(timetableService.publishTimetable(timetableId)).thenReturn(publishedResponse);

        mockMvc.perform(post("/api/v1/tenant/timetables/{id}/publish", timetableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TIMETABLE_PUBLISHED"))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    @DisplayName("POST /api/v1/tenant/timetables/{id}/validate validates timetable")
    void validateTimetable_success() throws Exception {
        ScheduleValidationResponse validation = new ScheduleValidationResponse(true, 0, Collections.emptyList(), 10);
        when(timetableService.validateTimetable(timetableId)).thenReturn(validation);

        mockMvc.perform(post("/api/v1/tenant/timetables/{id}/validate", timetableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TIMETABLE_VALIDATED"))
                .andExpect(jsonPath("$.data.valid").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/tenant/timetables/{timetableId}/entries creates timetable entry")
    void createEntry_success() throws Exception {
        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                UUID.randomUUID(), DayOfWeek.MONDAY, UUID.randomUUID(), UUID.randomUUID(), null, null, UUID.randomUUID(), null, UUID.randomUUID(), TimetableEntryType.REGULAR
        );
        when(timetableService.createEntry(eq(timetableId), any(CreateTimetableEntryRequest.class))).thenReturn(sampleEntry);

        mockMvc.perform(post("/api/v1/tenant/timetables/{timetableId}/entries", timetableId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("TIMETABLE_ENTRY_CREATED"))
                .andExpect(jsonPath("$.data.entryCode").value("TTE-12345678"));
    }

    @Test
    @DisplayName("GET /api/v1/tenant/timetables/{timetableId}/class/{academicClassId} returns grid")
    void getClassGrid_success() throws Exception {
        UUID classId = UUID.randomUUID();
        TimetableGridResponse grid = new TimetableGridResponse(timetableId, "TTB-1", "Master", "CLASS", classId, "Class 8", Collections.emptyList());
        when(timetableService.getClassTimetableGrid(timetableId, classId, null)).thenReturn(grid);

        mockMvc.perform(get("/api/v1/tenant/timetables/{timetableId}/class/{academicClassId}", timetableId, classId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TIMETABLE_GRID_RETRIEVED"))
                .andExpect(jsonPath("$.data.filterType").value("CLASS"));
    }
}
