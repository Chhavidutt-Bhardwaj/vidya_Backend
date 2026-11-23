package com.vidya.tenantdata.operations.repository;

import com.vidya.tenantdata.operations.entity.AttendanceSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceSettingsRepository extends JpaRepository<AttendanceSettings, Short> {
}
