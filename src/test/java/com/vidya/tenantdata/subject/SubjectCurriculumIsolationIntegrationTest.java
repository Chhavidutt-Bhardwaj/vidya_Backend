package com.vidya.tenantdata.subject;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenant.context.TenantContext;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.subject.dto.CreateCurriculumRequest;
import com.vidya.tenantdata.subject.dto.CreateCurriculumSubjectRequest;
import com.vidya.tenantdata.subject.dto.CreateSubjectRequest;
import com.vidya.tenantdata.subject.dto.CurriculumDetailResponse;
import com.vidya.tenantdata.subject.dto.CurriculumSubjectResponse;
import com.vidya.tenantdata.subject.dto.SubjectDetailResponse;
import com.vidya.tenantdata.subject.dto.UpdateCurriculumRequest;
import com.vidya.tenantdata.subject.dto.UpdateCurriculumSubjectRequest;
import com.vidya.tenantdata.subject.dto.UpdateSubjectRequest;
import com.vidya.tenantdata.subject.entity.Curriculum;
import com.vidya.tenantdata.subject.entity.CurriculumSubject;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.enums.CurriculumStatus;
import com.vidya.tenantdata.subject.enums.SubjectStatus;
import com.vidya.tenantdata.subject.enums.SubjectType;
import com.vidya.tenantdata.subject.repository.CurriculumRepository;
import com.vidya.tenantdata.subject.repository.CurriculumSubjectRepository;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import com.vidya.tenantdata.subject.service.CurriculumServiceImpl;
import com.vidya.tenantdata.subject.service.CurriculumSubjectServiceImpl;
import com.vidya.tenantdata.subject.service.SubjectServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tenant Isolation & IDOR Protection Integration Tests for Subject & Curriculum domains.
 *
 * <p>Verifies that cross-tenant access attempts fail safely with 404 (ResourceNotFoundException)
 * because repositories scoped to Tenant A never see entities from Tenant B.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Subject & Curriculum Isolation & IDOR Protection — Integration Tests")
class SubjectCurriculumIsolationIntegrationTest {

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private CurriculumRepository curriculumRepository;

    @Mock
    private AcademicYearRepository academicYearRepository;

    @Mock
    private AcademicClassRepository academicClassRepository;

    @Mock
    private CurriculumSubjectRepository curriculumSubjectRepository;

    @InjectMocks
    private SubjectServiceImpl subjectService;

    @InjectMocks
    private CurriculumServiceImpl curriculumService;

    @InjectMocks
    private CurriculumSubjectServiceImpl curriculumSubjectService;

    // Tenant A resources
    private UUID tenantASubjectId;
    private UUID tenantACurriculumId;
    private UUID tenantAAcademicYearId;
    private UUID tenantAAcademicClassId;
    private UUID tenantACurriculumSubjectId;

    private Subject tenantASubject;
    private Curriculum tenantACurriculum;
    private AcademicYear tenantAAcademicYear;
    private AcademicClass tenantAAcademicClass;
    private CurriculumSubject tenantACurriculumSubject;

    // Tenant B IDs (cross-tenant attack targets)
    private UUID tenantBSubjectId;
    private UUID tenantBCurriculumId;
    private UUID tenantBAcademicYearId;
    private UUID tenantBAcademicClassId;
    private UUID tenantBCurriculumSubjectId;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantCode("tenant_a");

        tenantASubjectId = UUID.randomUUID();
        tenantACurriculumId = UUID.randomUUID();
        tenantAAcademicYearId = UUID.randomUUID();
        tenantAAcademicClassId = UUID.randomUUID();
        tenantACurriculumSubjectId = UUID.randomUUID();

