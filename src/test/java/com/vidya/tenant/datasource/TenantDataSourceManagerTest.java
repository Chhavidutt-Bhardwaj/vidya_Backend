package com.vidya.tenant.datasource;

import com.vidya.common.exception.TenantNotFoundException;
import com.vidya.common.exception.TenantSuspendedException;
import com.vidya.tenant.registry.TenantRegistration;
import com.vidya.tenant.registry.TenantRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantDataSourceManagerTest {

    @Mock
    private TenantRegistryService tenantRegistryService;

    @Mock
    private TenantDataSourceFactory dataSourceFactory;

    private TenantDataSourceManager dataSourceManager;

    private TenantRegistration activeRegistration;
    private TenantRegistration suspendedRegistration;

    @BeforeEach
    void setUp() {
        dataSourceManager = new TenantDataSourceManager(tenantRegistryService, dataSourceFactory);

        activeRegistration = new TenantRegistration(
                UUID.randomUUID(), "TNT-001", UUID.randomUUID(), "ACTIVE",
                "localhost", 5432, "tenant_001_db", "user_ref", "pass_ref"
        );

        suspendedRegistration = new TenantRegistration(
                UUID.randomUUID(), "TNT-SUSPENDED", UUID.randomUUID(), "SUSPENDED",
                "localhost", 5432, "tenant_susp_db", "user_ref", "pass_ref"
        );
    }

    @Test
    @DisplayName("getDataSource creates and caches DataSource on first request and reuses on subsequent requests")
    void getDataSource_CreatesAndCachesDataSource() {
        DataSource mockDs = mock(DataSource.class);
        when(tenantRegistryService.findByTenantCode("TNT-001")).thenReturn(Optional.of(activeRegistration));
        when(dataSourceFactory.create(activeRegistration)).thenReturn(mockDs);

        // First call - cache miss
        DataSource firstCall = dataSourceManager.getDataSource("TNT-001");
        assertThat(firstCall).isSameAs(mockDs);
        assertThat(dataSourceManager.isCached("TNT-001")).isTrue();
        assertThat(dataSourceManager.cacheSize()).isEqualTo(1);

        // Second call - cache hit (should NOT call factory or registry again)
        DataSource secondCall = dataSourceManager.getDataSource("TNT-001");
        assertThat(secondCall).isSameAs(mockDs);

        verify(dataSourceFactory, times(1)).create(any());
        verify(tenantRegistryService, times(1)).findByTenantCode("TNT-001");
    }

    @Test
    @DisplayName("getDataSource throws TenantNotFoundException when tenant is not in registry")
    void getDataSource_NonExistentTenant_ThrowsNotFound() {
        when(tenantRegistryService.findByTenantCode("TNT-UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dataSourceManager.getDataSource("TNT-UNKNOWN"))
                .isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    @DisplayName("getDataSource throws TenantSuspendedException when tenant is suspended")
    void getDataSource_SuspendedTenant_ThrowsSuspendedException() {
        when(tenantRegistryService.findByTenantCode("TNT-SUSPENDED")).thenReturn(Optional.of(suspendedRegistration));

        assertThatThrownBy(() -> dataSourceManager.getDataSource("TNT-SUSPENDED"))
                .isInstanceOf(TenantSuspendedException.class);

        verify(dataSourceFactory, never()).create(any());
    }

    @Test
    @DisplayName("evict closes physical datasource pool and removes from cache")
    void evict_ClosesDataSourceAndRemovesFromCache() {
        DataSource mockDs = mock(DataSource.class);
        when(tenantRegistryService.findByTenantCode("TNT-001")).thenReturn(Optional.of(activeRegistration));
        when(dataSourceFactory.create(activeRegistration)).thenReturn(mockDs);

        dataSourceManager.getDataSource("TNT-001");
        assertThat(dataSourceManager.isCached("TNT-001")).isTrue();

        dataSourceManager.evict("TNT-001");

        assertThat(dataSourceManager.isCached("TNT-001")).isFalse();
        assertThat(dataSourceManager.cacheSize()).isEqualTo(0);
        verify(dataSourceFactory).close(mockDs, "TNT-001");
    }

    @Test
    @DisplayName("register adds pre-built DataSource and closes any pre-existing pool")
    void register_RegistersPreBuiltDataSource() {
        DataSource initialDs = mock(DataSource.class);
        DataSource replacementDs = mock(DataSource.class);

        dataSourceManager.register("TNT-001", initialDs);
        assertThat(dataSourceManager.isCached("TNT-001")).isTrue();

        // Register replacement
        dataSourceManager.register("TNT-001", replacementDs);

        assertThat(dataSourceManager.getDataSource("TNT-001")).isSameAs(replacementDs);
        verify(dataSourceFactory).close(initialDs, "TNT-001");
    }
}
