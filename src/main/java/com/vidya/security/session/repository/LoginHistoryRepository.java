package com.vidya.security.session.repository;

import com.vidya.security.session.entity.LoginHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, UUID> {

    Page<LoginHistory> findByUserIdOrderByOccurredAtDesc(UUID userId, Pageable pageable);

    Page<LoginHistory> findBySchoolIdOrderByOccurredAtDesc(UUID schoolId, Pageable pageable);
}
