package com.vidya.tenantdata.assignment;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.assignment.dto.ClassTeacherAssignmentDetailResponse;
import com.vidya.tenantdata.assignment.dto.CreateClassTeacherAssignmentRequest;
import com.vidya.tenantdata.assignment.entity.ClassTeacherAssignment;
import com.vidya.tenantdata.assignment.repository.ClassTeacherAssignmentRepository;
import com.vidya.tenantdata.assignment.service.ClassTeacherAssignmentServiceImpl;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.enums.EmploymentStatus;
import com.vidya.tenantdata.staff.enums.StaffType;
import com.vidya.tenantdata.staff.repository.StaffRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Class Teacher Assignment — Concurrency & Duplicate Race Condition Tests")
class ClassTeacherAssignmentConcurrencyTest {

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

    @Test
    @DisplayName("Concurrent class teacher assignment creation for same section: exactly one succeeds")
    void concurrentAssignmentCreation_onlyOneSucceeds() throws Exception {
        UUID staffId = UUID.randomUUID();
        UUID academicYearId = UUID.randomUUID();
        UUID academicClassId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();

        AcademicYear academicYear = new AcademicYear("AY-2026", "2026-2027", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), true, AcademicYearStatus.ACTIVE);
        academicYear.setId(academicYearId);

        AcademicClass academicClass = new AcademicClass("CLS-10", "Class 10", "Class 10", 10);
        academicClass.setId(academicClassId);

        Section section = new Section(academicYear, academicClass, "SEC-10A", "Section A", 40);
        section.setId(sectionId);

        TenantUser user = new TenantUser("USR-001", "teacher@test.com", "9876543210", TenantUserType.STAFF);
        user.setId(UUID.randomUUID());
        TenantUserProfile profile = new TenantUserProfile(user, "Jane", "Doe");
        user.setProfile(profile);

        Staff teacher = new Staff("STF-001", user, "EMP101", LocalDate.now(), StaffType.TEACHER, "Teacher", "English");
        teacher.setId(staffId);
        teacher.setEmploymentStatus(EmploymentStatus.ACTIVE);

        when(staffRepository.findByIdAndNotDeleted(staffId)).thenReturn(Optional.of(teacher));
        when(academicYearRepository.findByIdAndNotDeleted(academicYearId)).thenReturn(Optional.of(academicYear));
        when(academicClassRepository.findByIdAndNotDeleted(academicClassId)).thenReturn(Optional.of(academicClass));
        when(sectionRepository.findByIdAndNotDeleted(sectionId)).thenReturn(Optional.of(section));

        AtomicBoolean slotTaken = new AtomicBoolean(false);
        when(assignmentRepository.existsActiveBySectionAndAcademicYear(eq(academicYearId), eq(sectionId), isNull()))
                .thenAnswer(inv -> !slotTaken.compareAndSet(false, true));

        when(assignmentRepository.existsByAssignmentCode(any())).thenReturn(false);
        when(assignmentRepository.save(any(ClassTeacherAssignment.class))).thenAnswer(inv -> {
            ClassTeacherAssignment cta = inv.getArgument(0);
            cta.setId(UUID.randomUUID());
            return cta;
        });

        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<ClassTeacherAssignmentDetailResponse>> futures = new ArrayList<>();
        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numThreads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    CreateClassTeacherAssignmentRequest request = new CreateClassTeacherAssignmentRequest(
                            staffId, academicYearId, academicClassId, sectionId, LocalDate.of(2026, 4, 1), "Concurrent test"
                    );
                    return service.createAssignment(request);
                } catch (Throwable t) {
                    exceptions.add(t);
                    throw t;
                }
            }));
        }

        latch.countDown();
        executor.shutdown();

        int successes = 0;
        for (Future<ClassTeacherAssignmentDetailResponse> future : futures) {
            try {
                ClassTeacherAssignmentDetailResponse resp = future.get();
                if (resp != null) {
                    successes++;
                }
            } catch (Exception ignored) {
            }
        }

        assertThat(successes).isEqualTo(1);
        assertThat(exceptions).hasSize(numThreads - 1);
        for (Throwable t : exceptions) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            assertThat(cause).isInstanceOf(DuplicateResourceException.class);
        }
    }
}
