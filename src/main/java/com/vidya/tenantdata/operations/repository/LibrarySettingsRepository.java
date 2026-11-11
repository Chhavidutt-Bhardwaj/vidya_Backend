package com.vidya.tenantdata.operations.repository;

import com.vidya.tenantdata.operations.entity.LibrarySettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibrarySettingsRepository extends JpaRepository<LibrarySettings, Short> {
}
