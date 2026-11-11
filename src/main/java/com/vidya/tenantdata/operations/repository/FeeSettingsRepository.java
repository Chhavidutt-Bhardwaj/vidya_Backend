package com.vidya.tenantdata.operations.repository;

import com.vidya.tenantdata.operations.entity.FeeSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FeeSettingsRepository extends JpaRepository<FeeSettings, UUID> {
}
