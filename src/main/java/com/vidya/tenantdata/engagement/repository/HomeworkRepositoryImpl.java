package com.vidya.tenantdata.engagement.repository;

import com.vidya.tenantdata.engagement.entity.Homework;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

class HomeworkRepositoryImpl implements HomeworkRepositoryCustom {

    private static final String BASE = "SELECT h.* FROM homework h WHERE h.deleted_at IS NULL";

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    @Override
    public List<Homework> search(Filter f, OwnScope scope, int limit) {
        NativeSearch s = new NativeSearch(BASE)
                .filter("h.academic_year_id = :y", "y", f.academicYearId())
                .filter("h.academic_class_id = :c", "c", f.academicClassId())
                .filter("(h.section_id = :sec OR h.section_id IS NULL)", "sec", f.sectionId())
                .filter("h.subject_id = :s", "s", f.subjectId())
                .filter("h.teacher_id = :t", "t", f.teacherId())
                .filter("h.assigned_on >= :from", "from", f.from())
                .filter("h.assigned_on <= :to", "to", f.to());
        if (scope != null) {
            s.and("(h.teacher_id IN (:own) OR h.section_id IN (:ownSections) OR (h.section_id IS NULL AND h.academic_class_id IN (:ownClasses)))")
                    .ids("own", scope.staffIds())
                    .ids("ownSections", scope.sectionIds())
                    .ids("ownClasses", scope.classIds());
        }
        return s.list(em, Homework.class, "h.assigned_on DESC, h.created_at DESC", limit);
    }

    @Override
    public List<Homework> findPublishedDue(UUID yearId, UUID classId, UUID sectionId, LocalDate from, LocalDate to, int limit) {
        return new NativeSearch(BASE)
                .and("h.status = 'PUBLISHED' AND h.academic_year_id = :y AND h.academic_class_id = :c "
                        + "AND (h.section_id IS NULL OR h.section_id = CAST(:sec AS uuid)) AND h.due_on BETWEEN :from AND :to")
                .param("y", yearId).param("c", classId).param("sec", sectionId).param("from", from).param("to", to)
                .list(em, Homework.class, "h.due_on, h.assigned_on", limit);
    }
}
