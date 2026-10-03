package com.vidya.tenantdata.academic;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.tenantdata.academic.dto.AssignStudentRequest;
import com.vidya.tenantdata.academic.dto.StudentAcademicAssignmentResponse;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.entity.StudentAcademicAssignment;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.academic.repository.StudentAcademicAssignmentRepository;
import com.vidya.tenantdata.academic.service.StudentAcademicAssignmentServiceImpl;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.repository.StudentRepository;
import com.vidya.tenantdata.user.entity.TenantUser;
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
@DisplayName("Academic Structure — Concurrency & Invariant Tests")
class AcademicConcurrencyTest {

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

    @Test
    @DisplayName("Concurrent assignment of same student in same academic year — Exactly one succeeds, second fails safely")
    void testConcurrentStudentAssignment_RaceCondition() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID yearId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();

        TenantUser user = new TenantUser("USR-C", "student.c@school.edu.in", null, TenantUserType.STUDENT);
        user.setId(UUID.randomUUID());

        Student student = new Student("STU-C", user, "ADM-C", LocalDate.of(2026, 4, 1));
        student.setId(studentId);

        AcademicYear year = new AcademicYear("AY2026-27", "2026-2027", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), true, AcademicYearStatus.ACTIVE);
        year.setId(yearId);

        AcademicClass cls = new AcademicClass("CLASS_5", "Class 5", "Grade 5", 5);
        cls.setId(classId);

        Section section = new Section(year, cls, "A", "Section A", 40);
        section.setId(sectionId);

        when(studentRepository.findByIdAndNotDeleted(studentId)).thenReturn(Optional.of(student));
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(year));
        when(academicClassRepository.findByIdAndNotDeleted(classId)).thenReturn(Optional.of(cls));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(section));
        when(assignmentRepository.countActiveAssignmentsBySectionId(sectionId)).thenReturn(0L);

        AtomicBoolean hasActiveAssignment = new AtomicBoolean(false);
        when(assignmentRepository.findActiveAssignment(studentId, yearId)).thenAnswer(inv -> {
            if (hasActiveAssignment.compareAndSet(false, true)) {
                return Optional.empty();
            } else {
                StudentAcademicAssignment existing = new StudentAcademicAssignment(student, year, cls, section, "1", LocalDate.of(2026, 4, 1));
                return Optional.of(existing);
            }
        });

        when(assignmentRepository.save(any(StudentAcademicAssignment.class))).thenAnswer(inv -> {
            StudentAcademicAssignment a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        AssignStudentRequest request1 = AssignStudentRequest.builder()
                .academicYearId(yearId)
                .academicClassId(classId)
                .sectionId(sectionId)
                .rollNumber("1")
                .build();

        AssignStudentRequest request2 = AssignStudentRequest.builder()
                .academicYearId(yearId)
                .academicClassId(classId)
                .sectionId(sectionId)
                .rollNumber("2")
                .build();

        int numThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<StudentAcademicAssignmentResponse>> futures = new ArrayList<>();
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        futures.add(executor.submit(() -> {
            try {
                startLatch.await();
                return assignmentService.assignStudent(studentId, request1);
            } catch (Exception e) {
                exceptions.add(e);
                throw e;
            }
        }));

        futures.add(executor.submit(() -> {
            try {
                startLatch.await();
                return assignmentService.assignStudent(studentId, request2);
            } catch (Exception e) {
                exceptions.add(e);
                throw e;
            }
        }));

        startLatch.countDown();
        executor.shutdown();

        int successCount = 0;
        for (Future<StudentAcademicAssignmentResponse> f : futures) {
            try {
                StudentAcademicAssignmentResponse resp = f.get();
                if (resp != null) successCount++;
            } catch (Exception ignored) {
            }
        }

        assertThat(successCount).isEqualTo(1);
        assertThat(exceptions).hasSize(1);
        assertThat(exceptions.get(0)).isInstanceOf(DuplicateResourceException.class);
    }
}
