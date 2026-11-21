package com.vidya.tenantdata.engagement.repository;

import com.vidya.tenantdata.engagement.entity.NotificationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

    /** Messages due now, emergencies first. Rows are locked; other servers skip them. */
    @Query(value = "SELECT * FROM notification_outbox WHERE status = 'PENDING' AND next_attempt_at <= :now "
            + "ORDER BY CASE priority WHEN 'EMERGENCY' THEN 0 WHEN 'IMPORTANT' THEN 1 ELSE 2 END, next_attempt_at "
            + "LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<NotificationOutbox> lockDue(@Param("now") Instant now, @Param("limit") int limit);

    @Query("SELECT o.dedupeKey FROM NotificationOutbox o WHERE o.dedupeKey IN :keys")
    Set<String> findExistingKeys(@Param("keys") Collection<String> keys);

    @Query(value = "SELECT * FROM notification_outbox WHERE channel = 'IN_APP' AND status = 'SENT' AND recipient_user_id IN (:users) "
            + "AND (:unreadOnly = FALSE OR read_at IS NULL) ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    List<NotificationOutbox> inbox(@Param("users") Collection<UUID> userIds, @Param("unreadOnly") boolean unreadOnly, @Param("limit") int limit);

    @Query(value = "SELECT count(*) FROM notification_outbox WHERE channel = 'IN_APP' AND status = 'SENT' AND recipient_user_id IN (:users) AND read_at IS NULL",
            nativeQuery = true)
    long unread(@Param("users") Collection<UUID> userIds);

    @Modifying
    @Query(value = "UPDATE notification_outbox SET read_at = NOW() WHERE channel = 'IN_APP' AND status = 'SENT' AND recipient_user_id IN (:users) "
            + "AND read_at IS NULL", nativeQuery = true)
    int markAllRead(@Param("users") Collection<UUID> userIds);
}
