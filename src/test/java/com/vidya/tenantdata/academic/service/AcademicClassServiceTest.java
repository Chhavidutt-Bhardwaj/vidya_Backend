package com.vidya.tenantdata.academic.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.dto.AcademicClassResponse;
import com.vidya.tenantdata.academic.dto.CreateAcademicClassRequest;
import com.vidya.tenantdata.academic.dto.UpdateAcademicClassRequest;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.enums.AcademicClassStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AcademicClassService — Unit Tests")
class AcademicClassServiceTest {

    @Mock
    private AcademicClassRepository academicClassRepository;

    @Mock
    private SectionRepository sectionRepository;

    @InjectMocks
    private AcademicClassServiceImpl academicClassService;

    private UUID classId;
    private AcademicClass sampleClass;

    @BeforeEach
    void setUp() {
        classId = UUID.randomUUID();
        sampleClass = new AcademicClass("CLASS_5", "Class 5", "Grade 5", 5);
        sampleClass.setId(classId);
    }

    @Test
    @DisplayName("Create academic class succeeds when code is unique")
    void createAcademicClass_Success() {
        CreateAcademicClassRequest request = CreateAcademicClassRequest.builder()
                .code("CLASS_5")
                .name("Class 5")
                .displayName("Grade 5")
                .sortOrder(5)
                .build();

        when(academicClassRepository.existsByCode("CLASS_5")).thenReturn(false);
        when(academicClassRepository.save(any(AcademicClass.class))).thenAnswer(inv -> {
            AcademicClass c = inv.getArgument(0);
            c.setId(classId);
            return c;
        });

        AcademicClassResponse response = academicClassService.createAcademicClass(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(classId);
        assertThat(response.getCode()).isEqualTo("CLASS_5");
    }

    @Test
    @DisplayName("Create academic class throws DuplicateResourceException on existing code")
    void createAcademicClass_DuplicateCode() {
        CreateAcademicClassRequest request = CreateAcademicClassRequest.builder()
                .code("CLASS_5")
                .name("Class 5")
                .build();

        when(academicClassRepository.existsByCode("CLASS_5")).thenReturn(true);

        assertThatThrownBy(() -> academicClassService.createAcademicClass(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("Soft delete class fails when referenced by sections")
    void deleteAcademicClass_ReferencedBySections() {
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(sampleClass));
        when(sectionRepository.existsByAcademicClassIdAndDeletedAtIsNull(classId)).thenReturn(true);

        assertThatThrownBy(() -> academicClassService.deleteAcademicClass(classId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Soft delete class succeeds when not referenced")
    void deleteAcademicClass_Success() {
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(sampleClass));
        when(sectionRepository.existsByAcademicClassIdAndDeletedAtIsNull(classId)).thenReturn(false);
        when(academicClassRepository.save(any(AcademicClass.class))).thenAnswer(inv -> inv.getArgument(0));

        academicClassService.deleteAcademicClass(classId);

        assertThat(sampleClass.getStatus()).isEqualTo(AcademicClassStatus.DELETED);
        assertThat(sampleClass.isDeleted()).isTrue();
    }
}
