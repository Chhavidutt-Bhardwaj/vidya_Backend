package com.vidya.master.community.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.master.community.dto.AddCommunityMemberRequest;
import com.vidya.master.community.dto.CommunityMemberResponse;
import com.vidya.master.community.dto.CommunityResponse;
import com.vidya.master.community.dto.CreateCommunityRequest;
import com.vidya.master.community.dto.UpdateCommunityMemberRoleRequest;
import com.vidya.master.community.dto.UpdateCommunityRequest;
import com.vidya.master.community.entity.CommunityMemberRole;
import com.vidya.master.community.entity.CommunityMemberStatus;
import com.vidya.master.community.entity.CommunityStatus;
import com.vidya.master.community.entity.CommunityType;
import com.vidya.master.community.service.CommunityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
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
class CommunityControllerTest {

    @Mock
    private CommunityService communityService;

    @InjectMocks
    private CommunityController communityController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID communityId;
    private CommunityResponse sampleCommunityResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(communityController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        communityId = UUID.randomUUID();
        sampleCommunityResponse = new CommunityResponse(
                communityId,
                "COM-12345678",
                "EduPartners Agency",
                "Agency description",
                CommunityType.FIELD_AGENCY,
                CommunityStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
                2L
        );
    }

    @Test
    @DisplayName("POST /api/v1/communities should return 201 Created on valid input")
    void createCommunity_ValidInput_Returns201() throws Exception {
        CreateCommunityRequest request = new CreateCommunityRequest(
                "EduPartners Agency",
                "Agency description",
                CommunityType.FIELD_AGENCY
        );

        when(communityService.createCommunity(any(CreateCommunityRequest.class))).thenReturn(sampleCommunityResponse);

        mockMvc.perform(post("/api/v1/communities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.code", equalTo("COMMUNITY_CREATED")))
                .andExpect(jsonPath("$.data.name", equalTo("EduPartners Agency")))
                .andExpect(jsonPath("$.data.communityCode", equalTo("COM-12345678")));
    }

    @Test
    @DisplayName("POST /api/v1/communities with invalid input should return 400 Bad Request")
    void createCommunity_InvalidInput_Returns400() throws Exception {
        // Missing name and type
        CreateCommunityRequest invalidRequest = new CreateCommunityRequest(
                "",
                null,
                null
        );

        mockMvc.perform(post("/api/v1/communities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("POST /api/v1/communities with duplicate name should return 409 Conflict")
    void createCommunity_DuplicateName_Returns409() throws Exception {
        CreateCommunityRequest request = new CreateCommunityRequest(
                "EduPartners Agency",
                null,
                CommunityType.FIELD_AGENCY
        );

        when(communityService.createCommunity(any())).thenThrow(
                new DuplicateResourceException("Community", "name", "EduPartners Agency"));

        mockMvc.perform(post("/api/v1/communities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("DUPLICATE_RESOURCE")));
    }

    @Test
    @DisplayName("GET /api/v1/communities/{id} should return 200 OK with community")
    void getCommunityById_Success() throws Exception {
        when(communityService.getCommunityById(communityId)).thenReturn(sampleCommunityResponse);

        mockMvc.perform(get("/api/v1/communities/{id}", communityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.id", equalTo(communityId.toString())))
                .andExpect(jsonPath("$.data.memberCount", equalTo(2)));
    }

    @Test
    @DisplayName("GET /api/v1/communities/{id} with unknown ID should return 404 Not Found")
    void getCommunityById_NotFound() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(communityService.getCommunityById(unknownId)).thenThrow(
                new ResourceNotFoundException("Community", unknownId));

        mockMvc.perform(get("/api/v1/communities/{id}", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("GET /api/v1/communities should return 200 with paged response")
    void getAllCommunities_ReturnsPagedResponse() throws Exception {
        when(communityService.getAllCommunities(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleCommunityResponse), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/communities?status=ACTIVE&type=FIELD_AGENCY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.page.totalElements", equalTo(1)))
                .andExpect(jsonPath("$.page.number", equalTo(0)));
    }

    @Test
    @DisplayName("PUT /api/v1/communities/{id} should return 200 OK on update")
    void updateCommunity_Success() throws Exception {
        UpdateCommunityRequest request = new UpdateCommunityRequest(
                "Updated Name",
                "New description",
                CommunityType.NGO
        );

        when(communityService.updateCommunity(eq(communityId), any())).thenReturn(sampleCommunityResponse);

        mockMvc.perform(put("/api/v1/communities/{id}", communityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.code", equalTo("COMMUNITY_UPDATED")));
    }

    @Test
    @DisplayName("PATCH /api/v1/communities/{id}/status should update status")
    void changeCommunityStatus_Success() throws Exception {
        when(communityService.changeCommunityStatus(eq(communityId), eq(CommunityStatus.SUSPENDED)))
                .thenReturn(sampleCommunityResponse);

        mockMvc.perform(patch("/api/v1/communities/{id}/status?status=SUSPENDED", communityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.code", equalTo("COMMUNITY_STATUS_UPDATED")));
    }

    @Test
    @DisplayName("POST /api/v1/communities/{id}/members should return 201 on adding member")
    void addMember_Success() throws Exception {
        UUID platformUserId = UUID.randomUUID();
        UUID customRoleId = UUID.randomUUID();
        AddCommunityMemberRequest request = new AddCommunityMemberRequest(platformUserId, customRoleId);

        CommunityMemberResponse memberResponse = new CommunityMemberResponse(
                UUID.randomUUID(),
                communityId,
                "COM-12345678",
                platformUserId,
                "MEM-12345678",
                customRoleId,
                "FIELD_AGENT",
                CommunityMemberStatus.ACTIVE,
                Instant.now(),
                null,
                Instant.now()
        );

        when(communityService.addMember(eq(communityId), any())).thenReturn(memberResponse);

        mockMvc.perform(post("/api/v1/communities/{id}/members", communityId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.code", equalTo("COMMUNITY_MEMBER_ADDED")))
                .andExpect(jsonPath("$.data.memberCode", equalTo("MEM-12345678")));
    }

    @Test
    @DisplayName("PATCH /api/v1/communities/{id}/members/{memberId}/role should update role")
    void updateMemberRole_Success() throws Exception {
        UUID memberId = UUID.randomUUID();
        UUID customRoleId = UUID.randomUUID();
        UpdateCommunityMemberRoleRequest request = new UpdateCommunityMemberRoleRequest(customRoleId);

        CommunityMemberResponse memberResponse = new CommunityMemberResponse(
                memberId,
                communityId,
                "COM-12345678",
                UUID.randomUUID(),
                "MEM-12345678",
                null,
                "COMMUNITY_ADMIN",
                CommunityMemberStatus.ACTIVE,
                Instant.now(),
                null,
                Instant.now()
        );

        when(communityService.updateMemberRole(eq(communityId), eq(memberId), any())).thenReturn(memberResponse);

        mockMvc.perform(patch("/api/v1/communities/{id}/members/{memberId}/role", communityId, memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)));
    }

    @Test
    @DisplayName("DELETE /api/v1/communities/{id}/members/{memberId} should return 204 No Content")
    void removeMember_Success() throws Exception {
        UUID memberId = UUID.randomUUID();
        doNothing().when(communityService).removeMember(communityId, memberId);

        mockMvc.perform(delete("/api/v1/communities/{id}/members/{memberId}", communityId, memberId))
                .andExpect(status().isNoContent());
    }
}
