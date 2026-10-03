package com.vidya.tenantdata.student.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.tenantdata.student.dto.CreateStudentGuardianRequest;
import com.vidya.tenantdata.student.dto.StudentGuardianResponse;
import com.vidya.tenantdata.student.dto.UpdateStudentGuardianRequest;
import com.vidya.tenantdata.student.entity.GuardianRelationshipType;
import com.vidya.tenantdata.student.entity.GuardianStatus;
import com.vidya.tenantdata.student.service.StudentGuardianService;
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
@DisplayName("StudentGuardianController — REST API Unit Tests")
class StudentGuardianControllerTest {

    private MockMvc mockMvc;

    @Mock
    private StudentGuardianService guardianService;

    @InjectMocks
    private StudentGuardianController guardianController;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UUID sampleStudentId;
    private UUID sampleGuardianId;
    private StudentGuardianResponse sampleResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(guardianController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        sampleStudentId = UUID.randomUUID();
        sampleGuardianId = UUID.randomUUID();

        sampleResponse = StudentGuardianResponse.builder()
                .id(sampleGuardianId)
                .guardianCode("GDN-TEST1234")
                .studentId(sampleStudentId)
                .guardianName("Rajesh Sharma")
                .relationshipType(GuardianRelationshipType.FATHER)
                .isPrimary(true)
                .isEmergencyContact(true)
                .status(GuardianStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/tenant/students/{studentId}/guardians — 201 Created")
    void createGuardian_Success() throws Exception {
        CreateStudentGuardianRequest request = CreateStudentGuardianRequest.builder()
                .guardianName("Rajesh Sharma")
                .relationshipType(GuardianRelationshipType.FATHER)
                .isPrimary(true)
                .isEmergencyContact(true)
                .build();

        when(guardianService.createGuardian(eq(sampleStudentId), any(CreateStudentGuardianRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/tenant/students/{studentId}/guardians", sampleStudentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.guardianName").value("Rajesh Sharma"));
    }

    @Test
    @DisplayName("GET /api/v1/tenant/students/{studentId}/guardians — 200 OK")
    void getGuardians_Success() throws Exception {
        when(guardianService.getGuardiansByStudentId(sampleStudentId)).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/v1/tenant/students/{studentId}/guardians", sampleStudentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].guardianCode").value("GDN-TEST1234"));
    }

    @Test
    @DisplayName("PUT /api/v1/tenant/students/{studentId}/guardians/{guardianId} — 200 OK")
    void updateGuardian_Success() throws Exception {
        UpdateStudentGuardianRequest request = UpdateStudentGuardianRequest.builder()
                .relationshipType(GuardianRelationshipType.LEGAL_GUARDIAN)
                .build();

        when(guardianService.updateGuardian(eq(sampleStudentId), eq(sampleGuardianId), any(UpdateStudentGuardianRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(put("/api/v1/tenant/students/{studentId}/guardians/{guardianId}", sampleStudentId, sampleGuardianId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /api/v1/tenant/students/{studentId}/guardians/{guardianId} — 200 OK")
    void deleteGuardian_Success() throws Exception {
        doNothing().when(guardianService).deleteGuardian(sampleStudentId, sampleGuardianId);

        mockMvc.perform(delete("/api/v1/tenant/students/{studentId}/guardians/{guardianId}", sampleStudentId, sampleGuardianId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
