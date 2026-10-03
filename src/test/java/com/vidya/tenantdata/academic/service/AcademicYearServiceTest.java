package com.vidya.tenantdata.academic.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.dto.AcademicYearResponse;
import com.vidya.tenantdata.academic.dto.CreateAcademicYearRequest;
import com.vidya.tenantdata.academic.dto.UpdateAcademicYearRequest;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AcademicYearService — Unit Tests")
class AcademicYearServiceTest {

    @Mock
    private AcademicYearRepository academicYearRepository;

    @Mock
    private SectionRepository sectionRepository;

    @InjectMocks
    private AcademicYearServiceImpl academicYearService;

    private UUID yearId;
    private AcademicYear sampleYear;

    @BeforeEach
    void setUp() {
        yearId = UUID.randomUUID();
        sampleYear = new AcademicYear("AY2026-27", "2026-2027", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), true, AcademicYearStatus.ACTIVE);
        sampleYear.setId(yearId);
    }

    @Test
    @DisplayName("Create academic year successfully with valid code and dates")
    void createAcademicYear_Success() {
        CreateAcademicYearRequest request = CreateAcademicYearRequest.builder()
                .code("AY2026-27")
                .name("2026-2027")
                .startDate(LocalDate.of(2026, 4, 1))
                .endDate(LocalDate.of(2027, 3, 31))
                .isCurrent(true)
                .build();

        when(academicYearRepository.existsByCode("AY2026-27")).thenReturn(false);
        when(academicYearRepository.save(any(AcademicYear.class))).thenAnswer(inv -> {
            AcademicYear y = inv.getArgument(0);
            y.setId(yearId);
            return y;
        });

        AcademicYearResponse response = academicYearService.createAcademicYear(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(yearId);
        assertThat(response.getCode()).isEqualTo("AY2026-27");
        assertThat(response.isCurrent()).isTrue();
        assertThat(response.getStatus()).isEqualTo(AcademicYearStatus.ACTIVE);
        verify(academicYearRepository).unsetAllCurrent();
    }

    @Test
    @DisplayName("Create academic year throws DuplicateResourceException on existing code")
    void createAcademicYear_DuplicateCode() {
        CreateAcademicYearRequest request = CreateAcademicYearRequest.builder()
                .code("AY2026-27")
                .name("2026-2027")
                .startDate(LocalDate.of(2026, 4, 1))
                .endDate(LocalDate.of(2027, 3, 31))
                .build();

        when(academicYearRepository.existsByCode("AY2026-27")).thenReturn(true);

        assertThatThrownBy(() -> academicYearService.createAcademicYear(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("Create academic year throws BusinessException when end date is not after start date")
    void createAcademicYear_InvalidDates() {
        CreateAcademicYearRequest request = CreateAcademicYearRequest.builder()
                .code("AY2026-27")
                .name("2026-2027")
                .startDate(LocalDate.of(2026, 4, 1))
                .endDate(LocalDate.of(2026, 3, 31))
                .build();

        when(academicYearRepository.existsByCode("AY2026-27")).thenReturn(false);

        assertThatThrownBy(() -> academicYearService.createAcademicYear(request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Get current academic year returns response when found")
    void getCurrentAcademicYear_Success() {
        when(academicYearRepository.findCurrentYear()).thenReturn(Optional.of(sampleYear));

        AcademicYearResponse response = academicYearService.getCurrentAcademicYear();

        assertThat(response).isNotNull();
        assertThat(response.isCurrent()).isTrue();
    }

    @Test
    @DisplayName("Set current academic year unsets other years and marks target year active/current")
    void setCurrentAcademicYear_Success() {
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(sampleYear));
        when(academicYearRepository.save(any(AcademicYear.class))).thenAnswer(inv -> inv.getArgument(0));

        AcademicYearResponse response = academicYearService.setCurrentAcademicYear(yearId);

        assertThat(response.isCurrent()).isTrue();
        verify(academicYearRepository).unsetCurrentForOtherYears(yearId);
    }

    @Test
    @DisplayName("Soft delete academic year fails if referenced by active sections")
    void deleteAcademicYear_ReferencedBySections() {
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(sampleYear));
        when(sectionRepository.existsByAcademicYearIdAndDeletedAtIsNull(yearId)).thenReturn(true);

        assertThatThrownBy(() -> academicYearService.deleteAcademicYear(yearId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Soft delete academic year succeeds when not referenced")
    void deleteAcademicYear_Success() {
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(sampleYear));
        when(sectionRepository.existsByAcademicYearIdAndDeletedAtIsNull(yearId)).thenReturn(false);
        when(academicYearRepository.save(any(AcademicYear.class))).thenAnswer(inv -> inv.getArgument(0));

        academicYearService.deleteAcademicYear(yearId);

        assertThat(sampleYear.getStatus()).isEqualTo(AcademicYearStatus.DELETED);
        assertThat(sampleYear.isDeleted()).isTrue();
    }
}
