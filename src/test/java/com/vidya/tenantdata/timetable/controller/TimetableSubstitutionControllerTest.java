package com.vidya.tenantdata.timetable.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.timetable.dto.CreateSubstitutionRequest;
import com.vidya.tenantdata.timetable.dto.SubstitutionResponse;
import com.vidya.tenantdata.timetable.dto.UpdateSubstitutionRequest;
import com.vidya.tenantdata.timetable.enums.SubstitutionStatus;
import com.vidya.tenantdata.timetable.enums.SubstitutionType;
import com.vidya.tenantdata.timetable.service.TimetableSubstitutionService;
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

import java.time.Instant;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("TimetableSubstitutionController — REST API Tests")
class TimetableSubstitutionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TimetableSubstitutionService substitutionService;

    @InjectMocks
    private TimetableSubstitutionController controller;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private UUID subId;
    private UUID timetableId;
    private UUID periodId;
    private UUID classId;
    private UUID sectionId;
    private UUID teacherId;
    private SubstitutionResponse sampleResponse;

    @BeforeEach
    void setUp() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(converter)
                .build();

        subId = UUID.randomUUID();
        timetableId = UUID.randomUUID();
        periodId = UUID.randomUUID();
        classId = UUID.randomUUID();
        sectionId = UUID.randomUUID();
        teacherId = UUID.randomUUID();

        sampleResponse = new SubstitutionResponse(
                subId,
                "SUB-001",
                timetableId,
                "TT-01",
                LocalDate.of(2026, 10, 5),
                periodId,
                "Period 1",
                1,
                classId,
                "Class 10",
                sectionId,
                "Section A",
                SubstitutionType.TEACHER_SUBSTITUTE,
                UUID.randomUUID(),
                "EMP-001",
                UUID.randomUUID(),
                "Math",
                teacherId,
                "EMP-002",
                null,
                null,
                "Leave",
                SubstitutionStatus.ACTIVE,
                true,
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    @DisplayName("POST /api/v1/substitutions — 201 Created")
    void createSubstitution_returns201() throws Exception {
        CreateSubstitutionRequest req = new CreateSubstitutionRequest(
                timetableId, LocalDate.of(2026, 10, 5), periodId, classId, sectionId,
                SubstitutionType.TEACHER_SUBSTITUTE, teacherId, null, "Medical leave"
        );

        when(substitutionService.createSubstitution(any())).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/substitutions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUBSTITUTION_CREATED"))
                .andExpect(jsonPath("$.data.substitutionCode").value("SUB-001"))
                .andExpect(jsonPath("$.data.sameSubjectQualified").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/substitutions — 400 Bad Request on missing fields")
    void createSubstitution_missingFields_returns400() throws Exception {
        CreateSubstitutionRequest invalidReq = new CreateSubstitutionRequest(
                null, null, null, null, null, null, null, null, null
        );

        mockMvc.perform(post("/api/v1/substitutions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/substitutions — 409 Conflict on clash")
    void createSubstitution_clash_returns409() throws Exception {
        CreateSubstitutionRequest req = new CreateSubstitutionRequest(
                timetableId, LocalDate.of(2026, 10, 5), periodId, classId, sectionId,
                SubstitutionType.TEACHER_SUBSTITUTE, teacherId, null, "Leave"
        );

        when(substitutionService.createSubstitution(any()))
                .thenThrow(new DuplicateResourceException("Substitute teacher is already scheduled on that slot"));

        mockMvc.perform(post("/api/v1/substitutions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already scheduled")));
    }

    @Test
    @DisplayName("GET /api/v1/substitutions/{id} — 200 OK")
    void getSubstitutionById_returns200() throws Exception {
        when(substitutionService.getSubstitutionById(subId)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/substitutions/{id}", subId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUBSTITUTION_FETCHED"))
                .andExpect(jsonPath("$.data.id").value(subId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/substitutions/{id} — 404 Not Found")
    void getSubstitutionById_notFound_returns404() throws Exception {
        when(substitutionService.getSubstitutionById(subId))
                .thenThrow(new ResourceNotFoundException("TimetableSubstitution", subId));

        mockMvc.perform(get("/api/v1/substitutions/{id}", subId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/substitutions — 200 OK Paged")
    void searchSubstitutions_returns200() throws Exception {
        when(substitutionService.searchSubstitutions(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleResponse)));

        mockMvc.perform(get("/api/v1/substitutions?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].substitutionCode").value("SUB-001"));
    }

    @Test
    @DisplayName("PUT /api/v1/substitutions/{id} — 200 OK")
    void updateSubstitution_returns200() throws Exception {
        UpdateSubstitutionRequest req = new UpdateSubstitutionRequest(teacherId, null, "Updated reason");
        when(substitutionService.updateSubstitution(eq(subId), any())).thenReturn(sampleResponse);

        mockMvc.perform(put("/api/v1/substitutions/{id}", subId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUBSTITUTION_UPDATED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/substitutions/{id}/cancel — 200 OK")
    void cancelSubstitution_returns200() throws Exception {
        SubstitutionResponse cancelledResp = new SubstitutionResponse(
                subId, "SUB-001", timetableId, "TT-01", LocalDate.of(2026, 10, 5), periodId, "Period 1", 1,
                classId, "Class 10", sectionId, "Section A", SubstitutionType.TEACHER_SUBSTITUTE,
                UUID.randomUUID(), "EMP-001", UUID.randomUUID(), "Math", teacherId, "EMP-002", null, null,
                "Cancelled", SubstitutionStatus.CANCELLED, false, Instant.now(), Instant.now()
        );

        when(substitutionService.cancelSubstitution(subId)).thenReturn(cancelledResp);

        mockMvc.perform(patch("/api/v1/substitutions/{id}/cancel", subId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("DELETE /api/v1/substitutions/{id} — 200 OK")
    void deleteSubstitution_returns200() throws Exception {
        doNothing().when(substitutionService).deleteSubstitution(subId);

        mockMvc.perform(delete("/api/v1/substitutions/{id}", subId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUBSTITUTION_DELETED"));
    }
}
