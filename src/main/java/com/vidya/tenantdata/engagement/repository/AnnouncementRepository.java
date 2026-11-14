package com.vidya.tenantdata.engagement.repository;

import com.vidya.tenantdata.engagement.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

    Optional<Announcement> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByAnnouncementCode(String announcementCode);
}
