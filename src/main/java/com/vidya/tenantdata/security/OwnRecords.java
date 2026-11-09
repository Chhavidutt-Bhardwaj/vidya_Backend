package com.vidya.tenantdata.security;

import java.util.Set;
import java.util.UUID;

/**
 * The records a login is personally linked to inside one school (tenant DB). Used for the OWN
 * data scope.
 *
 * @param selfStudentIds       student rows of the login itself (a student account)
 * @param childStudentIds      students whose guardian row is linked to this login (a parent account)
 * @param staffIds             staff rows of the login (a teacher / staff account)
 * @param sectionIds           sections the staff member is class teacher of, or teaches a subject in
 * @param wholeClassIds        classes where the staff member teaches a subject to every section
 * @param subjectClassPairs    "subjectId:classId" pairs the staff member teaches (for exam marks)
 * @param taughtStudentIds     students currently enrolled in those sections / classes
 */
public record OwnRecords(
        Set<UUID> selfStudentIds,
        Set<UUID> childStudentIds,
        Set<UUID> staffIds,
        Set<UUID> sectionIds,
        Set<UUID> wholeClassIds,
        Set<String> subjectClassPairs,
        Set<UUID> taughtStudentIds
) {
    public static final OwnRecords NONE = new OwnRecords(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

    public OwnRecords {
        selfStudentIds = Set.copyOf(selfStudentIds);
        childStudentIds = Set.copyOf(childStudentIds);
        staffIds = Set.copyOf(staffIds);
        sectionIds = Set.copyOf(sectionIds);
        wholeClassIds = Set.copyOf(wholeClassIds);
        subjectClassPairs = Set.copyOf(subjectClassPairs);
        taughtStudentIds = Set.copyOf(taughtStudentIds);
    }

    public boolean includesStudent(UUID studentId) {
        return studentId != null && (selfStudentIds.contains(studentId)
                || childStudentIds.contains(studentId)
                || taughtStudentIds.contains(studentId));
    }

    /** All students visible under OWN scope. */
    public Set<UUID> allStudentIds() {
        java.util.Set<UUID> all = new java.util.LinkedHashSet<>(selfStudentIds);
        all.addAll(childStudentIds);
        all.addAll(taughtStudentIds);
        return all;
    }
}
