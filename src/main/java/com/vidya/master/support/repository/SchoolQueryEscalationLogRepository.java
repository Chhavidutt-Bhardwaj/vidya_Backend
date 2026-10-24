package com.vidya.master.support.repository;

import com.vidya.master.support.entity.SchoolQueryEscalationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SchoolQueryEscalationLogRepository extends JpaRepository<SchoolQueryEscalationLog, UUID> {

    List<SchoolQueryEscalationLog> findAllByQueryIdOrderByOccurredAtAsc(UUID queryId);
}
