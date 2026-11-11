package com.vidya.tenantdata.academic.config.repository;

import com.vidya.tenantdata.academic.config.entity.LessonPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LessonPlanRepository extends JpaRepository<LessonPlan, UUID> {

    Optional<LessonPlan> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByPlanCode(String planCode);
}
