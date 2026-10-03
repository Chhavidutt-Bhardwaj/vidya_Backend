package com.vidya.tenantdata.assignment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.assignment.dto.ClassSubjectAssignmentDetailResponse;
import com.vidya.tenantdata.assignment.dto.ClassSubjectAssignmentResponse;
import com.vidya.tenantdata.assignment.dto.CreateClassSubjectAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.UpdateClassSubjectAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.UpdateClassSubjectAssignmentStatusRequest;
import com.vidya.tenantdata.assignment.enums.ClassSubjectAssignmentStatus;
import com.vidya.tenantdata.assignment.service.ClassSubjectAssignmentService;
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
@DisplayName("ClassSubjectAssignmentController — REST API Unit Tests")
class ClassSubjectAssignmentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ClassSubjectAssignmentService classSubjectAssignmentService;

    @InjectMocks
    private ClassSubjectAssignmentController controller;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final UUID assignmentId = UUID.randomUUID();
    private final UUID academicYearId = UUID.randomUUID();
    private final UUID academicClassId = UUID.randomUUID();
    private final UUID subjectId = UUID.randomUUID();

    private ClassSubjectAssignmentDetailResponse sampleDetail;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        sampleDetail = ClassSubjectAssignmentDetailResponse.builder()
                .id(assignmentId)
                .assignmentCode("CSA-001")
                .academicYearId(academicYearId)
                .academicYearCode("2026-2027")
                .academicClassId(academicClassId)
                .academicClassName("Class 10")
                .subjectId(subjectId)
                .subjectName("Science")
                .subjectCode("SUB-001")
                .isCompulsory(true)
                .displayOrder(1)
                .periodsPerWeek(5)
                .status(ClassSubjectAssignmentStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/class-subject-assignments — Success returns 201")
    void createAssignment_success() throws Exception {
        CreateClassSubjectAssignmentRequest request = new CreateClassSubjectAssignmentRequest();
        request.setAcademicYearId(academicYearId);
        request.setAcademicClassId(academicClassId);
        request.setSubjectId(subjectId);
        request.setIsCompulsory(true);

        when(classSubjectAssignmentService.createAssignment(any(CreateClassSubjectAssignmentRequest.class))).thenReturn(sampleDetail);

        mockMvc.perform(post("/api/v1/class-subject-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("CLASS_SUBJECT_ASSIGNMENT_CREATED"))
                .andExpect(jsonPath("$.data.assignmentCode").value("CSA-001"));
    }

    @Test
    @DisplayName("GET /api/v1/class-subject-assignments — Success returns 200 with paged list")
    void searchAssignments_success() throws Exception {
        ClassSubjectAssignmentResponse item = ClassSubjectAssignmentResponse.builder()
                .id(assignmentId)
                .assignmentCode("CSA-001")
                .academicYearId(academicYearId)
                .academicYearCode("2026-2027")
                .academicClassId(academicClassId)
                .academicClassName("Class 10")
                .subjectId(subjectId)
                .subjectName("Science")
                .isCompulsory(true)
                .displayOrder(1)
                .status(ClassSubjectAssignmentStatus.ACTIVE)
                .build();

        when(classSubjectAssignmentService.searchAssignments(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));

        mockMvc.perform(get("/api/v1/class-subject-assignments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].assignmentCode").value("CSA-001"));
    }

    @Test
    @DisplayName("GET /api/v1/class-subject-assignments/{id} — Success returns 200")
    void getAssignment_success() throws Exception {
        when(classSubjectAssignmentService.getAssignment(assignmentId)).thenReturn(sampleDetail);

        mockMvc.perform(get("/api/v1/class-subject-assignments/{id}", assignmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(assignmentId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/class-subject-assignments/{id} — Not found returns 404")
    void getAssignment_notFound() throws Exception {
        when(classSubjectAssignmentService.getAssignment(assignmentId))
                .thenThrow(new ResourceNotFoundException("ClassSubjectAssignment", assignmentId));

        mockMvc.perform(get("/api/v1/class-subject-assignments/{id}", assignmentId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/v1/class-subject-assignments/{id} — Success returns 200")
    void updateAssignment_success() throws Exception {
        UpdateClassSubjectAssignmentRequest request = new UpdateClassSubjectAssignmentRequest();
        request.setIsCompulsory(false);

        when(classSubjectAssignmentService.updateAssignment(eq(assignmentId), any(UpdateClassSubjectAssignmentRequest.class)))
                .thenReturn(sampleDetail);

        mockMvc.perform(put("/api/v1/class-subject-assignments/{id}", assignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("CLASS_SUBJECT_ASSIGNMENT_UPDATED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/class-subject-assignments/{id}/status — Success returns 200")
    void updateStatus_success() throws Exception {
        UpdateClassSubjectAssignmentStatusRequest request = new UpdateClassSubjectAssignmentStatusRequest();
        request.setStatus(ClassSubjectAssignmentStatus.INACTIVE);

        when(classSubjectAssignmentService.updateStatus(eq(assignmentId), any(UpdateClassSubjectAssignmentStatusRequest.class)))
                .thenReturn(sampleDetail);

        mockMvc.perform(patch("/api/v1/class-subject-assignments/{id}/status", assignmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("CLASS_SUBJECT_ASSIGNMENT_STATUS_UPDATED"));
    }

    @Test
    @DisplayName("DELETE /api/v1/class-subject-assignments/{id} — Success returns 200")
    void deleteAssignment_success() throws Exception {
        doNothing().when(classSubjectAssignmentService).deleteAssignment(assignmentId);

        mockMvc.perform(delete("/api/v1/class-subject-assignments/{id}", assignmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("CLASS_SUBJECT_ASSIGNMENT_DELETED"));
    }
}
