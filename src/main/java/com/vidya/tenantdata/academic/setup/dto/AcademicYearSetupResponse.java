package com.vidya.tenantdata.academic.setup.dto;

import com.vidya.tenantdata.academic.dto.AcademicYearResponse;

import java.util.List;
import java.util.Map;

/**
 * Result of a one-call setup.
 *
 * @param created  rows created per item: sections, curricula, curriculumSubjects, classSubjects,
 *                 terms, workingDays, classTeachers, subjectTeachers
 * @param warnings things the admin should look at (nothing failed)
 */
public record AcademicYearSetupResponse(
        AcademicYearResponse academicYear,
        Map<String, Integer> created,
        List<String> warnings,
        SetupStatusResponse status
) {
}
