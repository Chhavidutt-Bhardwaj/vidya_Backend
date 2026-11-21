package com.vidya.tenantdata.assessment.repository;

import com.vidya.tenantdata.assessment.entity.CoScholasticArea;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CoScholasticAreaRepository extends JpaRepository<CoScholasticArea, UUID> {

    List<CoScholasticArea> findBySchemeIdOrderBySortOrderAscCodeAsc(UUID schemeId);
}
