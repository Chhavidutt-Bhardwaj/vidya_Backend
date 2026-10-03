package com.vidya.tenantdata.academic.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.tenantdata.academic.dto.CreateSectionRequest;
import com.vidya.tenantdata.academic.dto.SectionResponse;
import com.vidya.tenantdata.academic.enums.SectionStatus;
import com.vidya.tenantdata.academic.service.SectionService;
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
@DisplayName("SectionController — REST API Tests")
class SectionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SectionService sectionService;

    @InjectMocks
    private SectionController sectionController;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UUID sampleId;
    private SectionResponse sampleResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(sectionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        sampleId = UUID.randomUUID();
        sampleResponse = SectionResponse.builder()
                .id(sampleId)
                .academicYearId(UUID.randomUUID())
                .academicClassId(UUID.randomUUID())
                .code("A")
                .name("Section A")
                .capacity(40)
                .status(SectionStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/tenant/sections — 201 Created")
    void createSection_Success() throws Exception {
        CreateSectionRequest request = CreateSectionRequest.builder()
                .academicYearId(UUID.randomUUID())
                .academicClassId(UUID.randomUUID())
                .code("A")
                .name("Section A")
                .capacity(40)
                .build();

        when(sectionService.createSection(any(CreateSectionRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/tenant/sections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("A"));
    }

    @Test
    @DisplayName("GET /api/v1/tenant/sections/{id} — 200 OK")
    void getSectionById_Success() throws Exception {
        when(sectionService.getSectionById(sampleId)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/tenant/sections/{id}", sampleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(sampleId.toString()));
    }
}
