package com.vidya.tenantdata.academic.setup.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.enums.AcademicClassStatus;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.academic.service.AcademicYearService;
import com.vidya.tenantdata.academic.setup.dto.PromotionRequest;
import com.vidya.tenantdata.academic.setup.dto.PromotionRequest.ClassMapping;
import com.vidya.tenantdata.academic.setup.dto.PromotionRequest.PromotionAction;
import com.vidya.tenantdata.academic.setup.dto.PromotionRequest.StudentOverride;
import com.vidya.tenantdata.academic.setup.dto.PromotionResult;
import com.vidya.tenantdata.academic.setup.dto.PromotionResult.ClassSummary;
import com.vidya.tenantdata.academic.setup.dto.PromotionResult.StudentLine;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.entity.StudentAcademicEnrollment;
import com.vidya.tenantdata.student.entity.StudentEnrollmentStatus;
import com.vidya.tenantdata.student.entity.StudentStatus;
import com.vidya.tenantdata.student.repository.StudentAcademicEnrollmentRepository;
import com.vidya.tenantdata.student.service.StudentSubjectEnrollmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Year-end promotion: moves every active student of one academic year into the next.
 *
 * <ul>
 *   <li>Next class = the next ACTIVE class by {@code sortOrder} (override with {@code classMappings}).
 *       Students of the last class graduate.</li>
 *   <li>Section = the new year's section of the target class with the same code (A → A), if it exists
 *       and has room; otherwise the student is enrolled without a section and listed in warnings.</li>
 *   <li>Per-student overrides: DETAIN (same class), GRADUATE, LEAVE, SKIP, or a specific class/section.</li>
 *   <li>Students already enrolled in the target year are skipped (re-running is safe).</li>
 * </ul>
 * Preview ({@code dryRun}) and execute share the same planning code, so the preview is exactly
 * what will happen. Execute runs in one transaction: all students or none.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionService {

    private final AcademicYearRepository academicYearRepository;
    private final AcademicClassRepository academicClassRepository;
    private final SectionRepository sectionRepository;
    private final StudentAcademicEnrollmentRepository enrollmentRepository;
    private final StudentSubjectEnrollmentService subjectEnrollmentService;
    private final AcademicYearService academicYearService;

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public PromotionResult preview(UUID sourceYearId, PromotionRequest request) {
        return run(sourceYearId, request, true);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public PromotionResult promote(UUID sourceYearId, PromotionRequest request) {
        return run(sourceYearId, request, false);
    }

    // -----------------------------------------------------------------------------------------

    private PromotionResult run(UUID sourceYearId, PromotionRequest request, boolean dryRun) {
        AcademicYear source = year(sourceYearId);
        AcademicYear target = year(request.targetAcademicYearId());
        if (source.getId().equals(target.getId())) {
            throw new BusinessException("Source and target academic year must differ");
        }
        if (!target.getStartDate().isAfter(source.getStartDate())) {
            throw new BusinessException("Target academic year must start after " + source.getCode());
        }
        if (target.getStatus() == AcademicYearStatus.CLOSED || target.getStatus() == AcademicYearStatus.CANCELLED) {
            throw new BusinessException("Cannot promote into a " + target.getStatus() + " academic year");
        }

        List<String> warnings = new ArrayList<>();
        List<AcademicClass> classes = academicClassRepository.findByDeletedAtIsNullOrderBySortOrderAsc().stream()
                .filter(c -> c.getStatus() == AcademicClassStatus.ACTIVE).toList();
        Map<UUID, AcademicClass> classById = classes.stream().collect(Collectors.toMap(AcademicClass::getId, Function.identity()));
        Map<UUID, Optional<AcademicClass>> nextClass = nextClasses(classes, request.classMappings(), classById, warnings);

        List<Section> targetSections = sectionRepository.findActiveByAcademicYearId(target.getId());
        Map<UUID, Section> targetSectionById = targetSections.stream().collect(Collectors.toMap(Section::getId, Function.identity()));
        Map<String, Section> targetSectionByClassAndCode = new HashMap<>();
        for (Section s : targetSections) {
            targetSectionByClassAndCode.put(key(s.getAcademicClass().getId(), s.getCode()), s);
        }
        Map<UUID, Long> seats = new HashMap<>();
        for (Object[] r : enrollmentRepository.countActiveBySection(target.getId())) {
            seats.put((UUID) r[0], ((Number) r[1]).longValue());
        }
        Set<String> takenRolls = new HashSet<>();
        for (Object[] r : enrollmentRepository.findActiveRollNumbers(target.getId())) {
            takenRolls.add(key((UUID) r[0], ((String) r[1]).trim().toLowerCase(Locale.ROOT)));
        }
        Set<UUID> alreadyInTarget = new HashSet<>(enrollmentRepository.findStudentIdsWithActiveEnrollment(target.getId()));

        Map<UUID, StudentOverride> overrides = new HashMap<>();
        if (request.overrides() != null) {
            for (StudentOverride o : request.overrides()) {
                if (overrides.put(o.studentId(), o) != null) {
                    throw new BusinessException("Student " + o.studentId() + " has more than one override");
                }
            }
        }

        LocalDate endDate = request.promotionDate() != null ? request.promotionDate() : source.getEndDate();
        boolean keepRolls = Boolean.TRUE.equals(request.keepRollNumbers());
        boolean autoSubjects = request.autoAssignSubjects() == null || request.autoAssignSubjects();

        List<StudentAcademicEnrollment> enrollments = enrollmentRepository.findActiveByAcademicYearId(source.getId());
        Set<UUID> seen = new HashSet<>();
        List<StudentLine> lines = new ArrayList<>();
        Map<PromotionAction, Integer> byAction = new EnumMap<>(PromotionAction.class);
        Map<UUID, int[]> perClass = new LinkedHashMap<>();
        int withoutSection = 0;

        for (StudentAcademicEnrollment e : enrollments) {
            Student student = e.getStudent();
            UUID studentId = student.getId();
            if (!seen.add(studentId)) {
                warnings.add("Student " + student.getStudentCode() + " has more than one active enrollment in " + source.getCode() + "; only the first was used.");
                continue;
            }
            AcademicClass from = e.getAcademicClass();
            StudentOverride o = overrides.remove(studentId);
            PromotionAction action;
            AcademicClass to = null;
            Section section = null;
            String note = null;

            if (alreadyInTarget.contains(studentId)) {
                action = PromotionAction.SKIP;
                note = "Already enrolled in " + target.getCode();
            } else if (o != null) {
                action = o.action();
            } else if (!nextClass.containsKey(from.getId())) {
                // An inactive class has no place in the order: never guess "graduate".
                action = PromotionAction.SKIP;
                note = "Class " + from.getName() + " is not active; add a class mapping or an override";
            } else {
                Optional<AcademicClass> next = nextClass.getOrDefault(from.getId(), Optional.empty());
                action = next.isPresent() ? PromotionAction.PROMOTE : PromotionAction.GRADUATE;
            }

            if (action == PromotionAction.PROMOTE || action == PromotionAction.DETAIN) {
                if (o != null && o.targetAcademicClassId() != null) {
                    to = classById.get(o.targetAcademicClassId());
                    if (to == null) {
                        throw new BusinessException("Target class " + o.targetAcademicClassId() + " for student "
                                + student.getStudentCode() + " is not an active class");
                    }
                } else if (action == PromotionAction.DETAIN) {
                    to = from;
                } else {
                    to = nextClass.getOrDefault(from.getId(), Optional.empty()).orElse(null);
                    if (to == null) {
                        throw new BusinessException("Class " + from.getName() + " has no next class; use GRADUATE or set targetAcademicClassId for "
                                + student.getStudentCode());
                    }
                }
                if (o != null && o.targetSectionId() != null) {
                    section = targetSectionById.get(o.targetSectionId());
                    if (section == null || !section.getAcademicClass().getId().equals(to.getId())) {
                        throw new BusinessException("Section " + o.targetSectionId() + " is not an active " + target.getCode()
                                + " section of " + to.getName() + " (student " + student.getStudentCode() + ")");
                    }
                } else if (e.getSection() != null) {
                    section = targetSectionByClassAndCode.get(key(to.getId(), e.getSection().getCode()));
                }
                if (section != null && section.getCapacity() != null
                        && seats.getOrDefault(section.getId(), 0L) >= section.getCapacity()) {
                    note = "Section " + section.getName() + " is full; enrolled without a section";
                    section = null;
                }
                if (section != null) {
                    seats.merge(section.getId(), 1L, Long::sum);
                } else {
                    withoutSection++;
                    if (note == null) {
                        note = "No matching section in " + target.getCode() + "; enrolled without a section";
                    }
                }
            }

            String roll = null;
            if (keepRolls && section != null && e.getRollNumber() != null && !e.getRollNumber().isBlank()) {
                String k = key(section.getId(), e.getRollNumber().trim().toLowerCase(Locale.ROOT));
                if (takenRolls.add(k)) {
                    roll = e.getRollNumber().trim();
                }
            }

            UUID newEnrollmentId = null;
            if (!dryRun) {
                newEnrollmentId = apply(e, action, to, section, roll, endDate, target, autoSubjects,
                        o != null ? o.remarks() : null);
            }

            byAction.merge(action, 1, Integer::sum);
            int[] c = perClass.computeIfAbsent(from.getId(), k -> new int[6]);
            c[action.ordinal()]++;
            if ((action == PromotionAction.PROMOTE || action == PromotionAction.DETAIN) && section == null) {
                c[5]++;
            }
            lines.add(new StudentLine(studentId, student.getStudentCode(), e.getId(), from.getName(),
                    e.getSection() != null ? e.getSection().getName() : null, e.getRollNumber(), action,
                    to != null ? to.getId() : null, to != null ? to.getName() : null,
                    section != null ? section.getId() : null, section != null ? section.getName() : null,
                    newEnrollmentId, note));
        }

        if (!overrides.isEmpty()) {
            warnings.add(overrides.size() + " override(s) refer to students with no active enrollment in " + source.getCode() + " and were ignored.");
        }
        if (withoutSection > 0) {
            warnings.add(withoutSection + " student(s) will be enrolled without a section. Create the missing sections in "
                    + target.getCode() + " first (or copy them with the year setup), or assign sections afterwards.");
        }

        List<ClassSummary> summaries = new ArrayList<>();
        for (Map.Entry<UUID, int[]> en : perClass.entrySet()) {
            AcademicClass from = classById.get(en.getKey());
            AcademicClass to = from == null ? null : nextClass.getOrDefault(from.getId(), Optional.empty()).orElse(null);
            int[] c = en.getValue();
            summaries.add(new ClassSummary(en.getKey(), from != null ? from.getName() : String.valueOf(en.getKey()),
                    to != null ? to.getId() : null, to != null ? to.getName() : "Graduates",
                    c[0] + c[1] + c[2] + c[3] + c[4], c[0], c[1], c[2], c[3], c[4], c[5]));
        }

        if (!dryRun) {
            if (Boolean.TRUE.equals(request.makeTargetCurrent())) {
                academicYearService.setCurrentAcademicYear(target.getId());
            }
            if (Boolean.TRUE.equals(request.closeSourceYear())) {
                if (source.getStatus() == AcademicYearStatus.ACTIVE) {
                    academicYearService.closeAcademicYear(source.getId());
                } else {
                    warnings.add(source.getCode() + " is " + source.getStatus() + " and was not closed.");
                }
            }
            log.info("Promotion {} -> {}: {} students {}", source.getCode(), target.getCode(), lines.size(), byAction);
        }

        return new PromotionResult(dryRun, source.getId(), target.getId(), lines.size(), byAction, withoutSection,
                summaries, lines, warnings);
    }

    /** Writes one student's outcome; returns the new enrollment id (null when none is created). */
    private UUID apply(StudentAcademicEnrollment e, PromotionAction action, AcademicClass to, Section section,
                       String roll, LocalDate endDate, AcademicYear target, boolean autoSubjects, String remarks) {
        if (action == PromotionAction.SKIP) {
            return null;
        }
        LocalDate closeOn = endDate.isBefore(e.getEnrollmentDate()) ? e.getEnrollmentDate() : endDate;
        if (e.getEnrollmentStatus() == StudentEnrollmentStatus.ADMITTED) {
            e.updateStatus(StudentEnrollmentStatus.ACTIVE);
        }
        switch (action) {
            case PROMOTE -> e.promote(closeOn);
            case DETAIN, GRADUATE -> e.complete(closeOn);
            case LEAVE -> e.withdraw(closeOn, remarks != null ? remarks : "Left at the end of the academic year", null);
            default -> { }
        }
        enrollmentRepository.save(e);

        Student student = e.getStudent();
        if (action == PromotionAction.GRADUATE || action == PromotionAction.LEAVE) {
            StudentStatus status = action == PromotionAction.GRADUATE ? StudentStatus.GRADUATED : StudentStatus.WITHDRAWN;
            try {
                student.updateStatus(status);
            } catch (RuntimeException ex) {
                log.warn("Student [{}] status {} not changed to {}: {}", student.getId(), student.getStatus(), status, ex.getMessage());
            }
            return null;
        }

        String code = CodeGenerator.enrollmentCode();
        while (enrollmentRepository.existsByEnrollmentCode(code)) {
            code = CodeGenerator.enrollmentCode();
        }
        StudentAcademicEnrollment next = new StudentAcademicEnrollment(code, student, target, to, section, roll,
                StudentEnrollmentStatus.ACTIVE, e.getAdmissionDate(), target.getStartDate(),
                remarks != null ? remarks : (action == PromotionAction.DETAIN ? "Detained in " + to.getName() : "Promoted from " + e.getAcademicClass().getName()));
        StudentAcademicEnrollment saved = enrollmentRepository.save(next);
        if (autoSubjects) {
            subjectEnrollmentService.autoEnrollCompulsorySubjects(saved);
        }
        return saved.getId();
    }

    /** Next class per class: explicit mappings first, then the next class by sort order. */
    static Map<UUID, Optional<AcademicClass>> nextClasses(List<AcademicClass> ordered, List<ClassMapping> mappings,
                                                         Map<UUID, AcademicClass> byId, List<String> warnings) {
        Map<UUID, Optional<AcademicClass>> next = new HashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            AcademicClass c = ordered.get(i);
            AcademicClass n = i + 1 < ordered.size() ? ordered.get(i + 1) : null;
            if (n != null && n.getSortOrder() == c.getSortOrder()) {
                warnings.add("Classes " + c.getName() + " and " + n.getName() + " have the same sortOrder (" + c.getSortOrder()
                        + "); set distinct sort orders or a class mapping so promotion order is certain.");
            }
            next.put(c.getId(), Optional.ofNullable(n));
        }
        if (mappings != null) {
            for (ClassMapping m : mappings) {
                if (m.toAcademicClassId() != null && !byId.containsKey(m.toAcademicClassId())) {
                    throw new BusinessException("Class mapping: " + m.toAcademicClassId() + " is not an active class");
                }
                next.put(m.fromAcademicClassId(), Optional.ofNullable(m.toAcademicClassId() == null ? null : byId.get(m.toAcademicClassId())));
            }
        }
        return next;
    }

    private AcademicYear year(UUID id) {
        return academicYearRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", id));
    }

    private static String key(UUID a, String b) {
        return a + "|" + b;
    }
}
