package com.vidya.tenantdata.subject.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.subject.dto.CreateCurriculumSubjectRequest;
import com.vidya.tenantdata.subject.dto.CurriculumSubjectResponse;
import com.vidya.tenantdata.subject.dto.UpdateCurriculumSubjectRequest;
import com.vidya.tenantdata.subject.enums.SubjectType;
import com.vidya.tenantdata.subject.service.CurriculumSubjectService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("CurriculumSubjectController — REST API Unit Tests")
class CurriculumSubjectControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CurriculumSubjectService curriculumSubjectService;

    @InjectMocks
    private CurriculumSubjectController curriculumSubjectController;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private UUID curriculumId;
    private UUID curriculumSubjectId;
    private UUID subjectId;
    private UUID academicClassId;
    private CurriculumSubjectResponse sampleResponse;

    @BeforeEach
    void setUp() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(curriculumSubjectController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(converter)
                .build();

        curriculumId = UUID.randomUUID();
        curriculumSubjectId = UUID.randomUUID();
        subjectId = UUID.randomUUID();
        academicClassId = UUID.randomUUID();

        sampleResponse = CurriculumSubjectResponse.builder()
                .id(curriculumSubjectId)
                .curriculumId(curriculumId)
                .curriculumCode("CUR-A1B2C3D4")
                .subjectId(subjectId)
                .subjectCode("SUB-A1B2C3D4")
                .subjectName("Mathematics")
                .subjectShortName("MATH")
                .subjectType(SubjectType.CORE)
                .academicClassId(academicClassId)
                .academicClassCode("CLS-10")
                .academicClassName("Class 10")
                .isCompulsory(true)
                .displayOrder(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/curricula/{curriculumId}/subjects — maps subject and returns 201 Created")
    void addSubjectToCurriculum_Success() throws Exception {
        CreateCurriculumSubjectRequest request = CreateCurriculumSubjectRequest.builder()
                .subjectId(subjectId)
                .academicClassId(academicClassId)
                .isCompulsory(true)
                .displayOrder(1)
                .build();

        when(curriculumSubjectService.addSubjectToCurriculum(eq(curriculumId), any(CreateCurriculumSubjectRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/curricula/{curriculumId}/subjects", curriculumId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("CURRICULUM_SUBJECT_ADDED"))
                .andExpect(jsonPath("$.data.subjectName").value("Mathematics"));
    }

    @Test
    @DisplayName("GET /api/v1/curricula/{curriculumId}/subjects — returns 200 OK with list")
    void listCurriculumSubjects_Success() throws Exception {
        when(curriculumSubjectService.listCurriculumSubjects(eq(curriculumId), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleResponse)));

        mockMvc.perform(get("/api/v1/curricula/{curriculumId}/subjects", curriculumId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CURRICULUM_SUBJECTS_RETRIEVED"))
                .andExpect(jsonPath("$.data[0].subjectName").value("Mathematics"));
    }

    @Test
    @DisplayName("GET /api/v1/curricula/{curriculumId}/subjects/{id} — returns 200 OK")
    void getCurriculumSubject_Success() throws Exception {
        when(curriculumSubjectService.getCurriculumSubject(curriculumId, curriculumSubjectId))
                .thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/curricula/{curriculumId}/subjects/{id}", curriculumId, curriculumSubjectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(curriculumSubjectId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/curricula/{curriculumId}/subjects/{id} — returns 404 Not Found when missing")
    void getCurriculumSubject_NotFound() throws Exception {
        when(curriculumSubjectService.getCurriculumSubject(curriculumId, curriculumSubjectId))
                .thenThrow(new ResourceNotFoundException("CurriculumSubject", curriculumSubjectId));

        mockMvc.perform(get("/api/v1/curricula/{curriculumId}/subjects/{id}", curriculumId, curriculumSubjectId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/v1/curricula/{curriculumId}/subjects/{id} — updates mapping and returns 200 OK")
    void updateCurriculumSubject_Success() throws Exception {
        UpdateCurriculumSubjectRequest request = UpdateCurriculumSubjectRequest.builder()
                .isCompulsory(false)
                .displayOrder(5)
                .build();

        when(curriculumSubjectService.updateCurriculumSubject(eq(curriculumId), eq(curriculumSubjectId), any(UpdateCurriculumSubjectRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(put("/api/v1/curricula/{curriculumId}/subjects/{id}", curriculumId, curriculumSubjectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("CURRICULUM_SUBJECT_UPDATED"));
    }

    @Test
    @DisplayName("DELETE /api/v1/curricula/{curriculumId}/subjects/{id} — removes subject from curriculum")
    void removeSubjectFromCurriculum_Success() throws Exception {
        doNothing().when(curriculumSubjectService).removeSubjectFromCurriculum(curriculumId, curriculumSubjectId);

        mockMvc.perform(delete("/api/v1/curricula/{curriculumId}/subjects/{id}", curriculumId, curriculumSubjectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("CURRICULUM_SUBJECT_REMOVED"));
    }
}
