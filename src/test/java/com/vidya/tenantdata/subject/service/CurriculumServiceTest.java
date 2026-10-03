package com.vidya.tenantdata.subject.service;

import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.subject.dto.CreateCurriculumRequest;
import com.vidya.tenantdata.subject.dto.CurriculumDetailResponse;
import com.vidya.tenantdata.subject.dto.CurriculumResponse;
import com.vidya.tenantdata.subject.dto.UpdateCurriculumRequest;
import com.vidya.tenantdata.subject.dto.UpdateCurriculumStatusRequest;
import com.vidya.tenantdata.subject.entity.Curriculum;
import com.vidya.tenantdata.subject.enums.CurriculumStatus;
import com.vidya.tenantdata.subject.repository.CurriculumRepository;
import com.vidya.tenantdata.subject.repository.CurriculumSubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CurriculumService — Unit Tests")
class CurriculumServiceTest {

    @Mock
    private CurriculumRepository curriculumRepository;

    @Mock
    private AcademicYearRepository academicYearRepository;

    @Mock
    private CurriculumSubjectRepository curriculumSubjectRepository;

    @InjectMocks
    private CurriculumServiceImpl curriculumService;

    private UUID curriculumId;
    private UUID academicYearId;
    private AcademicYear sampleAcademicYear;
    private Curriculum sampleCurriculum;

    @BeforeEach
    void setUp() {
        curriculumId = UUID.randomUUID();
        academicYearId = UUID.randomUUID();

        sampleAcademicYear = new AcademicYear(
                "AY2026-27",
                "Academic Year 2026-2027",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2027, 3, 31),
                true,
                AcademicYearStatus.ACTIVE
        );
        sampleAcademicYear.setId(academicYearId);

