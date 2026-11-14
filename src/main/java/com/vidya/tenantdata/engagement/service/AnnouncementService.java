package com.vidya.tenantdata.engagement.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.academic.config.service.TeachingScope;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.AnnouncementRequest;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.AnnouncementResponse;
import com.vidya.tenantdata.engagement.entity.Announcement;
import com.vidya.tenantdata.engagement.enums.AnnouncementAudience;
import com.vidya.tenantdata.engagement.enums.AnnouncementPriority;
import com.vidya.tenantdata.engagement.enums.AnnouncementStatus;
import com.vidya.tenantdata.engagement.notification.NotificationOutboxService;
import com.vidya.tenantdata.engagement.notification.NotificationTemplates;
import com.vidya.tenantdata.engagement.notification.NotificationTemplates.Resolved;
import com.vidya.tenantdata.engagement.notification.OutboxDraft;
import com.vidya.tenantdata.engagement.notification.RecipientTargets;
import com.vidya.tenantdata.engagement.repository.AnnouncementRepository;
import com.vidya.tenantdata.engagement.service.DailyReportQueries.EnrolledStudent;
import com.vidya.tenantdata.engagement.service.DailyReportQueries.GuardianContact;
import com.vidya.tenantdata.operations.entity.CommunicationSettings;
import com.vidya.tenantdata.operations.enums.NotificationChannel;
import com.vidya.tenantdata.operations.enums.NotificationEvent;
import com.vidya.tenantdata.security.OwnRecords;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Announcements to the school, a class or a section. Publishing tells the guardians of every student enrolled
 * there (in-app / email / SMS / WhatsApp as the school's template says); EMERGENCY ones use the Emergency alert
 * template and ignore quiet hours. School-wide announcements need STUDENT scope ALL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    public static final String REF_TYPE = "ANNOUNCEMENT";

    private final AnnouncementRepository repository;
    private final AcademicClassRepository classRepository;
    private final SectionRepository sectionRepository;
    private final DailyReportQueries queries;
    private final NotificationTemplates templates;
    private final NotificationOutboxService outbox;
    private final EngagementAccess access;
    private final Clock clock;

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    // ── read ─────────────────────────────────────────────────────────────────

    /** Staff list. Without full scope: own drafts, and published ones for the school or classes the user teaches. */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<AnnouncementResponse> list(AnnouncementStatus status, AnnouncementAudience audience, UUID academicClassId, Integer limit) {
        StringBuilder sql = new StringBuilder("SELECT a.* FROM announcement a WHERE a.deleted_at IS NULL");
        Map<String, Object> params = new HashMap<>();
        HomeworkService.filter(sql, params, "a.status = :st", "st", status != null ? status.name() : null);
        HomeworkService.filter(sql, params, "a.audience = :au", "au", audience != null ? audience.name() : null);
        HomeworkService.filter(sql, params, "a.academic_class_id = :c", "c", academicClassId);
        if (!access.all()) {
            OwnRecords own = access.own();
            Set<UUID> sections = new LinkedHashSet<>(own.sectionIds());
            sections.addAll(own.studentSectionIds());
            sql.append(" AND (a.created_by = CAST(:me AS uuid) OR (a.status = 'PUBLISHED' AND (a.audience = 'SCHOOL' "
                    + "OR (a.audience = 'CLASS' AND a.academic_class_id IN (:classes)) OR (a.audience = 'SECTION' AND a.section_id IN (:sections)))))");
            params.put("me", access.actorId());
            params.put("classes", HomeworkService.nonEmpty(classesOf(own)));
            params.put("sections", HomeworkService.nonEmpty(sections));
        }
        sql.append(" ORDER BY COALESCE(a.published_at, a.created_at) DESC");
        return respond(query(sql.toString(), params, limit), null);
    }

    /** Published, unexpired announcements for the signed-in family's children (and the school), newest first. */
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<AnnouncementResponse> forFamily(LocalDate from, LocalDate to, Integer limit) {
        LocalDate today = LocalDate.now(clock);
        OwnRecords own = access.own();
        Set<UUID> children = new LinkedHashSet<>(own.childStudentIds());
        children.addAll(own.selfStudentIds());
        Set<UUID> classes = new LinkedHashSet<>();
        Set<UUID> sections = new LinkedHashSet<>();
        for (EnrolledStudent e : queries.enrolledStudents(today, children, null, null)) {
            classes.add(e.academicClassId());
            if (e.sectionId() != null) {
                sections.add(e.sectionId());
            }
        }
        Map<String, Object> params = new HashMap<>();
        params.put("from", from != null ? from : today.minusDays(30));
        params.put("to", to != null ? to : today);
        params.put("today", today);
        params.put("classes", HomeworkService.nonEmpty(classes));
        params.put("sections", HomeworkService.nonEmpty(sections));
        return respond(query("SELECT a.* FROM announcement a WHERE a.deleted_at IS NULL AND a.status = 'PUBLISHED' "
                + "AND a.publish_on BETWEEN :from AND :to AND (a.expires_on IS NULL OR a.expires_on >= :today) "
                + "AND (a.audience = 'SCHOOL' OR (a.audience = 'CLASS' AND a.academic_class_id IN (:classes)) "
                + "OR (a.audience = 'SECTION' AND a.section_id IN (:sections))) "
                + "ORDER BY CASE a.priority WHEN 'EMERGENCY' THEN 0 ELSE 1 END, a.published_at DESC", params, limit), null);
    }

    // ── write ────────────────────────────────────────────────────────────────

    @Transactional(transactionManager = "tenantTransactionManager")
    public AnnouncementResponse create(AnnouncementRequest r) {
        Announcement a = new Announcement(code());
        a.setCreatedBy(access.actorId());
        apply(a, r);
        a = repository.save(a);
        return Boolean.TRUE.equals(r.publish()) ? publish(a) : respond(List.of(a), null).get(0);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public AnnouncementResponse update(UUID id, AnnouncementRequest r) {
        Announcement a = announcement(id);
        access.requireAuthorOrAll(a.getCreatedBy(), "change announcements");
        if (a.getStatus() != AnnouncementStatus.DRAFT) {
            throw new BusinessException("Only a draft can be changed; this announcement is " + a.getStatus());
        }
        apply(a, r);
        a = repository.save(a);
        return Boolean.TRUE.equals(r.publish()) ? publish(a) : respond(List.of(a), null).get(0);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public AnnouncementResponse publish(UUID id) {
        Announcement a = announcement(id);
        access.requireAuthorOrAll(a.getCreatedBy(), "publish announcements");
        return publish(a);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public AnnouncementResponse withdraw(UUID id) {
        Announcement a = announcement(id);
        access.requireAuthorOrAll(a.getCreatedBy(), "withdraw announcements");
        if (a.getStatus() != AnnouncementStatus.PUBLISHED) {
            throw new BusinessException("Only a published announcement can be withdrawn");
        }
        a.setStatus(AnnouncementStatus.WITHDRAWN);
        a.setUpdatedBy(access.actorId());
        return respond(List.of(repository.save(a)), null).get(0);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void delete(UUID id) {
        Announcement a = announcement(id);
        access.requireAuthorOrAll(a.getCreatedBy(), "delete announcements");
        if (a.getStatus() == AnnouncementStatus.PUBLISHED) {
            throw new BusinessException("Withdraw a published announcement instead of deleting it");
        }
        a.setDeletedAt(Instant.now(clock));
        a.setUpdatedBy(access.actorId());
        repository.save(a);
    }

    // ── publish & fan-out ────────────────────────────────────────────────────

    private AnnouncementResponse publish(Announcement a) {
        if (a.getStatus() != AnnouncementStatus.DRAFT) {
            throw new BusinessException("Only a draft can be published; this announcement is " + a.getStatus());
        }
        requireAudience(a.getAudience(), a.getAcademicClassId(), a.getSectionId());
        LocalDate today = LocalDate.now(clock);
        if (a.getExpiresOn() != null && a.getExpiresOn().isBefore(today)) {
            throw new BusinessException("expiresOn is in the past");
        }
        a.setStatus(AnnouncementStatus.PUBLISHED);
        a.setPublishOn(today);
        a.setPublishedAt(Instant.now(clock));
        a.setPublishedBy(access.actorId());
        a.setUpdatedBy(access.actorId());
        a = repository.save(a);
        int queued = a.isNotify() ? notifyGuardians(a, today) : 0;
        log.info("Announcement [{}] published to {} ({}), {} messages queued", a.getAnnouncementCode(), a.getAudience(), a.getPriority(), queued);
        return respond(List.of(a), queued).get(0);
    }

    int notifyGuardians(Announcement a, LocalDate today) {
        boolean emergency = a.getPriority() == AnnouncementPriority.EMERGENCY;
        Resolved t = templates.resolve(emergency ? NotificationEvent.EMERGENCY_ALERT : NotificationEvent.GENERAL_NOTICE);
        if (!t.enabled() || t.channels().isEmpty()) {
            return 0;
        }
        List<UUID> students = queries.enrolledStudents(today, null,
                a.getAudience() == AnnouncementAudience.SCHOOL ? null : a.getAcademicClassId(),
                a.getAudience() == AnnouncementAudience.SECTION ? a.getSectionId() : null).stream().map(EnrolledStudent::studentId).toList();
        CommunicationSettings cs = templates.settings();
        Map<String, String> values = Map.of("title", a.getTitle(), "message", a.getBody(), "school_name", templates.schoolName(cs),
                "today", com.vidya.tenantdata.engagement.service.DailyReportFormatter.DATE.format(today));
        String subject = NotificationTemplates.render(t.subject(), values);
        String body = NotificationTemplates.render(t.body(), values);
        Map<String, OutboxDraft> drafts = new LinkedHashMap<>();
        for (GuardianContact g : queries.guardians(students)) {
            for (NotificationChannel ch : t.channels()) {
                String to = RecipientTargets.target(ch, g);
                if (to == null) {
                    continue;
                }
                String key = REF_TYPE + ":" + a.getId() + ":" + ch + ":" + to;
                boolean inApp = ch == NotificationChannel.IN_APP;
                drafts.putIfAbsent(key, new OutboxDraft(t.event().name(), ch, inApp ? UUID.fromString(to) : null, g.name(), inApp ? null : to,
                        subject, body, a.getPriority(), REF_TYPE, a.getId(), today, key));
            }
        }
        return outbox.enqueue(new ArrayList<>(drafts.values()));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void apply(Announcement a, AnnouncementRequest r) {
        UUID classId = r.academicClassId();
        UUID sectionId = r.sectionId();
        switch (r.audience()) {
            case SCHOOL -> {
                classId = null;
                sectionId = null;
            }
            case CLASS -> {
                if (classId == null) {
                    throw new BusinessException("academicClassId is required for a class announcement");
                }
                classRepository.findByIdAndNotDeleted(classId).orElseThrow(() -> new ResourceNotFoundException("AcademicClass", r.academicClassId()));
                sectionId = null;
            }
            case SECTION -> {
                if (sectionId == null) {
                    throw new BusinessException("sectionId is required for a section announcement");
                }
                Section s = sectionRepository.findByIdAndNotDeleted(sectionId).orElseThrow(() -> new ResourceNotFoundException("Section", r.sectionId()));
                classId = s.getAcademicClass().getId();
            }
        }
        requireAudience(r.audience(), classId, sectionId);
        if (r.expiresOn() != null && r.expiresOn().isBefore(LocalDate.now(clock))) {
            throw new BusinessException("expiresOn is in the past");
        }
        a.setTitle(r.title().trim());
        a.setBody(r.body().trim());
        a.setAudience(r.audience());
        a.setAcademicClassId(classId);
        a.setSectionId(sectionId);
        a.setPriority(r.priority() != null ? r.priority() : AnnouncementPriority.NORMAL);
        a.setNotify(r.notifyFamilies() == null || r.notifyFamilies());
        a.setExpiresOn(r.expiresOn());
        a.setUpdatedBy(access.actorId());
    }

    private void requireAudience(AnnouncementAudience audience, UUID classId, UUID sectionId) {
        if (access.all()) {
            return;
        }
        if (audience == AnnouncementAudience.SCHOOL) {
            throw new AccessDeniedException("Only staff with school-wide access can announce to the whole school");
        }
        access.requireClassOrSection(classId, audience == AnnouncementAudience.SECTION ? sectionId : null, "post announcements");
    }

    private static Set<UUID> classesOf(OwnRecords own) {
        Set<UUID> out = new LinkedHashSet<>(own.teachingClassIds());
        return out;
    }

    private List<AnnouncementResponse> respond(List<Announcement> list, Integer queued) {
        if (list.isEmpty()) {
            return List.of();
        }
        TeachingScope n = access.teaching();
        Map<UUID, String> classes = n.classNames(list.stream().map(Announcement::getAcademicClassId).toList());
        Map<UUID, String> sections = n.sectionNames(list.stream().map(Announcement::getSectionId).toList());
        List<AnnouncementResponse> out = new ArrayList<>();
        for (Announcement a : list) {
            out.add(new AnnouncementResponse(a.getId(), a.getAnnouncementCode(), a.getTitle(), a.getBody(), a.getAudience(),
                    a.getAcademicClassId(), classes.get(a.getAcademicClassId()), a.getSectionId(), sections.get(a.getSectionId()),
                    a.getPriority(), a.getStatus(), a.isNotify(), a.getPublishOn(), a.getExpiresOn(), a.getPublishedAt(), queued,
                    a.getUpdatedAt()));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Announcement> query(String sql, Map<String, Object> params, Integer limit) {
        var q = em.createNativeQuery(sql, Announcement.class);
        params.forEach(q::setParameter);
        q.setMaxResults(limit == null ? 50 : Math.max(1, Math.min(limit, 200)));
        return q.getResultList();
    }

    private Announcement announcement(UUID id) {
        return repository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("Announcement", id));
    }

    private String code() {
        for (int i = 0; i < 6; i++) {
            String c = CodeGenerator.announcementCode();
            if (!repository.existsByAnnouncementCode(c)) {
                return c;
            }
        }
        throw new BusinessException("Could not generate a unique code; try again");
    }
}
