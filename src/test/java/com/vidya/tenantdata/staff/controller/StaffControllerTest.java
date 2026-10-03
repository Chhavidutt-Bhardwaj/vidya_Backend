package com.vidya.tenantdata.staff.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.staff.dto.CreateStaffRequest;
import com.vidya.tenantdata.staff.dto.StaffDetailResponse;
import com.vidya.tenantdata.staff.dto.StaffResponse;
import com.vidya.tenantdata.staff.dto.UpdateStaffRequest;
import com.vidya.tenantdata.staff.dto.UpdateStaffStatusRequest;
import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.enums.StaffType;
import com.vidya.tenantdata.staff.service.StaffService;
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
@DisplayName("StaffController — REST API Unit Tests")
class StaffControllerTest {

    private MockMvc mockMvc;

    @Mock
    private StaffService staffService;

    @InjectMocks
    private StaffController staffController;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private UUID sampleStaffId;
    private UUID sampleUserId;
    private StaffDetailResponse detailResponse;

    @BeforeEach
    void setUp() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(staffController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(converter)
                .build();

        sampleStaffId = UUID.randomUUID();
        sampleUserId = UUID.randomUUID();

        TenantUserDetailResponse userDto = TenantUserDetailResponse.builder()
                .id(sampleUserId)
                .userCode("USR-STAFF1234")
                .email("priya.sharma@school.edu.in")
                .userType(TenantUserType.STAFF)
                .status(TenantUserStatus.ACTIVE)
                .build();

        detailResponse = StaffDetailResponse.builder()
                .id(sampleStaffId)
                .staffCode("STF-TEST1234")
                .employeeCode("EMP-001")
                .staffType(StaffType.TEACHER)
                .employmentStatus(EmploymentStatus.ACTIVE)
                .designation("Senior Teacher")
                .department("Mathematics")
                .joiningDate(LocalDate.of(2026, 4, 1))
                .user(userDto)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/staff — 201 Created")
    void createStaff_Success() throws Exception {
        CreateStaffRequest request = CreateStaffRequest.builder()
                .tenantUserId(sampleUserId)
                .employeeCode("EMP-001")
                .joiningDate(LocalDate.of(2026, 4, 1))
                .staffType(StaffType.TEACHER)
                .designation("Senior Teacher")
                .department("Mathematics")
                .build();

        when(staffService.createStaff(any(CreateStaffRequest.class))).thenReturn(detailResponse);

        mockMvc.perform(post("/api/v1/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.staffCode").value("STF-TEST1234"))
                .andExpect(jsonPath("$.data.staffType").value("TEACHER"));
    }

    @Test
    @DisplayName("POST /api/v1/staff — 400 Bad Request on missing tenantUserId")
    void createStaff_ValidationError_MissingTenantUserId() throws Exception {
        CreateStaffRequest request = CreateStaffRequest.builder()
                .staffType(StaffType.TEACHER)
                .build(); // Missing tenantUserId

        mockMvc.perform(post("/api/v1/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/staff — 400 Bad Request on missing staffType")
    void createStaff_ValidationError_MissingStaffType() throws Exception {
        CreateStaffRequest request = CreateStaffRequest.builder()
                .tenantUserId(sampleUserId)
                .build(); // Missing staffType

        mockMvc.perform(post("/api/v1/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/staff/{id} — 200 OK")
    void getStaffById_Success() throws Exception {
        when(staffService.getStaffById(sampleStaffId)).thenReturn(detailResponse);

        mockMvc.perform(get("/api/v1/staff/{id}", sampleStaffId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(sampleStaffId.toString()))
                .andExpect(jsonPath("$.data.staffCode").value("STF-TEST1234"));
    }

    @Test
    @DisplayName("GET /api/v1/staff/{id} — 404 Not Found")
    void getStaffById_NotFound() throws Exception {
        when(staffService.getStaffById(sampleStaffId))
                .thenThrow(new ResourceNotFoundException("Staff", sampleStaffId));

        mockMvc.perform(get("/api/v1/staff/{id}", sampleStaffId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/staff/code/{staffCode} — 200 OK")
    void getStaffByCode_Success() throws Exception {
        when(staffService.getStaffByStaffCode("STF-TEST1234")).thenReturn(detailResponse);

        mockMvc.perform(get("/api/v1/staff/code/{staffCode}", "STF-TEST1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.staffCode").value("STF-TEST1234"));
    }

    @Test
    @DisplayName("GET /api/v1/staff/code/{staffCode} — 404 Not Found")
    void getStaffByCode_NotFound() throws Exception {
        when(staffService.getStaffByStaffCode("STF-UNKNOWN"))
                .thenThrow(new ResourceNotFoundException("Staff", "STF-UNKNOWN"));

        mockMvc.perform(get("/api/v1/staff/code/{staffCode}", "STF-UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/staff/tenant-user/{tenantUserId} — 200 OK")
    void getStaffByTenantUserId_Success() throws Exception {
        when(staffService.getStaffByTenantUserId(sampleUserId)).thenReturn(detailResponse);

        mockMvc.perform(get("/api/v1/staff/tenant-user/{tenantUserId}", sampleUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/staff/tenant-user/{tenantUserId} — 404 Not Found")
    void getStaffByTenantUserId_NotFound() throws Exception {
        when(staffService.getStaffByTenantUserId(sampleUserId))
                .thenThrow(new ResourceNotFoundException("Staff", sampleUserId));

        mockMvc.perform(get("/api/v1/staff/tenant-user/{tenantUserId}", sampleUserId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/staff — 200 OK paginated list")
    void listStaff_Success() throws Exception {
        StaffResponse summary = StaffResponse.builder()
                .id(sampleStaffId)
                .staffCode("STF-TEST1234")
                .tenantUserId(sampleUserId)
                .staffType(StaffType.TEACHER)
                .employmentStatus(EmploymentStatus.ACTIVE)
                .fullName("Priya Sharma")
                .email("priya.sharma@school.edu.in")
                .build();

        when(staffService.searchStaff(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summary)));

        mockMvc.perform(get("/api/v1/staff?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].staffCode").value("STF-TEST1234"));
    }

    @Test
    @DisplayName("PUT /api/v1/staff/{id} — 200 OK Updated")
    void updateStaff_Success() throws Exception {
        UpdateStaffRequest request = UpdateStaffRequest.builder()
                .designation("Head of Department")
                .department("Science")
                .build();

        when(staffService.updateStaff(eq(sampleStaffId), any(UpdateStaffRequest.class)))
                .thenReturn(detailResponse);

        mockMvc.perform(put("/api/v1/staff/{id}", sampleStaffId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PATCH /api/v1/staff/{id}/status — 200 OK Status Updated")
    void updateStaffStatus_Success() throws Exception {
        UpdateStaffStatusRequest request = UpdateStaffStatusRequest.builder()
                .status(EmploymentStatus.SUSPENDED)
                .reason("Under investigation")
                .build();

        when(staffService.updateStaffStatus(eq(sampleStaffId), any(UpdateStaffStatusRequest.class)))
                .thenReturn(detailResponse);

        mockMvc.perform(patch("/api/v1/staff/{id}/status", sampleStaffId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PATCH /api/v1/staff/{id}/status — 400 Bad Request on missing status")
    void updateStaffStatus_ValidationError() throws Exception {
        UpdateStaffStatusRequest request = new UpdateStaffStatusRequest(); // Missing status

        mockMvc.perform(patch("/api/v1/staff/{id}/status", sampleStaffId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("DELETE /api/v1/staff/{id} — 200 OK Deleted")
    void deleteStaff_Success() throws Exception {
        doNothing().when(staffService).deleteStaff(sampleStaffId);

        mockMvc.perform(delete("/api/v1/staff/{id}", sampleStaffId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /api/v1/staff/{id} — 404 Not Found")
    void deleteStaff_NotFound() throws Exception {
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Staff", sampleStaffId))
                .when(staffService).deleteStaff(sampleStaffId);

        mockMvc.perform(delete("/api/v1/staff/{id}", sampleStaffId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
