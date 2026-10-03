package com.vidya.tenantdata.student.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.tenantdata.student.dto.AdmissionResponse;
import com.vidya.tenantdata.student.dto.CreateAdmissionRequest;
import com.vidya.tenantdata.student.dto.EnrollmentResponse;
import com.vidya.tenantdata.student.dto.StudentDetailResponse;
import com.vidya.tenantdata.student.entity.StudentEnrollmentStatus;
import com.vidya.tenantdata.student.entity.StudentStatus;
import com.vidya.tenantdata.student.service.StudentAdmissionService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("StudentAdmissionController — REST API Unit Tests")
class StudentAdmissionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private StudentAdmissionService admissionService;

    @InjectMocks
    private StudentAdmissionController admissionController;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(admissionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/tenant/admissions — 201 Created")
    void admitStudent_Success() throws Exception {
        UUID academicYearId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();

        CreateAdmissionRequest request = CreateAdmissionRequest.builder()
                .firstName("Rahul")
                .lastName("Sharma")
                .dateOfBirth(LocalDate.of(2013, 5, 12))
                .admissionNumber("ADM-2026-999")
                .admissionDate(LocalDate.of(2026, 6, 1))
                .academicYearId(academicYearId)
                .academicClassId(classId)
                .sectionId(sectionId)
                .rollNumber("12")
                .autoAssignCompulsorySubjects(true)
                .build();

        AdmissionResponse response = AdmissionResponse.builder()
                .student(StudentDetailResponse.builder()
                        .id(UUID.randomUUID())
                        .studentCode("STU-ADM999")
                        .admissionNumber("ADM-2026-999")
                        .status(StudentStatus.ACTIVE)
                        .build())
                .enrollment(EnrollmentResponse.builder()
                        .id(UUID.randomUUID())
                        .enrollmentCode("ENR-ADM999")
                        .enrollmentStatus(StudentEnrollmentStatus.ACTIVE)
                        .rollNumber("12")
                        .build())
                .enrolledSubjects(List.of())
                .guardians(List.of())
                .build();

        when(admissionService.admitStudent(any(CreateAdmissionRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/tenant/admissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.student.studentCode").value("STU-ADM999"))
                .andExpect(jsonPath("$.data.enrollment.rollNumber").value("12"));
    }
}
