package com.vidya.tenantdata.student.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.tenantdata.student.dto.BatchAssignSubjectsRequest;
import com.vidya.tenantdata.student.dto.CreateStudentSubjectEnrollmentRequest;
import com.vidya.tenantdata.student.dto.StudentSubjectEnrollmentResponse;
import com.vidya.tenantdata.student.dto.UpdateStudentSubjectEnrollmentRequest;
import com.vidya.tenantdata.student.entity.StudentSubjectEnrollmentStatus;
import com.vidya.tenantdata.student.service.StudentSubjectEnrollmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
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
@DisplayName("StudentSubjectEnrollmentController — REST API Unit Tests")
class StudentSubjectEnrollmentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private StudentSubjectEnrollmentService subjectEnrollmentService;

    @InjectMocks
    private StudentSubjectEnrollmentController subjectEnrollmentController;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private UUID sampleEnrollmentId;
    private UUID sampleSubjectEnrollmentId;
    private StudentSubjectEnrollmentResponse sampleResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(subjectEnrollmentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        sampleEnrollmentId = UUID.randomUUID();
        sampleSubjectEnrollmentId = UUID.randomUUID();

        sampleResponse = StudentSubjectEnrollmentResponse.builder()
                .id(sampleSubjectEnrollmentId)
                .subjectEnrollmentCode("SSE-TEST1234")
                .enrollmentId(sampleEnrollmentId)
                .subjectId(UUID.randomUUID())
                .subjectName("Mathematics")
                .subjectCode("MATH-10")
                .isCompulsory(true)
                .status(StudentSubjectEnrollmentStatus.ACTIVE)
                .effectiveFrom(LocalDate.of(2026, 6, 1))
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/tenant/enrollments/{enrollmentId}/subjects — 201 Created")
    void assignSubject_Success() throws Exception {
        CreateStudentSubjectEnrollmentRequest request = CreateStudentSubjectEnrollmentRequest.builder()
                .subjectId(UUID.randomUUID())
                .effectiveFrom(LocalDate.of(2026, 6, 1))
                .build();

        when(subjectEnrollmentService.enrollSubject(eq(sampleEnrollmentId), any(CreateStudentSubjectEnrollmentRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/tenant/enrollments/{enrollmentId}/subjects", sampleEnrollmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.subjectName").value("Mathematics"));
    }

    @Test
    @DisplayName("GET /api/v1/tenant/enrollments/{enrollmentId}/subjects — 200 OK")
    void getSubjects_Success() throws Exception {
        when(subjectEnrollmentService.getEnrolledSubjects(sampleEnrollmentId))
                .thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/v1/tenant/enrollments/{enrollmentId}/subjects", sampleEnrollmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].subjectName").value("Mathematics"));
    }

    @Test
    @DisplayName("PUT /api/v1/tenant/enrollments/{enrollmentId}/subjects/{subjectEnrollmentId} — 200 OK")
    void updateSubject_Success() throws Exception {
        UpdateStudentSubjectEnrollmentRequest request = UpdateStudentSubjectEnrollmentRequest.builder()
                .status(StudentSubjectEnrollmentStatus.DROPPED)
                .remarks("Switched elective")
                .build();

        when(subjectEnrollmentService.updateSubjectEnrollment(eq(sampleEnrollmentId), eq(sampleSubjectEnrollmentId), any(UpdateStudentSubjectEnrollmentRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(put("/api/v1/tenant/enrollments/{enrollmentId}/subjects/{subjectEnrollmentId}", sampleEnrollmentId, sampleSubjectEnrollmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /api/v1/tenant/enrollments/{enrollmentId}/subjects/{subjectEnrollmentId} — 200 OK")
    void removeSubject_Success() throws Exception {
        doNothing().when(subjectEnrollmentService).removeSubjectEnrollment(sampleEnrollmentId, sampleSubjectEnrollmentId);

        mockMvc.perform(delete("/api/v1/tenant/enrollments/{enrollmentId}/subjects/{subjectEnrollmentId}", sampleEnrollmentId, sampleSubjectEnrollmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
