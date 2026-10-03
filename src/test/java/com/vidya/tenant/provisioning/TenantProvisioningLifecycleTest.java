package com.vidya.tenant.provisioning;

import com.vidya.common.exception.TenantProvisioningException;
import com.vidya.master.provisioning.entity.TenantProvisioning;
import com.vidya.master.provisioning.repository.TenantProvisioningRepository;
import com.vidya.master.tenant.entity.Tenant;
import com.vidya.master.tenant.entity.TenantStatus;
import com.vidya.master.tenant.repository.TenantRepository;
import com.vidya.tenant.datasource.CredentialResolver;
import com.vidya.tenant.datasource.TenantDataSourceFactory;
import com.vidya.tenant.datasource.TenantDataSourceManager;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantProvisioningLifecycleTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private TenantProvisioningRepository provisioningRepository;
    @Mock private TenantDatabaseCreator databaseCreator;
    @Mock private TenantMigrationService migrationService;
    @Mock private TenantInitializationService initializationService;
    @Mock private TenantDataSourceFactory dataSourceFactory;
    @Mock private TenantDataSourceManager dataSourceManager;
    @Mock private TenantRegistryService registryService;
    @Mock private CredentialResolver credentialResolver;

    private TenantProvisioningService provisioningService;

    private UUID tenantId;
    private Tenant tenant;
    private TenantProvisioning provisioning;
    private TenantRegistration registration;
    private DataSource tenantDs;

    @BeforeEach
    void setUp() {
        provisioningService = new TenantProvisioningService(
                tenantRepository,
                provisioningRepository,
                databaseCreator,
                migrationService,
                initializationService,
                dataSourceFactory,
                dataSourceManager,
                registryService,
                credentialResolver
        );

        tenantId = UUID.randomUUID();
        tenant = Tenant.create(
                UUID.randomUUID(), "TNT-HARVARD", "localhost", 5432, "tenant_harvard_db", "user_ref", "pass_ref"
        );
        tenant.setId(tenantId);

        provisioning = TenantProvisioning.create(tenantId, "PRV-HARVARD-01");
        registration = new TenantRegistration(
                tenantId, "TNT-HARVARD", tenant.getSchoolId(), "ACTIVE", "localhost", 5432, "tenant_harvard_db", "user_ref", "pass_ref"
        );
        tenantDs = mock(DataSource.class);
    }

    @Test
    @DisplayName("startProvisioning creates provisioning record and marks tenant as PROVISIONING")
    void startProvisioning_Success() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(provisioningRepository.existsByTenantId(tenantId)).thenReturn(false);
        when(provisioningRepository.save(any(TenantProvisioning.class))).thenAnswer(i -> i.getArgument(0));

        TenantProvisioning result = provisioningService.startProvisioning(tenantId);

        assertThat(result).isNotNull();
        assertThat(result.getTenantId()).isEqualTo(tenantId);
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.PROVISIONING);
        verify(provisioningRepository).save(any(TenantProvisioning.class));
        verify(tenantRepository).save(tenant);
    }

    @Test
    @DisplayName("startProvisioning is idempotent and returns existing record if already created")
    void startProvisioning_Idempotent() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(provisioningRepository.existsByTenantId(tenantId)).thenReturn(true);
        when(provisioningRepository.findByTenantId(tenantId)).thenReturn(Optional.of(provisioning));

        TenantProvisioning result = provisioningService.startProvisioning(tenantId);

        assertThat(result).isSameAs(provisioning);
        verify(provisioningRepository, never()).save(any(TenantProvisioning.class));
    }

    @Test
    @DisplayName("executeProvisioning executes full lifecycle: DB Creation -> Migration -> Seed Initialization -> Completed")
    void executeProvisioning_FullLifecycle_Success() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(provisioningRepository.findByTenantId(tenantId)).thenReturn(Optional.of(provisioning));
        when(credentialResolver.resolve("user_ref")).thenReturn("pg_user");
        when(credentialResolver.resolve("pass_ref")).thenReturn("pg_pass");
        when(registryService.findByTenantCode("TNT-HARVARD")).thenReturn(Optional.of(registration));
        when(dataSourceFactory.create(registration)).thenReturn(tenantDs);

        provisioningService.executeProvisioning(tenantId);

        verify(databaseCreator).createDatabase("tenant_harvard_db", "pg_user", "pg_pass", "TNT-HARVARD");
        verify(migrationService).migrate(tenantDs, "TNT-HARVARD");
        verify(initializationService).initialize(tenantDs, "TNT-HARVARD");
        verify(dataSourceFactory).close(tenantDs, "TNT-HARVARD-provisioning");
        verify(dataSourceManager).evict("TNT-HARVARD");

        assertThat(provisioning.isCompleted()).isTrue();
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.PROVISIONED);
    }

    @Test
    @DisplayName("executeProvisioning handles DB creation failure and marks PROVISIONING_FAILED")
    void executeProvisioning_DatabaseCreationFailure() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(provisioningRepository.findByTenantId(tenantId)).thenReturn(Optional.of(provisioning));
        when(credentialResolver.resolve("user_ref")).thenReturn("pg_user");
        when(credentialResolver.resolve("pass_ref")).thenReturn("pg_pass");

        doThrow(new RuntimeException("PostgreSQL connection refused"))
                .when(databaseCreator).createDatabase(anyString(), anyString(), anyString(), anyString());

        assertThatThrownBy(() -> provisioningService.executeProvisioning(tenantId))
                .isInstanceOf(TenantProvisioningException.class);

        assertThat(provisioning.isFailed()).isTrue();
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.PROVISIONING_FAILED);
    }

    @Test
    @DisplayName("executeProvisioning handles Migration failure and marks PROVISIONING_FAILED")
    void executeProvisioning_MigrationFailure() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(provisioningRepository.findByTenantId(tenantId)).thenReturn(Optional.of(provisioning));
        when(credentialResolver.resolve("user_ref")).thenReturn("pg_user");
        when(credentialResolver.resolve("pass_ref")).thenReturn("pg_pass");
        when(registryService.findByTenantCode("TNT-HARVARD")).thenReturn(Optional.of(registration));
        when(dataSourceFactory.create(registration)).thenReturn(tenantDs);

        doThrow(new RuntimeException("Flyway migration syntax error in V2__create_tables.sql"))
                .when(migrationService).migrate(tenantDs, "TNT-HARVARD");

        assertThatThrownBy(() -> provisioningService.executeProvisioning(tenantId))
                .isInstanceOf(TenantProvisioningException.class);

        assertThat(provisioning.isFailed()).isTrue();
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.PROVISIONING_FAILED);
        verify(dataSourceFactory).close(tenantDs, "TNT-HARVARD-provisioning");
    }

    @Test
    @DisplayName("retryProvisioning prepares failed provisioning record for retry and resets status")
    void retryProvisioning_Success() {
        provisioning.markFailed("DB creation timeout");
        tenant.markProvisioningFailed();

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(provisioningRepository.findByTenantId(tenantId)).thenReturn(Optional.of(provisioning));
        when(provisioningRepository.save(any(TenantProvisioning.class))).thenAnswer(i -> i.getArgument(0));

        TenantProvisioning retried = provisioningService.retryProvisioning(tenantId);

        assertThat(retried.getStatus()).isEqualTo("REQUESTED");
        assertThat(retried.getAttemptCount()).isEqualTo(2);
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.PROVISIONING);
    }
}
