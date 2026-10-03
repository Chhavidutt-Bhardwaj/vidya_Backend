package com.vidya.tenantdata.subject.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.subject.dto.CreateCurriculumSubjectRequest;
import com.vidya.tenantdata.subject.dto.CurriculumSubjectResponse;
import com.vidya.tenantdata.subject.dto.UpdateCurriculumSubjectRequest;
import com.vidya.tenantdata.subject.entity.Curriculum;
import com.vidya.tenantdata.subject.entity.CurriculumSubject;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.enums.CurriculumStatus;
import com.vidya.tenantdata.subject.enums.SubjectStatus;
import com.vidya.tenantdata.subject.enums.SubjectType;
import com.vidya.tenantdata.subject.repository.CurriculumRepository;
import com.vidya.tenantdata.subject.repository.CurriculumSubjectRepository;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
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
@DisplayName("CurriculumSubjectService — Unit Tests")
class CurriculumSubjectServiceTest {

    @Mock
    private CurriculumRepository curriculumRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private AcademicClassRepository academicClassRepository;

    @Mock
    private CurriculumSubjectRepository curriculumSubjectRepository;

    @InjectMocks
    private CurriculumSubjectServiceImpl curriculumSubjectService;

    private UUID curriculumId;
    private UUID subjectId;
    private UUID academicClassId;
    private UUID curriculumSubjectId;

    private Curriculum sampleCurriculum;
    private Subject sampleSubject;
    private AcademicClass sampleAcademicClass;
    private CurriculumSubject sampleCurriculumSubject;

    @BeforeEach
    void setUp() {
        curriculumId = UUID.randomUUID();
        subjectId = UUID.randomUUID();
        academicClassId = UUID.randomUUID();
        curriculumSubjectId = UUID.randomUUID();

        AcademicYear academicYear = new AcademicYear("AY2026-27", "2026-2027", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), true, com.vidya.tenantdata.academic.enums.AcademicYearStatus.ACTIVE);
        sampleCurriculum = new Curriculum("CUR-12345678", academicYear, "CBSE Curriculum", "Desc");
        sampleCurriculum.setId(curriculumId);
        sampleCurriculum.setStatus(CurriculumStatus.ACTIVE);

        sampleSubject = new Subject("SUB-12345678", "Physics", "PHY", "Physics Subject", SubjectType.CORE, 1);
        sampleSubject.setId(subjectId);

        sampleAcademicClass = new AcademicClass("CLS-10", "Class 10", "Class X", 10);
        sampleAcademicClass.setId(academicClassId);

