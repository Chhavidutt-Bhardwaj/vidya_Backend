package com.vidya.security.session.repository;

import com.vidya.security.session.entity.SessionStatus;
import com.vidya.security.session.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    List<UserSession> findByUserIdAndStatusOrderByCreatedAtAsc(UUID userId, SessionStatus status);

    List<UserSession> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Throttled heartbeat: only writes when last_seen_at is older than {@code staleBefore}. */
    @Modifying
    @Query("UPDATE UserSession s SET s.lastSeenAt = :now WHERE s.id = :id AND s.lastSeenAt < :staleBefore")
    int touch(@Param("id") UUID id, @Param("now") Instant now, @Param("staleBefore") Instant staleBefore);
}
