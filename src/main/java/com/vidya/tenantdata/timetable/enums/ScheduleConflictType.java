package com.vidya.tenantdata.timetable.enums;

/**
 * Controlled categorization of timetable scheduling conflicts.
 */
public enum ScheduleConflictType {
    TEACHER_CONFLICT,
    CLASS_CONFLICT,
    SECTION_CONFLICT,
    ROOM_CONFLICT,
    PERIOD_CONFLICT,
    ACADEMIC_YEAR_CONFLICT,
    TEACHER_ASSIGNMENT_CONFLICT,
    CLASS_SUBJECT_CONFLICT,
    PERIOD_OVERLAP,
    INVALID_SCHEDULE_STATE
}
