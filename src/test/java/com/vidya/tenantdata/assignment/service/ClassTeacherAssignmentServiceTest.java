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
import com.vidya.tenantdata.assignment.dto.ClassTeacherAssignmentDetailResponse;
import com.vidya.tenantdata.assignment.dto.ClassTeacherAssignmentResponse;
import com.vidya.tenantdata.assignment.dto.CreateClassTeacherAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.ReplaceClassTeacherRequest;
import com.vidya.tenantdata.assignment.dto.UpdateClassTeacherAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.UpdateClassTeacherAssignmentStatusRequest;
import com.vidya.tenantdata.assignment.entity.ClassTeacherAssignment;
import com.vidya.tenantdata.assignment.enums.ClassTeacherAssignmentStatus;
import com.vidya.tenantdata.assignment.repository.ClassTeacherAssignmentRepository;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.enums.StaffType;
import com.vidya.tenantdata.staff.repository.StaffRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClassTeacherAssignmentService — Unit Tests")
class ClassTeacherAssignmentServiceTest {

    @Mock
    private ClassTeacherAssignmentRepository assignmentRepository;

    @Mock
    private AcademicYearRepository academicYearRepository;

    @Mock
    private AcademicClassRepository academicClassRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private StaffRepository staffRepository;

    @InjectMocks
    private ClassTeacherAssignmentServiceImpl service;

    private AcademicYear academicYear;
    private AcademicClass academicClass;
    private Section section;
    private Staff staff;
    private Staff replacementStaff;
    private ClassTeacherAssignment assignment;

