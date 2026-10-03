package com.vidya.tenantdata.timetable.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.tenantdata.timetable.dto.BulkUpsertEntriesResponse;
import com.vidya.tenantdata.timetable.dto.BulkUpsertEntryRowRequest;
import com.vidya.tenantdata.timetable.dto.BulkUpsertSectionEntriesRequest;
import com.vidya.tenantdata.timetable.dto.CloneTimetableRequest;
import com.vidya.tenantdata.timetable.dto.CloneTimetableResponse;
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
    @DisplayName("POST /api/v1/timetables creates timetable")
    void createTimetable_success() throws Exception {
        CreateTimetableRequest request = new CreateTimetableRequest(UUID.randomUUID(), UUID.randomUUID(), "Master Timetable 2026", "Main", null, null);
        when(timetableService.createTimetable(any(CreateTimetableRequest.class))).thenReturn(sampleTimetable);

        mockMvc.perform(post("/api/v1/timetables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("TIMETABLE_CREATED"))
                .andExpect(jsonPath("$.data.name").value("Master Timetable 2026"));
    }

    @Test
    @DisplayName("POST /api/v1/timetables/{id}/publish publishes timetable")
    void publishTimetable_success() throws Exception {
        TimetableResponse publishedResponse = new TimetableResponse(
                timetableId, "TTB-12345678", UUID.randomUUID(), "AY-2026", "2026-2027",
                UUID.randomUUID(), "BSC-1", "Standard", "Master Timetable 2026",
                "Main", TimetableStatus.PUBLISHED, null, null, 0,
                Instant.now(), Instant.now(), 1L
        );
        when(timetableService.publishTimetable(timetableId)).thenReturn(publishedResponse);

        mockMvc.perform(post("/api/v1/timetables/{id}/publish", timetableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TIMETABLE_PUBLISHED"))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    @DisplayName("POST /api/v1/timetables/{id}/validate validates timetable")
    void validateTimetable_success() throws Exception {
        ScheduleValidationResponse validation = new ScheduleValidationResponse(true, 0, Collections.emptyList(), 10);
        when(timetableService.validateTimetable(timetableId)).thenReturn(validation);

        mockMvc.perform(post("/api/v1/timetables/{id}/validate", timetableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TIMETABLE_VALIDATED"))
                .andExpect(jsonPath("$.data.valid").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/timetables/{timetableId}/entries creates timetable entry")
    void createEntry_success() throws Exception {
        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                UUID.randomUUID(), DayOfWeek.MONDAY, UUID.randomUUID(), UUID.randomUUID(), null, null, UUID.randomUUID(), null, UUID.randomUUID(), TimetableEntryType.REGULAR
        );
        when(timetableService.createEntry(eq(timetableId), any(CreateTimetableEntryRequest.class))).thenReturn(sampleEntry);

        mockMvc.perform(post("/api/v1/timetables/{timetableId}/entries", timetableId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("TIMETABLE_ENTRY_CREATED"))
                .andExpect(jsonPath("$.data.entryCode").value("TTE-12345678"));
    }

    @Test
    @DisplayName("GET /api/v1/timetables/{timetableId}/class/{academicClassId} returns grid")
    void getClassGrid_success() throws Exception {
        UUID classId = UUID.randomUUID();
        TimetableGridResponse grid = new TimetableGridResponse(timetableId, "TTB-1", "Master", "CLASS", classId, "Class 8", Collections.emptyList());
        when(timetableService.getClassTimetableGrid(timetableId, classId, null, null)).thenReturn(grid);

        mockMvc.perform(get("/api/v1/timetables/{timetableId}/class/{academicClassId}", timetableId, classId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TIMETABLE_GRID_RETRIEVED"))
                .andExpect(jsonPath("$.data.filterType").value("CLASS"));
    }

    @Test
    @DisplayName("GET /api/v1/timetables/{id} returns 200 and detail response")
    void getTimetableById_success() throws Exception {
        TimetableDetailResponse detail = new TimetableDetailResponse(
                timetableId, "TTB-12345678", UUID.randomUUID(), "AY-2026", "2026-2027",
                null, "Master Timetable 2026", "Main", TimetableStatus.DRAFT, null, null,
                Collections.emptyList(), Instant.now(), Instant.now(), 0L
        );
        when(timetableService.getTimetableById(timetableId)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/timetables/{id}", timetableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TIMETABLE_RETRIEVED"))
                .andExpect(jsonPath("$.data.id").value(timetableId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/timetables/{id} returns 404 when not found")
    void getTimetableById_notFound() throws Exception {
        when(timetableService.getTimetableById(timetableId))
                .thenThrow(new com.vidya.common.exception.ResourceNotFoundException("Timetable", timetableId));

        mockMvc.perform(get("/api/v1/timetables/{id}", timetableId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/timetables returns 200 and paged list")
    void searchTimetables_success() throws Exception {
        when(timetableService.searchTimetables(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleTimetable)));

        mockMvc.perform(get("/api/v1/timetables")
                        .param("status", "DRAFT")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TIMETABLES_RETRIEVED"))
                .andExpect(jsonPath("$.data[0].name").value("Master Timetable 2026"));
    }

    @Test
    @DisplayName("PUT /api/v1/timetables/{id} updates timetable")
    void updateTimetable_success() throws Exception {
        com.vidya.tenantdata.timetable.dto.UpdateTimetableRequest request =
                new com.vidya.tenantdata.timetable.dto.UpdateTimetableRequest("Updated Name", "Desc", UUID.randomUUID(), null, null);
        when(timetableService.updateTimetable(eq(timetableId), any(com.vidya.tenantdata.timetable.dto.UpdateTimetableRequest.class)))
                .thenReturn(sampleTimetable);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/timetables/{id}", timetableId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TIMETABLE_UPDATED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/timetables/{id}/status updates status")
    void updateTimetableStatus_success() throws Exception {
        com.vidya.tenantdata.timetable.dto.UpdateTimetableStatusRequest request =
                new com.vidya.tenantdata.timetable.dto.UpdateTimetableStatusRequest(TimetableStatus.ACTIVE, true);
        when(timetableService.updateTimetableStatus(eq(timetableId), any(com.vidya.tenantdata.timetable.dto.UpdateTimetableStatusRequest.class)))
                .thenReturn(sampleTimetable);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/timetables/{id}/status", timetableId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TIMETABLE_STATUS_UPDATED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/timetables/{id}/status returns 409 when duplicate active timetable exists")
    void updateTimetableStatus_duplicateActive_returns409() throws Exception {
        com.vidya.tenantdata.timetable.dto.UpdateTimetableStatusRequest request =
                new com.vidya.tenantdata.timetable.dto.UpdateTimetableStatusRequest(TimetableStatus.ACTIVE, false);
        when(timetableService.updateTimetableStatus(eq(timetableId), any(com.vidya.tenantdata.timetable.dto.UpdateTimetableStatusRequest.class)))
                .thenThrow(new com.vidya.common.exception.DuplicateResourceException("Timetable", "status", "An ACTIVE timetable already exists"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/timetables/{id}/status", timetableId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("DELETE /api/v1/timetables/{id} returns 200 on soft delete")
    void deleteTimetable_success() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/timetables/{id}", timetableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TIMETABLE_DELETED"));
    }

    @Test
    @DisplayName("POST /api/v1/timetables returns 400 when request validation fails")
    void createTimetable_invalidRequest_returns400() throws Exception {
        CreateTimetableRequest invalidRequest = new CreateTimetableRequest(null, null, "", null, null, null);

        mockMvc.perform(post("/api/v1/timetables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // --- Timetable Entry MockMvc Status Tests (200/201/400/404/409/422) ---

    @Test
    @DisplayName("POST /api/v1/timetables/{timetableId}/entries returns 400 when request validation fails")
    void createEntry_invalidRequest_returns400() throws Exception {
        CreateTimetableEntryRequest invalidRequest = new CreateTimetableEntryRequest(
                null, null, null, null, null, null, null, null, null, null
        );

        mockMvc.perform(post("/api/v1/timetables/{timetableId}/entries", timetableId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/v1/timetables/{timetableId}/entries returns 422 when business rule violated")
    void createEntry_businessException_returns422() throws Exception {
        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                UUID.randomUUID(), DayOfWeek.MONDAY, UUID.randomUUID(), UUID.randomUUID(), null, null, UUID.randomUUID(), null, UUID.randomUUID(), TimetableEntryType.REGULAR
        );
        when(timetableService.createEntry(eq(timetableId), any(CreateTimetableEntryRequest.class)))
                .thenThrow(new com.vidya.common.exception.BusinessException("PERIOD_INVALID", "Period does not belong to bell schedule"));

        mockMvc.perform(post("/api/v1/timetables/{timetableId}/entries", timetableId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PERIOD_INVALID"));
    }

    @Test
    @DisplayName("POST /api/v1/timetables/{timetableId}/entries returns 404 when timetable not found")
    void createEntry_timetableNotFound_returns404() throws Exception {
        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                UUID.randomUUID(), DayOfWeek.MONDAY, UUID.randomUUID(), UUID.randomUUID(), null, null, UUID.randomUUID(), null, UUID.randomUUID(), TimetableEntryType.REGULAR
        );
        when(timetableService.createEntry(eq(timetableId), any(CreateTimetableEntryRequest.class)))
                .thenThrow(new com.vidya.common.exception.ResourceNotFoundException("Timetable", timetableId));

        mockMvc.perform(post("/api/v1/timetables/{timetableId}/entries", timetableId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/timetables/{timetableId}/entries returns 409 on teacher clash")
    void createEntry_teacherClash_returns409() throws Exception {
        CreateTimetableEntryRequest request = new CreateTimetableEntryRequest(
                UUID.randomUUID(), DayOfWeek.MONDAY, UUID.randomUUID(), UUID.randomUUID(), null, null, UUID.randomUUID(), null, UUID.randomUUID(), TimetableEntryType.REGULAR
        );
        when(timetableService.createEntry(eq(timetableId), any(CreateTimetableEntryRequest.class)))
                .thenThrow(new com.vidya.common.exception.DuplicateResourceException("TimetableEntry", "teacherId", "Teacher conflict: teacher is already assigned"));

        mockMvc.perform(post("/api/v1/timetables/{timetableId}/entries", timetableId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("GET /api/v1/timetables/{timetableId}/entries/{id} returns 200")
    void getEntryById_success() throws Exception {
        when(timetableService.getEntryById(timetableId, entryId)).thenReturn(sampleEntry);

        mockMvc.perform(get("/api/v1/timetables/{timetableId}/entries/{id}", timetableId, entryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TIMETABLE_ENTRY_RETRIEVED"))
                .andExpect(jsonPath("$.data.id").value(entryId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/timetables/{timetableId}/entries/{id} returns 404 when entry not found")
    void getEntryById_notFound() throws Exception {
        when(timetableService.getEntryById(timetableId, entryId))
                .thenThrow(new com.vidya.common.exception.ResourceNotFoundException("TimetableEntry", entryId));

        mockMvc.perform(get("/api/v1/timetables/{timetableId}/entries/{id}", timetableId, entryId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/timetables/{timetableId}/entries returns 200 paged list")
    void searchEntries_success() throws Exception {
        when(timetableService.searchEntries(eq(timetableId), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleEntry)));

        mockMvc.perform(get("/api/v1/timetables/{timetableId}/entries", timetableId)
                        .param("dayOfWeek", "MONDAY")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TIMETABLE_ENTRIES_RETRIEVED"))
                .andExpect(jsonPath("$.data[0].entryCode").value("TTE-12345678"));
    }

    @Test
    @DisplayName("PUT /api/v1/timetables/{timetableId}/entries/{id} returns 200 on update")
    void updateEntry_success() throws Exception {
        com.vidya.tenantdata.timetable.dto.UpdateTimetableEntryRequest request =
                new com.vidya.tenantdata.timetable.dto.UpdateTimetableEntryRequest(UUID.randomUUID(), DayOfWeek.TUESDAY, UUID.randomUUID(), UUID.randomUUID(), null, null, UUID.randomUUID(), null, UUID.randomUUID(), TimetableEntryType.REGULAR);
        when(timetableService.updateEntry(eq(timetableId), eq(entryId), any(com.vidya.tenantdata.timetable.dto.UpdateTimetableEntryRequest.class)))
                .thenReturn(sampleEntry);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/timetables/{timetableId}/entries/{id}", timetableId, entryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TIMETABLE_ENTRY_UPDATED"));
    }

    @Test
    @DisplayName("PUT /api/v1/timetables/{timetableId}/entries/{id} returns 409 on room clash")
    void updateEntry_roomClash_returns409() throws Exception {
        com.vidya.tenantdata.timetable.dto.UpdateTimetableEntryRequest request =
                new com.vidya.tenantdata.timetable.dto.UpdateTimetableEntryRequest(UUID.randomUUID(), DayOfWeek.TUESDAY, UUID.randomUUID(), UUID.randomUUID(), null, null, UUID.randomUUID(), null, UUID.randomUUID(), TimetableEntryType.REGULAR);
        when(timetableService.updateEntry(eq(timetableId), eq(entryId), any(com.vidya.tenantdata.timetable.dto.UpdateTimetableEntryRequest.class)))
                .thenThrow(new com.vidya.common.exception.DuplicateResourceException("TimetableEntry", "roomId", "Room conflict: room is already occupied"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/timetables/{timetableId}/entries/{id}", timetableId, entryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("DELETE /api/v1/timetables/{timetableId}/entries/{id} returns 200 on delete")
    void deleteEntry_success() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/timetables/{timetableId}/entries/{id}", timetableId, entryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TIMETABLE_ENTRY_DELETED"));
    }

    // --- Bulk Upsert Tests ---

    @Test
    @DisplayName("PUT /api/v1/timetables/{timetableId}/sections/{sectionId}/entries returns 200 on success")
    void bulkUpsertSectionEntries_success() throws Exception {
        UUID sectionId = UUID.randomUUID();
        BulkUpsertEntryRowRequest row = new BulkUpsertEntryRowRequest(
                UUID.randomUUID(), DayOfWeek.MONDAY, null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), TimetableEntryType.REGULAR
        );
        BulkUpsertSectionEntriesRequest request = new BulkUpsertSectionEntriesRequest(List.of(row), false);

        BulkUpsertEntriesResponse response = new BulkUpsertEntriesResponse(1, 0, 0, List.of(sampleEntry));
        when(timetableService.bulkUpsertSectionEntries(eq(timetableId), eq(sectionId), any(BulkUpsertSectionEntriesRequest.class)))
                .thenReturn(response);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/timetables/{timetableId}/sections/{sectionId}/entries", timetableId, sectionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TIMETABLE_ENTRIES_BULK_UPSERTED"))
                .andExpect(jsonPath("$.data.createdCount").value(1));
    }

    @Test
    @DisplayName("PUT /api/v1/timetables/{timetableId}/sections/{sectionId}/entries returns 404 when timetable/section not found")
    void bulkUpsertSectionEntries_notFound_returns404() throws Exception {
        UUID sectionId = UUID.randomUUID();
        BulkUpsertSectionEntriesRequest request = new BulkUpsertSectionEntriesRequest(List.of(), false);

        when(timetableService.bulkUpsertSectionEntries(eq(timetableId), eq(sectionId), any(BulkUpsertSectionEntriesRequest.class)))
                .thenThrow(new com.vidya.common.exception.ResourceNotFoundException("Timetable", timetableId));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/timetables/{timetableId}/sections/{sectionId}/entries", timetableId, sectionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("PUT /api/v1/timetables/{timetableId}/sections/{sectionId}/entries returns 422 on business validation error")
    void bulkUpsertSectionEntries_validationFailure_returns422() throws Exception {
        UUID sectionId = UUID.randomUUID();
        BulkUpsertSectionEntriesRequest request = new BulkUpsertSectionEntriesRequest(List.of(), false);

        when(timetableService.bulkUpsertSectionEntries(eq(timetableId), eq(sectionId), any(BulkUpsertSectionEntriesRequest.class)))
                .thenThrow(new com.vidya.common.exception.BusinessException("Bulk upsert validation failed with 1 error(s): Row 1: invalid"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/timetables/{timetableId}/sections/{sectionId}/entries", timetableId, sectionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("PUT /api/v1/timetables/{timetableId}/sections/{sectionId}/entries returns 409 on duplicate resource clash")
    void bulkUpsertSectionEntries_clash_returns409() throws Exception {
        UUID sectionId = UUID.randomUUID();
        BulkUpsertSectionEntriesRequest request = new BulkUpsertSectionEntriesRequest(List.of(), false);

        when(timetableService.bulkUpsertSectionEntries(eq(timetableId), eq(sectionId), any(BulkUpsertSectionEntriesRequest.class)))
                .thenThrow(new com.vidya.common.exception.DuplicateResourceException("TimetableEntry", "teacherId", "Teacher clash"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/timetables/{timetableId}/sections/{sectionId}/entries", timetableId, sectionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    // --- Clone Timetable Tests ---

    @Test
    @DisplayName("POST /api/v1/timetables/{id}/clone returns 201 on success")
    void cloneTimetable_success_returns201() throws Exception {
        CloneTimetableRequest request = new CloneTimetableRequest("Timetable Clone", null, null, false);
        CloneTimetableResponse response = new CloneTimetableResponse(sampleTimetable, 5, 0, List.of());

        when(timetableService.cloneTimetable(eq(timetableId), any(CloneTimetableRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/timetables/{id}/clone", timetableId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("TIMETABLE_CLONED"))
                .andExpect(jsonPath("$.data.clonedEntriesCount").value(5));
    }

    @Test
    @DisplayName("POST /api/v1/timetables/{id}/clone returns 404 when timetable not found")
    void cloneTimetable_notFound_returns404() throws Exception {
        when(timetableService.cloneTimetable(eq(timetableId), any()))
                .thenThrow(new com.vidya.common.exception.ResourceNotFoundException("Timetable", timetableId));

        mockMvc.perform(post("/api/v1/timetables/{id}/clone", timetableId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CloneTimetableRequest(null, null, null, false))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/timetables/{id}/clone returns 409 when target name already exists")
    void cloneTimetable_duplicateName_returns409() throws Exception {
        CloneTimetableRequest request = new CloneTimetableRequest("Existing Name", null, null, false);
        when(timetableService.cloneTimetable(eq(timetableId), any(CloneTimetableRequest.class)))
                .thenThrow(new com.vidya.common.exception.DuplicateResourceException("Timetable", "name", "Name exists"));

        mockMvc.perform(post("/api/v1/timetables/{id}/clone", timetableId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("POST /api/v1/timetables/{id}/clone returns 422 when strict clone fails")
    void cloneTimetable_strictFailure_returns422() throws Exception {
        CloneTimetableRequest request = new CloneTimetableRequest("Clone Strict", null, null, true);
        when(timetableService.cloneTimetable(eq(timetableId), any(CloneTimetableRequest.class)))
                .thenThrow(new com.vidya.common.exception.BusinessException("Strict clone failed: Entry is invalid"));

        mockMvc.perform(post("/api/v1/timetables/{id}/clone", timetableId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    // --- Step 24F Read APIs & Analytics Tests ---

    @Test
    @DisplayName("GET /api/v1/timetables/{id}/sections/{sectionId}/grid returns 200")
    void getSectionGrid_success() throws Exception {
        UUID sectionId = UUID.randomUUID();
        TimetableGridResponse sampleGrid = new TimetableGridResponse(timetableId, "TTB-1", "Master Timetable", "SECTION", sectionId, "Class 8 - A", Collections.emptyList());
        when(timetableService.getSectionTimetableGrid(eq(timetableId), eq(sectionId), any(), any()))
                .thenReturn(sampleGrid);

        mockMvc.perform(get("/api/v1/timetables/{id}/sections/{sectionId}/grid", timetableId, sectionId)
                        .param("days", "MONDAY", "TUESDAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TIMETABLE_GRID_RETRIEVED"))
                .andExpect(jsonPath("$.data.timetableName").value("Master Timetable"));
    }

    @Test
    @DisplayName("GET /api/v1/timetables/{id}/sections/{sectionId}/grid returns 404 when not found")
    void getSectionGrid_notFound() throws Exception {
        UUID sectionId = UUID.randomUUID();
        when(timetableService.getSectionTimetableGrid(eq(timetableId), eq(sectionId), any(), any()))
                .thenThrow(new com.vidya.common.exception.ResourceNotFoundException("Section", sectionId));

        mockMvc.perform(get("/api/v1/timetables/{id}/sections/{sectionId}/grid", timetableId, sectionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/timetables/{id}/rooms/{roomId}/occupancy returns 200")
    void getRoomOccupancy_success() throws Exception {
        UUID roomId = UUID.randomUUID();
        com.vidya.tenantdata.timetable.dto.RoomOccupancyResponse occ = new com.vidya.tenantdata.timetable.dto.RoomOccupancyResponse(
                timetableId, roomId, "ROM-101", "Room 101", 30, 15, 50.0, List.of()
        );
        when(timetableService.getRoomOccupancy(eq(timetableId), eq(roomId), any()))
                .thenReturn(occ);

        mockMvc.perform(get("/api/v1/timetables/{id}/rooms/{roomId}/occupancy", timetableId, roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ROOM_OCCUPANCY_RETRIEVED"))
                .andExpect(jsonPath("$.data.utilizationPercentage").value(50.0));
    }

    @Test
    @DisplayName("GET /api/v1/timetables/{id}/rooms/{roomId}/occupancy returns 404 when room not found")
    void getRoomOccupancy_notFound() throws Exception {
        UUID roomId = UUID.randomUUID();
        when(timetableService.getRoomOccupancy(eq(timetableId), eq(roomId), any()))
                .thenThrow(new com.vidya.common.exception.ResourceNotFoundException("Room", roomId));

        mockMvc.perform(get("/api/v1/timetables/{id}/rooms/{roomId}/occupancy", timetableId, roomId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/timetables/{id}/teachers/{teacherId}/load returns 200")
    void getTeacherLoad_success() throws Exception {
        UUID teacherId = UUID.randomUUID();
        com.vidya.tenantdata.timetable.dto.TeacherLoadResponse load = new com.vidya.tenantdata.timetable.dto.TeacherLoadResponse(
                timetableId, teacherId, "John Doe", "STF-1", 18, 30, List.of()
        );
        when(timetableService.getTeacherLoad(eq(timetableId), eq(teacherId), any()))
                .thenReturn(load);

        mockMvc.perform(get("/api/v1/timetables/{id}/teachers/{teacherId}/load", timetableId, teacherId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TEACHER_LOAD_RETRIEVED"))
                .andExpect(jsonPath("$.data.totalWeeklyTeachingPeriods").value(18));
    }

    @Test
    @DisplayName("GET /api/v1/timetables/{id}/teachers/{teacherId}/load returns 404 when teacher not found")
    void getTeacherLoad_notFound() throws Exception {
        UUID teacherId = UUID.randomUUID();
        when(timetableService.getTeacherLoad(eq(timetableId), eq(teacherId), any()))
                .thenThrow(new com.vidya.common.exception.ResourceNotFoundException("Staff", teacherId));

        mockMvc.perform(get("/api/v1/timetables/{id}/teachers/{teacherId}/load", timetableId, teacherId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/timetables/{id}/sections/{sectionId}/grid with weekOf returns 200")
    void getSectionGrid_withWeekOf_success() throws Exception {
        UUID sectionId = UUID.randomUUID();
        TimetableGridResponse grid = new TimetableGridResponse(timetableId, "TTB-1", "Timetable 1", "SECTION", sectionId, "Class 8 - A", List.of());
        when(timetableService.getSectionTimetableGrid(eq(timetableId), eq(sectionId), any(), any()))
                .thenReturn(grid);

        mockMvc.perform(get("/api/v1/timetables/{id}/sections/{sectionId}/grid", timetableId, sectionId)
                        .param("weekOf", "2026-10-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TIMETABLE_GRID_RETRIEVED"));
    }

    @Test
    @DisplayName("GET /api/v1/timetables/{id}/sections/{sectionId}/day returns 200")
    void getSectionDaySchedule_success() throws Exception {
        UUID sectionId = UUID.randomUUID();
        com.vidya.tenantdata.timetable.dto.SectionDayScheduleResponse schedule =
                new com.vidya.tenantdata.timetable.dto.SectionDayScheduleResponse(
                        timetableId, "TTB-1", "Timetable 1", UUID.randomUUID(), "Class 8", sectionId, "Section A",
                        java.time.LocalDate.of(2026, 10, 5), DayOfWeek.MONDAY, List.of()
                );

        when(timetableService.getSectionDaySchedule(eq(timetableId), eq(sectionId), eq(java.time.LocalDate.of(2026, 10, 5))))
                .thenReturn(schedule);

        mockMvc.perform(get("/api/v1/timetables/{id}/sections/{sectionId}/day", timetableId, sectionId)
                        .param("date", "2026-10-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SECTION_DAY_SCHEDULE_RETRIEVED"))
                .andExpect(jsonPath("$.data.className").value("Class 8"));
    }

    @Test
    @DisplayName("GET /api/v1/timetables/{id}/sections/{sectionId}/day returns 400 when date is outside timetable effective dates")
    void getSectionDaySchedule_dateOutsideEffectiveDates_badRequest() throws Exception {
        UUID sectionId = UUID.randomUUID();
        when(timetableService.getSectionDaySchedule(eq(timetableId), eq(sectionId), any()))
                .thenThrow(new IllegalArgumentException("Requested date is outside timetable effective dates."));

        mockMvc.perform(get("/api/v1/timetables/{id}/sections/{sectionId}/day", timetableId, sectionId)
                        .param("date", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    @DisplayName("GET /api/v1/timetables/{id}/sections/{sectionId}/day returns 404 when section not found")
    void getSectionDaySchedule_sectionNotFound() throws Exception {
        UUID sectionId = UUID.randomUUID();
        when(timetableService.getSectionDaySchedule(eq(timetableId), eq(sectionId), any()))
                .thenThrow(new com.vidya.common.exception.ResourceNotFoundException("Section", sectionId));

        mockMvc.perform(get("/api/v1/timetables/{id}/sections/{sectionId}/day", timetableId, sectionId)
                        .param("date", "2026-10-05"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/timetables/{id}/teachers/{teacherId}/day returns 200")
    void getTeacherDaySchedule_success() throws Exception {
        UUID teacherId = UUID.randomUUID();
        com.vidya.tenantdata.timetable.dto.TeacherDayScheduleResponse schedule =
                new com.vidya.tenantdata.timetable.dto.TeacherDayScheduleResponse(
                        timetableId, "TTB-1", "Timetable 1", teacherId, "STF-1", "Teacher A",
                        java.time.LocalDate.of(2026, 10, 5), DayOfWeek.MONDAY, List.of()
                );

        when(timetableService.getTeacherDaySchedule(eq(timetableId), eq(teacherId), eq(java.time.LocalDate.of(2026, 10, 5))))
                .thenReturn(schedule);

        mockMvc.perform(get("/api/v1/timetables/{id}/teachers/{teacherId}/day", timetableId, teacherId)
                        .param("date", "2026-10-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TEACHER_DAY_SCHEDULE_RETRIEVED"))
                .andExpect(jsonPath("$.data.teacherCode").value("STF-1"));
    }

    @Test
    @DisplayName("GET /api/v1/timetables/{id}/teachers/{teacherId}/day returns 404 when teacher not found")
    void getTeacherDaySchedule_teacherNotFound() throws Exception {
        UUID teacherId = UUID.randomUUID();
        when(timetableService.getTeacherDaySchedule(eq(timetableId), eq(teacherId), any()))
                .thenThrow(new com.vidya.common.exception.ResourceNotFoundException("Staff", teacherId));

        mockMvc.perform(get("/api/v1/timetables/{id}/teachers/{teacherId}/day", timetableId, teacherId)
                        .param("date", "2026-10-05"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