        sampleCurriculumSubject = new CurriculumSubject(sampleCurriculum, sampleSubject, sampleAcademicClass, true, 1);
        sampleCurriculumSubject.setId(curriculumSubjectId);
    }

    @Test
    @DisplayName("addSubjectToCurriculum maps subject to class in curriculum successfully")
    void addSubjectToCurriculum_Success() {
        CreateCurriculumSubjectRequest request = CreateCurriculumSubjectRequest.builder()
                .subjectId(subjectId)
                .academicClassId(academicClassId)
                .isCompulsory(true)
                .displayOrder(1)
                .build();

        when(curriculumRepository.findByIdAndNotDeleted(curriculumId)).thenReturn(Optional.of(sampleCurriculum));
        when(subjectRepository.findByIdAndNotDeleted(subjectId)).thenReturn(Optional.of(sampleSubject));
        when(academicClassRepository.findByIdAndNotDeleted(academicClassId)).thenReturn(Optional.of(sampleAcademicClass));
        when(curriculumSubjectRepository.existsActiveByCurriculumSubjectClass(curriculumId, subjectId, academicClassId, null))
                .thenReturn(false);
        when(curriculumSubjectRepository.save(any(CurriculumSubject.class))).thenAnswer(invocation -> {
            CurriculumSubject cs = invocation.getArgument(0);
            cs.setId(curriculumSubjectId);
            return cs;
        });

        CurriculumSubjectResponse response = curriculumSubjectService.addSubjectToCurriculum(curriculumId, request);

        assertThat(response).isNotNull();
        assertThat(response.getCurriculumId()).isEqualTo(curriculumId);
        assertThat(response.getSubjectId()).isEqualTo(subjectId);
        assertThat(response.getAcademicClassId()).isEqualTo(academicClassId);
        assertThat(response.isCompulsory()).isTrue();
        verify(curriculumSubjectRepository).save(any(CurriculumSubject.class));
    }

    @Test
    @DisplayName("addSubjectToCurriculum rejects duplicate active mapping")
    void addSubjectToCurriculum_DuplicateMapping() {
        CreateCurriculumSubjectRequest request = CreateCurriculumSubjectRequest.builder()
                .subjectId(subjectId)
                .academicClassId(academicClassId)
                .isCompulsory(true)
                .build();

        when(curriculumRepository.findByIdAndNotDeleted(curriculumId)).thenReturn(Optional.of(sampleCurriculum));
        when(subjectRepository.findByIdAndNotDeleted(subjectId)).thenReturn(Optional.of(sampleSubject));
        when(academicClassRepository.findByIdAndNotDeleted(academicClassId)).thenReturn(Optional.of(sampleAcademicClass));
        when(curriculumSubjectRepository.existsActiveByCurriculumSubjectClass(curriculumId, subjectId, academicClassId, null))
                .thenReturn(true);

        assertThatThrownBy(() -> curriculumSubjectService.addSubjectToCurriculum(curriculumId, request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("addSubjectToCurriculum throws BusinessException when Curriculum is ARCHIVED or DELETED")
    void addSubjectToCurriculum_ArchivedCurriculum() {
        sampleCurriculum.setStatus(CurriculumStatus.ARCHIVED);

        CreateCurriculumSubjectRequest request = CreateCurriculumSubjectRequest.builder()
                .subjectId(subjectId)
                .academicClassId(academicClassId)
                .build();

        when(curriculumRepository.findByIdAndNotDeleted(curriculumId)).thenReturn(Optional.of(sampleCurriculum));

        assertThatThrownBy(() -> curriculumSubjectService.addSubjectToCurriculum(curriculumId, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("addSubjectToCurriculum throws BusinessException when Subject is DELETED")
    void addSubjectToCurriculum_DeletedSubject() {
        sampleSubject.setStatus(SubjectStatus.DELETED);

        CreateCurriculumSubjectRequest request = CreateCurriculumSubjectRequest.builder()
                .subjectId(subjectId)
                .academicClassId(academicClassId)
                .build();

        when(curriculumRepository.findByIdAndNotDeleted(curriculumId)).thenReturn(Optional.of(sampleCurriculum));
        when(subjectRepository.findByIdAndNotDeleted(subjectId)).thenReturn(Optional.of(sampleSubject));

        assertThatThrownBy(() -> curriculumSubjectService.addSubjectToCurriculum(curriculumId, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("getCurriculumSubject returns mapping by id and curriculumId")
    void getCurriculumSubject_Success() {
        when(curriculumSubjectRepository.findByIdAndCurriculumIdAndNotDeleted(curriculumSubjectId, curriculumId))
                .thenReturn(Optional.of(sampleCurriculumSubject));

        CurriculumSubjectResponse response = curriculumSubjectService.getCurriculumSubject(curriculumId, curriculumSubjectId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(curriculumSubjectId);
        assertThat(response.getSubjectName()).isEqualTo("Physics");
    }

    @Test
    @DisplayName("getCurriculumSubject throws ResourceNotFoundException when mapping not found")
    void getCurriculumSubject_NotFound() {
        when(curriculumSubjectRepository.findByIdAndCurriculumIdAndNotDeleted(curriculumSubjectId, curriculumId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> curriculumSubjectService.getCurriculumSubject(curriculumId, curriculumSubjectId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("listCurriculumSubjects filters and paginates correctly")
    void listCurriculumSubjects_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        when(curriculumRepository.existsById(curriculumId)).thenReturn(true);
        when(curriculumSubjectRepository.searchCurriculumSubjects(eq(curriculumId), eq(academicClassId), eq(SubjectType.CORE), eq(true), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(sampleCurriculumSubject), pageable, 1));

        Page<CurriculumSubjectResponse> page = curriculumSubjectService.listCurriculumSubjects(
                curriculumId, academicClassId, SubjectType.CORE, true, pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getSubjectName()).isEqualTo("Physics");
    }

    @Test
    @DisplayName("updateCurriculumSubject updates compulsory and displayOrder")
    void updateCurriculumSubject_Success() {
        UpdateCurriculumSubjectRequest request = UpdateCurriculumSubjectRequest.builder()
                .isCompulsory(false)
                .displayOrder(10)
                .build();

        when(curriculumSubjectRepository.findByIdAndCurriculumIdAndNotDeleted(curriculumSubjectId, curriculumId))
                .thenReturn(Optional.of(sampleCurriculumSubject));
        when(curriculumSubjectRepository.save(any(CurriculumSubject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CurriculumSubjectResponse response = curriculumSubjectService.updateCurriculumSubject(curriculumId, curriculumSubjectId, request);

        assertThat(response.isCompulsory()).isFalse();
        assertThat(response.getDisplayOrder()).isEqualTo(10);
    }

    @Test
    @DisplayName("removeSubjectFromCurriculum soft-deletes mapping")
    void removeSubjectFromCurriculum_Success() {
        when(curriculumSubjectRepository.findByIdAndCurriculumIdAndNotDeleted(curriculumSubjectId, curriculumId))
                .thenReturn(Optional.of(sampleCurriculumSubject));
        when(curriculumSubjectRepository.save(any(CurriculumSubject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        curriculumSubjectService.removeSubjectFromCurriculum(curriculumId, curriculumSubjectId);

        assertThat(sampleCurriculumSubject.isDeleted()).isTrue();
        verify(curriculumSubjectRepository).save(sampleCurriculumSubject);
    }
}
