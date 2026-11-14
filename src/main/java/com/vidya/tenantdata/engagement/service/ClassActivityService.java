package com.vidya.tenantdata.engagement.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.academic.config.service.TeachingScope;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.ClassActivityRequest;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.ClassActivityResponse;
import com.vidya.tenantdata.engagement.entity.ClassActivity;
import com.vidya.tenantdata.engagement.repository.ClassActivityRepository;
import com.vidya.tenantdata.engagement.service.EngagementAccess.Place;
import com.vidya.tenantdata.security.OwnRecords;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** What a class or section did on a day. Recorded by anyone teaching there; shown in the daily report. */
@Service
@RequiredArgsConstructor
public class ClassActivityService {

    private final ClassActivityRepository repository;
    private final SubjectRepository subjectRepository;
    private final EngagementAccess access;
    private final Clock clock;

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<ClassActivityResponse> list(UUID academicYearId, UUID academicClassId, UUID sectionId, LocalDate from, LocalDate to,
                                            Integer limit) {
        StringBuilder sql = new StringBuilder("SELECT a.* FROM class_activity a WHERE a.deleted_at IS NULL");
        Map<String, Object> params = new HashMap<>();
        HomeworkService.filter(sql, params, "a.academic_year_id = :y", "y", academicYearId);
        HomeworkService.filter(sql, params, "a.academic_class_id = :c", "c", academicClassId);
        HomeworkService.filter(sql, params, "(a.section_id = :sec OR a.section_id IS NULL)", "sec", sectionId);
        HomeworkService.filter(sql, params, "a.activity_date >= :from", "from", from);
        HomeworkService.filter(sql, params, "a.activity_date <= :to", "to", to);
        if (!access.all()) {
            OwnRecords own = access.own();
            sql.append(" AND (a.created_by = CAST(:me AS uuid) OR a.section_id IN (:ownSections) "
                    + "OR (a.section_id IS NULL AND a.academic_class_id IN (:ownClasses)))");
            params.put("me", access.actorId());
            params.put("ownSections", HomeworkService.nonEmpty(own.sectionIds()));
            params.put("ownClasses", HomeworkService.nonEmpty(own.teachingClassIds()));
        }
        sql.append(" ORDER BY a.activity_date DESC, a.created_at DESC");
        var q = em.createNativeQuery(sql.toString(), ClassActivity.class);
        params.forEach(q::setParameter);
        q.setMaxResults(limit == null ? 100 : Math.max(1, Math.min(limit, 500)));
        @SuppressWarnings("unchecked")
        List<ClassActivity> rows = q.getResultList();
        return respond(rows);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public ClassActivityResponse create(ClassActivityRequest r) {
        ClassActivity a = new ClassActivity(code());
        a.setCreatedBy(access.actorId());
        a.setTeacherId(access.ownStaffIds().stream().findFirst().orElse(null));
        apply(a, r);
        return respond(List.of(repository.save(a))).get(0);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public ClassActivityResponse update(UUID id, ClassActivityRequest r) {
        ClassActivity a = activity(id);
        access.requireAuthorOrAll(a.getCreatedBy(), "change class activities");
        apply(a, r);
        return respond(List.of(repository.save(a))).get(0);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void delete(UUID id) {
        ClassActivity a = activity(id);
        access.requireAuthorOrAll(a.getCreatedBy(), "delete class activities");
        a.setDeletedAt(Instant.now(clock));
        a.setUpdatedBy(access.actorId());
        repository.save(a);
    }

    private void apply(ClassActivity a, ClassActivityRequest r) {
        Place place = access.place(r.academicYearId(), r.academicClassId(), r.sectionId());
        if (!access.all()) {
            access.requireClassOrSection(r.academicClassId(), r.sectionId(), "record class activities");
        }
        if (r.subjectId() != null) {
            subjectRepository.findByIdAndNotDeleted(r.subjectId()).orElseThrow(() -> new ResourceNotFoundException("Subject", r.subjectId()));
        }
        if (r.activityDate().isAfter(LocalDate.now(clock).plusDays(30))) {
            throw new BusinessException("activityDate is more than 30 days ahead; use an announcement for future events");
        }
        if (r.activityDate().isBefore(place.year().getStartDate()) || r.activityDate().isAfter(place.year().getEndDate())) {
            throw new BusinessException("activityDate is outside " + place.year().getCode());
        }
        a.setAcademicYearId(place.year().getId());
        a.setAcademicClassId(place.clazz().getId());
        a.setSectionId(r.sectionId());
        a.setSubjectId(r.subjectId());
        a.setActivityDate(r.activityDate());
        a.setActivityType(r.activityType());
        a.setTitle(r.title().trim());
        a.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        a.setUpdatedBy(access.actorId());
    }

    private List<ClassActivityResponse> respond(List<ClassActivity> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        TeachingScope n = access.teaching();
        Map<UUID, String> classes = n.classNames(list.stream().map(ClassActivity::getAcademicClassId).toList());
        Map<UUID, String> sections = n.sectionNames(list.stream().map(ClassActivity::getSectionId).toList());
        Map<UUID, String> subjects = n.subjectNames(list.stream().map(ClassActivity::getSubjectId).toList());
        Map<UUID, String> teachers = n.staffNames(list.stream().map(ClassActivity::getTeacherId).toList());
        List<ClassActivityResponse> out = new ArrayList<>();
        for (ClassActivity a : list) {
            out.add(new ClassActivityResponse(a.getId(), a.getActivityCode(), a.getAcademicYearId(), a.getAcademicClassId(),
                    classes.get(a.getAcademicClassId()), a.getSectionId(), sections.get(a.getSectionId()), a.getSubjectId(),
                    subjects.get(a.getSubjectId()), a.getTeacherId(), teachers.get(a.getTeacherId()), a.getActivityDate(),
                    a.getActivityType(), a.getTitle(), a.getDescription(), a.getUpdatedAt()));
        }
        return out;
    }

    private ClassActivity activity(UUID id) {
        return repository.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("ClassActivity", id));
    }

    private String code() {
        for (int i = 0; i < 6; i++) {
            String c = CodeGenerator.classActivityCode();
            if (!repository.existsByActivityCode(c)) {
                return c;
            }
        }
        throw new BusinessException("Could not generate a unique code; try again");
    }
}
