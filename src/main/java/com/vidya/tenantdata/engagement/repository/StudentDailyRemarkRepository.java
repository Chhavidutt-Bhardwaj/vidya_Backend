package com.vidya.tenantdata.engagement.repository;

import com.vidya.tenantdata.engagement.entity.StudentDailyRemark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StudentDailyRemarkRepository extends JpaRepository<StudentDailyRemark, UUID>, StudentDailyRemarkRepositoryCustom {

    Optional<StudentDailyRemark> findByIdAndDeletedAtIsNull(UUID id);
}
