package com.vidya.tenantdata.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.user.dto.CreateTenantUserRequest;
import com.vidya.tenantdata.user.dto.TenantUserDetailResponse;
import com.vidya.tenantdata.user.dto.TenantUserProfileDto;
import com.vidya.tenantdata.user.dto.TenantUserResponse;
import com.vidya.tenantdata.user.dto.UpdateTenantUserRequest;
import com.vidya.tenantdata.user.dto.UpdateTenantUserStatusRequest;
import com.vidya.tenantdata.user.entity.TenantUserStatus;
import com.vidya.tenantdata.user.entity.TenantUserType;
import com.vidya.tenantdata.user.service.TenantUserService;
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
@DisplayName("TenantUserController — Web / REST API Unit Tests")
class TenantUserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TenantUserService tenantUserService;

    @InjectMocks
    private TenantUserController tenantUserController;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UUID sampleId;
    private TenantUserDetailResponse detailResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(tenantUserController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        sampleId = UUID.randomUUID();
        TenantUserProfileDto profileDto = TenantUserProfileDto.builder()
                .id(UUID.randomUUID())
                .firstName("Ramesh")
                .lastName("Sharma")
                .displayName("Ramesh Sharma")
                .city("Bengaluru")
                .build();

        detailResponse = TenantUserDetailResponse.builder()
                .id(sampleId)
                .userCode("USR-TEST1234")
                .email("teacher@greenwood.edu.in")
                .userType(TenantUserType.TEACHER)
                .status(TenantUserStatus.ACTIVE)
                .profile(profileDto)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/tenant/users — 201 Created")
    void createTenantUser_Success() throws Exception {
        CreateTenantUserRequest request = CreateTenantUserRequest.builder()
                .email("teacher@greenwood.edu.in")
                .firstName("Ramesh")
                .lastName("Sharma")
                .userType(TenantUserType.TEACHER)
                .build();

        when(tenantUserService.createTenantUser(any(CreateTenantUserRequest.class))).thenReturn(detailResponse);

        mockMvc.perform(post("/api/v1/tenant/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("teacher@greenwood.edu.in"))
                .andExpect(jsonPath("$.data.profile.firstName").value("Ramesh"));
    }

    @Test
    @DisplayName("POST /api/v1/tenant/users — 400 Bad Request on missing email")
    void createTenantUser_ValidationError() throws Exception {
        CreateTenantUserRequest request = CreateTenantUserRequest.builder()
                .firstName("Ramesh")
                .build(); // Missing required email

        mockMvc.perform(post("/api/v1/tenant/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/tenant/users/{id} — 200 OK")
    void getTenantUserById_Success() throws Exception {
        when(tenantUserService.getTenantUserById(sampleId)).thenReturn(detailResponse);

        mockMvc.perform(get("/api/v1/tenant/users/{id}", sampleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(sampleId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/tenant/users/{id} — 404 Not Found")
    void getTenantUserById_NotFound() throws Exception {
        when(tenantUserService.getTenantUserById(sampleId))
                .thenThrow(new ResourceNotFoundException("TenantUser", sampleId));

        mockMvc.perform(get("/api/v1/tenant/users/{id}", sampleId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/tenant/users — 200 OK List with Pagination")
    void listTenantUsers_Success() throws Exception {
        TenantUserResponse summaryResponse = TenantUserResponse.builder()
                .id(sampleId)
                .userCode("USR-TEST1234")
                .email("teacher@greenwood.edu.in")
                .fullName("Ramesh Sharma")
                .userType(TenantUserType.TEACHER)
                .status(TenantUserStatus.ACTIVE)
                .build();

        when(tenantUserService.listTenantUsers(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summaryResponse)));

        mockMvc.perform(get("/api/v1/tenant/users?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].email").value("teacher@greenwood.edu.in"));
    }

    @Test
    @DisplayName("PUT /api/v1/tenant/users/{id} — 200 OK Updated")
    void updateTenantUser_Success() throws Exception {
        UpdateTenantUserRequest request = UpdateTenantUserRequest.builder()
                .firstName("Ramesh Kumar")
                .build();

        when(tenantUserService.updateTenantUser(eq(sampleId), any(UpdateTenantUserRequest.class)))
                .thenReturn(detailResponse);

        mockMvc.perform(put("/api/v1/tenant/users/{id}", sampleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PATCH /api/v1/tenant/users/{id}/status — 200 OK Status Updated")
    void updateTenantUserStatus_Success() throws Exception {
        UpdateTenantUserStatusRequest request = UpdateTenantUserStatusRequest.builder()
                .status(TenantUserStatus.SUSPENDED)
                .build();

        when(tenantUserService.updateTenantUserStatus(eq(sampleId), any(UpdateTenantUserStatusRequest.class)))
                .thenReturn(detailResponse);

        mockMvc.perform(patch("/api/v1/tenant/users/{id}/status", sampleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /api/v1/tenant/users/{id} — 200 OK Deleted")
    void deleteTenantUser_Success() throws Exception {
        doNothing().when(tenantUserService).deleteTenantUser(sampleId);

        mockMvc.perform(delete("/api/v1/tenant/users/{id}", sampleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
