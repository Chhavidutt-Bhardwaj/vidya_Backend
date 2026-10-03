package com.vidya.tenantdata.staff;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.tenantdata.staff.dto.CreateStaffRequest;
import com.vidya.tenantdata.staff.dto.StaffDetailResponse;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.enums.StaffType;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.staff.service.StaffServiceImpl;
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
@DisplayName("Staff — Concurrency & Duplicate Race Condition Tests")
class StaffConcurrencyTest {

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private TenantUserRepository tenantUserRepository;

    @InjectMocks
    private StaffServiceImpl staffService;

    @Test
    @DisplayName("Concurrent staff creation with same tenantUserId — Exactly one succeeds, other fails safely")
    void testConcurrentStaffCreation_DuplicateTenantUserRace() throws Exception {
        UUID sharedUserId = UUID.randomUUID();

        TenantUser sharedUser = new TenantUser("USR-CONC-01", "shared.teacher@school.edu.in",
                "+919876543210", TenantUserType.STAFF);
        sharedUser.setId(sharedUserId);
        sharedUser.setProfile(new TenantUserProfile(sharedUser, "Shared", "Teacher"));

        when(tenantUserRepository.findByIdAndNotDeleted(sharedUserId)).thenReturn(Optional.of(sharedUser));
        when(staffRepository.existsByStaffCode(anyString())).thenReturn(false);

        // Simulate race condition: first call sees no duplicate, second sees one
        AtomicBoolean staffExists = new AtomicBoolean(false);
        when(staffRepository.existsActiveByTenantUserId(sharedUserId, null))
                .thenAnswer(inv -> staffExists.getAndSet(true));

        when(staffRepository.save(any(Staff.class))).thenAnswer(inv -> {
            Staff s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        CreateStaffRequest request = CreateStaffRequest.builder()
                .tenantUserId(sharedUserId)
                .staffType(StaffType.TEACHER)
                .designation("Teacher")
                .department("Science")
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        List<Future<Object>> futures = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                try {
                    return staffService.createStaff(request);
                } catch (DuplicateResourceException e) {
                    return e;
                }
            }));
        }

        latch.countDown();
        executor.shutdown();

        List<Object> results = new ArrayList<>();
        for (Future<Object> f : futures) {
            results.add(f.get());
        }

        long successCount = results.stream().filter(r -> r instanceof StaffDetailResponse).count();
        long failureCount = results.stream().filter(r -> r instanceof DuplicateResourceException).count();

        assertThat(successCount).isEqualTo(1).as("Exactly one concurrent creation should succeed");
        assertThat(failureCount).isEqualTo(1).as("The other concurrent creation should fail with DuplicateResourceException");
    }

    @Test
    @DisplayName("Concurrent staff creation with same employee code — Exactly one succeeds, other fails safely")
    void testConcurrentStaffCreation_DuplicateEmployeeCodeRace() throws Exception {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        String sharedEmployeeCode = "EMP-CONCURRENT-001";

        TenantUser user1 = new TenantUser("USR-CONC-A", "user1@school.edu.in", null, TenantUserType.STAFF);
        user1.setId(userId1);
        user1.setProfile(new TenantUserProfile(user1, "User", "One"));

        TenantUser user2 = new TenantUser("USR-CONC-B", "user2@school.edu.in", null, TenantUserType.STAFF);
        user2.setId(userId2);
        user2.setProfile(new TenantUserProfile(user2, "User", "Two"));

        when(tenantUserRepository.findByIdAndNotDeleted(userId1)).thenReturn(Optional.of(user1));
        when(tenantUserRepository.findByIdAndNotDeleted(userId2)).thenReturn(Optional.of(user2));
        when(staffRepository.existsActiveByTenantUserId(userId1, null)).thenReturn(false);
        when(staffRepository.existsActiveByTenantUserId(userId2, null)).thenReturn(false);
        when(staffRepository.existsByStaffCode(anyString())).thenReturn(false);

        // Simulate race on employee code: first call returns false (no conflict), second returns true
        AtomicBoolean empCodeExists = new AtomicBoolean(false);
        when(staffRepository.existsActiveByEmployeeCode(sharedEmployeeCode, null))
                .thenAnswer(inv -> empCodeExists.getAndSet(true));

        when(staffRepository.save(any(Staff.class))).thenAnswer(inv -> {
            Staff s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Object>> futures = new ArrayList<>();

        UUID[] userIds = {userId1, userId2};
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                latch.await();
                try {
                    return staffService.createStaff(CreateStaffRequest.builder()
                            .tenantUserId(userIds[idx])
                            .employeeCode(sharedEmployeeCode)
                            .staffType(StaffType.ADMINISTRATIVE)
                            .build());
                } catch (DuplicateResourceException e) {
                    return e;
                }
            }));
        }

        latch.countDown();
        executor.shutdown();

        List<Object> results = new ArrayList<>();
        for (Future<Object> f : futures) {
            results.add(f.get());
        }

        long successCount = results.stream().filter(r -> r instanceof StaffDetailResponse).count();
        long failureCount = results.stream().filter(r -> r instanceof DuplicateResourceException).count();

        assertThat(successCount).isEqualTo(1).as("Exactly one should win the employee code race");
        assertThat(failureCount).isEqualTo(1).as("The other should fail with DuplicateResourceException");
    }

    @Test
    @DisplayName("Staff code generation handles concurrent collisions gracefully")
    void testStaffCodeCollisionUnderConcurrency() throws Exception {
        UUID userId = UUID.randomUUID();
        TenantUser user = new TenantUser("USR-CONC-CODE", "code.race@school.edu.in", null, TenantUserType.STAFF);
        user.setId(userId);
        user.setProfile(new TenantUserProfile(user, "Code", "Race"));

        when(tenantUserRepository.findByIdAndNotDeleted(userId)).thenReturn(Optional.of(user));
        when(staffRepository.existsActiveByTenantUserId(userId, null)).thenReturn(false);

        // First two staff codes collide; third is unique
        when(staffRepository.existsByStaffCode(anyString())).thenReturn(true, true, false);

        Staff savedStaff = new Staff("STF-FINAL-CODE", user, null,
                LocalDate.of(2026, 4, 1), StaffType.SUPPORT, null, null);
        savedStaff.setId(UUID.randomUUID());

        when(staffRepository.save(any(Staff.class))).thenReturn(savedStaff);

        StaffDetailResponse response = staffService.createStaff(CreateStaffRequest.builder()
                .tenantUserId(userId)
                .staffType(StaffType.SUPPORT)
                .build());

        assertThat(response).isNotNull();
        assertThat(response.getStaffCode()).isEqualTo("STF-FINAL-CODE");
    }

    @Test
    @DisplayName("NON_TEACHING staff can be created alongside TEACHER staff with different users")
    void testMultipleStaffTypes_DifferentUsers() throws Exception {
        UUID teacherUserId = UUID.randomUUID();
        UUID nonTeachingUserId = UUID.randomUUID();

        TenantUser teacherUser = new TenantUser("USR-TEACHER-01", "teacher@school.edu.in", null, TenantUserType.STAFF);
        teacherUser.setId(teacherUserId);
        teacherUser.setProfile(new TenantUserProfile(teacherUser, "Mr", "Teacher"));

        TenantUser adminUser = new TenantUser("USR-ADMIN-01", "admin@school.edu.in", null, TenantUserType.STAFF);
        adminUser.setId(nonTeachingUserId);
        adminUser.setProfile(new TenantUserProfile(adminUser, "Ms", "Admin"));

        when(tenantUserRepository.findByIdAndNotDeleted(teacherUserId)).thenReturn(Optional.of(teacherUser));
        when(tenantUserRepository.findByIdAndNotDeleted(nonTeachingUserId)).thenReturn(Optional.of(adminUser));
        when(staffRepository.existsActiveByTenantUserId(any(UUID.class), any())).thenReturn(false);
        when(staffRepository.existsByStaffCode(anyString())).thenReturn(false);

        Staff teacherStaff = new Staff("STF-TEACHER-01", teacherUser, null,
                LocalDate.of(2026, 4, 1), StaffType.TEACHER, "Teacher", "Mathematics");
        teacherStaff.setId(UUID.randomUUID());

        Staff adminStaff = new Staff("STF-ADMIN-01", adminUser, null,
                LocalDate.of(2026, 6, 1), StaffType.NON_TEACHING, "Admin Staff", "Administration");
        adminStaff.setId(UUID.randomUUID());

        when(staffRepository.save(any(Staff.class))).thenReturn(teacherStaff, adminStaff);

        StaffDetailResponse teacherResponse = staffService.createStaff(CreateStaffRequest.builder()
                .tenantUserId(teacherUserId)
                .staffType(StaffType.TEACHER)
                .build());

        StaffDetailResponse adminResponse = staffService.createStaff(CreateStaffRequest.builder()
                .tenantUserId(nonTeachingUserId)
                .staffType(StaffType.NON_TEACHING)
                .build());

        assertThat(teacherResponse.getStaffType()).isEqualTo(StaffType.TEACHER);
        assertThat(adminResponse.getStaffType()).isEqualTo(StaffType.NON_TEACHING);
    }
}
