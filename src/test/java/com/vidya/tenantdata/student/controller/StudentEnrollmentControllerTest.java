package com.vidya.tenantdata.student.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.tenantdata.student.dto.CreateEnrollmentRequest;
import com.vidya.tenantdata.student.dto.CurrentEnrollmentResponse;
import com.vidya.tenantdata.student.dto.EnrollmentDetailResponse;
import com.vidya.tenantdata.student.dto.TransferEnrollmentRequest;
import com.vidya.tenantdata.student.dto.UpdateEnrollmentRequest;
import com.vidya.tenantdata.student.dto.WithdrawEnrollmentRequest;
import com.vidya.tenantdata.student.entity.StudentEnrollmentStatus;
import com.vidya.tenantdata.student.service.StudentEnrollmentService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("StudentEnrollmentController — REST API Unit Tests")
class StudentEnrollmentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private StudentEnrollmentService enrollmentService;

    @InjectMocks
    private StudentEnrollmentController enrollmentController;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private UUID sampleStudentId;
    private UUID sampleEnrollmentId;
    private EnrollmentDetailResponse sampleResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(enrollmentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        sampleStudentId = UUID.randomUUID();
        sampleEnrollmentId = UUID.randomUUID();

        sampleResponse = EnrollmentDetailResponse.builder()
                .id(sampleEnrollmentId)
                .enrollmentCode("ENR-TEST1234")
                .academicYearId(UUID.randomUUID())
                .academicYearName("Academic Year 2026-2027")
                .academicClassId(UUID.randomUUID())
                .academicClassName("Class 10")
                .sectionId(UUID.randomUUID())
                .sectionName("Section A")
                .rollNumber("1")
                .enrollmentStatus(StudentEnrollmentStatus.ACTIVE)
                .admissionDate(LocalDate.of(2026, 6, 1))
                .enrollmentDate(LocalDate.of(2026, 6, 1))
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/tenant/students/{studentId}/enrollments — 201 Created")
    void createEnrollment_Success() throws Exception {
        CreateEnrollmentRequest request = CreateEnrollmentRequest.builder()
                .academicYearId(UUID.randomUUID())
                .academicClassId(UUID.randomUUID())
                .sectionId(UUID.randomUUID())
                .rollNumber("1")
                .enrollmentDate(LocalDate.of(2026, 6, 1))
                .build();

        when(enrollmentService.createEnrollment(eq(sampleStudentId), any(CreateEnrollmentRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/tenant/students/{studentId}/enrollments", sampleStudentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.enrollmentCode").value("ENR-TEST1234"))
                .andExpect(jsonPath("$.data.rollNumber").value("1"));
    }

    @Test
    @DisplayName("GET /api/v1/tenant/enrollments/{id} — 200 OK")
    void getEnrollmentById_Success() throws Exception {
        when(enrollmentService.getEnrollmentById(sampleEnrollmentId)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/tenant/enrollments/{id}", sampleEnrollmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(sampleEnrollmentId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/tenant/students/{studentId}/current-enrollment — 200 OK")
    void getCurrentEnrollment_Success() throws Exception {
        CurrentEnrollmentResponse currentResponse = CurrentEnrollmentResponse.builder()
                .enrollmentId(sampleEnrollmentId)
                .academicClassName("Class 10")
                .sectionName("Section A")
                .rollNumber("1")
                .enrollmentStatus(StudentEnrollmentStatus.ACTIVE)
                .build();

        when(enrollmentService.getCurrentEnrollment(sampleStudentId)).thenReturn(currentResponse);

        mockMvc.perform(get("/api/v1/tenant/students/{studentId}/current-enrollment", sampleStudentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.academicClassName").value("Class 10"));
    }

    @Test
    @DisplayName("POST /api/v1/tenant/enrollments/{id}/transfer — 200 OK")
    void transferEnrollment_Success() throws Exception {
        TransferEnrollmentRequest request = TransferEnrollmentRequest.builder()
                .newAcademicClassId(UUID.randomUUID())
                .newSectionId(UUID.randomUUID())
                .newRollNumber("2")
                .remarks("Reallocation")
                .build();

        when(enrollmentService.transferStudent(eq(sampleEnrollmentId), any(TransferEnrollmentRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/tenant/enrollments/{id}/transfer", sampleEnrollmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/tenant/enrollments/{id}/withdraw — 200 OK")
    void withdrawEnrollment_Success() throws Exception {
        WithdrawEnrollmentRequest request = WithdrawEnrollmentRequest.builder()
                .withdrawalDate(LocalDate.of(2026, 8, 1))
                .withdrawalReason("Relocation")
                .build();

        when(enrollmentService.withdrawStudent(eq(sampleEnrollmentId), any(WithdrawEnrollmentRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/tenant/enrollments/{id}/withdraw", sampleEnrollmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
