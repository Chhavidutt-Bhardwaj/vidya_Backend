package com.vidya.tenantdata.academic.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.tenantdata.academic.dto.AssignStudentRequest;
import com.vidya.tenantdata.academic.dto.StudentAcademicAssignmentResponse;
import com.vidya.tenantdata.academic.dto.TransferStudentAssignmentRequest;
import com.vidya.tenantdata.academic.enums.StudentAssignmentStatus;
import com.vidya.tenantdata.academic.service.StudentAcademicAssignmentService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("StudentAcademicAssignmentController — REST API Tests")
class StudentAcademicAssignmentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private StudentAcademicAssignmentService assignmentService;

    @InjectMocks
    private StudentAcademicAssignmentController assignmentController;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private UUID studentId;
    private UUID assignmentId;
    private StudentAcademicAssignmentResponse sampleResponse;

    @BeforeEach
    void setUp() {
        org.springframework.http.converter.json.MappingJackson2HttpMessageConverter converter =
                new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(assignmentController)
                .setMessageConverters(converter)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        studentId = UUID.randomUUID();
        assignmentId = UUID.randomUUID();

        sampleResponse = StudentAcademicAssignmentResponse.builder()
                .id(assignmentId)
                .studentId(studentId)
                .rollNumber("12")
                .status(StudentAssignmentStatus.ACTIVE)
                .startDate(LocalDate.of(2026, 4, 1))
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/tenant/students/{studentId}/academic-assignments — 201 Created")
    void assignStudent_Success() throws Exception {
        AssignStudentRequest request = AssignStudentRequest.builder()
                .academicYearId(UUID.randomUUID())
                .academicClassId(UUID.randomUUID())
                .sectionId(UUID.randomUUID())
                .rollNumber("12")
                .build();

        when(assignmentService.assignStudent(eq(studentId), any(AssignStudentRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/tenant/students/{studentId}/academic-assignments", studentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.rollNumber").value("12"));
    }

    @Test
    @DisplayName("GET /api/v1/tenant/students/{studentId}/academic-assignments/current — 200 OK")
    void getCurrentAssignment_Success() throws Exception {
        when(assignmentService.getCurrentAssignment(studentId)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/tenant/students/{studentId}/academic-assignments/current", studentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(assignmentId.toString()));
    }

    @Test
    @DisplayName("PATCH /api/v1/tenant/students/{studentId}/academic-assignments/{assignmentId}/transfer — 200 OK")
    void transferStudent_Success() throws Exception {
        TransferStudentAssignmentRequest request = TransferStudentAssignmentRequest.builder()
                .newAcademicClassId(UUID.randomUUID())
                .newSectionId(UUID.randomUUID())
                .newRollNumber("15")
                .transferDate(LocalDate.of(2026, 7, 1))
                .build();

        when(assignmentService.transferStudent(eq(studentId), eq(assignmentId), any(TransferStudentAssignmentRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(patch("/api/v1/tenant/students/{studentId}/academic-assignments/{assignmentId}/transfer", studentId, assignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
