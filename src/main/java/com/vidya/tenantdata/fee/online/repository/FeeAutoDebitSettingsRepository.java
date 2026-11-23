package com.vidya.tenantdata.fee.online.repository;

import com.vidya.tenantdata.fee.online.entity.FeeAutoDebitSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeAutoDebitSettingsRepository extends JpaRepository<FeeAutoDebitSettings, Short> {
}
