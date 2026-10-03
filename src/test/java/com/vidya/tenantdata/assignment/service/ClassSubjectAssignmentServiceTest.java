package com.vidya.tenantdata.assignment.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.assignment.dto.ClassSubjectAssignmentDetailResponse;
import com.vidya.tenantdata.assignment.dto.CreateClassSubjectAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.UpdateClassSubjectAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.UpdateClassSubjectAssignmentStatusRequest;
import com.vidya.tenantdata.assignment.entity.ClassSubjectAssignment;
import com.vidya.tenantdata.assignment.enums.ClassSubjectAssignmentStatus;
import com.vidya.tenantdata.assignment.repository.ClassSubjectAssignmentRepository;
import com.vidya.tenantdata.subject.entity.Curriculum;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.enums.CurriculumStatus;
import com.vidya.tenantdata.subject.enums.SubjectStatus;
import com.vidya.tenantdata.subject.enums.SubjectType;
import com.vidya.tenantdata.subject.repository.CurriculumRepository;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
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
@DisplayName("ClassSubjectAssignmentService — Unit Tests")
class ClassSubjectAssignmentServiceTest {

    @Mock
    private ClassSubjectAssignmentRepository classSubjectAssignmentRepository;

    @Mock
    private AcademicYearRepository academicYearRepository;

    @Mock
    private AcademicClassRepository academicClassRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private CurriculumRepository curriculumRepository;

    @InjectMocks
    private ClassSubjectAssignmentServiceImpl classSubjectAssignmentService;

    private AcademicYear academicYear;
    private AcademicClass academicClass;
    private Section section;
    private Subject subject;
    private Curriculum curriculum;
    private ClassSubjectAssignment assignment;

