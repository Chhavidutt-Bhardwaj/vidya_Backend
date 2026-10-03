package com.vidya.tenantdata.academic.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.tenantdata.academic.dto.AcademicYearResponse;
import com.vidya.tenantdata.academic.dto.CreateAcademicYearRequest;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.service.AcademicYearService;
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

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AcademicYearController — REST API Tests")
class AcademicYearControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AcademicYearService academicYearService;

    @InjectMocks
    private AcademicYearController academicYearController;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private UUID sampleId;
    private AcademicYearResponse sampleResponse;

    @BeforeEach
    void setUp() {
        org.springframework.http.converter.json.MappingJackson2HttpMessageConverter converter =
                new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(academicYearController)
                .setMessageConverters(converter)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        sampleId = UUID.randomUUID();
        sampleResponse = AcademicYearResponse.builder()
                .id(sampleId)
                .code("AY2026-27")
                .name("Academic Year 2026-2027")
                .startDate(LocalDate.of(2026, 4, 1))
                .endDate(LocalDate.of(2027, 3, 31))
                .isCurrent(true)
                .status(AcademicYearStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/tenant/academic-years — 201 Created")
    void createAcademicYear_Success() throws Exception {
        CreateAcademicYearRequest request = CreateAcademicYearRequest.builder()
                .code("AY2026-27")
                .name("Academic Year 2026-2027")
                .startDate(LocalDate.of(2026, 4, 1))
                .endDate(LocalDate.of(2027, 3, 31))
                .isCurrent(true)
                .build();

        when(academicYearService.createAcademicYear(any(CreateAcademicYearRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/tenant/academic-years")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("AY2026-27"));
    }

    @Test
    @DisplayName("GET /api/v1/tenant/academic-years/{id} — 200 OK")
    void getAcademicYearById_Success() throws Exception {
        when(academicYearService.getAcademicYearById(sampleId)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/tenant/academic-years/{id}", sampleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(sampleId.toString()));
    }

    @Test
    @DisplayName("PATCH /api/v1/tenant/academic-years/{id}/current — 200 OK")
    void setCurrentAcademicYear_Success() throws Exception {
        when(academicYearService.setCurrentAcademicYear(sampleId)).thenReturn(sampleResponse);

        mockMvc.perform(patch("/api/v1/tenant/academic-years/{id}/current", sampleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
