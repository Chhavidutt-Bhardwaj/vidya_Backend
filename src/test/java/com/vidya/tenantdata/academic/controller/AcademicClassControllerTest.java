package com.vidya.tenantdata.academic.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.tenantdata.academic.dto.AcademicClassResponse;
import com.vidya.tenantdata.academic.dto.CreateAcademicClassRequest;
import com.vidya.tenantdata.academic.enums.AcademicClassStatus;
import com.vidya.tenantdata.academic.service.AcademicClassService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AcademicClassController — REST API Tests")
class AcademicClassControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AcademicClassService academicClassService;

    @InjectMocks
    private AcademicClassController academicClassController;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UUID sampleId;
    private AcademicClassResponse sampleResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(academicClassController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        sampleId = UUID.randomUUID();
        sampleResponse = AcademicClassResponse.builder()
                .id(sampleId)
                .code("CLASS_5")
                .name("Class 5")
                .displayName("Grade 5")
                .sortOrder(5)
                .status(AcademicClassStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/tenant/academic-classes — 201 Created")
    void createAcademicClass_Success() throws Exception {
        CreateAcademicClassRequest request = CreateAcademicClassRequest.builder()
                .code("CLASS_5")
                .name("Class 5")
                .sortOrder(5)
                .build();

        when(academicClassService.createAcademicClass(any(CreateAcademicClassRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/tenant/academic-classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("CLASS_5"));
    }

    @Test
    @DisplayName("GET /api/v1/tenant/academic-classes/{id} — 200 OK")
    void getAcademicClassById_Success() throws Exception {
        when(academicClassService.getAcademicClassById(sampleId)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/tenant/academic-classes/{id}", sampleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(sampleId.toString()));
    }
}
