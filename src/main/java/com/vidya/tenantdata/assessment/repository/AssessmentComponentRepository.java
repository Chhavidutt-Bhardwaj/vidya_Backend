package com.vidya.tenantdata.assessment.repository;

import com.vidya.tenantdata.assessment.entity.AssessmentComponent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssessmentComponentRepository extends JpaRepository<AssessmentComponent, UUID> {

    List<AssessmentComponent> findBySchemeIdOrderBySortOrderAscCodeAsc(UUID schemeId);

    void deleteBySchemeId(UUID schemeId);
}
