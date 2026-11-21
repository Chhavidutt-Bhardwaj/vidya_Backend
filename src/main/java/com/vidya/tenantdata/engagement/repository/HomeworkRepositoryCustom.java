package com.vidya.tenantdata.engagement.repository;

import com.vidya.tenantdata.engagement.entity.Homework;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Dynamic homework searches (implemented in {@link HomeworkRepositoryImpl}). */
public interface HomeworkRepositoryCustom {

    /** Staff-list filters; null fields are ignored. {@code from}/{@code to} apply to the assigned date. */
    record Filter(UUID academicYearId, UUID academicClassId, UUID sectionId, UUID subjectId, UUID teacherId,
                  LocalDate from, LocalDate to) {
    }

    /**
     * Newest first. With a scope: homework the user set, or set for one of their sections, or class-wide for one of
     * their classes. {@code scope == null} means full access.
     */
    List<Homework> search(Filter filter, OwnScope scope, int limit);

    /** Published homework of a class (and section, or class-wide) due between the dates, soonest first. */
    List<Homework> findPublishedDue(UUID academicYearId, UUID academicClassId, UUID sectionId, LocalDate from, LocalDate to, int limit);
}
