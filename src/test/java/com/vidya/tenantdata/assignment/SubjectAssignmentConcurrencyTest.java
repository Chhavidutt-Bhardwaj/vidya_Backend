package com.vidya.tenantdata.assignment;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.assignment.dto.CreateClassSubjectAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.CreateTeacherAssignmentRequest;
import com.vidya.tenantdata.assignment.dto.TeacherAssignmentDetailResponse;
import com.vidya.tenantdata.assignment.entity.ClassSubjectAssignment;
import com.vidya.tenantdata.assignment.entity.TeacherSubjectAssignment;
import com.vidya.tenantdata.assignment.repository.ClassSubjectAssignmentRepository;
import com.vidya.tenantdata.assignment.repository.TeacherSubjectAssignmentRepository;
import com.vidya.tenantdata.assignment.service.ClassSubjectAssignmentServiceImpl;
import com.vidya.tenantdata.assignment.service.TeacherSubjectAssignmentServiceImpl;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.enums.StaffType;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.enums.SubjectType;
import com.vidya.tenantdata.subject.repository.CurriculumRepository;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserProfile;
import com.vidya.tenantdata.user.entity.TenantUserType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Subject Assignment — Concurrency & Duplicate Race Condition Tests")
class SubjectAssignmentConcurrencyTest {

    @Mock
    private TeacherSubjectAssignmentRepository teacherAssignmentRepository;

    @Mock
    private ClassSubjectAssignmentRepository classSubjectAssignmentRepository;

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

    @InjectMocks
    private ClassSubjectAssignmentServiceImpl classSubjectAssignmentService;

    @Test
    @DisplayName("Concurrent duplicate teacher assignment creation: exactly one succeeds, others fail")
    void concurrentTeacherAssignmentCreation_onlyOneSucceeds() throws Exception {
        UUID teacherId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID academicYearId = UUID.randomUUID();
        UUID academicClassId = UUID.randomUUID();

        TenantUser tenantUser = new TenantUser("USR-001", "teacher@school.com", "9876543210", TenantUserType.STAFF);
        tenantUser.setId(UUID.randomUUID());
        TenantUserProfile profile = new TenantUserProfile(tenantUser, "Jane", "Doe");
        tenantUser.setProfile(profile);

        Staff teacher = new Staff("STF-001", tenantUser, "EMP-101", LocalDate.now(), StaffType.TEACHER, "Teacher", "Science");
        teacher.setId(teacherId);

        Subject subject = new Subject("SUB-001", "Physics", "PHY", "Physics", SubjectType.CORE, 1);
        subject.setId(subjectId);

        AcademicYear academicYear = new AcademicYear("AY-2026", "2026-2027", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), true, AcademicYearStatus.ACTIVE);
        academicYear.setId(academicYearId);

        AcademicClass academicClass = new AcademicClass("CLS-11", "Class 11", "Class 11", 11);
        academicClass.setId(academicClassId);

        when(staffRepository.findByIdAndNotDeleted(teacherId)).thenReturn(Optional.of(teacher));
        when(subjectRepository.findByIdAndNotDeleted(subjectId)).thenReturn(Optional.of(subject));
        when(academicYearRepository.findByIdAndNotDeleted(academicYearId)).thenReturn(Optional.of(academicYear));
        when(academicClassRepository.findByIdAndNotDeleted(academicClassId)).thenReturn(Optional.of(academicClass));

        AtomicBoolean savedFirst = new AtomicBoolean(false);
        when(teacherAssignmentRepository.existsActiveDuplicate(teacherId, subjectId, academicYearId, academicClassId, null, null))
                .thenAnswer(i -> savedFirst.get());
        when(teacherAssignmentRepository.save(any(TeacherSubjectAssignment.class))).thenAnswer(i -> {
            if (!savedFirst.compareAndSet(false, true)) {
                throw new DuplicateResourceException("TeacherSubjectAssignment", "unique_active", "Duplicate active assignment");
            }
            TeacherSubjectAssignment a = i.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            results.add(executor.submit(() -> {
                startLatch.await();
                try {
                    CreateTeacherAssignmentRequest request = new CreateTeacherAssignmentRequest();
                    request.setTeacherId(teacherId);
                    request.setSubjectId(subjectId);
                    request.setAcademicYearId(academicYearId);
                    request.setAcademicClassId(academicClassId);
                    request.setIsPrimary(false);

                    TeacherAssignmentDetailResponse resp = teacherAssignmentService.createAssignment(request);
                    return resp != null;
                } catch (DuplicateResourceException e) {
                    return false;
                }
            }));
        }

        startLatch.countDown();
        executor.shutdown();

        int successes = 0;
        int failures = 0;
        for (Future<Boolean> f : results) {
            if (f.get()) {
                successes++;
            } else {
                failures++;
            }
        }

        assertThat(successes).isEqualTo(1);
        assertThat(failures).isEqualTo(threadCount - 1);
    }

    @Test
    @DisplayName("Concurrent duplicate class subject deployment: exactly one succeeds, others fail")
    void concurrentClassSubjectDeployment_onlyOneSucceeds() throws Exception {
        UUID subjectId = UUID.randomUUID();
        UUID academicYearId = UUID.randomUUID();
        UUID academicClassId = UUID.randomUUID();

        Subject subject = new Subject("SUB-002", "Chemistry", "CHE", "Chemistry", SubjectType.CORE, 2);
        subject.setId(subjectId);

        AcademicYear academicYear = new AcademicYear("AY-2026", "2026-2027", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), true, AcademicYearStatus.ACTIVE);
        academicYear.setId(academicYearId);

        AcademicClass academicClass = new AcademicClass("CLS-11", "Class 11", "Class 11", 11);
        academicClass.setId(academicClassId);

        when(subjectRepository.findByIdAndNotDeleted(subjectId)).thenReturn(Optional.of(subject));
        when(academicYearRepository.findByIdAndNotDeleted(academicYearId)).thenReturn(Optional.of(academicYear));
        when(academicClassRepository.findByIdAndNotDeleted(academicClassId)).thenReturn(Optional.of(academicClass));

        AtomicBoolean savedFirst = new AtomicBoolean(false);
        when(classSubjectAssignmentRepository.existsActiveDuplicate(academicYearId, academicClassId, null, subjectId, null))
                .thenAnswer(i -> savedFirst.get());
        when(classSubjectAssignmentRepository.save(any(ClassSubjectAssignment.class))).thenAnswer(i -> {
            if (!savedFirst.compareAndSet(false, true)) {
                throw new DuplicateResourceException("ClassSubjectAssignment", "unique_active", "Duplicate active deployment");
            }
            ClassSubjectAssignment a = i.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            results.add(executor.submit(() -> {
                startLatch.await();
                try {
                    CreateClassSubjectAssignmentRequest request = new CreateClassSubjectAssignmentRequest();
                    request.setSubjectId(subjectId);
                    request.setAcademicYearId(academicYearId);
                    request.setAcademicClassId(academicClassId);

                    return classSubjectAssignmentService.createAssignment(request) != null;
                } catch (DuplicateResourceException e) {
                    return false;
                }
            }));
        }

        startLatch.countDown();
        executor.shutdown();

        int successes = 0;
        int failures = 0;
        for (Future<Boolean> f : results) {
            if (f.get()) {
                successes++;
            } else {
                failures++;
            }
        }

        assertThat(successes).isEqualTo(1);
        assertThat(failures).isEqualTo(threadCount - 1);
    }
}
