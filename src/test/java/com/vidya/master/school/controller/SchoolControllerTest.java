package com.vidya.master.school.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.GlobalExceptionHandler;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.master.school.dto.CreateBranchRequest;
import com.vidya.master.school.dto.CreateSchoolRequest;
import com.vidya.master.school.dto.SchoolBranchResponse;
import com.vidya.master.school.dto.SchoolResponse;
import com.vidya.master.school.dto.UpdateBranchRequest;
import com.vidya.master.school.dto.UpdateSchoolRequest;
import com.vidya.master.school.entity.BranchStatus;
import com.vidya.master.school.entity.SchoolBoard;
import com.vidya.master.school.entity.SchoolStatus;
import com.vidya.master.school.entity.SchoolType;
import com.vidya.master.school.service.SchoolService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SchoolControllerTest {

    @Mock
    private SchoolService schoolService;

    @InjectMocks
    private SchoolController schoolController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID schoolId;
    private UUID branchId;
    private SchoolResponse sampleSchoolResponse;
    private SchoolBranchResponse sampleBranchResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(schoolController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        schoolId = UUID.randomUUID();
        branchId = UUID.randomUUID();

        sampleBranchResponse = new SchoolBranchResponse(
                branchId,
                schoolId,
                "SCH-12345678",
                "BRN-12345678",
                "Main Campus",
                true,
                "Sector 12",
                "Dwarka",
                "New Delhi",
                "Delhi",
                "110075",
                "India",
                "dwarka@dpa.edu.in",
                "+919876543210",
                BranchStatus.ACTIVE,
                Instant.now(),
                Instant.now()
        );

        sampleSchoolResponse = new SchoolResponse(
                schoolId,
                "SCH-12345678",
                null,
                "Delhi Public Academy",
                "Delhi Public Academy Trust",
                "REG-987654",
                SchoolBoard.CBSE,
                SchoolType.SENIOR_SECONDARY,
                "principal@dpa.edu.in",
                "+919876543210",
                "https://dpa.edu.in",
                SchoolStatus.ACTIVE,
                Instant.now(),
                Instant.now(),
                1L,
                sampleBranchResponse
        );
    }

    @Test
    @DisplayName("POST /api/v1/schools should return 201 Created on valid input")
    void createSchool_ValidInput_Returns201() throws Exception {
        CreateSchoolRequest request = new CreateSchoolRequest(
                "Delhi Public Academy",
                "Delhi Public Academy Trust",
                "REG-987654",
                SchoolBoard.CBSE,
                SchoolType.SENIOR_SECONDARY,
                "principal@dpa.edu.in",
                "+919876543210",
                "https://dpa.edu.in",
                null
        );

        when(schoolService.createSchool(any(CreateSchoolRequest.class))).thenReturn(sampleSchoolResponse);

        mockMvc.perform(post("/api/v1/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.schoolName", equalTo("Delhi Public Academy")))
                .andExpect(jsonPath("$.data.schoolCode", equalTo("SCH-12345678")));
    }

    @Test
    @DisplayName("POST /api/v1/schools with duplicate email should return 409 Conflict")
    void createSchool_DuplicateEmail_Returns409() throws Exception {
        CreateSchoolRequest request = new CreateSchoolRequest(
                "Delhi Public Academy", null, null, null, null,
                "principal@dpa.edu.in", null, null, null);

        when(schoolService.createSchool(any())).thenThrow(
                new DuplicateResourceException("School", "email", "principal@dpa.edu.in"));

        mockMvc.perform(post("/api/v1/schools")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("DUPLICATE_RESOURCE")));
    }

    @Test
    @DisplayName("GET /api/v1/schools/{id} should return 200 OK with school")
    void getSchoolById_Success() throws Exception {
        when(schoolService.getSchoolById(schoolId)).thenReturn(sampleSchoolResponse);

        mockMvc.perform(get("/api/v1/schools/{id}", schoolId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.id", equalTo(schoolId.toString())))
                .andExpect(jsonPath("$.data.mainBranch.branchName", equalTo("Main Campus")));
    }

    @Test
    @DisplayName("GET /api/v1/schools/{id} with unknown ID should return 404 Not Found")
    void getSchoolById_NotFound() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(schoolService.getSchoolById(unknownId)).thenThrow(
                new ResourceNotFoundException("School", unknownId));

        mockMvc.perform(get("/api/v1/schools/{id}", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.code", equalTo("RESOURCE_NOT_FOUND")));
    }

    @Test
    @DisplayName("GET /api/v1/schools should return 200 with paged response")
    void getAllSchools_ReturnsPagedResponse() throws Exception {
        when(schoolService.getAllSchools(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleSchoolResponse), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/schools?status=ACTIVE&board=CBSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.page.totalElements", equalTo(1)));
    }

    @Test
    @DisplayName("PUT /api/v1/schools/{id} should update school details")
    void updateSchool_Success() throws Exception {
        UpdateSchoolRequest request = new UpdateSchoolRequest(
                "Updated Name", null, null, SchoolBoard.ICSE, SchoolType.ALL,
                "updated@dpa.edu.in", null, null);

        when(schoolService.updateSchool(eq(schoolId), any())).thenReturn(sampleSchoolResponse);

        mockMvc.perform(put("/api/v1/schools/{id}", schoolId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)));
    }

    @Test
    @DisplayName("PATCH /api/v1/schools/{id}/status should update status")
    void changeSchoolStatus_Success() throws Exception {
        when(schoolService.changeSchoolStatus(eq(schoolId), eq(SchoolStatus.SUSPENDED)))
                .thenReturn(sampleSchoolResponse);

        mockMvc.perform(patch("/api/v1/schools/{id}/status?status=SUSPENDED", schoolId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.code", equalTo("SCHOOL_ACTIVATED")));
    }

    @Test
    @DisplayName("POST /api/v1/schools/{id}/branches should create new branch")
    void addBranch_Success() throws Exception {
        CreateBranchRequest request = new CreateBranchRequest(
                "North Campus", false, "Sector 45", null, "Gurugram", "Haryana", "122001", "India", "north@dpa.edu.in", "+919876543211");

        when(schoolService.addBranch(eq(schoolId), any())).thenReturn(sampleBranchResponse);

        mockMvc.perform(post("/api/v1/schools/{id}/branches", schoolId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.branchName", equalTo("Main Campus")));
    }

    @Test
    @DisplayName("PUT /api/v1/schools/{id}/branches/{branchId} should update branch")
    void updateBranch_Success() throws Exception {
        UpdateBranchRequest request = new UpdateBranchRequest(
                "Main Campus Updated", true, "Sector 12", null, "Dwarka", "Delhi", "110075", "India", "dwarka@dpa.edu.in", "+919876543210", BranchStatus.ACTIVE);

        when(schoolService.updateBranch(eq(schoolId), eq(branchId), any())).thenReturn(sampleBranchResponse);

        mockMvc.perform(put("/api/v1/schools/{id}/branches/{branchId}", schoolId, branchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)));
    }

    @Test
    @DisplayName("GET /api/v1/schools/stats should return master data statistics")
    void getMasterStats_Success() throws Exception {
        com.vidya.master.school.dto.MasterStatsResponse stats =
                new com.vidya.master.school.dto.MasterStatsResponse(10, 8, 1, 1, 15, 8, 8, 12);
        when(schoolService.getMasterStats()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/schools/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.totalSchools", equalTo(10)))
                .andExpect(jsonPath("$.data.activeSchools", equalTo(8)))
                .andExpect(jsonPath("$.data.totalBranches", equalTo(15)))
                .andExpect(jsonPath("$.data.totalTenants", equalTo(8)));
    }
}
