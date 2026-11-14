package com.vidya.tenantdata.assessment.repository;

import com.vidya.tenantdata.assessment.entity.ReevaluationRequest;
import com.vidya.tenantdata.assessment.enums.ReevaluationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReevaluationRequestRepository extends JpaRepository<ReevaluationRequest, UUID> {

    @Query("SELECT r FROM ReevaluationRequest r WHERE (:status IS NULL OR r.status = :status) AND (:configId IS NULL OR r.examSubjectConfigId = :configId) "
            + "AND (:studentId IS NULL OR r.studentId = :studentId) ORDER BY r.createdAt DESC")
    List<ReevaluationRequest> search(@Param("status") ReevaluationStatus status, @Param("configId") UUID configId, @Param("studentId") UUID studentId);

    @Query("SELECT r FROM ReevaluationRequest r WHERE r.studentId IN :studentIds ORDER BY r.createdAt DESC")
    List<ReevaluationRequest> findByStudents(@Param("studentIds") Collection<UUID> studentIds);
}
