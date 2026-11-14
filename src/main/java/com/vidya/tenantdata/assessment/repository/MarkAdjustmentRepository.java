package com.vidya.tenantdata.assessment.repository;

import com.vidya.tenantdata.assessment.entity.MarkAdjustment;
import com.vidya.tenantdata.assessment.enums.AdjustmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface MarkAdjustmentRepository extends JpaRepository<MarkAdjustment, UUID> {

    List<MarkAdjustment> findByExamSubjectConfigIdOrderByCreatedAtDesc(UUID configId);

    List<MarkAdjustment> findByAssessmentIdOrderByCreatedAtDesc(UUID assessmentId);

    List<MarkAdjustment> findByModerationBatchId(UUID batchId);

    @Query("SELECT a FROM MarkAdjustment a WHERE a.status = :status ORDER BY a.createdAt")
    List<MarkAdjustment> findByStatus(@Param("status") AdjustmentStatus status);

    @Query("SELECT COALESCE(SUM(a.delta), 0) FROM MarkAdjustment a WHERE a.examSubjectConfigId = :configId AND a.studentId = :studentId "
            + "AND a.status = com.vidya.tenantdata.assessment.enums.AdjustmentStatus.APPROVED")
    BigDecimal approvedDelta(@Param("configId") UUID configId, @Param("studentId") UUID studentId);
}
