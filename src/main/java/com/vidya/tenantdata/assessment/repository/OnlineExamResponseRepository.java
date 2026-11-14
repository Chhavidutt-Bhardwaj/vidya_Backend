package com.vidya.tenantdata.assessment.repository;

import com.vidya.tenantdata.assessment.entity.OnlineExamResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnlineExamResponseRepository extends JpaRepository<OnlineExamResponse, UUID> {

    List<OnlineExamResponse> findByAttemptId(UUID attemptId);

    List<OnlineExamResponse> findByAttemptIdIn(Collection<UUID> attemptIds);

    Optional<OnlineExamResponse> findByAttemptIdAndPaperItemId(UUID attemptId, UUID paperItemId);
}
