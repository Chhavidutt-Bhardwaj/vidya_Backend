package com.vidya.tenantdata.security;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The records a login is personally linked to inside one school (tenant DB). Used for the OWN
 * data scope.
 *
 * @param tenantUserIds        tenant_user rows of the login (its user accounts in this school)
 * @param selfStudentIds       student rows of the login itself (a student account)
 * @param childStudentIds      students whose guardian row is linked to this login (a parent account)
 * @param staffIds             staff rows of the login (a teacher / staff account)
 * @param sectionIds           sections the staff member is class teacher of, or teaches a subject in
 * @param wholeClassIds        classes where the staff member teaches a subject to every section
 * @param teachingClassIds     every class the staff member teaches in (classes of sectionIds + wholeClassIds)
 * @param subjectClassPairs    "subjectId:classId" pairs the staff member teaches (for exam marks)
 * @param taughtStudentIds     students currently enrolled in those sections / classes
 * @param studentSectionIds    sections the user's own / children's students are enrolled in (for timetables)
 */
public record OwnRecords(
        Set<UUID> tenantUserIds,
        Set<UUID> selfStudentIds,
        Set<UUID> childStudentIds,
        Set<UUID> staffIds,
        Set<UUID> sectionIds,
        Set<UUID> wholeClassIds,
        Set<UUID> teachingClassIds,
        Set<String> subjectClassPairs,
        Set<UUID> taughtStudentIds,
        Set<UUID> studentSectionIds
) {
    public static final OwnRecords NONE = builder().build();

    public OwnRecords {
        tenantUserIds = Set.copyOf(tenantUserIds);
        selfStudentIds = Set.copyOf(selfStudentIds);
        childStudentIds = Set.copyOf(childStudentIds);
        staffIds = Set.copyOf(staffIds);
        sectionIds = Set.copyOf(sectionIds);
        wholeClassIds = Set.copyOf(wholeClassIds);
        teachingClassIds = Set.copyOf(teachingClassIds);
        subjectClassPairs = Set.copyOf(subjectClassPairs);
        taughtStudentIds = Set.copyOf(taughtStudentIds);
        studentSectionIds = Set.copyOf(studentSectionIds);
    }

    public boolean includesStudent(UUID studentId) {
        return studentId != null && (selfStudentIds.contains(studentId)
                || childStudentIds.contains(studentId)
                || taughtStudentIds.contains(studentId));
    }

    public boolean includesStaff(UUID staffId) {
        return staffId != null && staffIds.contains(staffId);
    }

    /** All students visible under OWN scope. */
    public Set<UUID> allStudentIds() {
        Set<UUID> all = new LinkedHashSet<>(selfStudentIds);
        all.addAll(childStudentIds);
        all.addAll(taughtStudentIds);
        return all;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Mutable builder (also convenient in tests). */
    public static final class Builder {
        private Set<UUID> tenantUserIds = Set.of();
        private Set<UUID> selfStudentIds = Set.of();
        private Set<UUID> childStudentIds = Set.of();
        private Set<UUID> staffIds = Set.of();
        private Set<UUID> sectionIds = Set.of();
        private Set<UUID> wholeClassIds = Set.of();
        private Set<UUID> teachingClassIds = Set.of();
        private Set<String> subjectClassPairs = Set.of();
        private Set<UUID> taughtStudentIds = Set.of();
        private Set<UUID> studentSectionIds = Set.of();

        public Builder tenantUserIds(Set<UUID> v) { tenantUserIds = v; return this; }
        public Builder selfStudentIds(Set<UUID> v) { selfStudentIds = v; return this; }
        public Builder childStudentIds(Set<UUID> v) { childStudentIds = v; return this; }
        public Builder staffIds(Set<UUID> v) { staffIds = v; return this; }
        public Builder sectionIds(Set<UUID> v) { sectionIds = v; return this; }
        public Builder wholeClassIds(Set<UUID> v) { wholeClassIds = v; return this; }
        public Builder teachingClassIds(Set<UUID> v) { teachingClassIds = v; return this; }
        public Builder subjectClassPairs(Set<String> v) { subjectClassPairs = v; return this; }
        public Builder taughtStudentIds(Set<UUID> v) { taughtStudentIds = v; return this; }
        public Builder studentSectionIds(Set<UUID> v) { studentSectionIds = v; return this; }

        public OwnRecords build() {
            return new OwnRecords(tenantUserIds, selfStudentIds, childStudentIds, staffIds, sectionIds, wholeClassIds,
                    teachingClassIds, subjectClassPairs, taughtStudentIds, studentSectionIds);
        }
    }
}
