package com.vidya.tenantdata.examination.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.examination.dto.CreateExamTermRequest;
import com.vidya.tenantdata.examination.dto.ExamTermResponse;
import com.vidya.tenantdata.examination.dto.UpdateExamTermRequest;
import com.vidya.tenantdata.examination.entity.ExamTerm;
import com.vidya.tenantdata.examination.enums.ExamTermStatus;
import com.vidya.tenantdata.examination.mapper.ExamTermMapper;
import com.vidya.tenantdata.examination.repository.ExamTermRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
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
@DisplayName("ExamTermServiceImpl Unit Tests")
class ExamTermServiceImplTest {

    @Mock
    private ExamTermRepository examTermRepository;

    @Mock
    private AcademicYearRepository academicYearRepository;

    @Spy
    private ExamTermMapper examTermMapper = new ExamTermMapper();

    @InjectMocks
    private ExamTermServiceImpl examTermService;

    private UUID termId;
    private UUID academicYearId;
    private AcademicYear academicYear;
    private ExamTerm sampleTerm;

    @BeforeEach
    void setUp() {
        termId = UUID.randomUUID();
        academicYearId = UUID.randomUUID();

        academicYear = new AcademicYear(
                "AY-2026",
                "Academic Year 2026-2027",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2027, 3, 31),
                true,
                AcademicYearStatus.ACTIVE
        );
        academicYear.setId(academicYearId);

        sampleTerm = new ExamTerm(
                "TRM-12345678",
                academicYear,
                "Mid-Term Examination",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 15),
                new BigDecimal("25.00")
        );
        sampleTerm.setId(termId);
    }

    @Test
    @DisplayName("createExamTerm creates and returns ExamTermResponse")
    void createExamTerm_Success() {
        CreateExamTermRequest request = CreateExamTermRequest.builder()
                .academicYearId(academicYearId)
                .name("Mid-Term Examination")
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 15))
                .weightagePercentage(new BigDecimal("25.00"))
                .build();

        when(academicYearRepository.findById(academicYearId)).thenReturn(Optional.of(academicYear));
        when(examTermRepository.existsByAcademicYearIdAndNameIgnoreCase(academicYearId, "Mid-Term Examination")).thenReturn(false);
        when(examTermRepository.save(any(ExamTerm.class))).thenAnswer(invocation -> {
            ExamTerm saved = invocation.getArgument(0);
            saved.setId(termId);
            return saved;
        });

        ExamTermResponse response = examTermService.createExamTerm(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(termId);
        assertThat(response.getName()).isEqualTo("Mid-Term Examination");
        assertThat(response.getStatus()).isEqualTo(ExamTermStatus.PLANNED);
        verify(examTermRepository).save(any(ExamTerm.class));
    }

    @Test
    @DisplayName("createExamTerm throws BusinessException when startDate is after endDate")
    void createExamTerm_InvalidDates_ThrowsException() {
        CreateExamTermRequest request = CreateExamTermRequest.builder()
                .academicYearId(academicYearId)
                .name("Mid-Term Examination")
                .startDate(LocalDate.of(2026, 9, 20))
                .endDate(LocalDate.of(2026, 9, 10))
                .build();

        assertThatThrownBy(() -> examTermService.createExamTerm(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Term start date cannot be after end date");
    }

    @Test
    @DisplayName("createExamTerm throws DuplicateResourceException if term name exists in academic year")
    void createExamTerm_DuplicateName_ThrowsException() {
        CreateExamTermRequest request = CreateExamTermRequest.builder()
                .academicYearId(academicYearId)
                .name("Mid-Term Examination")
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 15))
                .build();

        when(academicYearRepository.findById(academicYearId)).thenReturn(Optional.of(academicYear));
        when(examTermRepository.existsByAcademicYearIdAndNameIgnoreCase(academicYearId, "Mid-Term Examination")).thenReturn(true);

        assertThatThrownBy(() -> examTermService.createExamTerm(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("getExamTermById returns response when found")
    void getExamTermById_Success() {
        when(examTermRepository.findActiveById(termId)).thenReturn(Optional.of(sampleTerm));

        ExamTermResponse response = examTermService.getExamTermById(termId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(termId);
        assertThat(response.getName()).isEqualTo("Mid-Term Examination");
    }

    @Test
    @DisplayName("getExamTermById throws ResourceNotFoundException when not found")
    void getExamTermById_NotFound() {
        when(examTermRepository.findActiveById(termId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> examTermService.getExamTermById(termId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("updateTermStatus updates status properly")
    void updateTermStatus_Success() {
        when(examTermRepository.findActiveById(termId)).thenReturn(Optional.of(sampleTerm));
        when(examTermRepository.save(any(ExamTerm.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExamTermResponse response = examTermService.updateTermStatus(termId, ExamTermStatus.IN_PROGRESS);

        assertThat(response.getStatus()).isEqualTo(ExamTermStatus.IN_PROGRESS);
        assertThat(sampleTerm.getStatus()).isEqualTo(ExamTermStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("searchExamTerms returns paged results")
    void searchExamTerms_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<ExamTerm> page = new PageImpl<>(List.of(sampleTerm), pageable, 1);

        when(examTermRepository.searchTerms(eq(academicYearId), eq(ExamTermStatus.PLANNED), eq(pageable)))
                .thenReturn(page);

        Page<ExamTermResponse> result = examTermService.searchExamTerms(academicYearId, ExamTermStatus.PLANNED, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Mid-Term Examination");
    }
}
