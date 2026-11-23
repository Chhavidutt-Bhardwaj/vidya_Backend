package com.vidya.tenantdata.academic.setup.dto;

import com.vidya.tenantdata.academic.setup.dto.PromotionRequest.PromotionAction;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Promotion plan (preview) or outcome (execute). Same shape for both so the UI can show the
 * preview, let the admin adjust overrides, and then run it.
 */
public record PromotionResult(
        boolean dryRun,
        UUID sourceAcademicYearId,
        UUID targetAcademicYearId,
        int totalStudents,
        Map<PromotionAction, Integer> byAction,
        int withoutSection,
        List<ClassSummary> classes,
        List<StudentLine> students,
        List<String> warnings
) {
    public record ClassSummary(UUID fromClassId, String fromClass, UUID toClassId, String toClass,
                               int students, int promoted, int detained, int graduated, int left, int skipped,
                               int withoutSection) {
    }

    public record StudentLine(UUID studentId, String studentCode, UUID enrollmentId,
                              String fromClass, String fromSection, String rollNumber,
                              PromotionAction action, UUID toClassId, String toClass,
                              UUID toSectionId, String toSection, UUID newEnrollmentId, String note) {
    }
}