    private final UUID academicYearId = UUID.randomUUID();
    private final UUID academicClassId = UUID.randomUUID();
    private final UUID sectionId = UUID.randomUUID();
    private final UUID subjectId = UUID.randomUUID();
    private final UUID curriculumId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        academicYear = new AcademicYear("AY-2026", "2026-2027", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), true, AcademicYearStatus.ACTIVE);
        academicYear.setId(academicYearId);

        academicClass = new AcademicClass("CLS-10", "Class 10", "Class 10", 10);
        academicClass.setId(academicClassId);

        section = new Section(academicYear, academicClass, "SEC-10A", "Section A", 40);
        section.setId(sectionId);

        subject = new Subject("SUB-001", "Science", "SCI", "Science Subject", SubjectType.CORE, 2);
        subject.setId(subjectId);

        curriculum = new Curriculum("CUR-001", academicYear, "CBSE Class 10", "CBSE Curriculum");
        curriculum.setId(curriculumId);
        curriculum.setStatus(CurriculumStatus.ACTIVE);

        assignment = new ClassSubjectAssignment("CSA-001", academicYear, academicClass, section, subject, curriculum, true, 1, 5);
        assignment.setId(assignmentId);
    }

    @Test
    @DisplayName("Create class subject deployment successfully")
    void createAssignment_success() {
        CreateClassSubjectAssignmentRequest request = new CreateClassSubjectAssignmentRequest();
        request.setAcademicYearId(academicYearId);
        request.setAcademicClassId(academicClassId);
        request.setSectionId(sectionId);
        request.setSubjectId(subjectId);
        request.setCurriculumId(curriculumId);
        request.setIsCompulsory(true);
        request.setDisplayOrder(1);
        request.setPeriodsPerWeek(5);

        when(academicYearRepository.findByIdAndNotDeleted(academicYearId)).thenReturn(Optional.of(academicYear));
        when(academicClassRepository.findByIdAndNotDeleted(academicClassId)).thenReturn(Optional.of(academicClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(section));
        when(subjectRepository.findByIdAndNotDeleted(subjectId)).thenReturn(Optional.of(subject));
        when(curriculumRepository.findByIdAndNotDeleted(curriculumId)).thenReturn(Optional.of(curriculum));
        when(classSubjectAssignmentRepository.existsActiveDuplicate(academicYearId, academicClassId, sectionId, subjectId, null))
                .thenReturn(false);
        when(classSubjectAssignmentRepository.save(any(ClassSubjectAssignment.class))).thenAnswer(i -> {
            ClassSubjectAssignment saved = i.getArgument(0);
            saved.setId(assignmentId);
            return saved;
        });

        ClassSubjectAssignmentDetailResponse response = classSubjectAssignmentService.createAssignment(request);

        assertThat(response).isNotNull();
        assertThat(response.getAcademicClassId()).isEqualTo(academicClassId);
        assertThat(response.getSubjectId()).isEqualTo(subjectId);
        assertThat(response.getPeriodsPerWeek()).isEqualTo(5);
        assertThat(response.getStatus()).isEqualTo(ClassSubjectAssignmentStatus.ACTIVE);
    }

    @Test
    @DisplayName("Create class subject deployment fails on duplicate")
    void createAssignment_duplicate_fails() {
        CreateClassSubjectAssignmentRequest request = new CreateClassSubjectAssignmentRequest();
        request.setAcademicYearId(academicYearId);
        request.setAcademicClassId(academicClassId);
        request.setSubjectId(subjectId);

        when(academicYearRepository.findByIdAndNotDeleted(academicYearId)).thenReturn(Optional.of(academicYear));
        when(academicClassRepository.findByIdAndNotDeleted(academicClassId)).thenReturn(Optional.of(academicClass));
        when(subjectRepository.findByIdAndNotDeleted(subjectId)).thenReturn(Optional.of(subject));
        when(classSubjectAssignmentRepository.existsActiveDuplicate(academicYearId, academicClassId, null, subjectId, null))
                .thenReturn(true);

        assertThatThrownBy(() -> classSubjectAssignmentService.createAssignment(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("Create class subject deployment fails when subject is inactive")
    void createAssignment_inactiveSubject_fails() {
        subject.setStatus(SubjectStatus.INACTIVE);

        CreateClassSubjectAssignmentRequest request = new CreateClassSubjectAssignmentRequest();
        request.setAcademicYearId(academicYearId);
        request.setAcademicClassId(academicClassId);
        request.setSubjectId(subjectId);

        when(academicYearRepository.findByIdAndNotDeleted(academicYearId)).thenReturn(Optional.of(academicYear));
        when(academicClassRepository.findByIdAndNotDeleted(academicClassId)).thenReturn(Optional.of(academicClass));
        when(subjectRepository.findByIdAndNotDeleted(subjectId)).thenReturn(Optional.of(subject));

        assertThatThrownBy(() -> classSubjectAssignmentService.createAssignment(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot assign subject that is not ACTIVE");
    }

    @Test
    @DisplayName("Get assignment by ID successfully")
    void getAssignment_success() {
        when(classSubjectAssignmentRepository.findByIdAndNotDeleted(assignmentId)).thenReturn(Optional.of(assignment));

        ClassSubjectAssignmentDetailResponse response = classSubjectAssignmentService.getAssignment(assignmentId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(assignmentId);
        assertThat(response.getAssignmentCode()).isEqualTo("CSA-001");
    }

    @Test
    @DisplayName("Get assignment throws 404 when not found")
    void getAssignment_notFound() {
        when(classSubjectAssignmentRepository.findByIdAndNotDeleted(assignmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> classSubjectAssignmentService.getAssignment(assignmentId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Update assignment properties successfully")
    void updateAssignment_success() {
        UpdateClassSubjectAssignmentRequest request = new UpdateClassSubjectAssignmentRequest();
        request.setIsCompulsory(false);
        request.setDisplayOrder(5);
        request.setPeriodsPerWeek(6);

        when(classSubjectAssignmentRepository.findByIdAndNotDeleted(assignmentId)).thenReturn(Optional.of(assignment));
        when(classSubjectAssignmentRepository.save(any(ClassSubjectAssignment.class))).thenReturn(assignment);

        ClassSubjectAssignmentDetailResponse response = classSubjectAssignmentService.updateAssignment(assignmentId, request);

        assertThat(response).isNotNull();
        assertThat(assignment.isCompulsory()).isFalse();
        assertThat(assignment.getDisplayOrder()).isEqualTo(5);
        assertThat(assignment.getPeriodsPerWeek()).isEqualTo(6);
    }

    @Test
    @DisplayName("Update status successfully")
    void updateStatus_success() {
        UpdateClassSubjectAssignmentStatusRequest request = new UpdateClassSubjectAssignmentStatusRequest();
        request.setStatus(ClassSubjectAssignmentStatus.INACTIVE);

        when(classSubjectAssignmentRepository.findByIdAndNotDeleted(assignmentId)).thenReturn(Optional.of(assignment));
        when(classSubjectAssignmentRepository.save(any(ClassSubjectAssignment.class))).thenReturn(assignment);

        ClassSubjectAssignmentDetailResponse response = classSubjectAssignmentService.updateStatus(assignmentId, request);

        assertThat(response).isNotNull();
        assertThat(assignment.getStatus()).isEqualTo(ClassSubjectAssignmentStatus.INACTIVE);
    }

    @Test
    @DisplayName("Delete assignment soft deletes successfully")
    void deleteAssignment_success() {
        when(classSubjectAssignmentRepository.findByIdAndNotDeleted(assignmentId)).thenReturn(Optional.of(assignment));
        when(classSubjectAssignmentRepository.save(any(ClassSubjectAssignment.class))).thenReturn(assignment);

        classSubjectAssignmentService.deleteAssignment(assignmentId);

        assertThat(assignment.isDeleted()).isTrue();
        assertThat(assignment.getStatus()).isEqualTo(ClassSubjectAssignmentStatus.DELETED);
    }
}