    private final UUID academicYearId = UUID.randomUUID();
    private final UUID academicClassId = UUID.randomUUID();
    private final UUID sectionId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID replacementStaffId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        academicYear = new AcademicYear("AY-2026", "2026-2027", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), true, AcademicYearStatus.ACTIVE);
        academicYear.setId(academicYearId);

        academicClass = new AcademicClass("CLS-10", "Class 10", "Class 10", 10);
        academicClass.setId(academicClassId);

        section = new Section(academicYear, academicClass, "SEC-10A", "Section A", 40);
        section.setId(sectionId);

        TenantUser user = new TenantUser("USR-001", "user1@test.com", "9876543210", TenantUserType.STAFF);
        user.setId(UUID.randomUUID());
        TenantUserProfile profile = new TenantUserProfile(user, "John", "Doe");
        user.setProfile(profile);

        staff = new Staff("STF-001", user, "EMP001", LocalDate.now(), StaffType.TEACHER, "Teacher", "Computer Science");
        staff.setId(staffId);
        staff.setEmploymentStatus(EmploymentStatus.ACTIVE);

        TenantUser user2 = new TenantUser("USR-002", "user2@test.com", "9876543211", TenantUserType.STAFF);
        user2.setId(UUID.randomUUID());
        TenantUserProfile profile2 = new TenantUserProfile(user2, "Jane", "Smith");
        user2.setProfile(profile2);

        replacementStaff = new Staff("STF-002", user2, "EMP002", LocalDate.now(), StaffType.TEACHER, "Teacher", "Mathematics");
        replacementStaff.setId(replacementStaffId);
        replacementStaff.setEmploymentStatus(EmploymentStatus.ACTIVE);

        assignment = new ClassTeacherAssignment("CTA-A1B2C3D4", staff, academicYear, academicClass, section, LocalDate.of(2026, 4, 1), "Class teacher assignment");
        assignment.setId(assignmentId);
    }

    @Test
    @DisplayName("Create Assignment — Success")
    void createAssignment_Success() {
        CreateClassTeacherAssignmentRequest request = new CreateClassTeacherAssignmentRequest(
                staffId, academicYearId, academicClassId, sectionId, LocalDate.of(2026, 4, 1), "Class Teacher for 10-A"
        );

        when(staffRepository.findByIdAndNotDeleted(staffId)).thenReturn(Optional.of(staff));
        when(academicYearRepository.findByIdAndNotDeleted(academicYearId)).thenReturn(Optional.of(academicYear));
        when(academicClassRepository.findByIdAndNotDeleted(academicClassId)).thenReturn(Optional.of(academicClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(section));
        when(assignmentRepository.existsActiveBySectionAndAcademicYear(academicYearId, sectionId, null)).thenReturn(false);
        when(assignmentRepository.existsByAssignmentCode(any())).thenReturn(false);
        when(assignmentRepository.save(any(ClassTeacherAssignment.class))).thenAnswer(invocation -> {
            ClassTeacherAssignment cta = invocation.getArgument(0);
            cta.setId(assignmentId);
            return cta;
        });

        ClassTeacherAssignmentDetailResponse response = service.createAssignment(request);

        assertThat(response).isNotNull();
        assertThat(response.getStaffId()).isEqualTo(staffId);
        assertThat(response.getSectionId()).isEqualTo(sectionId);
        assertThat(response.getStatus()).isEqualTo(ClassTeacherAssignmentStatus.ACTIVE);
    }

    @Test
    @DisplayName("Create Assignment — Fails if section already has active class teacher")
    void createAssignment_FailsWhenActiveAssignmentExists() {
        CreateClassTeacherAssignmentRequest request = new CreateClassTeacherAssignmentRequest(
                staffId, academicYearId, academicClassId, sectionId, LocalDate.of(2026, 4, 1), "Class Teacher"
        );

        when(staffRepository.findByIdAndNotDeleted(staffId)).thenReturn(Optional.of(staff));
        when(academicYearRepository.findByIdAndNotDeleted(academicYearId)).thenReturn(Optional.of(academicYear));
        when(academicClassRepository.findByIdAndNotDeleted(academicClassId)).thenReturn(Optional.of(academicClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(section));
        when(assignmentRepository.existsActiveBySectionAndAcademicYear(academicYearId, sectionId, null)).thenReturn(true);

        assertThatThrownBy(() -> service.createAssignment(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already assigned to this section");
    }

    @Test
    @DisplayName("Create Assignment — Fails if staff is not active")
    void createAssignment_FailsWhenStaffNotActive() {
        staff.setEmploymentStatus(EmploymentStatus.TERMINATED);

        CreateClassTeacherAssignmentRequest request = new CreateClassTeacherAssignmentRequest(
                staffId, academicYearId, academicClassId, sectionId, LocalDate.of(2026, 4, 1), "Class Teacher"
        );

        when(staffRepository.findByIdAndNotDeleted(staffId)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> service.createAssignment(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("employment status: TERMINATED");
    }

    @Test
    @DisplayName("Create Assignment — Fails if section does not belong to class")
    void createAssignment_FailsWhenSectionMismatch() {
        AcademicClass otherClass = new AcademicClass("CLS-11", "Class 11", "Class 11", 11);
        otherClass.setId(UUID.randomUUID());
        Section mismatchedSection = new Section(academicYear, otherClass, "SEC-11A", "Section A", 40);
        mismatchedSection.setId(sectionId);

        CreateClassTeacherAssignmentRequest request = new CreateClassTeacherAssignmentRequest(
                staffId, academicYearId, academicClassId, sectionId, LocalDate.of(2026, 4, 1), "Class Teacher"
        );

        when(staffRepository.findByIdAndNotDeleted(staffId)).thenReturn(Optional.of(staff));
        when(academicYearRepository.findByIdAndNotDeleted(academicYearId)).thenReturn(Optional.of(academicYear));
        when(academicClassRepository.findByIdAndNotDeleted(academicClassId)).thenReturn(Optional.of(academicClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(mismatchedSection));

        assertThatThrownBy(() -> service.createAssignment(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Section does not belong to the specified AcademicClass");
    }

    @Test
    @DisplayName("Get by ID — Success and Not Found")
    void getById_SuccessAndNotFound() {
        when(assignmentRepository.findByIdAndNotDeleted(assignmentId)).thenReturn(Optional.of(assignment));
        ClassTeacherAssignmentDetailResponse response = service.getAssignment(assignmentId);
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(assignmentId);

        UUID nonExistentId = UUID.randomUUID();
        when(assignmentRepository.findByIdAndNotDeleted(nonExistentId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getAssignment(nonExistentId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Get Current Class Teacher — Success and Not Found")
    void getCurrentClassTeacher() {
        when(assignmentRepository.findActiveBySectionAndAcademicYear(academicYearId, sectionId))
                .thenReturn(Optional.of(assignment));

        ClassTeacherAssignmentDetailResponse current = service.getCurrentClassTeacher(sectionId, academicYearId);
        assertThat(current).isNotNull();
        assertThat(current.getStaffId()).isEqualTo(staffId);

        when(assignmentRepository.findActiveBySectionAndAcademicYear(academicYearId, sectionId))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getCurrentClassTeacher(sectionId, academicYearId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Replace Class Teacher — Success")
    void replaceClassTeacher_Success() {
        ReplaceClassTeacherRequest request = new ReplaceClassTeacherRequest(
                replacementStaffId,
                LocalDate.of(2026, 8, 1),
                "Handover due to maternity leave"
        );

        when(assignmentRepository.findByIdAndNotDeleted(assignmentId)).thenReturn(Optional.of(assignment));
        when(staffRepository.findByIdAndNotDeleted(replacementStaffId)).thenReturn(Optional.of(replacementStaff));
        when(assignmentRepository.existsByAssignmentCode(any())).thenReturn(false);
        when(assignmentRepository.save(any(ClassTeacherAssignment.class))).thenAnswer(invocation -> {
            ClassTeacherAssignment cta = invocation.getArgument(0);
            if (cta.getId() == null) {
                cta.setId(UUID.randomUUID());
            }
            return cta;
        });

        ClassTeacherAssignmentDetailResponse response = service.replaceClassTeacher(assignmentId, request);

        assertThat(response).isNotNull();
        assertThat(response.getStaffId()).isEqualTo(replacementStaffId);
        assertThat(response.getStatus()).isEqualTo(ClassTeacherAssignmentStatus.ACTIVE);
        assertThat(assignment.getStatus()).isEqualTo(ClassTeacherAssignmentStatus.COMPLETED);
        assertThat(assignment.getEndDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test
    @DisplayName("Replace Class Teacher — Fails if replacing with same teacher")
    void replaceClassTeacher_FailsWhenSameTeacher() {
        ReplaceClassTeacherRequest request = new ReplaceClassTeacherRequest(
                staffId,
                LocalDate.of(2026, 8, 1),
                "Handover notes"
        );

        when(assignmentRepository.findByIdAndNotDeleted(assignmentId)).thenReturn(Optional.of(assignment));
        when(staffRepository.findByIdAndNotDeleted(staffId)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> service.replaceClassTeacher(assignmentId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot replace class teacher with the same staff member");
    }

    @Test
    @DisplayName("Update Status — Success and Invalid Transition")
    void updateStatus() {
        when(assignmentRepository.findByIdAndNotDeleted(assignmentId)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any())).thenReturn(assignment);

        UpdateClassTeacherAssignmentStatusRequest req = new UpdateClassTeacherAssignmentStatusRequest(
                ClassTeacherAssignmentStatus.COMPLETED
        );

        ClassTeacherAssignmentDetailResponse resp = service.updateStatus(assignmentId, req);
        assertThat(resp.getStatus()).isEqualTo(ClassTeacherAssignmentStatus.COMPLETED);

        // From COMPLETED, transition to ACTIVE is invalid
        UpdateClassTeacherAssignmentStatusRequest invalidReq = new UpdateClassTeacherAssignmentStatusRequest(
                ClassTeacherAssignmentStatus.ACTIVE
        );
        assertThatThrownBy(() -> service.updateStatus(assignmentId, invalidReq))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Delete Assignment — Soft deletes")
    void deleteAssignment() {
        when(assignmentRepository.findByIdAndNotDeleted(assignmentId)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any())).thenReturn(assignment);

        service.deleteAssignment(assignmentId);

        assertThat(assignment.isDeleted()).isTrue();
        assertThat(assignment.getStatus()).isEqualTo(ClassTeacherAssignmentStatus.DELETED);
    }

    @Test
    @DisplayName("Search Assignments — Success")
    void searchAssignments() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<ClassTeacherAssignment> page = new PageImpl<>(List.of(assignment), pageable, 1);
        when(assignmentRepository.searchAssignments(null, academicYearId, null, null, ClassTeacherAssignmentStatus.ACTIVE, pageable))
                .thenReturn(page);

        Page<ClassTeacherAssignmentResponse> result = service.searchAssignments(null, academicYearId, null, null, ClassTeacherAssignmentStatus.ACTIVE, pageable);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }
}
