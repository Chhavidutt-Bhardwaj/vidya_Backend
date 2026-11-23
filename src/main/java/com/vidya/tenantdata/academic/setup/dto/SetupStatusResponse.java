package com.vidya.tenantdata.academic.setup.dto;

import com.vidya.tenantdata.academic.enums.AcademicYearStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Setup checklist of one academic year, in the order the admin should work through it.
 *
 * @param readyToStart every REQUIRED step is done (safe to activate / make current)
 */
public record SetupStatusResponse(
        UUID academicYearId,
        String code,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        AcademicYearStatus status,
        boolean current,
        int requiredDone,
        int requiredTotal,
        int percentComplete,
        boolean readyToStart,
        List<Step> steps
) {
    public enum Level { REQUIRED, RECOMMENDED, OPTIONAL }

    /**
     * @param key     stable id for the UI, e.g. "SECTIONS"
     * @param count   rows found (meaning depends on the step)
     * @param pending rows still missing something (e.g. sections without a class teacher), 0 if n/a
     * @param hint    what to do next, empty when done
     * @param api     endpoint that completes the step
     */
    public record Step(String key, String label, Level level, boolean done, long count, long pending,
                       String hint, String api) {
    }
}
