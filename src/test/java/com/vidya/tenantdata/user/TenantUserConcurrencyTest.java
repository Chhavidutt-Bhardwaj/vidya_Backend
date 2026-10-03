package com.vidya.tenantdata.user;

import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.tenantdata.user.dto.CreateTenantUserRequest;
import com.vidya.tenantdata.user.dto.TenantUserDetailResponse;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.entity.TenantUserType;
import com.vidya.tenantdata.user.repository.TenantUserRepository;
import com.vidya.tenantdata.user.service.TenantUserServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
@DisplayName("TenantUser — Concurrency & Duplicate Race Condition Tests")
class TenantUserConcurrencyTest {

    @Mock
    private TenantUserRepository tenantUserRepository;

    @InjectMocks
    private TenantUserServiceImpl tenantUserService;

    @Test
    @DisplayName("Concurrent user creation with same email — Exactly one succeeds, other fails safely")
    void testConcurrentUserCreation_RaceCondition() throws Exception {
        String email = "duplicate.teacher@greenwood.edu.in";
        CreateTenantUserRequest request = CreateTenantUserRequest.builder()
                .email(email)
                .firstName("Same")
                .lastName("Teacher")
                .userType(TenantUserType.TEACHER)
                .build();

        AtomicBoolean emailExists = new AtomicBoolean(false);

        when(tenantUserRepository.existsByEmailIgnoreCase(email)).thenAnswer(inv -> emailExists.getAndSet(true));
        when(tenantUserRepository.existsByUserCode(anyString())).thenReturn(false);
        when(tenantUserRepository.save(any(TenantUser.class))).thenAnswer(inv -> {
            TenantUser u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        int numThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<TenantUserDetailResponse>> futures = new ArrayList<>();
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numThreads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    return tenantUserService.createTenantUser(request);
                } catch (Exception e) {
                    exceptions.add(e);
                    throw e;
                }
            }));
        }

        startLatch.countDown();
        executor.shutdown();

        int successCount = 0;
        for (Future<TenantUserDetailResponse> f : futures) {
            try {
                TenantUserDetailResponse resp = f.get();
                if (resp != null) successCount++;
            } catch (Exception ignored) {
            }
        }

        assertThat(successCount).isEqualTo(1);
        assertThat(exceptions).hasSize(1);
        assertThat(exceptions.get(0)).isInstanceOf(DuplicateResourceException.class);
    }
}
