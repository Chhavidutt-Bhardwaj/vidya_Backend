package com.vidya.tenantdata.assignment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.assignment.dto.ClassTeacherAssignmentDetailResponse;
import com.vidya.tenantdata.assignment.dto.ClassTeacherAssignmentResponse;
import com.vidya.tenantdata.assignment.dto.CreateClassTeacherAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.ReplaceClassTeacherRequest;
import com.vidya.tenantdata.assignment.dto.UpdateClassTeacherAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.UpdateClassTeacherAssignmentStatusRequest;
import com.vidya.tenantdata.assignment.enums.ClassTeacherAssignmentStatus;
import com.vidya.tenantdata.assignment.service.ClassTeacherAssignmentService;
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
@DisplayName("ClassTeacherAssignmentController — REST API Unit Tests")
class ClassTeacherAssignmentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ClassTeacherAssignmentService service;

    @InjectMocks
    private ClassTeacherAssignmentController controller;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final UUID assignmentId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID academicYearId = UUID.randomUUID();
    private final UUID academicClassId = UUID.randomUUID();
    private final UUID sectionId = UUID.randomUUID();

    private ClassTeacherAssignmentDetailResponse sampleDetail;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        sampleDetail = ClassTeacherAssignmentDetailResponse.builder()
                .id(assignmentId)
                .assignmentCode("CTA-001")
                .staffId(staffId)
                .staffName("Jane Doe")
                .staffCode("STF-001")
                .academicYearId(academicYearId)
                .academicYearCode("2026-2027")
                .academicClassId(academicClassId)
                .academicClassName("Class 10")
                .sectionId(sectionId)
                .sectionName("Section A")
                .startDate(LocalDate.of(2026, 4, 1))
                .status(ClassTeacherAssignmentStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/class-teacher-assignments — 201 Created")
    void createAssignment() throws Exception {
        CreateClassTeacherAssignmentRequest request = new CreateClassTeacherAssignmentRequest(
                staffId, academicYearId, academicClassId, sectionId, LocalDate.of(2026, 4, 1), "Notes"
        );

        when(service.createAssignment(any(CreateClassTeacherAssignmentRequest.class))).thenReturn(sampleDetail);

        mockMvc.perform(post("/api/v1/class-teacher-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assignmentCode").value("CTA-001"));
    }

    @Test
    @DisplayName("GET /api/v1/class-teacher-assignments/{id} — 200 OK")
    void getById() throws Exception {
        when(service.getAssignment(assignmentId)).thenReturn(sampleDetail);

        mockMvc.perform(get("/api/v1/class-teacher-assignments/{id}", assignmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(assignmentId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/class-teacher-assignments/{id} — 404 Not Found")
    void getById_NotFound() throws Exception {
        when(service.getAssignment(assignmentId))
                .thenThrow(new ResourceNotFoundException("ClassTeacherAssignment", assignmentId));

        mockMvc.perform(get("/api/v1/class-teacher-assignments/{id}", assignmentId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/class-teacher-assignments/code/{code} — 200 OK")
    void getByCode() throws Exception {
        when(service.getAssignmentByCode("CTA-001")).thenReturn(sampleDetail);

        mockMvc.perform(get("/api/v1/class-teacher-assignments/code/{code}", "CTA-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignmentCode").value("CTA-001"));
    }

    @Test
    @DisplayName("GET /api/v1/class-teacher-assignments/section/{sectionId}/current — 200 OK")
    void getCurrentClassTeacher() throws Exception {
        when(service.getCurrentClassTeacher(sectionId, academicYearId)).thenReturn(sampleDetail);

        mockMvc.perform(get("/api/v1/class-teacher-assignments/section/{sectionId}/current", sectionId)
                        .param("academicYearId", academicYearId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.staffName").value("Jane Doe"));
    }

    @Test
    @DisplayName("GET /api/v1/class-teacher-assignments/academic-year/{yearId} — 200 OK")
    void listByYear() throws Exception {
        ClassTeacherAssignmentResponse summary = ClassTeacherAssignmentResponse.builder()
                .id(assignmentId)
                .assignmentCode("CTA-001")
                .staffId(staffId)
                .staffName("Jane Doe")
                .academicYearId(academicYearId)
                .academicClassId(academicClassId)
                .sectionId(sectionId)
                .sectionName("Section A")
                .status(ClassTeacherAssignmentStatus.ACTIVE)
                .build();

        when(service.getAssignmentsByAcademicYear(academicYearId))
                .thenReturn(List.of(summary));

        mockMvc.perform(get("/api/v1/class-teacher-assignments/academic-year/{yearId}", academicYearId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].assignmentCode").value("CTA-001"));
    }

    @Test
    @DisplayName("PUT /api/v1/class-teacher-assignments/{id} — 200 OK")
    void updateAssignment() throws Exception {
        UpdateClassTeacherAssignmentRequest req = new UpdateClassTeacherAssignmentRequest(
                LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), "Updated notes"
        );

        when(service.updateAssignment(eq(assignmentId), any())).thenReturn(sampleDetail);

        mockMvc.perform(put("/api/v1/class-teacher-assignments/{id}", assignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PATCH /api/v1/class-teacher-assignments/{id}/status — 200 OK")
    void updateStatus() throws Exception {
        UpdateClassTeacherAssignmentStatusRequest req = new UpdateClassTeacherAssignmentStatusRequest(
                ClassTeacherAssignmentStatus.COMPLETED
        );

        when(service.updateStatus(eq(assignmentId), any())).thenReturn(sampleDetail);

        mockMvc.perform(patch("/api/v1/class-teacher-assignments/{id}/status", assignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/class-teacher-assignments/{id}/replace — 201 Created")
    void replaceClassTeacher() throws Exception {
        UUID newStaffId = UUID.randomUUID();
        ReplaceClassTeacherRequest req = new ReplaceClassTeacherRequest(
                newStaffId, LocalDate.of(2026, 9, 1), "Handover notes"
        );

        when(service.replaceClassTeacher(eq(assignmentId), any())).thenReturn(sampleDetail);

        mockMvc.perform(post("/api/v1/class-teacher-assignments/{id}/replace", assignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("DELETE /api/v1/class-teacher-assignments/{id} — 200 OK")
    void deleteAssignment() throws Exception {
        doNothing().when(service).deleteAssignment(assignmentId);

        mockMvc.perform(delete("/api/v1/class-teacher-assignments/{id}", assignmentId))
                .andExpect(status().isOk());
    }
}
