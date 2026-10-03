package com.vidya.tenantdata.student.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.student.dto.CreateStudentRequest;
import com.vidya.tenantdata.student.dto.StudentDetailResponse;
import com.vidya.tenantdata.student.dto.StudentResponse;
import com.vidya.tenantdata.student.dto.UpdateStudentRequest;
import com.vidya.tenantdata.student.dto.UpdateStudentStatusRequest;
import com.vidya.tenantdata.student.entity.StudentStatus;
import com.vidya.tenantdata.student.service.StudentService;
import com.vidya.tenantdata.user.dto.TenantUserDetailResponse;
import com.vidya.tenantdata.user.entity.TenantUserStatus;
import com.vidya.tenantdata.user.entity.TenantUserType;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
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
@DisplayName("StudentController — REST API Unit Tests")
class StudentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private StudentService studentService;

    @InjectMocks
    private StudentController studentController;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private UUID sampleStudentId;
    private UUID sampleUserId;
    private StudentDetailResponse detailResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(studentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        sampleStudentId = UUID.randomUUID();
        sampleUserId = UUID.randomUUID();

        TenantUserDetailResponse userDto = TenantUserDetailResponse.builder()
                .id(sampleUserId)
                .userCode("USR-TEST1234")
                .email("aarav@greenwood.edu.in")
                .userType(TenantUserType.STUDENT)
                .status(TenantUserStatus.ACTIVE)
                .build();

        detailResponse = StudentDetailResponse.builder()
                .id(sampleStudentId)
                .studentCode("STU-TEST1234")
                .admissionNumber("ADM-2026-0042")
                .admissionDate(LocalDate.of(2026, 6, 1))
                .status(StudentStatus.ACTIVE)
                .user(userDto)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/tenant/students — 201 Created")
    void createStudent_Success() throws Exception {
        CreateStudentRequest request = CreateStudentRequest.builder()
                .tenantUserId(sampleUserId)
                .admissionNumber("ADM-2026-0042")
                .admissionDate(LocalDate.of(2026, 6, 1))
                .build();

        when(studentService.createStudent(any(CreateStudentRequest.class))).thenReturn(detailResponse);

        mockMvc.perform(post("/api/v1/tenant/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.admissionNumber").value("ADM-2026-0042"))
                .andExpect(jsonPath("$.data.studentCode").value("STU-TEST1234"));
    }

    @Test
    @DisplayName("POST /api/v1/tenant/students — 400 Bad Request on missing admission number")
    void createStudent_ValidationError() throws Exception {
        CreateStudentRequest request = CreateStudentRequest.builder()
                .tenantUserId(sampleUserId)
                .admissionDate(LocalDate.of(2026, 6, 1))
                .build(); // Missing admissionNumber

        mockMvc.perform(post("/api/v1/tenant/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/tenant/students/{id} — 200 OK")
    void getStudentById_Success() throws Exception {
        when(studentService.getStudentById(sampleStudentId)).thenReturn(detailResponse);

        mockMvc.perform(get("/api/v1/tenant/students/{id}", sampleStudentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(sampleStudentId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/tenant/students/{id} — 404 Not Found")
    void getStudentById_NotFound() throws Exception {
        when(studentService.getStudentById(sampleStudentId))
                .thenThrow(new ResourceNotFoundException("Student", sampleStudentId));

        mockMvc.perform(get("/api/v1/tenant/students/{id}", sampleStudentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/tenant/students/code/{studentCode} — 200 OK")
    void getStudentByCode_Success() throws Exception {
        when(studentService.getStudentByStudentCode("STU-TEST1234")).thenReturn(detailResponse);

        mockMvc.perform(get("/api/v1/tenant/students/code/{studentCode}", "STU-TEST1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.studentCode").value("STU-TEST1234"));
    }

    @Test
    @DisplayName("GET /api/v1/tenant/students — 200 OK List with Pagination")
    void listStudents_Success() throws Exception {
        StudentResponse summaryResponse = StudentResponse.builder()
                .id(sampleStudentId)
                .studentCode("STU-TEST1234")
                .admissionNumber("ADM-2026-0042")
                .admissionDate(LocalDate.of(2026, 6, 1))
                .status(StudentStatus.ACTIVE)
                .fullName("Aarav Sharma")
                .email("aarav@greenwood.edu.in")
                .build();

        when(studentService.searchStudents(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summaryResponse)));

        mockMvc.perform(get("/api/v1/tenant/students?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].admissionNumber").value("ADM-2026-0042"));
    }

    @Test
    @DisplayName("PUT /api/v1/tenant/students/{id} — 200 OK Updated")
    void updateStudent_Success() throws Exception {
        UpdateStudentRequest request = UpdateStudentRequest.builder()
                .admissionNumber("ADM-2026-0043")
                .build();

        when(studentService.updateStudent(eq(sampleStudentId), any(UpdateStudentRequest.class)))
                .thenReturn(detailResponse);

        mockMvc.perform(put("/api/v1/tenant/students/{id}", sampleStudentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PATCH /api/v1/tenant/students/{id}/status — 200 OK Status Updated")
    void updateStudentStatus_Success() throws Exception {
        UpdateStudentStatusRequest request = UpdateStudentStatusRequest.builder()
                .status(StudentStatus.SUSPENDED)
                .reason("Temporary leave")
                .build();

        when(studentService.updateStudentStatus(eq(sampleStudentId), any(UpdateStudentStatusRequest.class)))
                .thenReturn(detailResponse);

        mockMvc.perform(patch("/api/v1/tenant/students/{id}/status", sampleStudentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /api/v1/tenant/students/{id} — 200 OK Deleted")
    void deleteStudent_Success() throws Exception {
        doNothing().when(studentService).deleteStudent(sampleStudentId);

        mockMvc.perform(delete("/api/v1/tenant/students/{id}", sampleStudentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
