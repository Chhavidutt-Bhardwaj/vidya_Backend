package com.vidya.tenantdata.readiness.service;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.SecurityUtils;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.setup.dto.SetupStatusResponse;
import com.vidya.tenantdata.academic.setup.dto.SetupStatusResponse.Level;
import com.vidya.tenantdata.academic.setup.service.AcademicSetupService;
import com.vidya.tenantdata.operations.dto.OperationsDtos.Area;
import com.vidya.tenantdata.operations.dto.OperationsDtos.Issue;
import com.vidya.tenantdata.operations.dto.OperationsDtos.OperationsOverview;
import com.vidya.tenantdata.operations.service.OperationsOverviewService;
import com.vidya.tenantdata.readiness.dto.ReadinessDtos.Category;
import com.vidya.tenantdata.readiness.dto.ReadinessDtos.CategoryResult;
import com.vidya.tenantdata.readiness.dto.ReadinessDtos.Check;
import com.vidya.tenantdata.readiness.dto.ReadinessDtos.ReadinessReport;
import com.vidya.tenantdata.readiness.dto.ReadinessDtos.Sample;
import com.vidya.tenantdata.readiness.dto.ReadinessDtos.Severity;
import com.vidya.tenantdata.readiness.dto.ReadinessDtos.Summary;
import com.vidya.tenantdata.readiness.dto.ReadinessDtos.Verdict;
import com.vidya.tenantdata.readiness.service.ReadinessRules.Rule;
import com.vidya.tenantdata.timetable.dto.ScheduleConflictResponse;
import com.vidya.tenantdata.timetable.dto.ScheduleValidationResponse;
import com.vidya.tenantdata.timetable.enums.ConflictSeverity;
import com.vidya.tenantdata.timetable.service.TimetableConflictValidator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Validation and readiness of an academic year, in one place: settings that do not add up, data that contradicts other
 * data, what is still missing, and work waiting for someone's approval. Read-only.
 *
 * <p>Built from the year setup checklist, the operations overview, the timetable clash validator and the
 * {@link ReadinessRules}. Areas the user may not read are listed in {@code notChecked} instead of failing the request.
 */
@Service
@RequiredArgsConstructor
public class ReadinessService {

    static final int SAMPLES = 5;
    static final int MAX_ROWS = 1000;

    /** Operations issues already reported by the setup checklist or a pending-approval rule. */
    private static final Set<String> COVERED_ELSEWHERE = Set.of("SECTIONS_WITHOUT_CLASS_TEACHER", "NO_WORKING_DAYS", "NO_BELL_SCHEDULE",
            "NO_TIMETABLE", "NO_TERMS", "CLASSES_WITHOUT_GRADING_SCALE", "FEE_STRUCTURE_DRAFT");
    /** Checklist steps reported elsewhere (lesson plans are a pending approval) or by the operations overview. */
    private static final Set<String> STEPS_COVERED_ELSEWHERE = Set.of("LESSON_PLANS");

    private final AcademicYearRepository yearRepository;
    private final AcademicSetupService setupService;
    private final OperationsOverviewService operationsOverview;
    private final TimetableConflictValidator timetableValidator;

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public ReadinessReport check(UUID academicYearId) {
        AcademicYear year = yearRepository.findByIdAndNotDeleted(academicYearId)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", academicYearId));
        UUID y = year.getId();
        List<Check> checks = new ArrayList<>();
        Set<String> notChecked = new TreeSet<>();
        Set<String> checked = new TreeSet<>();

        SetupStatusResponse status = setupService.status(y);
        checks.addAll(fromChecklist(status));

        for (Rule rule : ReadinessRules.RULES) {
            if (rule.authority() != null && !SecurityUtils.hasAuthority(rule.authority())) {
                notChecked.add(rule.area());
                continue;
            }
            checked.add(rule.area());
            List<Sample> rows = rows(rule.sql(), y);
            if (!rows.isEmpty()) {
                checks.add(new Check(rule.code(), rule.category(), rule.area(), rule.severity(), rule.message().formatted(rows.size()),
                        rows.size(), samples(rows), rule.api()));
            }
        }

        if (SecurityUtils.hasAuthority("TIMETABLE_READ")) {
            checks.addAll(timetableClashes(y));
            checked.add("TIMETABLE");
        } else {
            notChecked.add("TIMETABLE");
        }

        if (SecurityUtils.hasAuthority("OPERATIONS_CONFIG_READ")) {
            checks.addAll(fromOperations(operationsOverview.overview(y), notChecked));
        } else {
            notChecked.add("OPERATIONS");
        }

        if (year.getStatus() == AcademicYearStatus.DRAFT) {
            checks.add(new Check("YEAR_NOT_STARTED", Category.PENDING_APPROVALS, "ACADEMICS", Severity.INFO,
                    "The year is still a draft. Start it once the blockers are cleared.", 0, List.of(), "POST /api/v1/academic-years/{id}/start"));
        }

        notChecked.removeAll(checked);
        return report(year, status.percentComplete(), checks, notChecked);
    }

