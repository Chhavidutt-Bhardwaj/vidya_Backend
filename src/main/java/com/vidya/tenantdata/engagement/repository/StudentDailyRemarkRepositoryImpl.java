package com.vidya.tenantdata.engagement.repository;

import com.vidya.tenantdata.engagement.entity.StudentDailyRemark;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

class StudentDailyRemarkRepositoryImpl implements StudentDailyRemarkRepositoryCustom {

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    @Override
    public List<StudentDailyRemark> search(UUID studentId, UUID sectionId, LocalDate from, LocalDate to, boolean guardianVisibleOnly,
                                           int limit) {
        NativeSearch s = new NativeSearch("SELECT r.* FROM student_daily_remark r WHERE r.deleted_at IS NULL")
                .filter("r.student_id = :s", "s", studentId)
                .filter("r.student_id IN (SELECT e.student_id FROM student_academic_enrollment e WHERE e.section_id = :sec "
                        + "AND e.deleted_at IS NULL AND e.enrollment_status IN ('ADMITTED', 'ACTIVE'))", "sec", sectionId)
                .filter("r.remark_date >= :from", "from", from)
                .filter("r.remark_date <= :to", "to", to);
        if (guardianVisibleOnly) {
            s.and("r.visible_to_guardians = TRUE");
        }
        return s.list(em, StudentDailyRemark.class, "r.remark_date DESC, r.created_at DESC", limit);
    }
}
