package com.vidya.tenantdata.academic.setup.dto;

import com.vidya.tenantdata.timetable.dto.TimetableResponse;

import java.util.List;
import java.util.Map;

/**
 * @param skipped        source slots not copied, by reason (SECTION_NOT_IN_NEW_YEAR, CLASS_INACTIVE,
 *                       SUBJECT_INACTIVE, PERIOD_NOT_IN_BELL_SCHEDULE)
 * @param withoutTeacher copied slots that still need a teacher
 * @param teacherClashes slots left without a teacher because that teacher already teaches another
 *                       class in the same period
 */
public record TimetableCarryOverResponse(
        TimetableResponse timetable,
        int sourceSlots,
        int copied,
        int withTeacher,
        int withoutTeacher,
        int teacherClashes,
        Map<String, Integer> skipped,
        List<String> nextSteps
) {
}
