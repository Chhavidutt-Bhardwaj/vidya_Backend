package com.vidya.tenantdata.assessment.repository;

import com.vidya.tenantdata.assessment.entity.ModerationBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ModerationBatchRepository extends JpaRepository<ModerationBatch, UUID> {

    List<ModerationBatch> findByExamSubjectConfigIdOrderByCreatedAtDesc(UUID configId);
}
