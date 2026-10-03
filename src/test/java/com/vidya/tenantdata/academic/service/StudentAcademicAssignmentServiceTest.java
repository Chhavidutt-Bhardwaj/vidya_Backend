package com.vidya.tenantdata.academic.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.dto.AssignStudentRequest;
import com.vidya.tenantdata.academic.dto.StudentAcademicAssignmentResponse;
import com.vidya.tenantdata.academic.dto.TransferStudentAssignmentRequest;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.entity.StudentAcademicAssignment;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.enums.StudentAssignmentStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.academic.repository.StudentAcademicAssignmentRepository;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.repository.StudentRepository;
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

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StudentAcademicAssignmentService — Unit Tests")
class StudentAcademicAssignmentServiceTest {

    @Mock
    private StudentAcademicAssignmentRepository assignmentRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private AcademicYearRepository academicYearRepository;

    @Mock
    private AcademicClassRepository academicClassRepository;

    @Mock
    private SectionRepository sectionRepository;

    @InjectMocks
    private StudentAcademicAssignmentServiceImpl assignmentService;

    private UUID studentId;
    private UUID yearId;
    private UUID classId;
    private UUID sectionId;
    private UUID assignmentId;

    private Student sampleStudent;
    private AcademicYear sampleYear;
    private AcademicClass sampleClass;
    private Section sampleSection;
    private StudentAcademicAssignment sampleAssignment;

    @BeforeEach
    void setUp() {
        studentId = UUID.randomUUID();
        yearId = UUID.randomUUID();
        classId = UUID.randomUUID();
        sectionId = UUID.randomUUID();
        assignmentId = UUID.randomUUID();

        TenantUser user = new TenantUser("USR-01", "student@school.edu.in", null, TenantUserType.STUDENT);
        user.setId(UUID.randomUUID());
        user.setProfile(new TenantUserProfile(user, "Aarav", "Sharma"));

        sampleStudent = new Student("STU-01", user, "ADM-01", LocalDate.of(2026, 4, 1));
        sampleStudent.setId(studentId);

        sampleYear = new AcademicYear("AY2026-27", "2026-2027", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), true, AcademicYearStatus.ACTIVE);
        sampleYear.setId(yearId);

        sampleClass = new AcademicClass("CLASS_5", "Class 5", "Grade 5", 5);
        sampleClass.setId(classId);

        sampleSection = new Section(sampleYear, sampleClass, "A", "Section A", 40);
        sampleSection.setId(sectionId);

        sampleAssignment = new StudentAcademicAssignment(sampleStudent, sampleYear, sampleClass, sampleSection, "12", LocalDate.of(2026, 4, 1));
        sampleAssignment.setId(assignmentId);
    }

    @Test
    @DisplayName("Assign student succeeds when valid structure and no existing active placement")
    void assignStudent_Success() {
        AssignStudentRequest request = AssignStudentRequest.builder()
                .academicYearId(yearId)
                .academicClassId(classId)
                .sectionId(sectionId)
                .rollNumber("12")
                .startDate(LocalDate.of(2026, 4, 1))
                .build();

        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(sampleStudent));
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(sampleYear));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(sampleClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(sampleSection));
        when(assignmentRepository.findActiveAssignment(studentId, yearId)).thenReturn(Optional.empty());
        when(assignmentRepository.countActiveAssignmentsBySectionId(sectionId)).thenReturn(10L);
        when(assignmentRepository.save(any(StudentAcademicAssignment.class))).thenAnswer(inv -> {
            StudentAcademicAssignment a = inv.getArgument(0);
            a.setId(assignmentId);
            return a;
        });

        StudentAcademicAssignmentResponse response = assignmentService.assignStudent(studentId, request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(assignmentId);
        assertThat(response.getRollNumber()).isEqualTo("12");
        assertThat(response.getStatus()).isEqualTo(StudentAssignmentStatus.ACTIVE);
        assertThat(response.getStudentName()).isEqualTo("Aarav Sharma");
    }

    @Test
    @DisplayName("Assign student fails when student already has active assignment in same academic year")
    void assignStudent_DuplicateActiveAssignment() {
        AssignStudentRequest request = AssignStudentRequest.builder()
                .academicYearId(yearId)
                .academicClassId(classId)
                .sectionId(sectionId)
                .build();

        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(sampleStudent));
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(sampleYear));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(sampleClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(sampleSection));
        when(assignmentRepository.findActiveAssignment(studentId, yearId)).thenReturn(Optional.of(sampleAssignment));

        assertThatThrownBy(() -> assignmentService.assignStudent(studentId, request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("Assign student fails when section capacity is reached")
    void assignStudent_CapacityReached() {
        AssignStudentRequest request = AssignStudentRequest.builder()
                .academicYearId(yearId)
                .academicClassId(classId)
                .sectionId(sectionId)
                .build();

        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(sampleStudent));
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(sampleYear));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(sampleClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(sampleSection));
        when(assignmentRepository.findActiveAssignment(studentId, yearId)).thenReturn(Optional.empty());
        when(assignmentRepository.countActiveAssignmentsBySectionId(sectionId)).thenReturn(40L); // Capacity is 40

        assertThatThrownBy(() -> assignmentService.assignStudent(studentId, request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Transfer student completes old assignment and creates new active assignment with transferDate")
    void transferStudent_Success() {
        UUID newClassId = UUID.randomUUID();
        UUID newSectionId = UUID.randomUUID();
        AcademicClass newClass = new AcademicClass("CLASS_5", "Class 5", "Grade 5", 5);
        newClass.setId(newClassId);

        Section newSection = new Section(sampleYear, newClass, "B", "Section B", 40);
        newSection.setId(newSectionId);

        TransferStudentAssignmentRequest request = TransferStudentAssignmentRequest.builder()
                .newAcademicClassId(newClassId)
                .newSectionId(newSectionId)
                .newRollNumber("15")
                .transferDate(LocalDate.of(2026, 7, 1))
                .build();

        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(sampleStudent));
        when(assignmentRepository.findByIdAndNotDeleted(assignmentId)).thenReturn(Optional.of(sampleAssignment));
        when(academicClassRepository.findByIdAndNotDeleted(newClassId)).thenReturn(Optional.of(newClass));
        when(sectionRepository.findByIdAndNotDeleted(newSectionId)).thenReturn(Optional.of(newSection));
        when(assignmentRepository.countActiveAssignmentsBySectionId(newSectionId)).thenReturn(5L);
        when(assignmentRepository.save(any(StudentAcademicAssignment.class))).thenAnswer(inv -> {
            StudentAcademicAssignment a = inv.getArgument(0);
            if (a.getId() == null) a.setId(UUID.randomUUID());
            return a;
        });

        StudentAcademicAssignmentResponse newAssignmentResp = assignmentService.transferStudent(studentId, assignmentId, request);

        assertThat(newAssignmentResp).isNotNull();
        assertThat(newAssignmentResp.getStatus()).isEqualTo(StudentAssignmentStatus.ACTIVE);
        assertThat(newAssignmentResp.getRollNumber()).isEqualTo("15");
        assertThat(newAssignmentResp.getStartDate()).isEqualTo(LocalDate.of(2026, 7, 1));

        // Verify old assignment transitioned
        assertThat(sampleAssignment.getStatus()).isEqualTo(StudentAssignmentStatus.TRANSFERRED);
        assertThat(sampleAssignment.getEndDate()).isEqualTo(LocalDate.of(2026, 7, 1));
    }
}
