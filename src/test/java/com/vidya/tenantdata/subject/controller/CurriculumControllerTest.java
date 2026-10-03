package com.vidya.tenantdata.subject.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.subject.dto.CreateCurriculumRequest;
import com.vidya.tenantdata.subject.dto.CurriculumDetailResponse;
import com.vidya.tenantdata.subject.dto.CurriculumResponse;
import com.vidya.tenantdata.subject.dto.UpdateCurriculumRequest;
import com.vidya.tenantdata.subject.dto.UpdateCurriculumStatusRequest;
import com.vidya.tenantdata.subject.enums.CurriculumStatus;
import com.vidya.tenantdata.subject.service.CurriculumService;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("CurriculumController — REST API Unit Tests")
class CurriculumControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CurriculumService curriculumService;

    @InjectMocks
    private CurriculumController curriculumController;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private UUID curriculumId;
    private UUID academicYearId;
    private CurriculumDetailResponse sampleDetailResponse;
    private CurriculumResponse sampleSummaryResponse;

    @BeforeEach
    void setUp() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(curriculumController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(converter)
                .build();

        curriculumId = UUID.randomUUID();
        academicYearId = UUID.randomUUID();

        sampleDetailResponse = CurriculumDetailResponse.builder()
                .id(curriculumId)
                .curriculumCode("CUR-A1B2C3D4")
                .academicYearId(academicYearId)
                .academicYearCode("AY2026-27")
                .name("CBSE 2026-27")
                .description("CBSE Curriculum")
                .status(CurriculumStatus.DRAFT)
                .subjects(List.of())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();

        sampleSummaryResponse = CurriculumResponse.builder()
                .id(curriculumId)
                .curriculumCode("CUR-A1B2C3D4")
                .academicYearId(academicYearId)
                .academicYearCode("AY2026-27")
                .name("CBSE 2026-27")
                .status(CurriculumStatus.DRAFT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/curricula — creates curriculum and returns 201 Created")
    void createCurriculum_Success() throws Exception {
        CreateCurriculumRequest request = CreateCurriculumRequest.builder()
                .academicYearId(academicYearId)
                .name("CBSE 2026-27")
                .description("CBSE Curriculum")
                .build();

        when(curriculumService.createCurriculum(any(CreateCurriculumRequest.class))).thenReturn(sampleDetailResponse);

        mockMvc.perform(post("/api/v1/curricula")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("CURRICULUM_CREATED"))
                .andExpect(jsonPath("$.data.curriculumCode").value("CUR-A1B2C3D4"));
    }

    @Test
    @DisplayName("GET /api/v1/curricula — returns 200 OK with paginated list")
    void searchCurricula_Success() throws Exception {
        when(curriculumService.searchCurricula(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleSummaryResponse)));

        mockMvc.perform(get("/api/v1/curricula")
                        .param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CURRICULA_RETRIEVED"))
                .andExpect(jsonPath("$.data[0].curriculumCode").value("CUR-A1B2C3D4"));
    }

    @Test
    @DisplayName("GET /api/v1/curricula/{id} — returns 200 OK")
    void getCurriculum_Success() throws Exception {
        when(curriculumService.getCurriculum(curriculumId)).thenReturn(sampleDetailResponse);

        mockMvc.perform(get("/api/v1/curricula/{id}", curriculumId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(curriculumId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/curricula/{id} — returns 404 Not Found when missing")
    void getCurriculum_NotFound() throws Exception {
        when(curriculumService.getCurriculum(curriculumId)).thenThrow(new ResourceNotFoundException("Curriculum", curriculumId));

        mockMvc.perform(get("/api/v1/curricula/{id}", curriculumId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/curricula/code/{curriculumCode} — returns 200 OK")
    void getCurriculumByCode_Success() throws Exception {
        when(curriculumService.getCurriculumByCode("CUR-A1B2C3D4")).thenReturn(sampleDetailResponse);

        mockMvc.perform(get("/api/v1/curricula/code/{curriculumCode}", "CUR-A1B2C3D4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.curriculumCode").value("CUR-A1B2C3D4"));
    }

    @Test
    @DisplayName("PUT /api/v1/curricula/{id} — updates curriculum and returns 200 OK")
    void updateCurriculum_Success() throws Exception {
        UpdateCurriculumRequest request = UpdateCurriculumRequest.builder()
                .name("CBSE Revised 2026-27")
                .description("Updated")
                .build();

        when(curriculumService.updateCurriculum(eq(curriculumId), any(UpdateCurriculumRequest.class))).thenReturn(sampleDetailResponse);

        mockMvc.perform(put("/api/v1/curricula/{id}", curriculumId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("CURRICULUM_UPDATED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/curricula/{id}/status — transitions status and returns 200 OK")
    void updateStatus_Success() throws Exception {
        UpdateCurriculumStatusRequest request = UpdateCurriculumStatusRequest.builder()
                .status(CurriculumStatus.ACTIVE)
                .build();

        when(curriculumService.updateStatus(eq(curriculumId), any(UpdateCurriculumStatusRequest.class))).thenReturn(sampleDetailResponse);

        mockMvc.perform(patch("/api/v1/curricula/{id}/status", curriculumId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("CURRICULUM_STATUS_UPDATED"));
    }

    @Test
    @DisplayName("DELETE /api/v1/curricula/{id} — soft deletes and returns 200 OK")
    void deleteCurriculum_Success() throws Exception {
        doNothing().when(curriculumService).deleteCurriculum(curriculumId);

        mockMvc.perform(delete("/api/v1/curricula/{id}", curriculumId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("CURRICULUM_DELETED"));
    }
}
