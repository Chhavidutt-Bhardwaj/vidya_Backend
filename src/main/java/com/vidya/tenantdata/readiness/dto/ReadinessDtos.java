package com.vidya.tenantdata.readiness.dto;

import com.vidya.tenantdata.academic.enums.AcademicYearStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Validation and readiness of one academic year: what blocks go-live, what should be fixed, what waits for someone's approval. */
public final class ReadinessDtos {

    private ReadinessDtos() {
    }

    /** BLOCKER: the school cannot run on this. WARNING: fix before going live. INFO: worth knowing. */
    public enum Severity { BLOCKER, WARNING, INFO }

    public enum Category {
        CONFIGURATION("Configuration"),
        CONFLICTS("Conflicts"),
        MISSING_DATA("Missing data"),
        PENDING_APPROVALS("Pending approvals");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** BLOCKED while any blocker is open; READY_WITH_WARNINGS when only warnings and notes are left. */
    public enum Verdict { READY, READY_WITH_WARNINGS, BLOCKED }

    /** One offending row, e.g. a section, a teacher or a timetable entry. */
    public record Sample(String id, String label) {
    }

    /**
     * @param count   rows found (sections, teachers, papers…); 0 when the finding is not countable
     * @param samples up to five of them, to show or link to
     * @param api     endpoint that fixes it
     */
    public record Check(String code, Category category, String area, Severity severity, String message, long count,
                        List<Sample> samples, String api) {
    }

    public record CategoryResult(Category key, String label, int blockers, int warnings, int notes, List<Check> checks) {
    }

    public record Summary(int blockers, int warnings, int notes, int pendingApprovals) {
    }

    /**
     * @param readyToGoLive no blockers (warnings may remain)
     * @param notChecked    areas skipped because the user may not read them, or the school's plan lacks them
     */
    public record ReadinessReport(UUID academicYearId, String code, String name, AcademicYearStatus status, boolean current,
                                  Verdict verdict, boolean readyToGoLive, int percentComplete, Summary summary,
                                  List<CategoryResult> categories, List<String> notChecked, Instant checkedAt) {
    }
}
