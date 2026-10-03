package com.vidya.tenant.datasource;

import com.vidya.tenant.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantRoutingDataSourceTest {

    @Mock
    private TenantDataSourceManager tenantDataSourceManager;

    private TenantRoutingDataSource routingDataSource;

    @BeforeEach
    void setUp() {
        routingDataSource = new TenantRoutingDataSource(tenantDataSourceManager);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("determineCurrentLookupKey returns current tenant code from TenantContext")
    void determineCurrentLookupKey_ReturnsTenantCode() {
        TenantContext.setTenantCode("TNT-SPRINGFIELD");

        Object key = routingDataSource.determineCurrentLookupKey();

        assertThat(key).isEqualTo("TNT-SPRINGFIELD");
    }

    @Test
    @DisplayName("determineTargetDataSource delegates to TenantDataSourceManager when context is present")
    void determineTargetDataSource_DelegatesToManager() {
        TenantContext.setTenantCode("TNT-SPRINGFIELD");
        DataSource mockDataSource = mock(DataSource.class);
        when(tenantDataSourceManager.getDataSource("TNT-SPRINGFIELD")).thenReturn(mockDataSource);

        DataSource result = routingDataSource.determineTargetDataSource();

        assertThat(result).isSameAs(mockDataSource);
        verify(tenantDataSourceManager).getDataSource("TNT-SPRINGFIELD");
    }

    @Test
    @DisplayName("determineTargetDataSource throws IllegalStateException when TenantContext is missing")
    void determineTargetDataSource_ThrowsException_WhenNoContext() {
        TenantContext.clear();

        assertThatThrownBy(() -> routingDataSource.determineTargetDataSource())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No tenant context is set");
    }

    @Test
    @DisplayName("Concurrent routing requests maintain strict thread isolation without cross-contamination")
    void concurrentRouting_MaintainsThreadIsolation() throws Exception {
        DataSource dsA = mock(DataSource.class);
        DataSource dsB = mock(DataSource.class);

        when(tenantDataSourceManager.getDataSource("TNT-A")).thenReturn(dsA);
        when(tenantDataSourceManager.getDataSource("TNT-B")).thenReturn(dsB);

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final String tenantCode = (i % 2 == 0) ? "TNT-A" : "TNT-B";
            final DataSource expectedDs = (i % 2 == 0) ? dsA : dsB;

            tasks.add(() -> {
                TenantContext.setTenantCode(tenantCode);
                try {
                    DataSource actual = routingDataSource.determineTargetDataSource();
                    return actual == expectedDs && tenantCode.equals(TenantContext.getTenantCode());
                } finally {
                    TenantContext.clear();
                }
            });
        }

        List<Future<Boolean>> results = executor.invokeAll(tasks);
        for (Future<Boolean> future : results) {
            assertThat(future.get()).isTrue();
        }

        executor.shutdown();
    }
}
