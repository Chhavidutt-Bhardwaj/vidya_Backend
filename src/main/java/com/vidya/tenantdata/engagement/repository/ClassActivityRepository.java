package com.vidya.tenantdata.engagement.repository;

import com.vidya.tenantdata.engagement.entity.ClassActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClassActivityRepository extends JpaRepository<ClassActivity, UUID> {

    Optional<ClassActivity> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByActivityCode(String activityCode);
}
