package com.vidya.tenantdata.subject.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.subject.dto.CreateSubjectRequest;
import com.vidya.tenantdata.subject.dto.SubjectDetailResponse;
import com.vidya.tenantdata.subject.dto.SubjectResponse;
import com.vidya.tenantdata.subject.dto.UpdateSubjectRequest;
import com.vidya.tenantdata.subject.dto.UpdateSubjectStatusRequest;
import com.vidya.tenantdata.subject.enums.SubjectStatus;
import com.vidya.tenantdata.subject.enums.SubjectType;
import com.vidya.tenantdata.subject.service.SubjectService;
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
@DisplayName("SubjectController — REST API Unit Tests")
class SubjectControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SubjectService subjectService;

    @InjectMocks
    private SubjectController subjectController;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private UUID subjectId;
    private SubjectDetailResponse sampleDetailResponse;
    private SubjectResponse sampleSummaryResponse;

    @BeforeEach
    void setUp() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(subjectController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(converter)
                .build();

        subjectId = UUID.randomUUID();

        sampleDetailResponse = SubjectDetailResponse.builder()
                .id(subjectId)
                .subjectCode("SUB-A1B2C3D4")
                .name("Mathematics")
                .shortName("MATH")
                .description("Core Math")
                .subjectType(SubjectType.CORE)
                .status(SubjectStatus.ACTIVE)
                .displayOrder(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();

        sampleSummaryResponse = SubjectResponse.builder()
                .id(subjectId)
                .subjectCode("SUB-A1B2C3D4")
                .name("Mathematics")
                .shortName("MATH")
                .subjectType(SubjectType.CORE)
                .status(SubjectStatus.ACTIVE)
                .displayOrder(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/subjects — creates subject and returns 201 Created")
    void createSubject_Success() throws Exception {
        CreateSubjectRequest request = CreateSubjectRequest.builder()
                .name("Mathematics")
                .shortName("MATH")
                .subjectType(SubjectType.CORE)
                .displayOrder(1)
                .build();

        when(subjectService.createSubject(any(CreateSubjectRequest.class))).thenReturn(sampleDetailResponse);

        mockMvc.perform(post("/api/v1/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUBJECT_CREATED"))
                .andExpect(jsonPath("$.data.subjectCode").value("SUB-A1B2C3D4"))
                .andExpect(jsonPath("$.data.name").value("Mathematics"));
    }

    @Test
    @DisplayName("POST /api/v1/subjects — returns 400 Bad Request on invalid payload")
    void createSubject_ValidationFailure() throws Exception {
        CreateSubjectRequest invalid = CreateSubjectRequest.builder()
                .name("") // blank name
                .build();

        mockMvc.perform(post("/api/v1/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/subjects — returns 200 OK with paginated list")
    void searchSubjects_Success() throws Exception {
        when(subjectService.searchSubjects(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleSummaryResponse)));

        mockMvc.perform(get("/api/v1/subjects")
                        .param("subjectType", "CORE")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUBJECTS_RETRIEVED"))
                .andExpect(jsonPath("$.data[0].name").value("Mathematics"));
    }

    @Test
    @DisplayName("GET /api/v1/subjects/{id} — returns 200 OK")
    void getSubject_Success() throws Exception {
        when(subjectService.getSubject(subjectId)).thenReturn(sampleDetailResponse);

        mockMvc.perform(get("/api/v1/subjects/{id}", subjectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(subjectId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/subjects/{id} — returns 404 Not Found when missing")
    void getSubject_NotFound() throws Exception {
        when(subjectService.getSubject(subjectId)).thenThrow(new ResourceNotFoundException("Subject", subjectId));

        mockMvc.perform(get("/api/v1/subjects/{id}", subjectId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/subjects/code/{subjectCode} — returns 200 OK")
    void getSubjectByCode_Success() throws Exception {
        when(subjectService.getSubjectByCode("SUB-A1B2C3D4")).thenReturn(sampleDetailResponse);

        mockMvc.perform(get("/api/v1/subjects/code/{subjectCode}", "SUB-A1B2C3D4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subjectCode").value("SUB-A1B2C3D4"));
    }

    @Test
    @DisplayName("PUT /api/v1/subjects/{id} — updates subject and returns 200 OK")
    void updateSubject_Success() throws Exception {
        UpdateSubjectRequest request = UpdateSubjectRequest.builder()
                .name("Advanced Mathematics")
                .subjectType(SubjectType.CORE)
                .displayOrder(2)
                .build();

        when(subjectService.updateSubject(eq(subjectId), any(UpdateSubjectRequest.class))).thenReturn(sampleDetailResponse);

        mockMvc.perform(put("/api/v1/subjects/{id}", subjectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUBJECT_UPDATED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/subjects/{id}/status — transitions status and returns 200 OK")
    void updateStatus_Success() throws Exception {
        UpdateSubjectStatusRequest request = UpdateSubjectStatusRequest.builder()
                .status(SubjectStatus.INACTIVE)
                .build();

        when(subjectService.updateStatus(eq(subjectId), any(UpdateSubjectStatusRequest.class))).thenReturn(sampleDetailResponse);

        mockMvc.perform(patch("/api/v1/subjects/{id}/status", subjectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUBJECT_STATUS_UPDATED"));
    }

    @Test
    @DisplayName("DELETE /api/v1/subjects/{id} — soft deletes and returns 200 OK")
    void deleteSubject_Success() throws Exception {
        doNothing().when(subjectService).deleteSubject(subjectId);

        mockMvc.perform(delete("/api/v1/subjects/{id}", subjectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUBJECT_DELETED"));
    }
}
