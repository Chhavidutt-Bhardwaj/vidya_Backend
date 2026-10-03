package com.vidya.master.school.service;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.master.school.dto.CreateBranchRequest;
import com.vidya.master.school.dto.CreateSchoolRequest;
import com.vidya.master.school.dto.SchoolBranchResponse;
import com.vidya.master.school.dto.SchoolResponse;
import com.vidya.master.school.dto.UpdateBranchRequest;
import com.vidya.master.school.dto.UpdateSchoolRequest;
import com.vidya.master.school.entity.BranchStatus;
import com.vidya.master.school.entity.School;
import com.vidya.master.school.entity.SchoolBoard;
import com.vidya.master.school.entity.SchoolBranch;
import com.vidya.master.school.entity.SchoolStatus;
import com.vidya.master.school.entity.SchoolType;
import com.vidya.master.school.mapper.SchoolMapper;
import com.vidya.master.school.repository.SchoolBranchRepository;
import com.vidya.master.school.repository.SchoolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolServiceTest {

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private SchoolBranchRepository schoolBranchRepository;

    @Spy
    private SchoolMapper schoolMapper = new SchoolMapper();

    @InjectMocks
    private SchoolServiceImpl schoolService;

    private School sampleSchool;
    private SchoolBranch sampleBranch;
    private UUID schoolId;
    private UUID branchId;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        branchId = UUID.randomUUID();

        sampleSchool = new School(
                "SCH-12345678",
                "Delhi Public Academy",
                "Delhi Public Academy Trust",
                "REG-987654",
                SchoolBoard.CBSE,
                SchoolType.SENIOR_SECONDARY,
                "principal@dpa.edu.in",
                "+919876543210",
                "https://dpa.edu.in",
                SchoolStatus.ACTIVE
        );
        sampleSchool.setId(schoolId);

        sampleBranch = new SchoolBranch(
                sampleSchool,
                "BRN-12345678",
                "Main Campus",
                true,
                "Sector 12",
                "Near City Center",
                "Dwarka",
                "Delhi",
                "110075",
                "India",
                "dwarka@dpa.edu.in",
                "+919876543210"
        );
        sampleBranch.setId(branchId);
    }

    @Test
    @DisplayName("createSchool should register school and optional main branch")
    void createSchool_Success() {
        CreateBranchRequest branchReq = new CreateBranchRequest(
                "Main Campus", true, "Sector 12", null, "Dwarka", "Delhi", "110075", "India", "branch@dpa.edu.in", "+919999999999");

        CreateSchoolRequest request = new CreateSchoolRequest(
                "Greenwood High", "Greenwood Education Trust", "REG-12345",
                SchoolBoard.ICSE, SchoolType.ALL, "admin@greenwood.edu.in", "+919999999999", "https://greenwood.edu.in", branchReq);

        when(schoolRepository.existsByEmailIgnoreCase(request.email())).thenReturn(false);
        when(schoolRepository.existsBySchoolCode(any())).thenReturn(false);
        when(schoolRepository.save(any(School.class))).thenAnswer(inv -> {
            School s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(schoolBranchRepository.findByBranchCode(any())).thenReturn(Optional.empty());
        when(schoolBranchRepository.save(any(SchoolBranch.class))).thenAnswer(inv -> {
            SchoolBranch b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });

        SchoolResponse response = schoolService.createSchool(request);

        assertThat(response).isNotNull();
        assertThat(response.schoolName()).isEqualTo("Greenwood High");
        assertThat(response.board()).isEqualTo(SchoolBoard.ICSE);
        assertThat(response.status()).isEqualTo(SchoolStatus.DRAFT);
        assertThat(response.mainBranch()).isNotNull();
        verify(schoolRepository).save(any(School.class));
        verify(schoolBranchRepository).save(any(SchoolBranch.class));
    }

    @Test
    @DisplayName("createSchool with duplicate email should throw DuplicateResourceException")
    void createSchool_DuplicateEmail_ThrowsException() {
        CreateSchoolRequest request = new CreateSchoolRequest(
                "Greenwood High", null, null, null, null, "admin@greenwood.edu.in", null, null, null);

        when(schoolRepository.existsByEmailIgnoreCase(request.email())).thenReturn(true);

        assertThatThrownBy(() -> schoolService.createSchool(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("admin@greenwood.edu.in");
    }

    @Test
    @DisplayName("getSchoolById should return school with branch info")
    void getSchoolById_Success() {
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(sampleSchool));
        when(schoolBranchRepository.countBySchoolId(schoolId)).thenReturn(1L);
        when(schoolBranchRepository.findBySchoolIdAndMainBranchTrue(schoolId)).thenReturn(Optional.of(sampleBranch));

        SchoolResponse response = schoolService.getSchoolById(schoolId);

        assertThat(response).isNotNull();
        assertThat(response.schoolCode()).isEqualTo("SCH-12345678");
        assertThat(response.branchCount()).isEqualTo(1L);
        assertThat(response.mainBranch()).isNotNull();
        assertThat(response.mainBranch().branchName()).isEqualTo("Main Campus");
    }

    @Test
    @DisplayName("getSchoolById with unknown id should throw ResourceNotFoundException")
    void getSchoolById_NotFound_ThrowsException() {
        UUID unknownId = UUID.randomUUID();
        when(schoolRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> schoolService.getSchoolById(unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getSchoolByCode should return school when code exists")
    void getSchoolByCode_Success() {
        when(schoolRepository.findBySchoolCode("SCH-12345678")).thenReturn(Optional.of(sampleSchool));
        when(schoolBranchRepository.countBySchoolId(schoolId)).thenReturn(1L);
        when(schoolBranchRepository.findBySchoolIdAndMainBranchTrue(schoolId)).thenReturn(Optional.of(sampleBranch));

        SchoolResponse response = schoolService.getSchoolByCode("SCH-12345678");

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(schoolId);
    }

    @Test
    @DisplayName("getAllSchools should return paged results")
    void getAllSchools_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<School> page = new PageImpl<>(List.of(sampleSchool), pageable, 1);

        when(schoolRepository.findWithFilters(eq(SchoolStatus.ACTIVE), eq(SchoolBoard.CBSE), eq("Delhi"), eq(pageable)))
                .thenReturn(page);
        when(schoolBranchRepository.countBySchoolId(schoolId)).thenReturn(1L);
        when(schoolBranchRepository.findBySchoolIdAndMainBranchTrue(schoolId)).thenReturn(Optional.of(sampleBranch));

        Page<SchoolResponse> result = schoolService.getAllSchools(pageable, SchoolStatus.ACTIVE, SchoolBoard.CBSE, "Delhi");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).schoolName()).isEqualTo("Delhi Public Academy");
    }

    @Test
    @DisplayName("updateSchool should update details")
    void updateSchool_Success() {
        UpdateSchoolRequest request = new UpdateSchoolRequest(
                "Updated Academy Name", "New Legal Name", "REG-999",
                SchoolBoard.IB, SchoolType.ALL, "newemail@dpa.edu.in", "+919999999999", "https://newdpa.edu.in");

        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(sampleSchool));
        when(schoolRepository.existsByEmailIgnoreCaseAndIdNot("newemail@dpa.edu.in", schoolId)).thenReturn(false);
        when(schoolRepository.save(any(School.class))).thenReturn(sampleSchool);
        when(schoolBranchRepository.countBySchoolId(schoolId)).thenReturn(1L);
        when(schoolBranchRepository.findBySchoolIdAndMainBranchTrue(schoolId)).thenReturn(Optional.of(sampleBranch));

        SchoolResponse response = schoolService.updateSchool(schoolId, request);

        assertThat(response).isNotNull();
        assertThat(sampleSchool.getSchoolName()).isEqualTo("Updated Academy Name");
        assertThat(sampleSchool.getBoard()).isEqualTo(SchoolBoard.IB);
    }

    @Test
    @DisplayName("changeSchoolStatus should change status and save")
    void changeSchoolStatus_Success() {
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(sampleSchool));
        when(schoolRepository.save(any(School.class))).thenReturn(sampleSchool);
        when(schoolBranchRepository.countBySchoolId(schoolId)).thenReturn(1L);
        when(schoolBranchRepository.findBySchoolIdAndMainBranchTrue(schoolId)).thenReturn(Optional.of(sampleBranch));

        SchoolResponse response = schoolService.changeSchoolStatus(schoolId, SchoolStatus.SUSPENDED);

        assertThat(response).isNotNull();
        assertThat(sampleSchool.getStatus()).isEqualTo(SchoolStatus.SUSPENDED);
    }

    @Test
    @DisplayName("linkTenant should set tenant ID")
    void linkTenant_Success() {
        UUID tenantId = UUID.randomUUID();
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(sampleSchool));
        when(schoolRepository.save(any(School.class))).thenReturn(sampleSchool);
        when(schoolBranchRepository.countBySchoolId(schoolId)).thenReturn(1L);
        when(schoolBranchRepository.findBySchoolIdAndMainBranchTrue(schoolId)).thenReturn(Optional.of(sampleBranch));

        SchoolResponse response = schoolService.linkTenant(schoolId, tenantId);

        assertThat(response).isNotNull();
        assertThat(sampleSchool.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("addBranch should add branch to school")
    void addBranch_Success() {
        CreateBranchRequest request = new CreateBranchRequest(
                "North Campus", false, "Sector 45", null, "Gurugram", "Haryana", "122001", "India", "north@dpa.edu.in", "+919876543211");

        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(sampleSchool));
        when(schoolBranchRepository.findByBranchCode(any())).thenReturn(Optional.empty());
        when(schoolBranchRepository.save(any(SchoolBranch.class))).thenAnswer(inv -> {
            SchoolBranch b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });

        SchoolBranchResponse response = schoolService.addBranch(schoolId, request);

        assertThat(response).isNotNull();
        assertThat(response.branchName()).isEqualTo("North Campus");
        assertThat(response.isMainBranch()).isFalse();
        assertThat(response.status()).isEqualTo(BranchStatus.ACTIVE);
    }

    @Test
    @DisplayName("updateBranch should update branch details")
    void updateBranch_Success() {
        UpdateBranchRequest request = new UpdateBranchRequest(
                "Updated Campus Name", true, "New Address", null, "Dwarka", "Delhi", "110075", "India", "dwarka2@dpa.edu.in", "+919876543219", BranchStatus.ACTIVE);

        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(sampleSchool));
        when(schoolBranchRepository.findById(branchId)).thenReturn(Optional.of(sampleBranch));
        when(schoolBranchRepository.save(any(SchoolBranch.class))).thenReturn(sampleBranch);

        SchoolBranchResponse response = schoolService.updateBranch(schoolId, branchId, request);

        assertThat(response).isNotNull();
        assertThat(sampleBranch.getBranchName()).isEqualTo("Updated Campus Name");
    }
}
