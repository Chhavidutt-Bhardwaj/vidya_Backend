package com.vidya.tenantdata.operations.repository;

import com.vidya.tenantdata.operations.entity.CommunicationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunicationSettingsRepository extends JpaRepository<CommunicationSettings, Short> {
}
