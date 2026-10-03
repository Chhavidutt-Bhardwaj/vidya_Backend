package com.vidya.tenantdata.academic.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.tenantdata.academic.dto.CreateSectionRequest;
import com.vidya.tenantdata.academic.dto.SectionResponse;
import com.vidya.tenantdata.academic.dto.UpdateSectionRequest;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.enums.SectionStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.academic.repository.StudentAcademicAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SectionService — Unit Tests")
class SectionServiceTest {

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private AcademicYearRepository academicYearRepository;

    @Mock
    private AcademicClassRepository academicClassRepository;

    @Mock
    private StudentAcademicAssignmentRepository assignmentRepository;

    @InjectMocks
    private SectionServiceImpl sectionService;

    private UUID yearId;
    private UUID classId;
    private UUID sectionId;
    private AcademicYear sampleYear;
    private AcademicClass sampleClass;
    private Section sampleSection;

    @BeforeEach
    void setUp() {
        yearId = UUID.randomUUID();
        classId = UUID.randomUUID();
        sectionId = UUID.randomUUID();

        sampleYear = new AcademicYear("AY2026-27", "2026-2027", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), true, AcademicYearStatus.ACTIVE);
        sampleYear.setId(yearId);

        sampleClass = new AcademicClass("CLASS_5", "Class 5", "Grade 5", 5);
        sampleClass.setId(classId);

        sampleSection = new Section(sampleYear, sampleClass, "A", "Section A", 40);
        sampleSection.setId(sectionId);
    }

    @Test
    @DisplayName("Create section succeeds with valid year, class, and unique section code")
    void createSection_Success() {
        CreateSectionRequest request = CreateSectionRequest.builder()
                .academicYearId(yearId)
                .academicClassId(classId)
                .code("A")
                .name("Section A")
                .capacity(40)
                .build();

        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(sampleYear));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(sampleClass));
        when(sectionRepository.existsByAcademicYearIdAndAcademicClassIdAndCode(yearId, classId, "A")).thenReturn(false);
        when(sectionRepository.save(any(Section.class))).thenAnswer(inv -> {
            Section s = inv.getArgument(0);
            s.setId(sectionId);
            return s;
        });

        SectionResponse response = sectionService.createSection(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(sectionId);
        assertThat(response.getCode()).isEqualTo("A");
        assertThat(response.getCapacity()).isEqualTo(40);
    }

    @Test
    @DisplayName("Create section throws DuplicateResourceException on duplicate code in same year/class")
    void createSection_DuplicateCode() {
        CreateSectionRequest request = CreateSectionRequest.builder()
                .academicYearId(yearId)
                .academicClassId(classId)
                .code("A")
                .name("Section A")
                .build();

        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(sampleYear));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(sampleClass));
        when(sectionRepository.existsByAcademicYearIdAndAcademicClassIdAndCode(yearId, classId, "A")).thenReturn(true);

        assertThatThrownBy(() -> sectionService.createSection(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("Update section capacity fails if smaller than active student count")
    void updateSection_CapacityExceeded() {
        UpdateSectionRequest request = UpdateSectionRequest.builder()
                .capacity(25)
                .build();

        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(sampleSection));
        when(assignmentRepository.countActiveAssignmentsBySectionId(sectionId)).thenReturn(30L);

        assertThatThrownBy(() -> sectionService.updateSection(sectionId, request))
                .isInstanceOf(BusinessException.class);
    }
}
