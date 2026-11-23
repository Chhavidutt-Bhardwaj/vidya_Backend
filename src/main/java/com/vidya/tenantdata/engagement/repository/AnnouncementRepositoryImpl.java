package com.vidya.tenantdata.engagement.repository;

import com.vidya.tenantdata.engagement.entity.Announcement;
import com.vidya.tenantdata.engagement.enums.AnnouncementAudience;
import com.vidya.tenantdata.engagement.enums.AnnouncementStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

class AnnouncementRepositoryImpl implements AnnouncementRepositoryCustom {

    private static final String BASE = "SELECT a.* FROM announcement a WHERE a.deleted_at IS NULL";
    private static final String ADDRESSED_TO = "(a.audience = 'SCHOOL' OR (a.audience = 'CLASS' AND a.academic_class_id IN (:classes)) "
            + "OR (a.audience = 'SECTION' AND a.section_id IN (:sections)))";

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    @Override
    public List<Announcement> search(AnnouncementStatus status, AnnouncementAudience audience, UUID classId, OwnScope scope, int limit) {
        NativeSearch s = new NativeSearch(BASE)
                .filter("a.status = :st", "st", status != null ? status.name() : null)
                .filter("a.audience = :au", "au", audience != null ? audience.name() : null)
                .filter("a.academic_class_id = :c", "c", classId);
        if (scope != null) {
            s.and("(a.created_by = CAST(:me AS uuid) OR (a.status = 'PUBLISHED' AND " + ADDRESSED_TO + "))")
                    .param("me", scope.actorId())
                    .ids("classes", scope.classIds())
                    .ids("sections", scope.sectionIds());
        }
        return s.list(em, Announcement.class, "COALESCE(a.published_at, a.created_at) DESC", limit);
    }

    @Override
    public List<Announcement> findPublishedFor(Set<UUID> classIds, Set<UUID> sectionIds, LocalDate from, LocalDate to, LocalDate today,
                                               int limit) {
        return new NativeSearch(BASE)
                .and("a.status = 'PUBLISHED' AND a.publish_on BETWEEN :from AND :to AND (a.expires_on IS NULL OR a.expires_on >= :today)")
                .and(ADDRESSED_TO)
                .param("from", from).param("to", to).param("today", today)
                .ids("classes", classIds).ids("sections", sectionIds)
                .list(em, Announcement.class, "CASE a.priority WHEN 'EMERGENCY' THEN 0 ELSE 1 END, a.published_at DESC", limit);
    }
}
