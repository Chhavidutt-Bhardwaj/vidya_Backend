package com.vidya.tenantdata.engagement.repository;

import com.vidya.tenantdata.engagement.entity.DailyReportSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyReportSettingsRepository extends JpaRepository<DailyReportSettings, Short> {
}