        tenantAAcademicYear = new AcademicYear("AY2026-27", "2026-2027", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), true, AcademicYearStatus.ACTIVE);
        tenantAAcademicYear.setId(tenantAAcademicYearId);

        tenantASubject = new Subject("SUB-TENANT-A", "Tenant A Subject", "TAS", "Desc", SubjectType.CORE, 1);
        tenantASubject.setId(tenantASubjectId);

        tenantACurriculum = new Curriculum("CUR-TENANT-A", tenantAAcademicYear, "Tenant A Curriculum", "Desc");
        tenantACurriculum.setId(tenantACurriculumId);
        tenantACurriculum.setStatus(CurriculumStatus.ACTIVE);

        tenantAAcademicClass = new AcademicClass("CLS-A", "Class A", "A", 1);
        tenantAAcademicClass.setId(tenantAAcademicClassId);

        tenantACurriculumSubject = new CurriculumSubject(tenantACurriculum, tenantASubject, tenantAAcademicClass, true, 1);
        tenantACurriculumSubject.setId(tenantACurriculumSubjectId);

        // Tenant B identifiers
        tenantBSubjectId = UUID.randomUUID();
        tenantBCurriculumId = UUID.randomUUID();
        tenantBAcademicYearId = UUID.randomUUID();
        tenantBAcademicClassId = UUID.randomUUID();
        tenantBCurriculumSubjectId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ==========================================
    // 1. Tenant A Normal Operations (Success)
    // ==========================================

    @Test
    @DisplayName("Tenant A creates Subject successfully in Tenant A context")
    void tenantA_CreateSubject_Success() {
        CreateSubjectRequest request = CreateSubjectRequest.builder()
                .name("Mathematics A")
                .subjectType(SubjectType.CORE)
                .build();

        when(subjectRepository.existsBySubjectCode(anyString())).thenReturn(false);
        when(subjectRepository.save(any(Subject.class))).thenAnswer(i -> {
            Subject s = i.getArgument(0);
            s.setId(tenantASubjectId);
            return s;
        });

        SubjectDetailResponse response = subjectService.createSubject(request);
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(tenantASubjectId);
    }

    @Test
    @DisplayName("Tenant A creates Curriculum with Tenant A AcademicYear successfully")
    void tenantA_CreateCurriculum_Success() {
        CreateCurriculumRequest request = CreateCurriculumRequest.builder()
                .academicYearId(tenantAAcademicYearId)
                .name("Curriculum A")
                .build();

        when(academicYearRepository.findByIdAndNotDeleted(tenantAAcademicYearId)).thenReturn(Optional.of(tenantAAcademicYear));
        when(curriculumRepository.existsByCurriculumCode(anyString())).thenReturn(false);
        when(curriculumRepository.save(any(Curriculum.class))).thenAnswer(i -> {
            Curriculum c = i.getArgument(0);
            c.setId(tenantACurriculumId);
            return c;
        });

        CurriculumDetailResponse response = curriculumService.createCurriculum(request);
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(tenantACurriculumId);
    }

    @Test
    @DisplayName("Tenant A maps Tenant A Subject to Tenant A Class in Tenant A Curriculum successfully")
    void tenantA_AddCurriculumSubject_Success() {
        CreateCurriculumSubjectRequest request = CreateCurriculumSubjectRequest.builder()
                .subjectId(tenantASubjectId)
                .academicClassId(tenantAAcademicClassId)
                .isCompulsory(true)
                .build();

        when(curriculumRepository.findByIdAndNotDeleted(tenantACurriculumId)).thenReturn(Optional.of(tenantACurriculum));
        when(subjectRepository.findByIdAndNotDeleted(tenantASubjectId)).thenReturn(Optional.of(tenantASubject));
        when(academicClassRepository.findByIdAndNotDeleted(tenantAAcademicClassId)).thenReturn(Optional.of(tenantAAcademicClass));
        when(curriculumSubjectRepository.existsActiveByCurriculumSubjectClass(tenantACurriculumId, tenantASubjectId, tenantAAcademicClassId, null))
                .thenReturn(false);
        when(curriculumSubjectRepository.save(any(CurriculumSubject.class))).thenAnswer(i -> {
            CurriculumSubject cs = i.getArgument(0);
            cs.setId(tenantACurriculumSubjectId);
            return cs;
        });

        CurriculumSubjectResponse response = curriculumSubjectService.addSubjectToCurriculum(tenantACurriculumId, request);
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(tenantACurriculumSubjectId);
    }

    // ==========================================
    // 2. IDOR Protection (Tenant A -> Tenant B IDs)
    // ==========================================

    @Test
    @DisplayName("IDOR: Tenant A querying Tenant B Subject ID returns 404")
    void idor_GetSubject_TenantBId_Returns404() {
        when(subjectRepository.findByIdAndNotDeleted(tenantBSubjectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subjectService.getSubject(tenantBSubjectId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("IDOR: Tenant A querying Tenant B Subject Code returns 404")
    void idor_GetSubjectByCode_TenantBCode_Returns404() {
        when(subjectRepository.findBySubjectCodeAndNotDeleted("SUB-TENANT-B")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subjectService.getSubjectByCode("SUB-TENANT-B"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("IDOR: Tenant A updating Tenant B Subject returns 404")
    void idor_UpdateSubject_TenantBId_Returns404() {
        when(subjectRepository.findByIdAndNotDeleted(tenantBSubjectId)).thenReturn(Optional.empty());

        UpdateSubjectRequest request = UpdateSubjectRequest.builder()
                .name("Attacked Subject")
                .subjectType(SubjectType.CORE)
                .build();

        assertThatThrownBy(() -> subjectService.updateSubject(tenantBSubjectId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("IDOR: Tenant A deleting Tenant B Subject returns 404")
    void idor_DeleteSubject_TenantBId_Returns404() {
        when(subjectRepository.findByIdAndNotDeleted(tenantBSubjectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subjectService.deleteSubject(tenantBSubjectId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("IDOR: Tenant A querying Tenant B Curriculum ID returns 404")
    void idor_GetCurriculum_TenantBId_Returns404() {
        when(curriculumRepository.findByIdAndNotDeleted(tenantBCurriculumId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> curriculumService.getCurriculum(tenantBCurriculumId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("IDOR: Tenant A querying Tenant B Curriculum Code returns 404")
    void idor_GetCurriculumByCode_TenantBCode_Returns404() {
        when(curriculumRepository.findByCurriculumCodeAndNotDeleted("CUR-TENANT-B")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> curriculumService.getCurriculumByCode("CUR-TENANT-B"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("IDOR: Tenant A updating Tenant B Curriculum returns 404")
    void idor_UpdateCurriculum_TenantBId_Returns404() {
        when(curriculumRepository.findByIdAndNotDeleted(tenantBCurriculumId)).thenReturn(Optional.empty());

        UpdateCurriculumRequest request = UpdateCurriculumRequest.builder()
                .name("Attacked Curriculum")
                .build();

        assertThatThrownBy(() -> curriculumService.updateCurriculum(tenantBCurriculumId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("IDOR: Tenant A deleting Tenant B Curriculum returns 404")
    void idor_DeleteCurriculum_TenantBId_Returns404() {
        when(curriculumRepository.findByIdAndNotDeleted(tenantBCurriculumId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> curriculumService.deleteCurriculum(tenantBCurriculumId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("IDOR: Tenant A querying Tenant B CurriculumSubject returns 404")
    void idor_GetCurriculumSubject_TenantB_Returns404() {
        when(curriculumSubjectRepository.findByIdAndCurriculumIdAndNotDeleted(tenantBCurriculumSubjectId, tenantACurriculumId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> curriculumSubjectService.getCurriculumSubject(tenantACurriculumId, tenantBCurriculumSubjectId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("IDOR: Tenant A updating Tenant B CurriculumSubject returns 404")
    void idor_UpdateCurriculumSubject_TenantB_Returns404() {
        when(curriculumSubjectRepository.findByIdAndCurriculumIdAndNotDeleted(tenantBCurriculumSubjectId, tenantACurriculumId))
                .thenReturn(Optional.empty());

        UpdateCurriculumSubjectRequest request = UpdateCurriculumSubjectRequest.builder().isCompulsory(false).build();

        assertThatThrownBy(() -> curriculumSubjectService.updateCurriculumSubject(tenantACurriculumId, tenantBCurriculumSubjectId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("IDOR: Tenant A deleting Tenant B CurriculumSubject returns 404")
    void idor_DeleteCurriculumSubject_TenantB_Returns404() {
        when(curriculumSubjectRepository.findByIdAndCurriculumIdAndNotDeleted(tenantBCurriculumSubjectId, tenantACurriculumId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> curriculumSubjectService.removeSubjectFromCurriculum(tenantACurriculumId, tenantBCurriculumSubjectId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ==========================================
    // 3. Cross-Tenant Relationship Attack Tests
    // ==========================================

    @Test
    @DisplayName("Attack: Tenant A Curriculum referencing Tenant B AcademicYear fails with 404")
    void crossTenantAttack_CurriculumWithTenantBAcademicYear_Fails() {
        CreateCurriculumRequest request = CreateCurriculumRequest.builder()
                .academicYearId(tenantBAcademicYearId)
                .name("Malicious Curriculum")
                .build();

        // AcademicYear is from Tenant B, so Tenant A repo does not find it
        when(academicYearRepository.findByIdAndNotDeleted(tenantBAcademicYearId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> curriculumService.createCurriculum(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Attack: Tenant A Curriculum mapping Tenant B Subject fails with 404")
    void crossTenantAttack_CurriculumSubjectWithTenantBSubject_Fails() {
        CreateCurriculumSubjectRequest request = CreateCurriculumSubjectRequest.builder()
                .subjectId(tenantBSubjectId)
                .academicClassId(tenantAAcademicClassId)
                .isCompulsory(true)
                .build();

        when(curriculumRepository.findByIdAndNotDeleted(tenantACurriculumId)).thenReturn(Optional.of(tenantACurriculum));
        when(subjectRepository.findByIdAndNotDeleted(tenantBSubjectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> curriculumSubjectService.addSubjectToCurriculum(tenantACurriculumId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Attack: Tenant A Curriculum mapping Tenant B AcademicClass fails with 404")
    void crossTenantAttack_CurriculumSubjectWithTenantBClass_Fails() {
        CreateCurriculumSubjectRequest request = CreateCurriculumSubjectRequest.builder()
                .subjectId(tenantASubjectId)
                .academicClassId(tenantBAcademicClassId)
                .isCompulsory(true)
                .build();

        when(curriculumRepository.findByIdAndNotDeleted(tenantACurriculumId)).thenReturn(Optional.of(tenantACurriculum));
        when(subjectRepository.findByIdAndNotDeleted(tenantASubjectId)).thenReturn(Optional.of(tenantASubject));
        when(academicClassRepository.findByIdAndNotDeleted(tenantBAcademicClassId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> curriculumSubjectService.addSubjectToCurriculum(tenantACurriculumId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Attack: Tenant B Curriculum mapping Tenant A Subject and Class fails with 404")
    void crossTenantAttack_TenantBCurriculumMappingTenantAResources_Fails() {
        CreateCurriculumSubjectRequest request = CreateCurriculumSubjectRequest.builder()
                .subjectId(tenantASubjectId)
                .academicClassId(tenantAAcademicClassId)
                .isCompulsory(true)
                .build();

        // In Tenant A's context, Tenant B's Curriculum is not found
        when(curriculumRepository.findByIdAndNotDeleted(tenantBCurriculumId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> curriculumSubjectService.addSubjectToCurriculum(tenantBCurriculumId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
