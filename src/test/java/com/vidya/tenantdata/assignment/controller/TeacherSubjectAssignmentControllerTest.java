package com.vidya.tenantdata.assignment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.assignment.dto.CreateTeacherAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.TeacherAssignmentDetailResponse;
import com.vidya.tenantdata.assignment.dto.TeacherAssignmentResponse;
import com.vidya.tenantdata.assignment.dto.UpdateTeacherAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.UpdateTeacherAssignmentStatusRequest;
import com.vidya.tenantdata.assignment.enums.TeacherAssignmentStatus;
import com.vidya.tenantdata.assignment.service.TeacherSubjectAssignmentService;
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
@DisplayName("TeacherSubjectAssignmentController — REST API Unit Tests")
class TeacherSubjectAssignmentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TeacherSubjectAssignmentService teacherAssignmentService;

    @InjectMocks
    private TeacherSubjectAssignmentController controller;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final UUID assignmentId = UUID.randomUUID();
    private final UUID teacherId = UUID.randomUUID();
    private final UUID subjectId = UUID.randomUUID();
    private final UUID academicYearId = UUID.randomUUID();
    private final UUID academicClassId = UUID.randomUUID();

    private TeacherAssignmentDetailResponse sampleDetail;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        sampleDetail = TeacherAssignmentDetailResponse.builder()
                .id(assignmentId)
                .assignmentCode("TSA-001")
                .teacherId(teacherId)
                .teacherName("Jane Doe")
                .teacherStaffCode("STF-001")
                .subjectId(subjectId)
                .subjectName("Mathematics")
                .subjectCode("SUB-001")
                .academicYearId(academicYearId)
                .academicYearCode("2026-2027")
                .academicClassId(academicClassId)
                .academicClassName("Class 10")
                .isPrimary(true)
                .startDate(LocalDate.of(2026, 4, 1))
                .status(TeacherAssignmentStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/teacher-assignments — Success returns 201")
    void createAssignment_success() throws Exception {
        CreateTeacherAssignmentRequest request = new CreateTeacherAssignmentRequest();
        request.setTeacherId(teacherId);
        request.setSubjectId(subjectId);
        request.setAcademicYearId(academicYearId);
        request.setAcademicClassId(academicClassId);
        request.setIsPrimary(true);

        when(teacherAssignmentService.createAssignment(any(CreateTeacherAssignmentRequest.class))).thenReturn(sampleDetail);

        mockMvc.perform(post("/api/v1/teacher-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("TEACHER_ASSIGNMENT_CREATED"))
                .andExpect(jsonPath("$.data.assignmentCode").value("TSA-001"));
    }

    @Test
    @DisplayName("GET /api/v1/teacher-assignments — Success returns 200 with paged list")
    void searchAssignments_success() throws Exception {
        TeacherAssignmentResponse item = TeacherAssignmentResponse.builder()
                .id(assignmentId)
                .assignmentCode("TSA-001")
                .teacherId(teacherId)
                .teacherName("Jane Doe")
                .teacherStaffCode("STF-001")
                .subjectId(subjectId)
                .subjectName("Mathematics")
                .academicYearId(academicYearId)
                .academicYearCode("2026-2027")
                .academicClassId(academicClassId)
                .academicClassName("Class 10")
                .isPrimary(true)
                .status(TeacherAssignmentStatus.ACTIVE)
                .build();

        when(teacherAssignmentService.searchAssignments(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));

        mockMvc.perform(get("/api/v1/teacher-assignments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].assignmentCode").value("TSA-001"));
    }

    @Test
    @DisplayName("GET /api/v1/teacher-assignments/{id} — Success returns 200")
    void getAssignment_success() throws Exception {
        when(teacherAssignmentService.getAssignment(assignmentId)).thenReturn(sampleDetail);

        mockMvc.perform(get("/api/v1/teacher-assignments/{id}", assignmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(assignmentId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/teacher-assignments/{id} — Not found returns 404")
    void getAssignment_notFound() throws Exception {
        when(teacherAssignmentService.getAssignment(assignmentId))
                .thenThrow(new ResourceNotFoundException("TeacherSubjectAssignment", assignmentId));

        mockMvc.perform(get("/api/v1/teacher-assignments/{id}", assignmentId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/v1/teacher-assignments/{id} — Success returns 200")
    void updateAssignment_success() throws Exception {
        UpdateTeacherAssignmentRequest request = new UpdateTeacherAssignmentRequest();
        request.setIsPrimary(false);

        when(teacherAssignmentService.updateAssignment(eq(assignmentId), any(UpdateTeacherAssignmentRequest.class)))
                .thenReturn(sampleDetail);

        mockMvc.perform(put("/api/v1/teacher-assignments/{id}", assignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("TEACHER_ASSIGNMENT_UPDATED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/teacher-assignments/{id}/status — Success returns 200")
    void updateStatus_success() throws Exception {
        UpdateTeacherAssignmentStatusRequest request = new UpdateTeacherAssignmentStatusRequest();
        request.setStatus(TeacherAssignmentStatus.COMPLETED);

        when(teacherAssignmentService.updateStatus(eq(assignmentId), any(UpdateTeacherAssignmentStatusRequest.class)))
                .thenReturn(sampleDetail);

        mockMvc.perform(patch("/api/v1/teacher-assignments/{id}/status", assignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("TEACHER_ASSIGNMENT_STATUS_UPDATED"));
    }

    @Test
    @DisplayName("DELETE /api/v1/teacher-assignments/{id} — Success returns 200")
    void deleteAssignment_success() throws Exception {
        doNothing().when(teacherAssignmentService).deleteAssignment(assignmentId);

        mockMvc.perform(delete("/api/v1/teacher-assignments/{id}", assignmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("TEACHER_ASSIGNMENT_DELETED"));
    }
}