    // ── Sources ──────────────────────────────────────────────────────────────

    /** Unfinished checklist steps: REQUIRED ones block go-live, RECOMMENDED ones warn; OPTIONAL ones come from the operations overview. */
    static List<Check> fromChecklist(SetupStatusResponse status) {
        List<Check> out = new ArrayList<>();
        for (SetupStatusResponse.Step step : status.steps()) {
            if (step.done() || step.level() == Level.OPTIONAL || STEPS_COVERED_ELSEWHERE.contains(step.key())) {
                continue;
            }
            Severity severity = step.level() == Level.REQUIRED ? Severity.BLOCKER : Severity.WARNING;
            String message = step.hint() == null || step.hint().isBlank() ? step.label() + " is not done." : step.label() + ": " + step.hint();
            out.add(new Check("MISSING_" + step.key(), Category.MISSING_DATA, areaOf(step.key()), severity, message, step.pending(),
                    List.of(), step.api()));
        }
        return out;
    }

    /** Operations issues keep their code; ERROR blocks only once the school has started using the area. */
    static List<Check> fromOperations(OperationsOverview overview, Set<String> notChecked) {
        List<Check> out = new ArrayList<>();
        for (Area area : overview.areas()) {
            if (!area.available()) {
                notChecked.add(area.key());
                continue;
            }
            if ("NOT_STARTED".equals(area.state())) {
                String api = area.issues().isEmpty() ? null : area.issues().get(0).api();
                out.add(new Check(area.key() + "_NOT_SET_UP", Category.MISSING_DATA, area.key(), Severity.INFO,
                        area.label() + " is not set up yet. Skip it if the school does not use it.", 0, List.of(), api));
                continue;
            }
            for (Issue issue : area.issues()) {
                if (COVERED_ELSEWHERE.contains(issue.code())) {
                    continue;
                }
                Severity severity = switch (issue.severity()) {
                    case "ERROR" -> Severity.BLOCKER;
                    case "WARNING" -> Severity.WARNING;
                    default -> Severity.INFO;
                };
                out.add(new Check(issue.code(), Category.CONFIGURATION, area.key(), severity, issue.message(), 0, List.of(), issue.api()));
            }
        }
        return out;
    }

    /**
     * Runs the clash validator on every draft, published and active timetable of the year. A clash in a live timetable
     * blocks; in a draft it warns. Validator warnings (not errors) are one level softer.
     */
    private List<Check> timetableClashes(UUID y) {
        @SuppressWarnings("unchecked")
        List<Object[]> timetables = em.createNativeQuery("SELECT id, name, status FROM timetable WHERE academic_year_id = :y "
                        + "AND deleted_at IS NULL AND status IN ('DRAFT', 'PUBLISHED', 'ACTIVE') ORDER BY name")
                .setParameter("y", y).getResultList();
        List<Check> out = new ArrayList<>();
        for (Object[] t : timetables) {
            UUID id = t[0] instanceof UUID u ? u : UUID.fromString(t[0].toString());
            ScheduleValidationResponse result = timetableValidator.validateTimetable(id);
            Check check = timetableCheck(id, (String) t[1], (String) t[2], result);
            if (check != null) {
                out.add(check);
            }
        }
        return out;
    }

