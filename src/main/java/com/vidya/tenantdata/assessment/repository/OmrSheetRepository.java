package com.vidya.tenantdata.assessment.repository;

import com.vidya.tenantdata.assessment.entity.OmrSheet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OmrSheetRepository extends JpaRepository<OmrSheet, UUID> {

    List<OmrSheet> findByBatchIdOrderByRowNo(UUID batchId);
}