        sampleCurriculum = new Curriculum(
                "CUR-12345678",
                sampleAcademicYear,
                "CBSE 2026-27 Curriculum",
                "Curriculum description"
        );
        sampleCurriculum.setId(curriculumId);
    }

    @Test
    @DisplayName("createCurriculum validates AcademicYear and saves Curriculum")
    void createCurriculum_Success() {
        CreateCurriculumRequest request = CreateCurriculumRequest.builder()
                .academicYearId(academicYearId)
                .name("CBSE 2026-27 Curriculum")
                .description("Curriculum description")
                .build();

        when(academicYearRepository.findByIdAndNotDeleted(academicYearId)).thenReturn(Optional.of(sampleAcademicYear));
        when(curriculumRepository.existsByCurriculumCode(anyString())).thenReturn(false);
        when(curriculumRepository.save(any(Curriculum.class))).thenAnswer(invocation -> {
            Curriculum c = invocation.getArgument(0);
            c.setId(curriculumId);
            return c;
        });

        CurriculumDetailResponse response = curriculumService.createCurriculum(request);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("CBSE 2026-27 Curriculum");
        assertThat(response.getCurriculumCode()).startsWith("CUR-");
        assertThat(response.getStatus()).isEqualTo(CurriculumStatus.DRAFT);
        assertThat(response.getAcademicYearId()).isEqualTo(academicYearId);
        verify(curriculumRepository).save(any(Curriculum.class));
    }

    @Test
    @DisplayName("createCurriculum throws ResourceNotFoundException when AcademicYear does not exist in tenant")
    void createCurriculum_AcademicYearNotFound() {
        CreateCurriculumRequest request = CreateCurriculumRequest.builder()
                .academicYearId(academicYearId)
                .name("CBSE 2026-27 Curriculum")
                .build();

        when(academicYearRepository.findByIdAndNotDeleted(academicYearId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> curriculumService.createCurriculum(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getCurriculum returns details with mapped subjects")
    void getCurriculum_Success() {
        when(curriculumRepository.findByIdAndNotDeleted(curriculumId)).thenReturn(Optional.of(sampleCurriculum));
        when(curriculumSubjectRepository.findByCurriculumIdAndNotDeleted(curriculumId)).thenReturn(List.of());

        CurriculumDetailResponse response = curriculumService.getCurriculum(curriculumId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(curriculumId);
        assertThat(response.getName()).isEqualTo("CBSE 2026-27 Curriculum");
        assertThat(response.getSubjects()).isEmpty();
    }

    @Test
    @DisplayName("getCurriculum throws ResourceNotFoundException when not found")
    void getCurriculum_NotFound() {
        when(curriculumRepository.findByIdAndNotDeleted(curriculumId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> curriculumService.getCurriculum(curriculumId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getCurriculumByCode returns details by code")
    void getCurriculumByCode_Success() {
        when(curriculumRepository.findByCurriculumCodeAndNotDeleted("CUR-12345678")).thenReturn(Optional.of(sampleCurriculum));
        when(curriculumSubjectRepository.findByCurriculumIdAndNotDeleted(curriculumId)).thenReturn(List.of());

        CurriculumDetailResponse response = curriculumService.getCurriculumByCode("CUR-12345678");

        assertThat(response).isNotNull();
        assertThat(response.getCurriculumCode()).isEqualTo("CUR-12345678");
    }

    @Test
    @DisplayName("searchCurricula filters and paginates correctly")
    void searchCurricula_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        when(curriculumRepository.searchCurricula(eq(academicYearId), eq(CurriculumStatus.DRAFT), eq("cbse"), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(sampleCurriculum), pageable, 1));

        Page<CurriculumResponse> page = curriculumService.searchCurricula(academicYearId, CurriculumStatus.DRAFT, "cbse", pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getName()).isEqualTo("CBSE 2026-27 Curriculum");
    }

    @Test
    @DisplayName("updateCurriculum updates name and description")
    void updateCurriculum_Success() {
        UpdateCurriculumRequest request = UpdateCurriculumRequest.builder()
                .name("Updated Curriculum")
                .description("Updated Description")
                .build();

        when(curriculumRepository.findByIdAndNotDeleted(curriculumId)).thenReturn(Optional.of(sampleCurriculum));
        when(curriculumRepository.save(any(Curriculum.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(curriculumSubjectRepository.findByCurriculumIdAndNotDeleted(curriculumId)).thenReturn(List.of());

        CurriculumDetailResponse response = curriculumService.updateCurriculum(curriculumId, request);

        assertThat(response.getName()).isEqualTo("Updated Curriculum");
        assertThat(response.getDescription()).isEqualTo("Updated Description");
    }

    @Test
    @DisplayName("updateStatus transitions status from DRAFT to ACTIVE")
    void updateStatus_Success() {
        UpdateCurriculumStatusRequest request = UpdateCurriculumStatusRequest.builder()
                .status(CurriculumStatus.ACTIVE)
                .build();

        when(curriculumRepository.findByIdAndNotDeleted(curriculumId)).thenReturn(Optional.of(sampleCurriculum));
        when(curriculumRepository.save(any(Curriculum.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(curriculumSubjectRepository.findByCurriculumIdAndNotDeleted(curriculumId)).thenReturn(List.of());

        CurriculumDetailResponse response = curriculumService.updateStatus(curriculumId, request);

        assertThat(response.getStatus()).isEqualTo(CurriculumStatus.ACTIVE);
    }

    @Test
    @DisplayName("updateStatus throws InvalidStateTransitionException on illegal transition")
    void updateStatus_InvalidTransition() {
        sampleCurriculum.updateStatus(CurriculumStatus.DELETED);

        UpdateCurriculumStatusRequest request = UpdateCurriculumStatusRequest.builder()
                .status(CurriculumStatus.ACTIVE)
                .build();

        when(curriculumRepository.findByIdAndNotDeleted(curriculumId)).thenReturn(Optional.of(sampleCurriculum));

        assertThatThrownBy(() -> curriculumService.updateStatus(curriculumId, request))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("deleteCurriculum soft-deletes the curriculum")
    void deleteCurriculum_Success() {
        when(curriculumRepository.findByIdAndNotDeleted(curriculumId)).thenReturn(Optional.of(sampleCurriculum));
        when(curriculumRepository.save(any(Curriculum.class))).thenAnswer(invocation -> invocation.getArgument(0));

        curriculumService.deleteCurriculum(curriculumId);

        assertThat(sampleCurriculum.isDeleted()).isTrue();
        assertThat(sampleCurriculum.getStatus()).isEqualTo(CurriculumStatus.DELETED);
        verify(curriculumRepository).save(sampleCurriculum);
    }
}