    static Check timetableCheck(UUID id, String name, String status, ScheduleValidationResponse result) {
        List<ScheduleConflictResponse> conflicts = result.conflicts() == null ? List.of() : result.conflicts();
        if (conflicts.isEmpty()) {
            return null;
        }
        boolean live = !"DRAFT".equals(status);
        boolean errors = conflicts.stream().anyMatch(c -> c.severity() == ConflictSeverity.ERROR);
        Severity severity = errors ? (live ? Severity.BLOCKER : Severity.WARNING) : (live ? Severity.WARNING : Severity.INFO);
        String byType = conflicts.stream()
                .collect(Collectors.groupingBy(c -> c.type().name().toLowerCase().replace('_', ' '), LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream().map(e -> e.getValue() + " " + e.getKey()).collect(Collectors.joining(", "));
        List<Sample> samples = conflicts.stream().limit(SAMPLES)
                .map(c -> new Sample(c.entryId() == null ? id.toString() : c.entryId().toString(), c.message())).toList();
        return new Check("TIMETABLE_CLASHES", Category.CONFLICTS, "TIMETABLE", severity,
                "Timetable '" + name + "' (" + status + ") has " + conflicts.size() + " clash(es): " + byType + ".",
                conflicts.size(), samples, "POST /api/v1/timetables/" + id + "/validate");
    }

    // ── Report ───────────────────────────────────────────────────────────────

    static ReadinessReport report(AcademicYear year, int percentComplete, List<Check> checks, Set<String> notChecked) {
        Comparator<Check> order = Comparator.comparing(Check::category).thenComparing(Check::severity).thenComparing(Check::area);
        List<Check> sorted = checks.stream().sorted(order).toList();
        Map<Category, List<Check>> byCategory = new LinkedHashMap<>();
        for (Category c : Category.values()) {
            byCategory.put(c, new ArrayList<>());
        }
        sorted.forEach(c -> byCategory.get(c.category()).add(c));

        List<CategoryResult> categories = byCategory.entrySet().stream()
                .map(e -> new CategoryResult(e.getKey(), e.getKey().label(), count(e.getValue(), Severity.BLOCKER),
                        count(e.getValue(), Severity.WARNING), count(e.getValue(), Severity.INFO), e.getValue()))
                .toList();
        int blockers = count(sorted, Severity.BLOCKER);
        int warnings = count(sorted, Severity.WARNING);
        int notes = count(sorted, Severity.INFO);
        int approvals = (int) sorted.stream().filter(c -> c.category() == Category.PENDING_APPROVALS && c.severity() != Severity.INFO).count();
        Verdict verdict = blockers > 0 ? Verdict.BLOCKED : warnings > 0 ? Verdict.READY_WITH_WARNINGS : Verdict.READY;

        List<String> skipped = List.copyOf(notChecked);

        return new ReadinessReport(year.getId(), year.getCode(), year.getName(), year.getStatus(), year.isCurrent(), verdict,
                blockers == 0, percentComplete, new Summary(blockers, warnings, notes, approvals), categories, skipped, Instant.now());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private List<Sample> rows(String sql, UUID y) {
        var q = em.createNativeQuery(sql);
        if (sql.contains(":y")) {
            q.setParameter("y", y);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.setMaxResults(MAX_ROWS).getResultList();
        return rows.stream().map(r -> new Sample(String.valueOf(r[0]), String.valueOf(r[1]))).toList();
    }

    private static List<Sample> samples(List<Sample> rows) {
        return rows.size() <= SAMPLES ? rows : rows.subList(0, SAMPLES);
    }

    private static int count(List<Check> checks, Severity severity) {
        return (int) checks.stream().filter(c -> c.severity() == severity).count();
    }

    private static final Map<String, String> STEP_AREAS = Map.ofEntries(
            Map.entry("STAFF", "STAFF"), Map.entry("STUDENTS", "STUDENTS"), Map.entry("PARENT_LOGINS", "STUDENTS"),
            Map.entry("TERMS", "EXAMINATIONS"), Map.entry("GRADING", "EXAMINATIONS"),
            Map.entry("WORKING_DAYS", "CALENDAR"), Map.entry("HOLIDAYS", "CALENDAR"),
            Map.entry("BELL_SCHEDULE", "TIMETABLE"), Map.entry("TIMETABLE", "TIMETABLE"));

    static String areaOf(String stepKey) {
        return STEP_AREAS.getOrDefault(stepKey, "ACADEMICS");
    }
}
