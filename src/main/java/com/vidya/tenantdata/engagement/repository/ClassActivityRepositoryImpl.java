package com.vidya.tenantdata.engagement.repository;

import com.vidya.tenantdata.engagement.entity.ClassActivity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

class ClassActivityRepositoryImpl implements ClassActivityRepositoryCustom {

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    @Override
    public List<ClassActivity> search(UUID yearId, UUID classId, UUID sectionId, LocalDate from, LocalDate to, OwnScope scope, int limit) {
        NativeSearch s = new NativeSearch("SELECT a.* FROM class_activity a WHERE a.deleted_at IS NULL")
                .filter("a.academic_year_id = :y", "y", yearId)
                .filter("a.academic_class_id = :c", "c", classId)
                .filter("(a.section_id = :sec OR a.section_id IS NULL)", "sec", sectionId)
                .filter("a.activity_date >= :from", "from", from)
                .filter("a.activity_date <= :to", "to", to);
        if (scope != null) {
            s.and("(a.created_by = CAST(:me AS uuid) OR a.section_id IN (:ownSections) "
                            + "OR (a.section_id IS NULL AND a.academic_class_id IN (:ownClasses)))")
                    .param("me", scope.actorId())
                    .ids("ownSections", scope.sectionIds())
                    .ids("ownClasses", scope.classIds());
        }
        return s.list(em, ClassActivity.class, "a.activity_date DESC, a.created_at DESC", limit);
    }
}
