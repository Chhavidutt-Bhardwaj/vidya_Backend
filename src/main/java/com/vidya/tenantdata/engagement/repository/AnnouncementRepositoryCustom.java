package com.vidya.tenantdata.engagement.repository;

import com.vidya.tenantdata.engagement.entity.Announcement;
import com.vidya.tenantdata.engagement.enums.AnnouncementAudience;
import com.vidya.tenantdata.engagement.enums.AnnouncementStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Dynamic announcement searches (implemented in {@link AnnouncementRepositoryImpl}). */
public interface AnnouncementRepositoryCustom {

    /**
     * Staff list, newest first; null filters are ignored. With a scope: the user's own announcements, plus published ones
     * for the school, one of the scope's classes, or one of its sections. {@code scope == null} means full access.
     */
    List<Announcement> search(AnnouncementStatus status, AnnouncementAudience audience, UUID academicClassId, OwnScope scope, int limit);

    /**
     * Published announcements with {@code publish_on} between the dates and not expired on {@code today}, addressed to the
     * school or one of the given classes / sections. Emergencies first, then newest.
     */
    List<Announcement> findPublishedFor(Set<UUID> classIds, Set<UUID> sectionIds, LocalDate from, LocalDate to, LocalDate today, int limit);
}
