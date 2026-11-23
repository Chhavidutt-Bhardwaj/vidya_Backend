package com.vidya.tenantdata.engagement.repository;

import com.vidya.tenantdata.engagement.entity.StudentDailyRemark;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Dynamic remark searches (implemented in {@link StudentDailyRemarkRepositoryImpl}). */
public interface StudentDailyRemarkRepositoryCustom {

    /**
     * Remarks of a student and/or of the students actively enrolled in a section, newest first; null filters are ignored.
     *
     * @param guardianVisibleOnly true to leave out staff-only remarks
     */
    List<StudentDailyRemark> search(UUID studentId, UUID sectionId, LocalDate from, LocalDate to, boolean guardianVisibleOnly, int limit);
}
