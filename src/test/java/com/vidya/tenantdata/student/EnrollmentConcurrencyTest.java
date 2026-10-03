package com.vidya.tenantdata.student;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.student.dto.CreateEnrollmentRequest;
import com.vidya.tenantdata.student.dto.EnrollmentDetailResponse;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.entity.StudentAcademicEnrollment;
import com.vidya.tenantdata.student.repository.StudentAcademicEnrollmentRepository;
import com.vidya.tenantdata.student.repository.StudentRepository;
import com.vidya.tenantdata.student.service.StudentEnrollmentServiceImpl;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.vidya.tenantdata.student.repository.StudentSubjectEnrollmentRepository;
import com.vidya.tenantdata.student.service.StudentSubjectEnrollmentService;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Student Enrollment — Concurrency & Duplicate Race Condition Tests")
class EnrollmentConcurrencyTest {

    @Mock
    private StudentAcademicEnrollmentRepository enrollmentRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private AcademicYearRepository academicYearRepository;

    @Mock
    private AcademicClassRepository academicClassRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private StudentSubjectEnrollmentService subjectEnrollmentService;

    @Mock
    private StudentSubjectEnrollmentRepository subjectEnrollmentRepository;

    @InjectMocks
    private StudentEnrollmentServiceImpl enrollmentService;

    @Test
    @DisplayName("Concurrent enrollment creation with same roll number in section — Exactly one succeeds, other fails safely")
    void testConcurrentEnrollment_DuplicateRollNumberRace() throws Exception {
        UUID student1Id = UUID.randomUUID();
        UUID student2Id = UUID.randomUUID();
        UUID yearId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();

        AcademicYear year = new AcademicYear("AY-2026", "Academic Year 2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 4, 30), true, AcademicYearStatus.ACTIVE);
        year.setId(yearId);
        AcademicClass clazz = new AcademicClass("CLS-10", "Class 10", "Grade 10", 10);
        clazz.setId(classId);
        Section section = new Section(year, clazz, "SEC-A", "Section A", 40);
        section.setId(sectionId);

        TenantUser u1 = new TenantUser("USR-1", "s1@school.edu.in", null, TenantUserType.STUDENT);
        u1.setId(UUID.randomUUID());
        u1.setProfile(new TenantUserProfile(u1, "Student", "One"));
        Student s1 = new Student("STU-1", u1, "ADM-1", LocalDate.of(2026, 6, 1));
        s1.setId(student1Id);

        TenantUser u2 = new TenantUser("USR-2", "s2@school.edu.in", null, TenantUserType.STUDENT);
        u2.setId(UUID.randomUUID());
        u2.setProfile(new TenantUserProfile(u2, "Student", "Two"));
        Student s2 = new Student("STU-2", u2, "ADM-2", LocalDate.of(2026, 6, 1));
        s2.setId(student2Id);

        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(year));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(clazz));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(section));
        when(studentRepository.findByIdAndNotDeleted(student1Id)).thenReturn(Optional.of(s1));
        when(studentRepository.findByIdAndNotDeleted(student2Id)).thenReturn(Optional.of(s2));
        when(enrollmentRepository.existsActiveEnrollmentForStudentAndYear(any(UUID.class), eq(yearId))).thenReturn(false);
        when(enrollmentRepository.existsByEnrollmentCode(anyString())).thenReturn(false);

        AtomicBoolean rollTaken = new AtomicBoolean(false);
        when(enrollmentRepository.existsActiveRollNumber(eq(yearId), eq(classId), eq(sectionId), eq("5"), any()))
                .thenAnswer(inv -> rollTaken.getAndSet(true));

        when(enrollmentRepository.save(any(StudentAcademicEnrollment.class))).thenAnswer(inv -> {
            StudentAcademicEnrollment e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        CreateEnrollmentRequest req1 = CreateEnrollmentRequest.builder()
                .academicYearId(yearId)
                .academicClassId(classId)
                .sectionId(sectionId)
                .rollNumber("5")
                .enrollmentDate(LocalDate.of(2026, 6, 1))
                .autoAssignCompulsorySubjects(false)
                .build();

        CreateEnrollmentRequest req2 = CreateEnrollmentRequest.builder()
                .academicYearId(yearId)
                .academicClassId(classId)
                .sectionId(sectionId)
                .rollNumber("5")
                .enrollmentDate(LocalDate.of(2026, 6, 1))
                .autoAssignCompulsorySubjects(false)
                .build();

        int numThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<EnrollmentDetailResponse>> futures = new ArrayList<>();
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        futures.add(executor.submit(() -> {
            try {
                startLatch.await();
                return enrollmentService.createEnrollment(student1Id, req1);
            } catch (Exception e) {
                exceptions.add(e);
                throw e;
            }
        }));

        futures.add(executor.submit(() -> {
            try {
                startLatch.await();
                return enrollmentService.createEnrollment(student2Id, req2);
            } catch (Exception e) {
                exceptions.add(e);
                throw e;
            }
        }));

        startLatch.countDown();
        executor.shutdown();

        int successCount = 0;
        for (Future<EnrollmentDetailResponse> f : futures) {
            try {
                EnrollmentDetailResponse resp = f.get();
                if (resp != null) successCount++;
            } catch (Exception ignored) {
            }
        }

        assertThat(successCount).isEqualTo(1);
        assertThat(exceptions).hasSize(1);
        assertThat(exceptions.get(0)).isInstanceOf(DuplicateResourceException.class);
    }
}
