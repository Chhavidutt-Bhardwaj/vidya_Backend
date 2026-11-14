package com.vidya.tenantdata.assessment.repository;

import com.vidya.tenantdata.assessment.entity.OnlineExamAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnlineExamAttemptRepository extends JpaRepository<OnlineExamAttempt, UUID> {

    List<OnlineExamAttempt> findByExamIdAndStudentIdOrderByAttemptNo(UUID examId, UUID studentId);

    List<OnlineExamAttempt> findByExamIdOrderByStudentIdAscAttemptNoAsc(UUID examId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM OnlineExamAttempt a WHERE a.id = :id")
    Optional<OnlineExamAttempt> lockById(@Param("id") UUID id);

    @Query("SELECT a.id FROM OnlineExamAttempt a WHERE a.status = com.vidya.tenantdata.assessment.enums.AttemptStatus.IN_PROGRESS AND a.deadlineAt < :before")
    List<UUID> expiredIds(@Param("before") Instant before, Pageable page);
}
