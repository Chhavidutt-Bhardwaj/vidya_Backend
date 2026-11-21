package com.vidya.tenantdata.engagement.repository;

import com.vidya.tenantdata.engagement.entity.ClassActivity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Dynamic class-activity searches (implemented in {@link ClassActivityRepositoryImpl}). */
public interface ClassActivityRepositoryCustom {

    /**
     * Newest first; null filters are ignored. With a scope: activities the user created, or for one of their sections, or
     * class-wide for one of their classes. {@code scope == null} means full access.
     */
    List<ClassActivity> search(UUID academicYearId, UUID academicClassId, UUID sectionId, LocalDate from, LocalDate to,
                               OwnScope scope, int limit);
}
