package com.vidya.tenantdata.operations.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.SecurityUtils;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.operations.dto.OperationsDtos.Area;
import com.vidya.tenantdata.operations.dto.OperationsDtos.CommunicationSettingsResponse;
import com.vidya.tenantdata.operations.dto.OperationsDtos.Issue;
import com.vidya.tenantdata.operations.dto.OperationsDtos.OperationsOverview;
import com.vidya.tenantdata.operations.dto.OperationsDtos.TemplateResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How far each area of operations is set up for an academic year, with what is still missing.
 * An area the signed-in user cannot read (not in the school's plan, or not their job) is returned as unavailable.
 */
@Service
@RequiredArgsConstructor
public class OperationsOverviewService {

    private static final String BASE = "/api/v1/operations-config";
    private static final String ERROR = "ERROR";
    private static final String WARNING = "WARNING";
    private static final String INFO = "INFO";

    private final AcademicYearRepository yearRepository;
    private final CommunicationConfigService communicationService;

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public OperationsOverview overview(UUID academicYearId) {
        AcademicYear year = academicYearId != null
                ? yearRepository.findByIdAndNotDeleted(academicYearId).orElseThrow(() -> new ResourceNotFoundException("AcademicYear", academicYearId))
                : yearRepository.findCurrentYear().orElseThrow(() -> new BusinessException("There is no current academic year; pass academicYearId"));
        UUID y = year.getId();
        List<Area> areas = new ArrayList<>();
        areas.add(SecurityUtils.hasAuthority("FEE_READ") ? fees(y) : unavailable("FEES", "Fees"));
        areas.add(SecurityUtils.hasAuthority("ATTENDANCE_READ") ? attendance(y) : unavailable("ATTENDANCE", "Attendance"));
        areas.add(SecurityUtils.hasAuthority("TIMETABLE_READ") ? timetable(y) : unavailable("TIMETABLE", "Timetable"));
        areas.add(SecurityUtils.hasAuthority("EXAM_READ") ? examinations(y) : unavailable("EXAMINATIONS", "Examinations"));
        areas.add(SecurityUtils.hasAuthority("TRANSPORT_READ") ? transport() : unavailable("TRANSPORT", "Transport"));
        areas.add(SecurityUtils.hasAuthority("LIBRARY_READ") ? library() : unavailable("LIBRARY", "Library"));
        areas.add(SecurityUtils.hasAuthority("COMMUNICATION_READ") ? communication() : unavailable("COMMUNICATION", "Communication"));
        return new OperationsOverview(y, year.getName(), areas);
    }

    private Area fees(UUID y) {
        long heads = count("SELECT count(*) FROM fee_head WHERE deleted_at IS NULL AND status = 'ACTIVE'", null);
        long active = count("SELECT count(*) FROM fee_structure WHERE academic_year_id = :y AND deleted_at IS NULL AND status = 'ACTIVE'", y);
        long draft = count("SELECT count(*) FROM fee_structure WHERE academic_year_id = :y AND deleted_at IS NULL AND status = 'DRAFT'", y);
        long classesWithout = count("SELECT count(*) FROM academic_class c WHERE c.deleted_at IS NULL AND c.status = 'ACTIVE' AND NOT EXISTS "
                + "(SELECT 1 FROM fee_structure f WHERE f.academic_class_id = c.id AND f.academic_year_id = :y AND f.deleted_at IS NULL AND f.status = 'ACTIVE')", y);
        long installments = count("SELECT count(*) FROM fee_installment WHERE academic_year_id = :y", y);
        long settings = count("SELECT count(*) FROM fee_settings WHERE academic_year_id = :y", y);
        long concessions = count("SELECT count(*) FROM fee_concession_type WHERE deleted_at IS NULL AND status = 'ACTIVE'", null);

        List<Issue> issues = new ArrayList<>();
        if (heads == 0) {
            issues.add(new Issue("NO_FEE_HEADS", ERROR, "No fee heads yet. Add your own or start from the standard set.", "POST " + BASE + "/fees/heads/standard"));
        }
        if (classesWithout > 0) {
            issues.add(new Issue("CLASSES_WITHOUT_FEE_STRUCTURE", WARNING, classesWithout + " class(es) have no active fee structure this year.", "PUT " + BASE + "/fees/structures"));
        }
        if (draft > 0) {
            issues.add(new Issue("FEE_STRUCTURE_DRAFT", WARNING, draft + " fee structure(s) are still DRAFT; make them ACTIVE once checked.", "PATCH " + BASE + "/fees/structures/{id}/status"));
        }
        if (installments == 0) {
            issues.add(new Issue("NO_INSTALLMENTS", WARNING, "No due dates yet: generate the instalment plan or enter your own.", "POST " + BASE + "/fees/installments/generate"));
        }
        if (settings == 0) {
            issues.add(new Issue("FEE_SETTINGS_DEFAULT", INFO, "Late fine, due day and receipt prefix are on the defaults.", "PUT " + BASE + "/fees/settings"));
        }
        return area("FEES", "Fees", heads + active + draft > 0, issues, "feeHeads", heads, "activeStructures", active, "draftStructures", draft,
                "classesWithoutStructure", classesWithout, "installments", installments, "concessionTypes", concessions);
    }

    private Area attendance(UUID y) {
        long settings = count("SELECT count(*) FROM attendance_settings", null);
        long workingDays = count("SELECT count(*) FROM working_day_config WHERE academic_year_id = :y AND deleted_at IS NULL", y);
        long holidays = count("SELECT count(*) FROM school_holiday WHERE academic_year_id = :y AND deleted_at IS NULL", y);
        long sectionsWithout = count("SELECT count(*) FROM section s WHERE s.academic_year_id = :y AND s.deleted_at IS NULL "
                + "AND s.status = 'ACTIVE' AND NOT EXISTS (SELECT 1 FROM class_teacher_assignment t WHERE t.section_id = s.id "
                + "AND t.academic_year_id = :y AND t.deleted_at IS NULL AND t.status = 'ACTIVE')", y);

        List<Issue> issues = new ArrayList<>();
        if (workingDays == 0) {
            issues.add(new Issue("NO_WORKING_DAYS", WARNING, "Working days are not set; Monday to Saturday is assumed.", "PUT /api/v1/calendar/config?academicYearId=…"));
        }
        if (sectionsWithout > 0) {
            issues.add(new Issue("SECTIONS_WITHOUT_CLASS_TEACHER", WARNING, sectionsWithout + " section(s) have no class teacher to mark attendance.",
                    "PUT /api/v1/academic-config/class-teachers"));
        }
        if (settings == 0) {
            issues.add(new Issue("ATTENDANCE_RULES_DEFAULT", INFO, "Marking rules are on the defaults (no back-date limit, 75% minimum).", "PUT " + BASE + "/attendance/settings"));
        }
        return area("ATTENDANCE", "Attendance", settings + workingDays > 0, issues, "rulesSaved", settings, "workingDayConfigs", workingDays,
                "holidays", holidays, "sectionsWithoutClassTeacher", sectionsWithout);
    }

    private Area timetable(UUID y) {
        long bells = count("SELECT count(*) FROM bell_schedule WHERE deleted_at IS NULL AND status = 'ACTIVE'", null);
        long rooms = count("SELECT count(*) FROM room WHERE deleted_at IS NULL AND status = 'ACTIVE'", null);
        long live = count("SELECT count(*) FROM timetable WHERE academic_year_id = :y AND deleted_at IS NULL AND status IN ('PUBLISHED', 'ACTIVE')", y);
        long draft = count("SELECT count(*) FROM timetable WHERE academic_year_id = :y AND deleted_at IS NULL AND status = 'DRAFT'", y);

        List<Issue> issues = new ArrayList<>();
        if (bells == 0) {
            issues.add(new Issue("NO_BELL_SCHEDULE", ERROR, "Define the periods of the school day before building a timetable.", "POST /api/v1/bell-schedules"));
        }
        if (live == 0) {
            issues.add(new Issue("NO_TIMETABLE", WARNING, draft > 0 ? "The timetable is still a draft; validate and publish it." : "No timetable for this year yet.",
                    "POST /api/v1/timetables"));
        }
        if (rooms == 0) {
            issues.add(new Issue("NO_ROOMS", INFO, "No rooms are defined; room clashes cannot be checked.", "POST /api/v1/rooms"));
        }
        return area("TIMETABLE", "Timetable", bells + live + draft > 0, issues, "bellSchedules", bells, "rooms", rooms, "publishedTimetables", live, "draftTimetables", draft);
    }

    private Area examinations(UUID y) {
        long scales = count("SELECT count(*) FROM grading_scale WHERE deleted_at IS NULL", null);
        long classesWithoutScale = count("SELECT count(*) FROM academic_class c WHERE c.deleted_at IS NULL AND c.status = 'ACTIVE' "
                + "AND NOT EXISTS (SELECT 1 FROM grading_scale g WHERE g.deleted_at IS NULL AND (g.id = c.grading_scale_id OR g.is_default))", null);
        long terms = count("SELECT count(*) FROM exam_term WHERE academic_year_id = :y AND deleted_at IS NULL", y);
        long schedules = count("SELECT count(*) FROM exam_schedule s JOIN exam_term t ON t.id = s.exam_term_id "
                + "WHERE t.academic_year_id = :y AND s.deleted_at IS NULL AND t.deleted_at IS NULL", y);

        List<Issue> issues = new ArrayList<>();
        if (classesWithoutScale > 0) {
            issues.add(new Issue("CLASSES_WITHOUT_GRADING_SCALE", WARNING, classesWithoutScale + " class(es) have no grading scale and there is no default one.",
                    "POST /api/v1/exams/grading-scales/presets/{key}"));
        }
        if (terms == 0) {
            issues.add(new Issue("NO_TERMS", WARNING, "No terms / exam periods for this year.", "POST /api/v1/exams/terms"));
        } else if (schedules == 0) {
            issues.add(new Issue("NO_EXAM_SCHEDULE", INFO, "No exam schedule yet; add one per class when the dates are known.", "POST /api/v1/exams/schedules"));
        }
        return area("EXAMINATIONS", "Examinations", scales + terms > 0, issues, "gradingScales", scales, "classesWithoutGradingScale", classesWithoutScale,
                "terms", terms, "examSchedules", schedules);
    }

    private Area transport() {
        long vehicles = count("SELECT count(*) FROM transport_vehicle WHERE deleted_at IS NULL AND status <> 'INACTIVE'", null);
        long routes = count("SELECT count(*) FROM transport_route WHERE deleted_at IS NULL AND status = 'ACTIVE'", null);
        long stops = count("SELECT count(*) FROM transport_stop s JOIN transport_route r ON r.id = s.route_id WHERE r.deleted_at IS NULL AND r.status = 'ACTIVE'", null);
        long routesWithoutVehicle = count("SELECT count(*) FROM transport_route r WHERE r.deleted_at IS NULL AND r.status = 'ACTIVE' AND NOT EXISTS "
                + "(SELECT 1 FROM transport_vehicle v WHERE v.id = r.vehicle_id AND v.deleted_at IS NULL AND v.status = 'ACTIVE')", null);
        long routesWithoutStops = count("SELECT count(*) FROM transport_route r WHERE r.deleted_at IS NULL AND r.status = 'ACTIVE' AND NOT EXISTS "
                + "(SELECT 1 FROM transport_stop s WHERE s.route_id = r.id)", null);
        long withoutDriver = count("SELECT count(*) FROM transport_vehicle WHERE deleted_at IS NULL AND status = 'ACTIVE' "
                + "AND driver_staff_id IS NULL AND COALESCE(trim(driver_name), '') = ''", null);
        String anyPaper = "LEAST(COALESCE(insurance_valid_till, 'infinity'), COALESCE(fitness_valid_till, 'infinity'), "
                + "COALESCE(permit_valid_till, 'infinity'), COALESCE(puc_valid_till, 'infinity'))";
        long expired = count("SELECT count(*) FROM transport_vehicle WHERE deleted_at IS NULL AND status <> 'INACTIVE' AND " + anyPaper + " < CURRENT_DATE", null);
        long expiring = count("SELECT count(*) FROM transport_vehicle WHERE deleted_at IS NULL AND status <> 'INACTIVE' AND " + anyPaper
                + " BETWEEN CURRENT_DATE AND CURRENT_DATE + " + TransportConfigService.PAPERS_WARNING_DAYS, null);
        long stopsWithoutFee = count("SELECT count(*) FROM transport_stop s JOIN transport_route r ON r.id = s.route_id "
                + "WHERE r.deleted_at IS NULL AND r.status = 'ACTIVE' AND s.monthly_fee IS NULL", null);

        List<Issue> issues = new ArrayList<>();
        if (vehicles + routes == 0) {
            issues.add(new Issue("NO_TRANSPORT", INFO, "No vehicles or routes yet. Skip this if the school runs no transport.", "POST " + BASE + "/transport/vehicles"));
        } else {
            if (expired > 0) {
                issues.add(new Issue("VEHICLE_PAPERS_EXPIRED", ERROR, expired + " vehicle(s) have expired papers (insurance, fitness, permit or pollution).", "GET " + BASE + "/transport/vehicles"));
            }
            if (expiring > 0) {
                issues.add(new Issue("VEHICLE_PAPERS_EXPIRING", WARNING, expiring + " vehicle(s) have papers expiring within " + TransportConfigService.PAPERS_WARNING_DAYS + " days.",
                        "GET " + BASE + "/transport/vehicles"));
            }
            if (routes == 0) {
                issues.add(new Issue("NO_ROUTES", WARNING, "Vehicles are added but there are no routes.", "PUT " + BASE + "/transport/routes"));
            }
            if (routesWithoutVehicle > 0) {
                issues.add(new Issue("ROUTE_WITHOUT_VEHICLE", WARNING, routesWithoutVehicle + " route(s) have no active vehicle.", "PUT " + BASE + "/transport/routes"));
            }
            if (routesWithoutStops > 0) {
                issues.add(new Issue("ROUTE_WITHOUT_STOPS", WARNING, routesWithoutStops + " route(s) have no stops.", "PUT " + BASE + "/transport/routes"));
            }
            if (withoutDriver > 0) {
                issues.add(new Issue("VEHICLE_WITHOUT_DRIVER", WARNING, withoutDriver + " vehicle(s) have no driver.", "PUT " + BASE + "/transport/vehicles/{id}"));
            }
            if (stopsWithoutFee > 0) {
                issues.add(new Issue("STOPS_WITHOUT_FEE", INFO, stopsWithoutFee + " stop(s) have no monthly fee.", "PUT " + BASE + "/transport/routes"));
            }
        }
        return area("TRANSPORT", "Transport", vehicles + routes > 0, issues, "vehicles", vehicles, "routes", routes, "stops", stops,
                "routesWithoutVehicle", routesWithoutVehicle, "routesWithoutStops", routesWithoutStops, "vehiclesWithExpiredPapers", expired);
    }

    private Area library() {
        long settings = count("SELECT count(*) FROM library_settings", null);
        long categories = count("SELECT count(*) FROM library_category WHERE deleted_at IS NULL AND status = 'ACTIVE'", null);
        List<Issue> issues = new ArrayList<>();
        if (categories == 0) {
            issues.add(new Issue("NO_BOOK_CATEGORIES", WARNING, "No book categories yet.", "POST " + BASE + "/library/categories"));
        }
        if (settings == 0) {
            issues.add(new Issue("LIBRARY_RULES_DEFAULT", INFO, "Lending rules are on the defaults (students 2 books for 14 days, no fine).", "PUT " + BASE + "/library/settings"));
        }
        return area("LIBRARY", "Library", settings + categories > 0, issues, "rulesSaved", settings, "categories", categories);
    }

    private Area communication() {
        CommunicationSettingsResponse s = communicationService.settings();
        List<TemplateResponse> templates = communicationService.templates();
        long customised = templates.stream().filter(TemplateResponse::customised).count();
        long off = templates.stream().filter(t -> !t.enabled()).count();
        long deadChannel = templates.stream().filter(t -> t.enabled() && !t.channelsOff().isEmpty()).count();
        long channels = (s.inAppEnabled() ? 1 : 0) + (s.emailEnabled() ? 1 : 0) + (s.smsEnabled() ? 1 : 0) + (s.whatsappEnabled() ? 1 : 0);

        List<Issue> issues = new ArrayList<>();
        if (channels == 0) {
            issues.add(new Issue("NO_CHANNELS", ERROR, "Every channel is switched off; nobody can be told anything.", "PUT " + BASE + "/communication/settings"));
        }
        if (s.smsEnabled() && s.smsSenderId() == null) {
            issues.add(new Issue("SMS_SENDER_ID_MISSING", WARNING, "SMS is on but there is no sender id.", "PUT " + BASE + "/communication/settings"));
        }
        if ((s.emailEnabled() || s.smsEnabled() || s.whatsappEnabled()) && s.senderName() == null) {
            issues.add(new Issue("NO_SENDER_NAME", WARNING, "Messages have no sender name (usually the school's name).", "PUT " + BASE + "/communication/settings"));
        }
        if (deadChannel > 0) {
            issues.add(new Issue("TEMPLATE_CHANNEL_OFF", WARNING, deadChannel + " message(s) use a channel that is switched off.", "GET " + BASE + "/communication/templates"));
        }
        if (!s.saved()) {
            issues.add(new Issue("COMMUNICATION_DEFAULT", INFO, "Channels are on the defaults (in-app only).", "PUT " + BASE + "/communication/settings"));
        }
        return area("COMMUNICATION", "Communication", s.saved() || customised > 0, issues, "channelsOn", channels, "templates", (long) templates.size(),
                "templatesCustomised", customised, "templatesOff", off);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Area unavailable(String key, String label) {
        return new Area(key, label, false, "NOT_AVAILABLE", Map.of(), List.of());
    }

    /** NOT_STARTED until something exists; READY once nothing but INFO notes is left. */
    private static Area area(String key, String label, boolean started, List<Issue> issues, Object... counts) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (int i = 0; i < counts.length; i += 2) {
            map.put((String) counts[i], (Long) counts[i + 1]);
        }
        String state = !started ? "NOT_STARTED" : issues.stream().allMatch(i -> INFO.equals(i.severity())) ? "READY" : "IN_PROGRESS";
        return new Area(key, label, true, state, map, issues);
    }

    private long count(String sql, UUID yearId) {
        var q = em.createNativeQuery(sql);
        if (yearId != null) {
            q.setParameter("y", yearId);
        }
        return ((Number) q.getSingleResult()).longValue();
    }
}
