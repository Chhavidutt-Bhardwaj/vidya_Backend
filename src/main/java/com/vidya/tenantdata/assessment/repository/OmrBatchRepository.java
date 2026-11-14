package com.vidya.tenantdata.assessment.repository;

import com.vidya.tenantdata.assessment.entity.OmrBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OmrBatchRepository extends JpaRepository<OmrBatch, UUID> {

    List<OmrBatch> findByQuestionPaperIdOrderByCreatedAtDesc(UUID paperId);
}
