package com.vidya.tenantdata.student.controller;

import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.tenantdata.student.dto.ClassSectionStudentResponse;
import com.vidya.tenantdata.student.entity.StudentEnrollmentStatus;
import com.vidya.tenantdata.student.service.StudentEnrollmentService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClassStudentController — REST API Unit Tests")
class ClassStudentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private StudentEnrollmentService enrollmentService;

    @InjectMocks
    private ClassStudentController classStudentController;

    private UUID academicYearId;
    private UUID classId;
    private UUID sectionId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(classStudentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        academicYearId = UUID.randomUUID();
        classId = UUID.randomUUID();
        sectionId = UUID.randomUUID();
    }

    @Test
    @DisplayName("GET /api/v1/tenant/classes/{classId}/sections/{sectionId}/students — 200 OK Roster")
    void getClassSectionStudents_Success() throws Exception {
        ClassSectionStudentResponse studentResponse = ClassSectionStudentResponse.builder()
                .enrollmentId(UUID.randomUUID())
                .studentId(UUID.randomUUID())
                .studentCode("STU-ROSTER1")
                .fullName("Aarav Sharma")
                .rollNumber("1")
                .admissionNumber("ADM-2026-001")
                .enrollmentStatus(StudentEnrollmentStatus.ACTIVE)
                .build();

        when(enrollmentService.getClassSectionStudents(eq(classId), eq(sectionId), eq(academicYearId), eq(StudentEnrollmentStatus.ACTIVE), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(studentResponse)));

        mockMvc.perform(get("/api/v1/tenant/classes/{classId}/sections/{sectionId}/students", classId, sectionId)
                        .param("academicYearId", academicYearId.toString())
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].fullName").value("Aarav Sharma"))
                .andExpect(jsonPath("$.data[0].rollNumber").value("1"));
    }
}
