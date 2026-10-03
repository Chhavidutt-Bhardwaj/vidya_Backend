package com.vidya.tenantdata.subject.service;

import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.subject.dto.CreateSubjectRequest;
import com.vidya.tenantdata.subject.dto.SubjectDetailResponse;
import com.vidya.tenantdata.subject.dto.SubjectResponse;
import com.vidya.tenantdata.subject.dto.UpdateSubjectRequest;
import com.vidya.tenantdata.subject.dto.UpdateSubjectStatusRequest;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.enums.SubjectStatus;
import com.vidya.tenantdata.subject.enums.SubjectType;
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
@DisplayName("SubjectService — Unit Tests")
class SubjectServiceTest {

    @Mock
    private SubjectRepository subjectRepository;

    @InjectMocks
    private SubjectServiceImpl subjectService;

    private UUID subjectId;
    private Subject sampleSubject;

    @BeforeEach
    void setUp() {
        subjectId = UUID.randomUUID();
        sampleSubject = new Subject(
                "SUB-12345678",
                "Mathematics",
                "MATH",
                "Core Mathematics",
                SubjectType.CORE,
                1
        );
        sampleSubject.setId(subjectId);
    }

    @Test
    @DisplayName("createSubject generates subjectCode and persists Subject")
    void createSubject_Success() {
        CreateSubjectRequest request = CreateSubjectRequest.builder()
                .name("Mathematics")
                .shortName("MATH")
                .description("Core Mathematics")
                .subjectType(SubjectType.CORE)
                .displayOrder(1)
                .build();

        when(subjectRepository.existsBySubjectCode(anyString())).thenReturn(false);
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> {
            Subject s = invocation.getArgument(0);
            s.setId(subjectId);
            return s;
        });

        SubjectDetailResponse response = subjectService.createSubject(request);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Mathematics");
        assertThat(response.getSubjectCode()).startsWith("SUB-");
        assertThat(response.getSubjectType()).isEqualTo(SubjectType.CORE);
        assertThat(response.getStatus()).isEqualTo(SubjectStatus.ACTIVE);
        verify(subjectRepository).save(any(Subject.class));
    }

    @Test
    @DisplayName("getSubject returns subject by id")
    void getSubject_Success() {
        when(subjectRepository.findByIdAndNotDeleted(subjectId)).thenReturn(Optional.of(sampleSubject));

        SubjectDetailResponse response = subjectService.getSubject(subjectId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(subjectId);
        assertThat(response.getName()).isEqualTo("Mathematics");
    }

    @Test
    @DisplayName("getSubject throws ResourceNotFoundException when not found")
    void getSubject_NotFound() {
        when(subjectRepository.findByIdAndNotDeleted(subjectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subjectService.getSubject(subjectId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getSubjectByCode returns subject by business code")
    void getSubjectByCode_Success() {
        when(subjectRepository.findBySubjectCodeAndNotDeleted("SUB-12345678")).thenReturn(Optional.of(sampleSubject));

        SubjectDetailResponse response = subjectService.getSubjectByCode("SUB-12345678");

        assertThat(response).isNotNull();
        assertThat(response.getSubjectCode()).isEqualTo("SUB-12345678");
    }

    @Test
    @DisplayName("searchSubjects filters and paginates correctly")
    void searchSubjects_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        when(subjectRepository.searchSubjects(eq(SubjectType.CORE), eq(SubjectStatus.ACTIVE), eq("math"), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(sampleSubject), pageable, 1));

        Page<SubjectResponse> page = subjectService.searchSubjects(SubjectType.CORE, SubjectStatus.ACTIVE, "math", pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getName()).isEqualTo("Mathematics");
    }

    @Test
    @DisplayName("updateSubject updates properties successfully")
    void updateSubject_Success() {
        UpdateSubjectRequest request = UpdateSubjectRequest.builder()
                .name("Advanced Mathematics")
                .shortName("ADV-MATH")
                .description("Updated description")
                .subjectType(SubjectType.CORE)
                .displayOrder(5)
                .build();

        when(subjectRepository.findByIdAndNotDeleted(subjectId)).thenReturn(Optional.of(sampleSubject));
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubjectDetailResponse response = subjectService.updateSubject(subjectId, request);

        assertThat(response.getName()).isEqualTo("Advanced Mathematics");
        assertThat(response.getShortName()).isEqualTo("ADV-MATH");
        assertThat(response.getDisplayOrder()).isEqualTo(5);
    }

    @Test
    @DisplayName("updateStatus transitions status successfully")
    void updateStatus_Success() {
        UpdateSubjectStatusRequest request = UpdateSubjectStatusRequest.builder()
                .status(SubjectStatus.INACTIVE)
                .build();

        when(subjectRepository.findByIdAndNotDeleted(subjectId)).thenReturn(Optional.of(sampleSubject));
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubjectDetailResponse response = subjectService.updateStatus(subjectId, request);

        assertThat(response.getStatus()).isEqualTo(SubjectStatus.INACTIVE);
    }

    @Test
    @DisplayName("updateStatus throws InvalidStateTransitionException on illegal transition")
    void updateStatus_InvalidTransition() {
        sampleSubject.updateStatus(SubjectStatus.DELETED);

        UpdateSubjectStatusRequest request = UpdateSubjectStatusRequest.builder()
                .status(SubjectStatus.ACTIVE)
                .build();

        when(subjectRepository.findByIdAndNotDeleted(subjectId)).thenReturn(Optional.of(sampleSubject));

        assertThatThrownBy(() -> subjectService.updateStatus(subjectId, request))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("deleteSubject soft-deletes the subject")
    void deleteSubject_Success() {
        when(subjectRepository.findByIdAndNotDeleted(subjectId)).thenReturn(Optional.of(sampleSubject));
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        subjectService.deleteSubject(subjectId);

        assertThat(sampleSubject.isDeleted()).isTrue();
        assertThat(sampleSubject.getStatus()).isEqualTo(SubjectStatus.DELETED);
        verify(subjectRepository).save(sampleSubject);
    }
}
