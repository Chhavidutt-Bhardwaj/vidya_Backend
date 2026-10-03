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
import com.vidya.tenantdata.assignment.dto.CreateTeacherAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.TeacherAssignmentDetailResponse;
import com.vidya.tenantdata.assignment.dto.UpdateTeacherAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.UpdateTeacherAssignmentStatusRequest;
import com.vidya.tenantdata.assignment.entity.TeacherSubjectAssignment;
import com.vidya.tenantdata.assignment.enums.TeacherAssignmentStatus;
import com.vidya.tenantdata.assignment.repository.TeacherSubjectAssignmentRepository;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.enums.StaffType;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.subject.entity.Curriculum;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.enums.CurriculumStatus;
import com.vidya.tenantdata.subject.enums.SubjectStatus;
import com.vidya.tenantdata.subject.enums.SubjectType;
import com.vidya.tenantdata.subject.repository.CurriculumRepository;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserProfile;
import com.vidya.tenantdata.user.entity.TenantUserType;
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
@DisplayName("TeacherSubjectAssignmentService — Unit Tests")
class TeacherSubjectAssignmentServiceTest {

    @Mock
    private TeacherSubjectAssignmentRepository teacherAssignmentRepository;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private AcademicYearRepository academicYearRepository;

    @Mock
    private AcademicClassRepository academicClassRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private CurriculumRepository curriculumRepository;

    @InjectMocks
    private TeacherSubjectAssignmentServiceImpl teacherAssignmentService;

    private Staff teacher;
    private Subject subject;
    private AcademicYear academicYear;
    private AcademicClass academicClass;
    private Section section;
    private Curriculum curriculum;
    private TeacherSubjectAssignment assignment;

    private final UUID teacherId = UUID.randomUUID();
    private final UUID subjectId = UUID.randomUUID();
    private final UUID academicYearId = UUID.randomUUID();
    private final UUID academicClassId = UUID.randomUUID();
    private final UUID sectionId = UUID.randomUUID();
    private final UUID curriculumId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantUser tenantUser = new TenantUser("USR-001", "teacher@school.com", "9876543210", TenantUserType.STAFF);
        tenantUser.setId(UUID.randomUUID());
        TenantUserProfile profile = new TenantUserProfile(tenantUser, "Jane", "Doe");
        tenantUser.setProfile(profile);

        teacher = new Staff("STF-001", tenantUser, "EMP-101", LocalDate.now(), StaffType.TEACHER, "Senior Teacher", "Math");
        teacher.setId(teacherId);

        subject = new Subject("SUB-001", "Mathematics", "MATH", "Mathematics subject", SubjectType.CORE, 1);
        subject.setId(subjectId);

