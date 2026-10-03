package com.vidya.tenantdata.student;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.tenantdata.student.dto.CreateStudentRequest;
import com.vidya.tenantdata.student.dto.StudentDetailResponse;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.repository.StudentRepository;
import com.vidya.tenantdata.student.service.StudentServiceImpl;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserProfile;
import com.vidya.tenantdata.user.entity.TenantUserType;
import com.vidya.tenantdata.user.repository.TenantUserRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Student — Concurrency & Duplicate Race Condition Tests")
class StudentConcurrencyTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private TenantUserRepository tenantUserRepository;

    @Mock
    private com.vidya.tenantdata.student.service.StudentEnrollmentService enrollmentService;

    @Mock
    private com.vidya.tenantdata.student.service.StudentGuardianService guardianService;

    @InjectMocks
    private StudentServiceImpl studentService;

    @Test
    @DisplayName("Concurrent student creation with same admission number — Exactly one succeeds, other fails safely")
    void testConcurrentStudentCreation_DuplicateAdmissionRace() throws Exception {
        UUID user1Id = UUID.randomUUID();
        UUID user2Id = UUID.randomUUID();
        String admissionNumber = "ADM-CONCURRENT-001";

        TenantUser user1 = new TenantUser("USR-C1", "u1@school.edu.in", null, TenantUserType.STUDENT);
        user1.setId(user1Id);
        user1.setProfile(new TenantUserProfile(user1, "User", "One"));

        TenantUser user2 = new TenantUser("USR-C2", "u2@school.edu.in", null, TenantUserType.STUDENT);
        user2.setId(user2Id);
        user2.setProfile(new TenantUserProfile(user2, "User", "Two"));

        when(tenantUserRepository.findByIdAndNotDeleted(user1Id)).thenReturn(Optional.of(user1));
        when(tenantUserRepository.findByIdAndNotDeleted(user2Id)).thenReturn(Optional.of(user2));
        when(studentRepository.existsByTenantUserId(any(UUID.class))).thenReturn(false);
        when(studentRepository.existsByStudentCode(anyString())).thenReturn(false);

        AtomicBoolean admissionExists = new AtomicBoolean(false);
        when(studentRepository.existsByAdmissionNumber(admissionNumber))
                .thenAnswer(inv -> admissionExists.getAndSet(true));

        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> {
            Student s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        CreateStudentRequest req1 = CreateStudentRequest.builder()
                .tenantUserId(user1Id)
                .admissionNumber(admissionNumber)
                .admissionDate(LocalDate.of(2026, 6, 1))
                .build();

        CreateStudentRequest req2 = CreateStudentRequest.builder()
                .tenantUserId(user2Id)
                .admissionNumber(admissionNumber)
                .admissionDate(LocalDate.of(2026, 6, 1))
                .build();

        int numThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<StudentDetailResponse>> futures = new ArrayList<>();
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        futures.add(executor.submit(() -> {
            try {
                startLatch.await();
                return studentService.createStudent(req1);
            } catch (Exception e) {
                exceptions.add(e);
                throw e;
            }
        }));

        futures.add(executor.submit(() -> {
            try {
                startLatch.await();
                return studentService.createStudent(req2);
            } catch (Exception e) {
                exceptions.add(e);
                throw e;
            }
        }));

        startLatch.countDown();
        executor.shutdown();

        int successCount = 0;
        for (Future<StudentDetailResponse> f : futures) {
            try {
                StudentDetailResponse resp = f.get();
                if (resp != null) successCount++;
            } catch (Exception ignored) {
            }
        }

        assertThat(successCount).isEqualTo(1);
        assertThat(exceptions).hasSize(1);
        assertThat(exceptions.get(0)).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("Concurrent student creation with same tenantUserId — Exactly one succeeds, other fails safely")
    void testConcurrentStudentCreation_DuplicateTenantUserRace() throws Exception {
        UUID userId = UUID.randomUUID();

        TenantUser user = new TenantUser("USR-C3", "u3@school.edu.in", null, TenantUserType.STUDENT);
        user.setId(userId);
        user.setProfile(new TenantUserProfile(user, "User", "Three"));

        when(tenantUserRepository.findByIdAndNotDeleted(userId)).thenReturn(Optional.of(user));
        when(studentRepository.existsByAdmissionNumber(anyString())).thenReturn(false);
        when(studentRepository.existsByStudentCode(anyString())).thenReturn(false);

        AtomicBoolean userLinked = new AtomicBoolean(false);
        when(studentRepository.existsByTenantUserId(userId))
                .thenAnswer(inv -> userLinked.getAndSet(true));

        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> {
            Student s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        CreateStudentRequest req1 = CreateStudentRequest.builder()
                .tenantUserId(userId)
                .admissionNumber("ADM-CONCURRENT-A")
                .admissionDate(LocalDate.of(2026, 6, 1))
                .build();

        CreateStudentRequest req2 = CreateStudentRequest.builder()
                .tenantUserId(userId)
                .admissionNumber("ADM-CONCURRENT-B")
                .admissionDate(LocalDate.of(2026, 6, 1))
                .build();

        int numThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<StudentDetailResponse>> futures = new ArrayList<>();
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        futures.add(executor.submit(() -> {
            try {
                startLatch.await();
                return studentService.createStudent(req1);
            } catch (Exception e) {
                exceptions.add(e);
                throw e;
            }
        }));

        futures.add(executor.submit(() -> {
            try {
                startLatch.await();
                return studentService.createStudent(req2);
            } catch (Exception e) {
                exceptions.add(e);
                throw e;
            }
        }));

        startLatch.countDown();
        executor.shutdown();

        int successCount = 0;
        for (Future<StudentDetailResponse> f : futures) {
            try {
                StudentDetailResponse resp = f.get();
                if (resp != null) successCount++;
            } catch (Exception ignored) {
            }
        }

        assertThat(successCount).isEqualTo(1);
        assertThat(exceptions).hasSize(1);
        assertThat(exceptions.get(0)).isInstanceOf(DuplicateResourceException.class);
    }
}