        academicYear = new AcademicYear("AY-2026", "2026-2027", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), true, AcademicYearStatus.ACTIVE);
        academicYear.setId(academicYearId);

        academicClass = new AcademicClass("CLS-10", "Class 10", "Class 10", 10);
        academicClass.setId(academicClassId);

        section = new Section(academicYear, academicClass, "SEC-10A", "Section A", 40);
        section.setId(sectionId);

        curriculum = new Curriculum("CUR-001", academicYear, "CBSE Class 10", "CBSE Curriculum");
        curriculum.setId(curriculumId);
        curriculum.setStatus(CurriculumStatus.ACTIVE);

        assignment = new TeacherSubjectAssignment(
                "TSA-001", teacher, subject, academicYear, academicClass, section, curriculum,
                true, LocalDate.of(2026, 4, 1), null
        );
        assignment.setId(assignmentId);
    }

    @Test
    @DisplayName("Create assignment successfully")
    void createAssignment_success() {
        CreateTeacherAssignmentRequest request = new CreateTeacherAssignmentRequest();
        request.setTeacherId(teacherId);
        request.setSubjectId(subjectId);
        request.setAcademicYearId(academicYearId);
        request.setAcademicClassId(academicClassId);
        request.setSectionId(sectionId);
        request.setCurriculumId(curriculumId);
        request.setIsPrimary(true);
        request.setStartDate(LocalDate.of(2026, 4, 1));

        when(staffRepository.findByIdAndNotDeleted(teacherId)).thenReturn(Optional.of(teacher));
        when(subjectRepository.findByIdAndNotDeleted(subjectId)).thenReturn(Optional.of(subject));
        when(academicYearRepository.findByIdAndNotDeleted(academicYearId)).thenReturn(Optional.of(academicYear));
        when(academicClassRepository.findByIdAndNotDeleted(academicClassId)).thenReturn(Optional.of(academicClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(section));
        when(curriculumRepository.findByIdAndNotDeleted(curriculumId)).thenReturn(Optional.of(curriculum));
        when(teacherAssignmentRepository.existsActiveDuplicate(teacherId, subjectId, academicYearId, academicClassId, sectionId, null))
                .thenReturn(false);
        when(teacherAssignmentRepository.existsActivePrimary(subjectId, academicYearId, academicClassId, sectionId, null))
                .thenReturn(false);
        when(teacherAssignmentRepository.save(any(TeacherSubjectAssignment.class))).thenAnswer(i -> {
            TeacherSubjectAssignment saved = i.getArgument(0);
            saved.setId(assignmentId);
            return saved;
        });

        TeacherAssignmentDetailResponse response = teacherAssignmentService.createAssignment(request);

        assertThat(response).isNotNull();
        assertThat(response.getTeacherId()).isEqualTo(teacherId);
        assertThat(response.getSubjectId()).isEqualTo(subjectId);
        assertThat(response.isPrimary()).isTrue();
        assertThat(response.getStatus()).isEqualTo(TeacherAssignmentStatus.ACTIVE);
    }

    @Test
    @DisplayName("Create assignment fails when teacher is TERMINATED")
    void createAssignment_terminatedTeacher_fails() {
        teacher.setEmploymentStatus(EmploymentStatus.TERMINATED);

        CreateTeacherAssignmentRequest request = new CreateTeacherAssignmentRequest();
        request.setTeacherId(teacherId);
        request.setSubjectId(subjectId);
        request.setAcademicYearId(academicYearId);
        request.setAcademicClassId(academicClassId);

        when(staffRepository.findByIdAndNotDeleted(teacherId)).thenReturn(Optional.of(teacher));

        assertThatThrownBy(() -> teacherAssignmentService.createAssignment(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot assign a new active teaching assignment");
    }

    @Test
    @DisplayName("Create assignment fails when staff type is not TEACHER (ADMINISTRATIVE)")
    void createAssignment_nonTeacherStaffType_administrative_fails() {
        teacher.setStaffType(StaffType.ADMINISTRATIVE);

        CreateTeacherAssignmentRequest request = new CreateTeacherAssignmentRequest();
        request.setTeacherId(teacherId);
        request.setSubjectId(subjectId);
        request.setAcademicYearId(academicYearId);
        request.setAcademicClassId(academicClassId);

        when(staffRepository.findByIdAndNotDeleted(teacherId)).thenReturn(Optional.of(teacher));

        assertThatThrownBy(() -> teacherAssignmentService.createAssignment(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("staffType TEACHER");
    }

    @Test
    @DisplayName("Create assignment fails when staff type is NON_TEACHING")
    void createAssignment_nonTeachingStaffType_fails() {
        teacher.setStaffType(StaffType.NON_TEACHING);

        CreateTeacherAssignmentRequest request = new CreateTeacherAssignmentRequest();
        request.setTeacherId(teacherId);
        request.setSubjectId(subjectId);
        request.setAcademicYearId(academicYearId);
        request.setAcademicClassId(academicClassId);

        when(staffRepository.findByIdAndNotDeleted(teacherId)).thenReturn(Optional.of(teacher));

        assertThatThrownBy(() -> teacherAssignmentService.createAssignment(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("staffType TEACHER");
    }

    @Test
    @DisplayName("Create assignment fails when staff type is SUPPORT")
    void createAssignment_supportStaffType_fails() {
        teacher.setStaffType(StaffType.SUPPORT);

        CreateTeacherAssignmentRequest request = new CreateTeacherAssignmentRequest();
        request.setTeacherId(teacherId);
        request.setSubjectId(subjectId);
        request.setAcademicYearId(academicYearId);
        request.setAcademicClassId(academicClassId);

        when(staffRepository.findByIdAndNotDeleted(teacherId)).thenReturn(Optional.of(teacher));

        assertThatThrownBy(() -> teacherAssignmentService.createAssignment(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("staffType TEACHER");
    }

    @Test
    @DisplayName("Create assignment fails when staff type is OTHER")
    void createAssignment_otherStaffType_fails() {
        teacher.setStaffType(StaffType.OTHER);

        CreateTeacherAssignmentRequest request = new CreateTeacherAssignmentRequest();
        request.setTeacherId(teacherId);
        request.setSubjectId(subjectId);
        request.setAcademicYearId(academicYearId);
        request.setAcademicClassId(academicClassId);

        when(staffRepository.findByIdAndNotDeleted(teacherId)).thenReturn(Optional.of(teacher));

        assertThatThrownBy(() -> teacherAssignmentService.createAssignment(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("staffType TEACHER");
    }

    @Test
    @DisplayName("Create assignment fails when teacher is SUSPENDED")
    void createAssignment_suspendedTeacher_fails() {
        teacher.setEmploymentStatus(EmploymentStatus.SUSPENDED);

        CreateTeacherAssignmentRequest request = new CreateTeacherAssignmentRequest();
        request.setTeacherId(teacherId);
        request.setSubjectId(subjectId);
        request.setAcademicYearId(academicYearId);
        request.setAcademicClassId(academicClassId);

        when(staffRepository.findByIdAndNotDeleted(teacherId)).thenReturn(Optional.of(teacher));

        assertThatThrownBy(() -> teacherAssignmentService.createAssignment(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot assign a new active teaching assignment");
    }

    @Test
    @DisplayName("Create assignment fails when teacher is RESIGNED")
    void createAssignment_resignedTeacher_fails() {
        teacher.setEmploymentStatus(EmploymentStatus.RESIGNED);

        CreateTeacherAssignmentRequest request = new CreateTeacherAssignmentRequest();
        request.setTeacherId(teacherId);
        request.setSubjectId(subjectId);
        request.setAcademicYearId(academicYearId);
        request.setAcademicClassId(academicClassId);

        when(staffRepository.findByIdAndNotDeleted(teacherId)).thenReturn(Optional.of(teacher));

        assertThatThrownBy(() -> teacherAssignmentService.createAssignment(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot assign a new active teaching assignment");
    }

    @Test
    @DisplayName("Create assignment fails when teacher is RETIRED")
    void createAssignment_retiredTeacher_fails() {
        teacher.setEmploymentStatus(EmploymentStatus.RETIRED);

        CreateTeacherAssignmentRequest request = new CreateTeacherAssignmentRequest();
        request.setTeacherId(teacherId);
        request.setSubjectId(subjectId);
        request.setAcademicYearId(academicYearId);
        request.setAcademicClassId(academicClassId);

        when(staffRepository.findByIdAndNotDeleted(teacherId)).thenReturn(Optional.of(teacher));

        assertThatThrownBy(() -> teacherAssignmentService.createAssignment(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot assign a new active teaching assignment");
    }

    @Test
    @DisplayName("Create assignment fails when subject is not active")
    void createAssignment_inactiveSubject_fails() {
        subject.setStatus(SubjectStatus.INACTIVE);

        CreateTeacherAssignmentRequest request = new CreateTeacherAssignmentRequest();
        request.setTeacherId(teacherId);
        request.setSubjectId(subjectId);
        request.setAcademicYearId(academicYearId);
        request.setAcademicClassId(academicClassId);

        when(staffRepository.findByIdAndNotDeleted(teacherId)).thenReturn(Optional.of(teacher));
        when(subjectRepository.findByIdAndNotDeleted(subjectId)).thenReturn(Optional.of(subject));

        assertThatThrownBy(() -> teacherAssignmentService.createAssignment(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot assign subject that is not ACTIVE");
    }

    @Test
    @DisplayName("Create assignment fails when section does not match class/year")
    void createAssignment_inconsistentSection_fails() {
        AcademicClass otherClass = new AcademicClass("CLS-09", "Class 9", "Class 9", 9);
        otherClass.setId(UUID.randomUUID());
        Section invalidSection = new Section(academicYear, otherClass, "SEC-9B", "Section B", 35);
        invalidSection.setId(sectionId);

        CreateTeacherAssignmentRequest request = new CreateTeacherAssignmentRequest();
        request.setTeacherId(teacherId);
        request.setSubjectId(subjectId);
        request.setAcademicYearId(academicYearId);
        request.setAcademicClassId(academicClassId);
        request.setSectionId(sectionId);

        when(staffRepository.findByIdAndNotDeleted(teacherId)).thenReturn(Optional.of(teacher));
        when(subjectRepository.findByIdAndNotDeleted(subjectId)).thenReturn(Optional.of(subject));
        when(academicYearRepository.findByIdAndNotDeleted(academicYearId)).thenReturn(Optional.of(academicYear));
        when(academicClassRepository.findByIdAndNotDeleted(academicClassId)).thenReturn(Optional.of(academicClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(invalidSection));

        assertThatThrownBy(() -> teacherAssignmentService.createAssignment(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Section does not belong to the specified AcademicClass");
    }

    @Test
    @DisplayName("Create assignment fails on duplicate active assignment")
    void createAssignment_duplicate_fails() {
        CreateTeacherAssignmentRequest request = new CreateTeacherAssignmentRequest();
        request.setTeacherId(teacherId);
        request.setSubjectId(subjectId);
        request.setAcademicYearId(academicYearId);
        request.setAcademicClassId(academicClassId);

        when(staffRepository.findByIdAndNotDeleted(teacherId)).thenReturn(Optional.of(teacher));
        when(subjectRepository.findByIdAndNotDeleted(subjectId)).thenReturn(Optional.of(subject));
        when(academicYearRepository.findByIdAndNotDeleted(academicYearId)).thenReturn(Optional.of(academicYear));
        when(academicClassRepository.findByIdAndNotDeleted(academicClassId)).thenReturn(Optional.of(academicClass));
        when(teacherAssignmentRepository.existsActiveDuplicate(teacherId, subjectId, academicYearId, academicClassId, null, null))
                .thenReturn(true);

        assertThatThrownBy(() -> teacherAssignmentService.createAssignment(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("Create assignment fails when another primary teacher is already active")
    void createAssignment_duplicatePrimary_fails() {
        CreateTeacherAssignmentRequest request = new CreateTeacherAssignmentRequest();
        request.setTeacherId(teacherId);
        request.setSubjectId(subjectId);
        request.setAcademicYearId(academicYearId);
        request.setAcademicClassId(academicClassId);
        request.setIsPrimary(true);

        when(staffRepository.findByIdAndNotDeleted(teacherId)).thenReturn(Optional.of(teacher));
        when(subjectRepository.findByIdAndNotDeleted(subjectId)).thenReturn(Optional.of(subject));
        when(academicYearRepository.findByIdAndNotDeleted(academicYearId)).thenReturn(Optional.of(academicYear));
        when(academicClassRepository.findByIdAndNotDeleted(academicClassId)).thenReturn(Optional.of(academicClass));
        when(teacherAssignmentRepository.existsActiveDuplicate(teacherId, subjectId, academicYearId, academicClassId, null, null))
                .thenReturn(false);
        when(teacherAssignmentRepository.existsActivePrimary(subjectId, academicYearId, academicClassId, null, null))
                .thenReturn(true);

        assertThatThrownBy(() -> teacherAssignmentService.createAssignment(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("Get assignment by ID successfully")
    void getAssignment_success() {
        when(teacherAssignmentRepository.findByIdAndNotDeleted(assignmentId)).thenReturn(Optional.of(assignment));

        TeacherAssignmentDetailResponse response = teacherAssignmentService.getAssignment(assignmentId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(assignmentId);
        assertThat(response.getAssignmentCode()).isEqualTo("TSA-001");
    }

    @Test
    @DisplayName("Get assignment throws 404 when not found")
    void getAssignment_notFound() {
        when(teacherAssignmentRepository.findByIdAndNotDeleted(assignmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teacherAssignmentService.getAssignment(assignmentId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Update assignment successfully")
    void updateAssignment_success() {
        UpdateTeacherAssignmentRequest request = new UpdateTeacherAssignmentRequest();
        request.setIsPrimary(false);
        request.setStartDate(LocalDate.of(2026, 4, 15));
        request.setEndDate(LocalDate.of(2026, 10, 31));

        when(teacherAssignmentRepository.findByIdAndNotDeleted(assignmentId)).thenReturn(Optional.of(assignment));
        when(teacherAssignmentRepository.save(any(TeacherSubjectAssignment.class))).thenReturn(assignment);

        TeacherAssignmentDetailResponse response = teacherAssignmentService.updateAssignment(assignmentId, request);

        assertThat(response).isNotNull();
        assertThat(assignment.isPrimary()).isFalse();
        assertThat(assignment.getStartDate()).isEqualTo(LocalDate.of(2026, 4, 15));
        assertThat(assignment.getEndDate()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    @DisplayName("Update assignment status to COMPLETED")
    void updateStatus_completed() {
        UpdateTeacherAssignmentStatusRequest request = new UpdateTeacherAssignmentStatusRequest();
        request.setStatus(TeacherAssignmentStatus.COMPLETED);

        when(teacherAssignmentRepository.findByIdAndNotDeleted(assignmentId)).thenReturn(Optional.of(assignment));
        when(teacherAssignmentRepository.save(any(TeacherSubjectAssignment.class))).thenReturn(assignment);

        TeacherAssignmentDetailResponse response = teacherAssignmentService.updateStatus(assignmentId, request);

        assertThat(response).isNotNull();
        assertThat(assignment.getStatus()).isEqualTo(TeacherAssignmentStatus.COMPLETED);
        assertThat(assignment.getEndDate()).isNotNull();
    }

    @Test
    @DisplayName("Delete assignment soft deletes successfully")
    void deleteAssignment_success() {
        when(teacherAssignmentRepository.findByIdAndNotDeleted(assignmentId)).thenReturn(Optional.of(assignment));
        when(teacherAssignmentRepository.save(any(TeacherSubjectAssignment.class))).thenReturn(assignment);

        teacherAssignmentService.deleteAssignment(assignmentId);

        assertThat(assignment.isDeleted()).isTrue();
        assertThat(assignment.getStatus()).isEqualTo(TeacherAssignmentStatus.DELETED);
    }
}
