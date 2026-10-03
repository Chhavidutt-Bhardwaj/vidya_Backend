package com.vidya.tenantdata.timetable.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.assignment.entity.ClassSubjectAssignment;
import com.vidya.tenantdata.assignment.entity.TeacherSubjectAssignment;
import com.vidya.tenantdata.assignment.repository.ClassSubjectAssignmentRepository;
import com.vidya.tenantdata.assignment.repository.TeacherSubjectAssignmentRepository;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.subject.entity.Subject;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import com.vidya.tenantdata.timetable.dto.BulkUpsertEntriesResponse;
import com.vidya.tenantdata.timetable.dto.BulkUpsertEntryRowRequest;
import com.vidya.tenantdata.timetable.dto.BulkUpsertSectionEntriesRequest;
import com.vidya.tenantdata.timetable.dto.CloneTimetableRequest;
import com.vidya.tenantdata.timetable.dto.CloneTimetableResponse;
import com.vidya.tenantdata.timetable.dto.CreateTimetableEntryRequest;
import com.vidya.tenantdata.timetable.dto.CreateTimetableRequest;
import com.vidya.tenantdata.timetable.dto.ScheduleConflictResponse;
import com.vidya.tenantdata.timetable.dto.ScheduleValidationResponse;
import com.vidya.tenantdata.timetable.dto.SkippedEntryResponse;
import com.vidya.tenantdata.timetable.dto.DayRoomOccupancyResponse;
import com.vidya.tenantdata.timetable.dto.DayTeacherLoadResponse;
import com.vidya.tenantdata.timetable.dto.RoomOccupancyResponse;
import com.vidya.tenantdata.timetable.dto.TeacherLoadResponse;
import com.vidya.tenantdata.timetable.dto.TimetableDayGridResponse;
import com.vidya.tenantdata.timetable.dto.TimetableDetailResponse;
import com.vidya.tenantdata.timetable.dto.TimetableEntryResponse;
import com.vidya.tenantdata.timetable.dto.TimetableGridResponse;
import com.vidya.tenantdata.timetable.dto.TimetableGridSlotResponse;
import com.vidya.tenantdata.timetable.dto.TimetableResponse;
import com.vidya.tenantdata.timetable.dto.UpdateTimetableEntryRequest;
import com.vidya.tenantdata.timetable.dto.UpdateTimetableRequest;
import com.vidya.tenantdata.timetable.dto.UpdateTimetableStatusRequest;
import com.vidya.tenantdata.timetable.entity.BellSchedule;
import com.vidya.tenantdata.timetable.entity.BellSchedulePeriod;
import com.vidya.tenantdata.timetable.entity.Room;
import com.vidya.tenantdata.timetable.entity.Timetable;
import com.vidya.tenantdata.timetable.entity.TimetableEntry;
import com.vidya.tenantdata.timetable.enums.BellScheduleStatus;
import com.vidya.tenantdata.timetable.enums.ConflictSeverity;
import com.vidya.tenantdata.timetable.enums.PeriodType;
import com.vidya.tenantdata.timetable.enums.RoomStatus;
import com.vidya.tenantdata.timetable.enums.RoomType;
import com.vidya.tenantdata.timetable.enums.TimetableEntryType;
import com.vidya.tenantdata.timetable.enums.TimetableStatus;
import com.vidya.tenantdata.timetable.mapper.TimetableEntryMapper;
import com.vidya.tenantdata.timetable.mapper.TimetableMapper;
import com.vidya.tenantdata.timetable.mapper.TimetableSubstitutionMapper;
import com.vidya.tenantdata.timetable.repository.BellSchedulePeriodRepository;
import com.vidya.tenantdata.timetable.repository.BellScheduleRepository;
import com.vidya.tenantdata.timetable.repository.RoomRepository;
import com.vidya.tenantdata.timetable.repository.TimetableEntryRepository;
import com.vidya.tenantdata.timetable.repository.TimetableRepository;
import com.vidya.tenantdata.timetable.repository.TimetableSubstitutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimetableServiceImpl implements TimetableService {

    private final TimetableRepository timetableRepository;
    private final TimetableEntryRepository timetableEntryRepository;
    private final BellScheduleRepository bellScheduleRepository;
    private final BellSchedulePeriodRepository bellSchedulePeriodRepository;
    private final AcademicYearRepository academicYearRepository;
    private final AcademicClassRepository academicClassRepository;
    private final SectionRepository sectionRepository;
    private final SubjectRepository subjectRepository;
    private final StaffRepository staffRepository;
    private final RoomRepository roomRepository;
    private final TeacherSubjectAssignmentRepository teacherSubjectAssignmentRepository;
    private final ClassSubjectAssignmentRepository classSubjectAssignmentRepository;
    private final TimetableConflictValidator conflictValidator;
    private final TimetableMapper timetableMapper;
    private final TimetableEntryMapper timetableEntryMapper;
    private final TimetableSubstitutionRepository timetableSubstitutionRepository;
    private final TimetableSubstitutionMapper timetableSubstitutionMapper;

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public TimetableResponse createTimetable(CreateTimetableRequest request) {
        log.info("Creating timetable: [{}] for academic year [{}]", request.name(), request.academicYearId());

        AcademicYear academicYear = academicYearRepository.findByIdAndNotDeleted(request.academicYearId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", request.academicYearId()));

        if (academicYear.getStatus() == AcademicYearStatus.CLOSED ||
            academicYear.getStatus() == AcademicYearStatus.CANCELLED ||
            academicYear.getStatus() == AcademicYearStatus.DELETED) {
            throw new BusinessException("Cannot create timetable for a closed or cancelled academic year.");
        }

        BellSchedule bellSchedule = bellScheduleRepository.findByIdAndNotDeleted(request.bellScheduleId())
                .orElseThrow(() -> new ResourceNotFoundException("BellSchedule", request.bellScheduleId()));

        if (bellSchedule.getStatus() != BellScheduleStatus.ACTIVE) {
            throw new BusinessException("Bell schedule must be in ACTIVE status to be used in a timetable.");
        }

        if (bellSchedule.getPeriods() == null || bellSchedule.getPeriods().isEmpty()) {
            throw new BusinessException("Bell schedule must have at least one defined period to be used in a timetable.");
        }

        validateEffectiveDates(request.effectiveFrom(), request.effectiveTo(), academicYear);

        String timetableCode = CodeGenerator.timetableCode();
        while (timetableRepository.existsByTimetableCode(timetableCode)) {
            timetableCode = CodeGenerator.timetableCode();
        }

        Timetable timetable = new Timetable(
                timetableCode,
                academicYear,
                bellSchedule,
                request.name(),
                request.description(),
                request.effectiveFrom(),
                request.effectiveTo()
        );
        timetable.setCreatedBy(getCurrentUserId());
        timetable.setUpdatedBy(getCurrentUserId());

        Timetable saved = timetableRepository.save(timetable);
        log.info("Timetable created successfully: [{}]", saved.getTimetableCode());
        return timetableMapper.toResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public TimetableResponse updateTimetable(UUID id, UpdateTimetableRequest request) {
        log.info("Updating timetable: [{}]", id);

        Timetable timetable = timetableRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", id));

        if (timetable.getStatus() != TimetableStatus.DRAFT) {
            throw new BusinessException("Timetable can only be updated while in DRAFT status.");
        }

        if (!timetable.getBellSchedule().getId().equals(request.bellScheduleId())) {
            List<TimetableEntry> existingEntries = timetableEntryRepository.findAllByTimetableIdWithDetails(id);
            boolean hasActiveEntries = existingEntries.stream().anyMatch(e -> !e.isDeleted());
            if (hasActiveEntries) {
                throw new BusinessException("Cannot change bell schedule for a timetable that already contains entries.");
            }
        }

        BellSchedule bellSchedule = bellScheduleRepository.findByIdAndNotDeleted(request.bellScheduleId())
                .orElseThrow(() -> new ResourceNotFoundException("BellSchedule", request.bellScheduleId()));

        if (bellSchedule.getStatus() != BellScheduleStatus.ACTIVE) {
            throw new BusinessException("Bell schedule must be ACTIVE to be used in a timetable.");
        }

        validateEffectiveDates(request.effectiveFrom(), request.effectiveTo(), timetable.getAcademicYear());

        timetable.setName(request.name().trim());
        timetable.setDescription(request.description() != null ? request.description().trim() : null);
        timetable.setBellSchedule(bellSchedule);
        timetable.setEffectiveFrom(request.effectiveFrom());
        timetable.setEffectiveTo(request.effectiveTo());
        timetable.setUpdatedBy(getCurrentUserId());

        Timetable updated = timetableRepository.save(timetable);
        log.info("Timetable updated successfully: [{}]", updated.getTimetableCode());
        return timetableMapper.toResponse(updated);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public TimetableResponse updateTimetableStatus(UUID id, UpdateTimetableStatusRequest request) {
        log.info("Updating timetable status: [{}] to [{}] (replaceActive={})", id, request.status(), request.replaceActive());

        Timetable timetable = timetableRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", id));

        if (request.status() == TimetableStatus.DELETED) {
            throw new InvalidStateTransitionException("Timetable", timetable.getStatus().name(), "DELETED (Use soft delete endpoint)");
        }

        if (request.status() == TimetableStatus.PUBLISHED) {
            List<TimetableEntry> entries = timetableEntryRepository.findAllByTimetableIdWithDetails(id);
            boolean hasActiveEntries = entries.stream().anyMatch(e -> !e.isDeleted());
            if (!hasActiveEntries) {
                throw new BusinessException("Cannot publish timetable with no active entries.");
            }

            ScheduleValidationResponse validation = conflictValidator.validateTimetable(timetable);
            if (!validation.valid()) {
                String errorMessages = validation.conflicts().stream()
                        .filter(c -> c.severity() == ConflictSeverity.ERROR)
                        .map(ScheduleConflictResponse::message)
                        .collect(Collectors.joining("; "));
                throw new BusinessException("Cannot publish timetable with unresolved conflicts: " + errorMessages);
            }
        } else if (request.status() == TimetableStatus.ACTIVE) {
            AcademicYear ay = timetable.getAcademicYear();
            if (ay.getStatus() != AcademicYearStatus.ACTIVE) {
                throw new BusinessException("Academic year must be ACTIVE to activate timetable.");
            }

            List<Timetable> currentActive = timetableRepository.findActiveByAcademicYear(ay.getId())
                    .stream()
                    .filter(t -> !t.getId().equals(id))
                    .toList();

            if (!currentActive.isEmpty()) {
                if (Boolean.TRUE.equals(request.replaceActive())) {
                    for (Timetable other : currentActive) {
                        other.updateStatus(TimetableStatus.ARCHIVED);
                        other.setUpdatedBy(getCurrentUserId());
                        timetableRepository.saveAndFlush(other);
                    }
                } else {
                    throw new DuplicateResourceException("Timetable", "status", "An ACTIVE timetable already exists for this academic year. Set replaceActive=true to archive it.");
                }
            }
        }

        timetable.updateStatus(request.status());
        timetable.setUpdatedBy(getCurrentUserId());

        try {
            Timetable updated = timetableRepository.saveAndFlush(timetable);
            log.info("Timetable status updated to [{}] for [{}]", updated.getStatus(), updated.getTimetableCode());
            return timetableMapper.toResponse(updated);
        } catch (DataIntegrityViolationException e) {
            log.warn("Database constraint violation updating timetable status: {}", e.getMessage());
            throw new DuplicateResourceException("Timetable", "status", "Active timetable constraint violation: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public TimetableDetailResponse getTimetableById(UUID id) {
        Timetable timetable = timetableRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", id));
        return timetableMapper.toDetailResponse(timetable);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public TimetableResponse getTimetableByCode(String timetableCode) {
        Timetable timetable = timetableRepository.findByTimetableCodeAndNotDeleted(timetableCode)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", timetableCode));
        return timetableMapper.toResponse(timetable);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Page<TimetableResponse> searchTimetables(UUID academicYearId, UUID bellScheduleId, TimetableStatus status, String search, Pageable pageable) {
        String query = (search != null && !search.isBlank()) ? search.trim() : null;
        return timetableRepository.searchTimetables(academicYearId, bellScheduleId, status, query, pageable)
                .map(timetableMapper::toResponse);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteTimetable(UUID id) {
        log.info("Deleting timetable: [{}]", id);
        Timetable timetable = timetableRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", id));

        if (timetable.getStatus() != TimetableStatus.DRAFT) {
            throw new BusinessException("Only DRAFT timetables can be deleted.");
        }

        List<TimetableEntry> entries = timetableEntryRepository.findAllByTimetableIdWithDetails(id);
        for (TimetableEntry entry : entries) {
            if (!entry.isDeleted()) {
                entry.softDelete();
                entry.setUpdatedBy(getCurrentUserId());
            }
        }
        timetableEntryRepository.saveAll(entries);

        timetable.softDelete();
        timetable.setUpdatedBy(getCurrentUserId());
        timetableRepository.save(timetable);
        log.info("Timetable and its entries soft deleted: [{}]", id);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public ScheduleValidationResponse validateTimetable(UUID id) {
        return conflictValidator.validateTimetable(id);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public TimetableResponse publishTimetable(UUID id) {
        return updateTimetableStatus(id, new UpdateTimetableStatusRequest(TimetableStatus.PUBLISHED, false));
    }

    private void validateEffectiveDates(java.time.LocalDate effectiveFrom, java.time.LocalDate effectiveTo, AcademicYear academicYear) {
        if (effectiveFrom != null && academicYear.getStartDate() != null && effectiveFrom.isBefore(academicYear.getStartDate())) {
            throw new BusinessException(String.format(
                    "effective_from (%s) cannot be before academic year start date (%s).",
                    effectiveFrom, academicYear.getStartDate()
            ));
        }
        if (effectiveTo != null && academicYear.getEndDate() != null && effectiveTo.isAfter(academicYear.getEndDate())) {
            throw new BusinessException(String.format(
                    "effective_to (%s) cannot be after academic year end date (%s).",
                    effectiveTo, academicYear.getEndDate()
            ));
        }
        if (effectiveFrom != null && effectiveTo != null && effectiveFrom.isAfter(effectiveTo)) {
            throw new BusinessException(String.format(
                    "effective_from (%s) cannot be after effective_to (%s).",
                    effectiveFrom, effectiveTo
            ));
        }
    }

    private UUID getCurrentUserId() {
        return com.vidya.common.util.SecurityUtils.getCurrentUserId().orElse(null);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public TimetableEntryResponse createEntry(UUID timetableId, CreateTimetableEntryRequest request) {
        log.info("Creating timetable entry for timetable [{}] on [{}] period [{}]", timetableId, request.dayOfWeek(), request.periodId());

        Timetable timetable = timetableRepository.findByIdAndNotDeleted(timetableId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", timetableId));

        if (timetable.getStatus() != TimetableStatus.DRAFT) {
            throw new BusinessException("Timetable entries can only be created while timetable is in DRAFT status.");
        }

        PreparedEntryData data = validateAndPrepareEntry(
                timetable,
                request.periodId(),
                request.dayOfWeek(),
                request.academicClassId(),
                request.sectionId(),
                request.subjectId(),
                request.teacherAssignmentId(),
                request.teacherId(),
                request.classSubjectAssignmentId(),
                request.roomId(),
                request.entryType(),
                null
        );

        String entryCode = CodeGenerator.timetableEntryCode();
        while (timetableEntryRepository.existsByEntryCode(entryCode)) {
            entryCode = CodeGenerator.timetableEntryCode();
        }

        TimetableEntry entry = new TimetableEntry(
                entryCode,
                timetable,
                timetable.getAcademicYear(),
                data.period(),
                request.dayOfWeek(),
                data.academicClass(),
                data.section(),
                data.subject(),
                data.teacherAssignment(),
                data.teacher(),
                data.classSubjectAssignment(),
                data.room(),
                data.entryType()
        );
        entry.setCreatedBy(getCurrentUserId());
        entry.setUpdatedBy(getCurrentUserId());

        try {
            TimetableEntry saved = timetableEntryRepository.saveAndFlush(entry);
            if (saved == null) {
                saved = timetableEntryRepository.save(entry);
            }
            if (saved == null) {
                saved = entry;
            }
            log.info("Timetable entry created successfully: [{}]", saved.getEntryCode());
            return timetableEntryMapper.toResponse(saved, data.warning());
        } catch (DataIntegrityViolationException e) {
            log.warn("Constraint violation saving timetable entry: {}", e.getMessage());
            handleDataIntegrityViolation(e);
            throw e;
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public TimetableEntryResponse updateEntry(UUID timetableId, UUID entryId, UpdateTimetableEntryRequest request) {
        log.info("Updating timetable entry [{}] for timetable [{}]", entryId, timetableId);

        Timetable timetable = timetableRepository.findByIdAndNotDeleted(timetableId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", timetableId));

        if (timetable.getStatus() != TimetableStatus.DRAFT) {
            throw new BusinessException("Timetable entries can only be updated while timetable is in DRAFT status.");
        }

        TimetableEntry entry = timetableEntryRepository.findByIdAndNotDeleted(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("TimetableEntry", entryId));

        if (!entry.getTimetable().getId().equals(timetableId)) {
            throw new ResourceNotFoundException("TimetableEntry", entryId);
        }

        PreparedEntryData data = validateAndPrepareEntry(
                timetable,
                request.periodId(),
                request.dayOfWeek(),
                request.academicClassId(),
                request.sectionId(),
                request.subjectId(),
                request.teacherAssignmentId(),
                request.teacherId(),
                request.classSubjectAssignmentId(),
                request.roomId(),
                request.entryType(),
                entryId
        );

        entry.setPeriod(data.period());
        entry.setDayOfWeek(request.dayOfWeek());
        entry.setAcademicClass(data.academicClass());
        entry.setSection(data.section());
        entry.setSubject(data.subject());
        entry.setTeacherAssignment(data.teacherAssignment());
        entry.setTeacher(data.teacher());
        entry.setClassSubjectAssignment(data.classSubjectAssignment());
        entry.setRoom(data.room());
        entry.setEntryType(data.entryType());
        entry.setUpdatedBy(getCurrentUserId());

        try {
            TimetableEntry updated = timetableEntryRepository.saveAndFlush(entry);
            if (updated == null) {
                updated = timetableEntryRepository.save(entry);
            }
            if (updated == null) {
                updated = entry;
            }
            log.info("Timetable entry updated successfully: [{}]", updated.getEntryCode());
            return timetableEntryMapper.toResponse(updated, data.warning());
        } catch (DataIntegrityViolationException e) {
            log.warn("Constraint violation updating timetable entry: {}", e.getMessage());
            handleDataIntegrityViolation(e);
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public TimetableEntryResponse getEntryById(UUID timetableId, UUID entryId) {
        TimetableEntry entry = timetableEntryRepository.findByIdAndNotDeleted(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("TimetableEntry", entryId));

        if (!entry.getTimetable().getId().equals(timetableId)) {
            throw new ResourceNotFoundException("TimetableEntry", entryId);
        }

        return timetableEntryMapper.toResponse(entry);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Page<TimetableEntryResponse> searchEntries(UUID timetableId, UUID academicClassId, UUID sectionId, UUID teacherId, UUID subjectId, UUID roomId, DayOfWeek dayOfWeek, UUID periodId, Pageable pageable) {
        return timetableEntryRepository.searchEntries(timetableId, academicClassId, sectionId, teacherId, subjectId, roomId, dayOfWeek, periodId, pageable)
                .map(timetableEntryMapper::toResponse);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteEntry(UUID timetableId, UUID entryId) {
        log.info("Deleting timetable entry [{}] from timetable [{}]", entryId, timetableId);
        TimetableEntry entry = timetableEntryRepository.findByIdAndNotDeleted(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("TimetableEntry", entryId));

        if (!entry.getTimetable().getId().equals(timetableId)) {
            throw new ResourceNotFoundException("TimetableEntry", entryId);
        }

        if (entry.getTimetable().getStatus() != TimetableStatus.DRAFT) {
            throw new BusinessException("Timetable entries can only be deleted while timetable is in DRAFT status.");
        }

        entry.softDelete();
        entry.setUpdatedBy(getCurrentUserId());
        timetableEntryRepository.save(entry);
        log.info("Timetable entry soft deleted: [{}]", entryId);
    }

    private record PreparedEntryData(
            BellSchedulePeriod period,
            AcademicClass academicClass,
            Section section,
            Subject subject,
            TeacherSubjectAssignment teacherAssignment,
            Staff teacher,
            ClassSubjectAssignment classSubjectAssignment,
            Room room,
            TimetableEntryType entryType,
            String warning
    ) {}

    private PreparedEntryData validateAndPrepareEntry(
            Timetable timetable,
            UUID periodId,
            DayOfWeek dayOfWeek,
            UUID academicClassId,
            UUID sectionId,
            UUID subjectId,
            UUID teacherAssignmentId,
            UUID teacherId,
            UUID classSubjectAssignmentId,
            UUID roomId,
            TimetableEntryType requestedEntryType,
            UUID excludeEntryId
    ) {
        // 1. Period validation
        BellSchedulePeriod period = bellSchedulePeriodRepository.findByIdAndNotDeleted(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("BellSchedulePeriod", periodId));

        if (!period.getBellSchedule().getId().equals(timetable.getBellSchedule().getId())) {
            throw new BusinessException("Period does not belong to the timetable's bell schedule.");
        }

        TimetableEntryType entryType = requestedEntryType != null ? requestedEntryType : TimetableEntryType.REGULAR;
        if (entryType == TimetableEntryType.SUBSTITUTE) {
            throw new BusinessException("SUBSTITUTE entry type is reserved for substitution management and cannot be created directly.");
        }

        if (period.getPeriodType() == PeriodType.FREE) {
            throw new BusinessException("FREE periods do not accept any timetable entries.");
        } else if (period.getPeriodType() == PeriodType.TEACHING) {
            if (entryType == TimetableEntryType.BREAK || entryType == TimetableEntryType.ASSEMBLY) {
                throw new BusinessException("TEACHING periods accept only REGULAR, LAB, ACTIVITY, and SPECIAL entry types.");
            }
        } else if (period.getPeriodType() == PeriodType.BREAK || period.getPeriodType() == PeriodType.LUNCH) {
            if (entryType != TimetableEntryType.BREAK) {
                throw new BusinessException("BREAK and LUNCH periods accept only BREAK entry type.");
            }
        } else if (period.getPeriodType() == PeriodType.ASSEMBLY) {
            if (entryType != TimetableEntryType.ASSEMBLY) {
                throw new BusinessException("ASSEMBLY periods accept only ASSEMBLY entry type.");
            }
        } else if (period.getPeriodType() == PeriodType.ACTIVITY) {
            if (entryType != TimetableEntryType.ACTIVITY && entryType != TimetableEntryType.SPECIAL) {
                throw new BusinessException("ACTIVITY periods accept only ACTIVITY and SPECIAL entry types.");
            }
        } else if (period.getPeriodType() == PeriodType.OTHER) {
            if (entryType == TimetableEntryType.BREAK || entryType == TimetableEntryType.ASSEMBLY) {
                throw new BusinessException("OTHER periods accept only REGULAR, LAB, ACTIVITY, and SPECIAL entry types.");
            }
        }

        // 2. Class and Section validation
        AcademicClass academicClass = academicClassRepository.findByIdAndNotDeleted(academicClassId)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicClass", academicClassId));

        Section section = null;
        if (sectionId != null) {
            section = sectionRepository.findByIdAndNotDeleted(sectionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Section", sectionId));
            if (!section.getAcademicClass().getId().equals(academicClass.getId())) {
                throw new BusinessException("Section does not belong to the specified academic class.");
            }
            if (!section.getAcademicYear().getId().equals(timetable.getAcademicYear().getId())) {
                throw new BusinessException("Section does not belong to the timetable's academic year.");
            }
        }

        // 3. Subject and Assignments
        ClassSubjectAssignment classSubjectAssignment = null;
        Subject subject = null;
        if (classSubjectAssignmentId != null) {
            classSubjectAssignment = classSubjectAssignmentRepository.findByIdAndNotDeleted(classSubjectAssignmentId)
                    .orElseThrow(() -> new ResourceNotFoundException("ClassSubjectAssignment", classSubjectAssignmentId));
            if (!classSubjectAssignment.getAcademicClass().getId().equals(academicClass.getId())) {
                throw new BusinessException("Class subject assignment does not belong to specified academic class.");
            }
            if (!classSubjectAssignment.getAcademicYear().getId().equals(timetable.getAcademicYear().getId())) {
                throw new BusinessException("Class subject assignment does not belong to timetable's academic year.");
            }
            if (classSubjectAssignment.getStatus() != com.vidya.tenantdata.assignment.enums.ClassSubjectAssignmentStatus.ACTIVE) {
                throw new BusinessException("Class subject assignment is not ACTIVE.");
            }
            subject = classSubjectAssignment.getSubject();
        }

        if (subjectId != null) {
            Subject directSub = subjectRepository.findByIdAndNotDeleted(subjectId)
                    .orElseThrow(() -> new ResourceNotFoundException("Subject", subjectId));
            if (subject != null && !subject.getId().equals(directSub.getId())) {
                throw new BusinessException("Specified subject does not match subject in class subject assignment.");
            }
            subject = directSub;
        }

        TeacherSubjectAssignment teacherAssignment = null;
        Staff teacher = null;
        if (teacherAssignmentId != null) {
            teacherAssignment = teacherSubjectAssignmentRepository.findByIdAndNotDeleted(teacherAssignmentId)
                    .orElseThrow(() -> new ResourceNotFoundException("TeacherSubjectAssignment", teacherAssignmentId));
            if (teacherAssignment.getStatus() != com.vidya.tenantdata.assignment.enums.TeacherAssignmentStatus.ACTIVE) {
                throw new BusinessException("Teacher subject assignment is not ACTIVE.");
            }
            if (!teacherAssignment.getAcademicYear().getId().equals(timetable.getAcademicYear().getId())) {
                throw new BusinessException("Teacher assignment does not belong to timetable's academic year.");
            }
            if (!teacherAssignment.getAcademicClass().getId().equals(academicClass.getId())) {
                throw new BusinessException("Teacher assignment does not belong to specified academic class.");
            }
            if (section != null && teacherAssignment.getSection() != null && !teacherAssignment.getSection().getId().equals(section.getId())) {
                throw new BusinessException("Teacher assignment does not match specified section.");
            }
            if (subject != null && !teacherAssignment.getSubject().getId().equals(subject.getId())) {
                throw new BusinessException("Teacher assignment does not match specified subject.");
            }
            if (subject == null) {
                subject = teacherAssignment.getSubject();
            }
            teacher = teacherAssignment.getTeacher();
            if (teacher.getEmploymentStatus() != com.vidya.tenantdata.staff.enums.EmploymentStatus.ACTIVE) {
                throw new BusinessException("Teacher is not in ACTIVE employment status.");
            }
        } else if (teacherId != null) {
            teacher = staffRepository.findByIdAndNotDeleted(teacherId)
                    .orElseThrow(() -> new ResourceNotFoundException("Staff", teacherId));
            if (teacher.getEmploymentStatus() != com.vidya.tenantdata.staff.enums.EmploymentStatus.ACTIVE) {
                throw new BusinessException("Teacher is not in ACTIVE employment status.");
            }
        }

        // Entry-Type Field Rules:
        if (entryType == TimetableEntryType.REGULAR || entryType == TimetableEntryType.LAB) {
            if (subject == null || teacherAssignment == null || teacher == null) {
                throw new BusinessException("REGULAR and LAB entries require subject, teacher assignment, and teacher.");
            }
        } else if (entryType == TimetableEntryType.BREAK || entryType == TimetableEntryType.ASSEMBLY) {
            if (subject != null || teacherAssignment != null || teacher != null || classSubjectAssignment != null) {
                throw new BusinessException(entryType + " entries must not have subject, teacher assignment, or teacher.");
            }
        }

        // 4. Room validation
        Room room = null;
        if (roomId != null) {
            room = roomRepository.findByIdAndNotDeleted(roomId)
                    .orElseThrow(() -> new ResourceNotFoundException("Room", roomId));
            if (room.getStatus() != RoomStatus.ACTIVE) {
                throw new BusinessException("Assigned room must be in ACTIVE status.");
            }
            if (entryType == TimetableEntryType.LAB &&
                room.getRoomType() != RoomType.LAB &&
                room.getRoomType() != RoomType.SCIENCE_LAB &&
                room.getRoomType() != RoomType.COMPUTER_LAB) {
                throw new BusinessException("LAB entries require a lab-type room (LAB, SCIENCE_LAB, or COMPUTER_LAB).");
            }
        }
        if (entryType == TimetableEntryType.LAB && room == null) {
            throw new BusinessException("LAB entries require an assigned lab room.");
        }

        String warning = null;
        if (room != null && section != null && room.getCapacity() != null && section.getCapacity() != null && room.getCapacity() < section.getCapacity()) {
            warning = String.format("Room capacity (%d) is below section student count (%d).", room.getCapacity(), section.getCapacity());
        }

        // 5. Clash Pre-checks (returning 409 DuplicateResourceException)
        if (teacher != null) {
            List<TimetableEntry> teacherClashes = timetableEntryRepository.findTeacherClashes(timetable.getId(), teacher.getId(), dayOfWeek, periodId, excludeEntryId);
            if (!teacherClashes.isEmpty()) {
                TimetableEntry clash = teacherClashes.getFirst();
                throw new DuplicateResourceException("TimetableEntry", "teacherId",
                        String.format("Teacher clash with entry [%s]: teacher is already scheduled on %s period %s.",
                                clash.getEntryCode(), dayOfWeek, period.getName()));
            }
        }

        List<TimetableEntry> classClashes = timetableEntryRepository.findClassClashes(timetable.getId(), academicClass.getId(), section != null ? section.getId() : null, dayOfWeek, periodId, excludeEntryId);
        if (!classClashes.isEmpty()) {
            TimetableEntry clash = classClashes.getFirst();
            throw new DuplicateResourceException("TimetableEntry", "academicClassId",
                    String.format("Class clash with entry [%s]: class/section is already scheduled on %s period %s.",
                            clash.getEntryCode(), dayOfWeek, period.getName()));
        }

        if (room != null) {
            List<TimetableEntry> roomClashes = timetableEntryRepository.findRoomClashes(timetable.getId(), room.getId(), dayOfWeek, periodId, excludeEntryId);
            if (!roomClashes.isEmpty()) {
                TimetableEntry clash = roomClashes.getFirst();
                throw new DuplicateResourceException("TimetableEntry", "roomId",
                        String.format("Room clash with entry [%s]: room is already occupied on %s period %s.",
                                clash.getEntryCode(), dayOfWeek, period.getName()));
            }
        }

        return new PreparedEntryData(period, academicClass, section, subject, teacherAssignment, teacher, classSubjectAssignment, room, entryType, warning);
    }

    private void handleDataIntegrityViolation(DataIntegrityViolationException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("idx_tte_teacher_clash")) {
            throw new DuplicateResourceException("TimetableEntry", "teacherId", "Teacher clash detected: teacher is already booked for this period.");
        }
        if (msg.contains("idx_tte_class_clash")) {
            throw new DuplicateResourceException("TimetableEntry", "academicClassId", "Class clash detected: class/section is already booked for this period.");
        }
        if (msg.contains("idx_tte_room_clash")) {
            throw new DuplicateResourceException("TimetableEntry", "roomId", "Room clash detected: room is already booked for this period.");
        }
        throw new DuplicateResourceException("TimetableEntry", "clash", "Timetable entry constraint violation: " + msg);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public TimetableGridResponse getClassTimetableGrid(UUID timetableId, UUID academicClassId, UUID sectionId) {
        return getClassTimetableGrid(timetableId, academicClassId, sectionId, null);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public TimetableGridResponse getClassTimetableGrid(UUID timetableId, UUID academicClassId, UUID sectionId, List<DayOfWeek> days) {
        Timetable timetable = timetableRepository.findByIdAndNotDeleted(timetableId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", timetableId));

        AcademicClass academicClass = academicClassRepository.findByIdAndNotDeleted(academicClassId)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicClass", academicClassId));

        String sectionName = "";
        if (sectionId != null) {
            Section section = sectionRepository.findByIdAndNotDeleted(sectionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Section", sectionId));
            sectionName = " - " + section.getName();
        }

        List<TimetableEntry> entries = timetableEntryRepository.findByTimetableAndClassAndSection(timetableId, academicClassId, sectionId);
        List<BellSchedulePeriod> periods = bellSchedulePeriodRepository.findByBellScheduleIdAndNotDeletedOrderBySequence(timetable.getBellSchedule().getId());

        List<DayOfWeek> targetDays = resolveDays(days);
        return buildGridResponse(
                timetable,
                "CLASS",
                academicClassId,
                academicClass.getName() + sectionName,
                periods,
                entries,
                targetDays
        );
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public TimetableGridResponse getSectionTimetableGrid(UUID timetableId, UUID sectionId, List<DayOfWeek> days) {
        return getSectionTimetableGrid(timetableId, sectionId, days, null);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public TimetableGridResponse getSectionTimetableGrid(UUID timetableId, UUID sectionId, List<DayOfWeek> days, java.time.LocalDate weekOf) {
        Timetable timetable = timetableRepository.findByIdAndNotDeleted(timetableId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", timetableId));

        Section section = sectionRepository.findByIdAndNotDeleted(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Section", sectionId));

        List<TimetableEntry> entries = timetableEntryRepository.findByTimetableAndClassAndSection(timetableId, section.getAcademicClass().getId(), sectionId);
        List<BellSchedulePeriod> periods = bellSchedulePeriodRepository.findByBellScheduleIdAndNotDeletedOrderBySequence(timetable.getBellSchedule().getId());

        List<DayOfWeek> targetDays = resolveDays(days);

        if (weekOf == null) {
            return buildGridResponse(
                    timetable,
                    "SECTION",
                    sectionId,
                    section.getAcademicClass().getName() + " - " + section.getName(),
                    periods,
                    entries,
                    targetDays
            );
        }

        validateDateWithinTimetableEffectiveDates(timetable, weekOf);

        java.time.LocalDate monday = weekOf.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        java.time.LocalDate sunday = weekOf.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        List<com.vidya.tenantdata.timetable.entity.TimetableSubstitution> substitutions =
                timetableSubstitutionRepository.findActiveForSectionInRange(timetableId, sectionId, monday, sunday);

        Map<String, com.vidya.tenantdata.timetable.entity.TimetableSubstitution> subMap = new java.util.HashMap<>();
        for (com.vidya.tenantdata.timetable.entity.TimetableSubstitution sub : substitutions) {
            subMap.put(sub.getSubstitutionDate() + ":" + sub.getPeriod().getId(), sub);
        }

        Map<String, TimetableEntry> entryMap = entries.stream()
                .collect(Collectors.toMap(
                        e -> e.getDayOfWeek() + ":" + e.getPeriod().getId(),
                        e -> e,
                        (existing, replacement) -> existing
                ));

        List<com.vidya.tenantdata.timetable.dto.TimetableDayGridResponse> dayGrids = new ArrayList<>();
        for (DayOfWeek day : targetDays) {
            java.time.LocalDate dateForDay = monday.plusDays(day.getValue() - 1);
            List<com.vidya.tenantdata.timetable.dto.TimetableGridSlotResponse> slots = new ArrayList<>();
            for (BellSchedulePeriod period : periods) {
                TimetableEntry entry = entryMap.get(day + ":" + period.getId());
                com.vidya.tenantdata.timetable.entity.TimetableSubstitution sub = subMap.get(dateForDay + ":" + period.getId());
                com.vidya.tenantdata.timetable.dto.TimetableGridSlotResponse baseSlot = timetableEntryMapper.toGridSlot(period, entry);
                if (sub != null) {
                    slots.add(new com.vidya.tenantdata.timetable.dto.TimetableGridSlotResponse(
                            baseSlot.periodId(),
                            baseSlot.periodCode(),
                            baseSlot.periodName(),
                            baseSlot.periodSequence(),
                            baseSlot.startTime(),
                            baseSlot.endTime(),
                            baseSlot.periodType(),
                            baseSlot.entryId(),
                            baseSlot.entryCode(),
                            baseSlot.subjectId(),
                            baseSlot.subjectCode(),
                            baseSlot.subjectName(),
                            baseSlot.teacherId(),
                            baseSlot.teacherName(),
                            baseSlot.roomId(),
                            baseSlot.roomCode(),
                            baseSlot.roomName(),
                            baseSlot.academicClassId(),
                            baseSlot.className(),
                            baseSlot.sectionId(),
                            baseSlot.sectionName(),
                            baseSlot.entryType(),
                            timetableSubstitutionMapper.toGridSubstitution(sub)
                    ));
                } else {
                    slots.add(baseSlot);
                }
            }
            dayGrids.add(new com.vidya.tenantdata.timetable.dto.TimetableDayGridResponse(day, slots));
        }

        return new TimetableGridResponse(
                timetable.getId(),
                timetable.getTimetableCode(),
                timetable.getName(),
                "SECTION",
                sectionId,
                section.getAcademicClass().getName() + " - " + section.getName(),
                dayGrids
        );
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public TimetableGridResponse getTeacherTimetableGrid(UUID timetableId, UUID teacherId) {
        return getTeacherTimetableGrid(timetableId, teacherId, null, null);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public TimetableGridResponse getTeacherTimetableGrid(UUID timetableId, UUID teacherId, List<DayOfWeek> days) {
        return getTeacherTimetableGrid(timetableId, teacherId, days, null);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public TimetableGridResponse getTeacherTimetableGrid(UUID timetableId, UUID teacherId, List<DayOfWeek> days, java.time.LocalDate weekOf) {
        Timetable timetable = timetableRepository.findByIdAndNotDeleted(timetableId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", timetableId));

        Staff teacher = staffRepository.findByIdAndNotDeleted(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff", teacherId));

        List<TimetableEntry> entries = timetableEntryRepository.findByTimetableAndTeacher(timetableId, teacherId);
        List<BellSchedulePeriod> periods = bellSchedulePeriodRepository.findByBellScheduleIdAndNotDeletedOrderBySequence(timetable.getBellSchedule().getId());

        String teacherFullName = getTeacherName(teacher);
        List<DayOfWeek> targetDays = resolveDays(days);

        if (weekOf == null) {
            return buildGridResponse(
                    timetable,
                    "TEACHER",
                    teacherId,
                    teacherFullName,
                    periods,
                    entries,
                    targetDays
            );
        }

        validateDateWithinTimetableEffectiveDates(timetable, weekOf);

        java.time.LocalDate monday = weekOf.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        java.time.LocalDate sunday = weekOf.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        List<com.vidya.tenantdata.timetable.entity.TimetableSubstitution> substitutions =
                timetableSubstitutionRepository.findActiveForTeacherInRange(timetableId, teacherId, monday, sunday);

        Map<String, com.vidya.tenantdata.timetable.entity.TimetableSubstitution> subMap = new java.util.HashMap<>();
        for (com.vidya.tenantdata.timetable.entity.TimetableSubstitution sub : substitutions) {
            subMap.put(sub.getSubstitutionDate() + ":" + sub.getPeriod().getId(), sub);
        }

        Map<String, TimetableEntry> entryMap = entries.stream()
                .collect(Collectors.toMap(
                        e -> e.getDayOfWeek() + ":" + e.getPeriod().getId(),
                        e -> e,
                        (existing, replacement) -> existing
                ));

        List<com.vidya.tenantdata.timetable.dto.TimetableDayGridResponse> dayGrids = new ArrayList<>();
        for (DayOfWeek day : targetDays) {
            java.time.LocalDate dateForDay = monday.plusDays(day.getValue() - 1);
            List<com.vidya.tenantdata.timetable.dto.TimetableGridSlotResponse> slots = new ArrayList<>();
            for (BellSchedulePeriod period : periods) {
                TimetableEntry entry = entryMap.get(day + ":" + period.getId());
                com.vidya.tenantdata.timetable.entity.TimetableSubstitution sub = subMap.get(dateForDay + ":" + period.getId());
                com.vidya.tenantdata.timetable.dto.TimetableGridSlotResponse baseSlot = timetableEntryMapper.toGridSlot(period, entry);
                if (sub != null) {
                    slots.add(new com.vidya.tenantdata.timetable.dto.TimetableGridSlotResponse(
                            baseSlot.periodId(),
                            baseSlot.periodCode(),
                            baseSlot.periodName(),
                            baseSlot.periodSequence(),
                            baseSlot.startTime(),
                            baseSlot.endTime(),
                            baseSlot.periodType(),
                            baseSlot.entryId(),
                            baseSlot.entryCode(),
                            baseSlot.subjectId(),
                            baseSlot.subjectCode(),
                            baseSlot.subjectName(),
                            baseSlot.teacherId(),
                            baseSlot.teacherName(),
                            baseSlot.roomId(),
                            baseSlot.roomCode(),
                            baseSlot.roomName(),
                            baseSlot.academicClassId(),
                            baseSlot.className(),
                            baseSlot.sectionId(),
                            baseSlot.sectionName(),
                            baseSlot.entryType(),
                            timetableSubstitutionMapper.toGridSubstitution(sub)
                    ));
                } else {
                    slots.add(baseSlot);
                }
            }
            dayGrids.add(new com.vidya.tenantdata.timetable.dto.TimetableDayGridResponse(day, slots));
        }

        return new TimetableGridResponse(
                timetable.getId(),
                timetable.getTimetableCode(),
                timetable.getName(),
                "TEACHER",
                teacherId,
                teacherFullName,
                dayGrids
        );
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public TimetableGridResponse getRoomTimetableGrid(UUID timetableId, UUID roomId) {
        return getRoomTimetableGrid(timetableId, roomId, null);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public TimetableGridResponse getRoomTimetableGrid(UUID timetableId, UUID roomId, List<DayOfWeek> days) {
        Timetable timetable = timetableRepository.findByIdAndNotDeleted(timetableId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", timetableId));

        Room room = roomRepository.findByIdAndNotDeleted(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room", roomId));

        List<TimetableEntry> entries = timetableEntryRepository.findByTimetableAndRoom(timetableId, roomId);
        List<BellSchedulePeriod> periods = bellSchedulePeriodRepository.findByBellScheduleIdAndNotDeletedOrderBySequence(timetable.getBellSchedule().getId());

        List<DayOfWeek> targetDays = resolveDays(days);
        return buildGridResponse(
                timetable,
                "ROOM",
                roomId,
                room.getName(),
                periods,
                entries,
                targetDays
        );
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public RoomOccupancyResponse getRoomOccupancy(UUID timetableId, UUID roomId, List<DayOfWeek> days) {
        Timetable timetable = timetableRepository.findByIdAndNotDeleted(timetableId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", timetableId));

        Room room = roomRepository.findByIdAndNotDeleted(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room", roomId));

        List<TimetableEntry> entries = timetableEntryRepository.findByTimetableAndRoom(timetableId, roomId);
        List<BellSchedulePeriod> periods = bellSchedulePeriodRepository.findByBellScheduleIdAndNotDeletedOrderBySequence(timetable.getBellSchedule().getId());

        List<DayOfWeek> targetDays = resolveDays(days);
        List<BellSchedulePeriod> teachingPeriods = periods.stream()
                .filter(p -> p.getPeriodType() == PeriodType.TEACHING)
                .toList();

        int availablePerDay = teachingPeriods.size();
        int totalAvailableSlots = availablePerDay * targetDays.size();
        int totalOccupiedSlots = 0;

        Map<String, TimetableEntry> entryMap = entries.stream()
                .collect(Collectors.toMap(
                        e -> e.getDayOfWeek() + ":" + e.getPeriod().getId(),
                        e -> e,
                        (existing, replacement) -> existing
                ));

        List<com.vidya.tenantdata.timetable.dto.DayRoomOccupancyResponse> dailyOccupancy = new ArrayList<>();
        for (DayOfWeek day : targetDays) {
            List<TimetableGridSlotResponse> daySlots = new ArrayList<>();
            int dayOccupiedCount = 0;
            for (BellSchedulePeriod period : periods) {
                TimetableEntry entry = entryMap.get(day + ":" + period.getId());
                if (entry != null) {
                    daySlots.add(timetableEntryMapper.toGridSlot(period, entry));
                    if (period.getPeriodType() == PeriodType.TEACHING) {
                        dayOccupiedCount++;
                    }
                }
            }
            totalOccupiedSlots += dayOccupiedCount;
            double dayUtil = availablePerDay > 0 ? ((double) dayOccupiedCount / availablePerDay) * 100.0 : 0.0;
            double dayUtilRounded = Math.round(dayUtil * 100.0) / 100.0;
            dailyOccupancy.add(new com.vidya.tenantdata.timetable.dto.DayRoomOccupancyResponse(
                    day,
                    dayOccupiedCount,
                    availablePerDay,
                    dayUtilRounded,
                    daySlots
            ));
        }

        double overallUtil = totalAvailableSlots > 0 ? ((double) totalOccupiedSlots / totalAvailableSlots) * 100.0 : 0.0;
        double overallUtilRounded = Math.round(overallUtil * 100.0) / 100.0;

        return new RoomOccupancyResponse(
                timetable.getId(),
                room.getId(),
                room.getRoomCode(),
                room.getName(),
                totalAvailableSlots,
                totalOccupiedSlots,
                overallUtilRounded,
                dailyOccupancy
        );
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public TeacherLoadResponse getTeacherLoad(UUID timetableId, UUID teacherId, List<DayOfWeek> days) {
        Timetable timetable = timetableRepository.findByIdAndNotDeleted(timetableId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", timetableId));

        Staff teacher = staffRepository.findByIdAndNotDeleted(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff", teacherId));

        List<TimetableEntry> entries = timetableEntryRepository.findByTimetableAndTeacher(timetableId, teacherId);
        List<BellSchedulePeriod> periods = bellSchedulePeriodRepository.findByBellScheduleIdAndNotDeletedOrderBySequence(timetable.getBellSchedule().getId());

        List<DayOfWeek> targetDays = resolveDays(days);
        List<BellSchedulePeriod> teachingPeriods = periods.stream()
                .filter(p -> p.getPeriodType() == PeriodType.TEACHING)
                .toList();

        int availablePerDay = teachingPeriods.size();
        int totalAvailableSlots = availablePerDay * targetDays.size();
        int totalWeeklyTeaching = 0;

        Map<String, TimetableEntry> entryMap = entries.stream()
                .collect(Collectors.toMap(
                        e -> e.getDayOfWeek() + ":" + e.getPeriod().getId(),
                        e -> e,
                        (existing, replacement) -> existing
                ));

        String teacherFullName = teacher.getStaffCode();
        if (teacher.getTenantUser() != null && teacher.getTenantUser().getProfile() != null) {
            teacherFullName = teacher.getTenantUser().getProfile().getFullName();
        }

        List<com.vidya.tenantdata.timetable.dto.DayTeacherLoadResponse> dailyLoad = new ArrayList<>();
        for (DayOfWeek day : targetDays) {
            List<TimetableGridSlotResponse> assignedSlots = new ArrayList<>();
            int dayTeachingCount = 0;
            for (BellSchedulePeriod period : periods) {
                TimetableEntry entry = entryMap.get(day + ":" + period.getId());
                if (entry != null) {
                    assignedSlots.add(timetableEntryMapper.toGridSlot(period, entry));
                    if (period.getPeriodType() == PeriodType.TEACHING) {
                        dayTeachingCount++;
                    }
                }
            }
            totalWeeklyTeaching += dayTeachingCount;
            int freeTeachingSlots = Math.max(0, availablePerDay - dayTeachingCount);
            dailyLoad.add(new com.vidya.tenantdata.timetable.dto.DayTeacherLoadResponse(
                    day,
                    dayTeachingCount,
                    freeTeachingSlots,
                    availablePerDay,
                    assignedSlots
            ));
        }

        return new TeacherLoadResponse(
                timetable.getId(),
                teacher.getId(),
                teacherFullName,
                teacher.getStaffCode(),
                totalWeeklyTeaching,
                totalAvailableSlots,
                dailyLoad
        );
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public TimetableGridResponse getActiveSectionTimetable(UUID academicYearId, UUID sectionId, List<DayOfWeek> days) {
        academicYearRepository.findByIdAndNotDeleted(academicYearId)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", academicYearId));

        Section section = sectionRepository.findByIdAndNotDeleted(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Section", sectionId));

        List<Timetable> activeTimetables = timetableRepository.findActiveByAcademicYear(academicYearId);
        if (activeTimetables.isEmpty()) {
            throw new ResourceNotFoundException("Active Timetable for AcademicYear", academicYearId);
        }

        Timetable activeTimetable = activeTimetables.getFirst();
        return getSectionTimetableGrid(activeTimetable.getId(), sectionId, days);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public com.vidya.tenantdata.timetable.dto.SectionDayScheduleResponse getSectionDaySchedule(UUID timetableId, UUID sectionId, java.time.LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Date parameter is required.");
        }

        Timetable timetable = timetableRepository.findByIdAndNotDeleted(timetableId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", timetableId));

        Section section = sectionRepository.findByIdAndNotDeleted(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Section", sectionId));

        validateDateWithinTimetableEffectiveDates(timetable, date);

        DayOfWeek dayOfWeek = date.getDayOfWeek();
        List<TimetableEntry> entries = timetableEntryRepository.findByTimetableAndClassAndSection(timetableId, section.getAcademicClass().getId(), sectionId);
        List<BellSchedulePeriod> periods = bellSchedulePeriodRepository.findByBellScheduleIdAndNotDeletedOrderBySequence(timetable.getBellSchedule().getId());
        List<com.vidya.tenantdata.timetable.entity.TimetableSubstitution> substitutions =
                timetableSubstitutionRepository.findActiveForSectionOnDate(timetableId, sectionId, date);

        Map<UUID, TimetableEntry> entryMap = entries.stream()
                .filter(e -> e.getDayOfWeek() == dayOfWeek)
                .collect(Collectors.toMap(e -> e.getPeriod().getId(), e -> e, (existing, replacement) -> existing));

        Map<UUID, com.vidya.tenantdata.timetable.entity.TimetableSubstitution> subMap = substitutions.stream()
                .collect(Collectors.toMap(s -> s.getPeriod().getId(), s -> s, (existing, replacement) -> existing));

        List<com.vidya.tenantdata.timetable.dto.SectionDaySlotResponse> slots = new ArrayList<>();
        for (BellSchedulePeriod period : periods) {
            TimetableEntry entry = entryMap.get(period.getId());
            com.vidya.tenantdata.timetable.entity.TimetableSubstitution sub = subMap.get(period.getId());

            if (entry == null && sub == null) {
                slots.add(new com.vidya.tenantdata.timetable.dto.SectionDaySlotResponse(
                        period.getId(),
                        period.getPeriodCode(),
                        period.getName(),
                        period.getPeriodSequence(),
                        period.getStartTime(),
                        period.getEndTime(),
                        period.getPeriodType(),
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, false, null
                ));
            } else {
                UUID entryId = entry != null ? entry.getId() : null;
                String entryCode = entry != null ? entry.getEntryCode() : null;
                UUID subjectId = entry != null && entry.getSubject() != null ? entry.getSubject().getId() : (sub != null && sub.getOriginalSubject() != null ? sub.getOriginalSubject().getId() : null);
                String subjectCode = entry != null && entry.getSubject() != null ? entry.getSubject().getSubjectCode() : (sub != null && sub.getOriginalSubject() != null ? sub.getOriginalSubject().getSubjectCode() : null);
                String subjectName = entry != null && entry.getSubject() != null ? entry.getSubject().getName() : (sub != null && sub.getOriginalSubject() != null ? sub.getOriginalSubject().getName() : null);

                UUID originalTeacherId = entry != null && entry.getTeacher() != null ? entry.getTeacher().getId() : (sub != null && sub.getOriginalTeacher() != null ? sub.getOriginalTeacher().getId() : null);
                String originalTeacherName = entry != null ? getTeacherName(entry.getTeacher()) : (sub != null && sub.getOriginalTeacher() != null ? getTeacherName(sub.getOriginalTeacher()) : null);

                UUID effectiveTeacherId = originalTeacherId;
                String effectiveTeacherName = originalTeacherName;

                UUID originalRoomId = entry != null && entry.getRoom() != null ? entry.getRoom().getId() : null;
                String originalRoomCode = entry != null && entry.getRoom() != null ? entry.getRoom().getRoomCode() : null;
                String originalRoomName = entry != null && entry.getRoom() != null ? entry.getRoom().getName() : null;

                UUID effectiveRoomId = originalRoomId;
                String effectiveRoomCode = originalRoomCode;
                String effectiveRoomName = originalRoomName;

                String status = "REGULAR";
                boolean isCancelled = false;

                if (sub != null) {
                    if (sub.getSubstitutionType() == com.vidya.tenantdata.timetable.enums.SubstitutionType.TEACHER_SUBSTITUTE) {
                        status = "TEACHER_SUBSTITUTE";
                        if (sub.getSubstituteTeacher() != null) {
                            effectiveTeacherId = sub.getSubstituteTeacher().getId();
                            effectiveTeacherName = getTeacherName(sub.getSubstituteTeacher());
                        }
                    } else if (sub.getSubstitutionType() == com.vidya.tenantdata.timetable.enums.SubstitutionType.ROOM_CHANGE) {
                        status = "ROOM_CHANGE";
                        if (sub.getSubstituteRoom() != null) {
                            effectiveRoomId = sub.getSubstituteRoom().getId();
                            effectiveRoomCode = sub.getSubstituteRoom().getRoomCode();
                            effectiveRoomName = sub.getSubstituteRoom().getName();
                        }
                    } else if (sub.getSubstitutionType() == com.vidya.tenantdata.timetable.enums.SubstitutionType.CLASS_CANCELLED) {
                        status = "CLASS_CANCELLED";
                        isCancelled = true;
                    }
                }

                slots.add(new com.vidya.tenantdata.timetable.dto.SectionDaySlotResponse(
                        period.getId(),
                        period.getPeriodCode(),
                        period.getName(),
                        period.getPeriodSequence(),
                        period.getStartTime(),
                        period.getEndTime(),
                        period.getPeriodType(),
                        entryId,
                        entryCode,
                        subjectId,
                        subjectCode,
                        subjectName,
                        effectiveTeacherId,
                        effectiveTeacherName,
                        originalTeacherId,
                        originalTeacherName,
                        effectiveRoomId,
                        effectiveRoomCode,
                        effectiveRoomName,
                        originalRoomId,
                        originalRoomName,
                        status,
                        isCancelled,
                        sub != null ? timetableSubstitutionMapper.toGridSubstitution(sub) : null
                ));
            }
        }

        return new com.vidya.tenantdata.timetable.dto.SectionDayScheduleResponse(
                timetable.getId(),
                timetable.getTimetableCode(),
                timetable.getName(),
                section.getAcademicClass().getId(),
                section.getAcademicClass().getName(),
                section.getId(),
                section.getName(),
                date,
                dayOfWeek,
                slots
        );
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public com.vidya.tenantdata.timetable.dto.TeacherDayScheduleResponse getTeacherDaySchedule(UUID timetableId, UUID teacherId, java.time.LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Date parameter is required.");
        }

        Timetable timetable = timetableRepository.findByIdAndNotDeleted(timetableId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", timetableId));

        Staff teacher = staffRepository.findByIdAndNotDeleted(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff", teacherId));

        validateDateWithinTimetableEffectiveDates(timetable, date);

        DayOfWeek dayOfWeek = date.getDayOfWeek();
        List<TimetableEntry> entries = timetableEntryRepository.findByTimetableAndTeacher(timetableId, teacherId);
        List<BellSchedulePeriod> periods = bellSchedulePeriodRepository.findByBellScheduleIdAndNotDeletedOrderBySequence(timetable.getBellSchedule().getId());
        List<com.vidya.tenantdata.timetable.entity.TimetableSubstitution> substitutions =
                timetableSubstitutionRepository.findActiveForTeacherOnDate(timetableId, teacherId, date);

        Map<UUID, TimetableEntry> regularEntryMap = entries.stream()
                .filter(e -> e.getDayOfWeek() == dayOfWeek)
                .collect(Collectors.toMap(e -> e.getPeriod().getId(), e -> e, (existing, replacement) -> existing));

        Map<UUID, com.vidya.tenantdata.timetable.entity.TimetableSubstitution> originalSubMap = new java.util.HashMap<>();
        Map<UUID, com.vidya.tenantdata.timetable.entity.TimetableSubstitution> substituteSubMap = new java.util.HashMap<>();
        for (com.vidya.tenantdata.timetable.entity.TimetableSubstitution sub : substitutions) {
            if (sub.getOriginalTeacher() != null && sub.getOriginalTeacher().getId().equals(teacherId)) {
                originalSubMap.put(sub.getPeriod().getId(), sub);
            }
            if (sub.getSubstituteTeacher() != null && sub.getSubstituteTeacher().getId().equals(teacherId)) {
                substituteSubMap.put(sub.getPeriod().getId(), sub);
            }
        }

        String teacherFullName = getTeacherName(teacher);

        List<com.vidya.tenantdata.timetable.dto.TeacherDaySlotResponse> slots = new ArrayList<>();
        for (BellSchedulePeriod period : periods) {
            TimetableEntry entry = regularEntryMap.get(period.getId());
            com.vidya.tenantdata.timetable.entity.TimetableSubstitution subOrig = originalSubMap.get(period.getId());
            com.vidya.tenantdata.timetable.entity.TimetableSubstitution subSub = substituteSubMap.get(period.getId());

            if (entry != null) {
                String slotType = "REGULAR";
                if (subOrig != null) {
                    if (subOrig.getSubstitutionType() == com.vidya.tenantdata.timetable.enums.SubstitutionType.CLASS_CANCELLED) {
                        slotType = "CANCELLED";
                    } else if (subOrig.getSubstitutionType() == com.vidya.tenantdata.timetable.enums.SubstitutionType.TEACHER_SUBSTITUTE) {
                        slotType = "COVERED";
                    }
                }
                slots.add(new com.vidya.tenantdata.timetable.dto.TeacherDaySlotResponse(
                        period.getId(),
                        period.getPeriodCode(),
                        period.getName(),
                        period.getPeriodSequence(),
                        period.getStartTime(),
                        period.getEndTime(),
                        period.getPeriodType(),
                        entry.getId(),
                        entry.getEntryCode(),
                        entry.getAcademicClass() != null ? entry.getAcademicClass().getId() : null,
                        entry.getAcademicClass() != null ? entry.getAcademicClass().getName() : null,
                        entry.getSection() != null ? entry.getSection().getId() : null,
                        entry.getSection() != null ? entry.getSection().getName() : null,
                        entry.getSubject() != null ? entry.getSubject().getId() : null,
                        entry.getSubject() != null ? entry.getSubject().getSubjectCode() : null,
                        entry.getSubject() != null ? entry.getSubject().getName() : null,
                        entry.getRoom() != null ? entry.getRoom().getId() : null,
                        entry.getRoom() != null ? entry.getRoom().getRoomCode() : null,
                        entry.getRoom() != null ? entry.getRoom().getName() : null,
                        slotType,
                        subOrig != null ? timetableSubstitutionMapper.toGridSubstitution(subOrig) : null
                ));
            } else if (subSub != null) {
                slots.add(new com.vidya.tenantdata.timetable.dto.TeacherDaySlotResponse(
                        period.getId(),
                        period.getPeriodCode(),
                        period.getName(),
                        period.getPeriodSequence(),
                        period.getStartTime(),
                        period.getEndTime(),
                        period.getPeriodType(),
                        null,
                        null,
                        subSub.getAcademicClass() != null ? subSub.getAcademicClass().getId() : null,
                        subSub.getAcademicClass() != null ? subSub.getAcademicClass().getName() : null,
                        subSub.getSection() != null ? subSub.getSection().getId() : null,
                        subSub.getSection() != null ? subSub.getSection().getName() : null,
                        subSub.getOriginalSubject() != null ? subSub.getOriginalSubject().getId() : null,
                        subSub.getOriginalSubject() != null ? subSub.getOriginalSubject().getSubjectCode() : null,
                        subSub.getOriginalSubject() != null ? subSub.getOriginalSubject().getName() : null,
                        subSub.getSubstituteRoom() != null ? subSub.getSubstituteRoom().getId() : null,
                        subSub.getSubstituteRoom() != null ? subSub.getSubstituteRoom().getRoomCode() : null,
                        subSub.getSubstituteRoom() != null ? subSub.getSubstituteRoom().getName() : null,
                        "SUBSTITUTING",
                        timetableSubstitutionMapper.toGridSubstitution(subSub)
                ));
            } else {
                slots.add(new com.vidya.tenantdata.timetable.dto.TeacherDaySlotResponse(
                        period.getId(),
                        period.getPeriodCode(),
                        period.getName(),
                        period.getPeriodSequence(),
                        period.getStartTime(),
                        period.getEndTime(),
                        period.getPeriodType(),
                        null, null, null, null, null, null, null, null, null, null, null, null,
                        "FREE",
                        null
                ));
            }
        }

        return new com.vidya.tenantdata.timetable.dto.TeacherDayScheduleResponse(
                timetable.getId(),
                timetable.getTimetableCode(),
                timetable.getName(),
                teacher.getId(),
                teacher.getStaffCode(),
                teacherFullName,
                date,
                dayOfWeek,
                slots
        );
    }

    private void validateDateWithinTimetableEffectiveDates(Timetable timetable, java.time.LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null.");
        }
        if (timetable.getEffectiveFrom() != null && date.isBefore(timetable.getEffectiveFrom())) {
            throw new IllegalArgumentException(String.format("Date [%s] is before timetable effective start date [%s].", date, timetable.getEffectiveFrom()));
        }
        if (timetable.getEffectiveTo() != null && date.isAfter(timetable.getEffectiveTo())) {
            throw new IllegalArgumentException(String.format("Date [%s] is after timetable effective end date [%s].", date, timetable.getEffectiveTo()));
        }
    }

    private List<DayOfWeek> resolveDays(List<DayOfWeek> days) {
        if (days != null && !days.isEmpty()) {
            return days;
        }
        return List.of(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY
        );
    }

    private TimetableGridResponse buildGridResponse(
            Timetable timetable,
            String filterType,
            UUID filterId,
            String filterName,
            List<BellSchedulePeriod> periods,
            List<TimetableEntry> entries,
            List<DayOfWeek> targetDays
    ) {
        Map<String, TimetableEntry> entryMap = entries.stream()
                .collect(Collectors.toMap(
                        e -> e.getDayOfWeek() + ":" + e.getPeriod().getId(),
                        e -> e,
                        (existing, replacement) -> existing
                ));

        List<TimetableDayGridResponse> dayGrids = new ArrayList<>();
        for (DayOfWeek day : targetDays) {
            List<TimetableGridSlotResponse> slots = new ArrayList<>();
            for (BellSchedulePeriod period : periods) {
                TimetableEntry entry = entryMap.get(day + ":" + period.getId());
                slots.add(timetableEntryMapper.toGridSlot(period, entry));
            }
            dayGrids.add(new TimetableDayGridResponse(day, slots));
        }

        return new TimetableGridResponse(
                timetable.getId(),
                timetable.getTimetableCode(),
                timetable.getName(),
                filterType,
                filterId,
                filterName,
                dayGrids
        );
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public BulkUpsertEntriesResponse bulkUpsertSectionEntries(UUID timetableId, UUID sectionId, BulkUpsertSectionEntriesRequest request) {
        log.info("Bulk upserting entries for timetable [{}] and section [{}]", timetableId, sectionId);

        Timetable timetable = timetableRepository.findByIdAndNotDeleted(timetableId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", timetableId));

        if (timetable.getStatus() != TimetableStatus.DRAFT) {
            throw new BusinessException("Timetable must be in DRAFT status to perform bulk entry operations.");
        }

        Section section = sectionRepository.findByIdAndNotDeleted(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Section", sectionId));
        AcademicClass academicClass = section.getAcademicClass();

        if (!section.getAcademicYear().getId().equals(timetable.getAcademicYear().getId())) {
            throw new BusinessException("Section does not belong to the timetable's academic year.");
        }

        List<BulkUpsertEntryRowRequest> rows = request != null && request.entries() != null ? request.entries() : java.util.Collections.emptyList();
        if (rows.size() > 300) {
            throw new BusinessException("Bulk upsert payload exceeds maximum limit of 300 entries.");
        }

        boolean replaceAll = request != null && Boolean.TRUE.equals(request.replaceAll());

        // Batch load reference data
        java.util.Set<UUID> periodIds = rows.stream().map(BulkUpsertEntryRowRequest::periodId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        java.util.Set<UUID> roomIds = rows.stream().map(BulkUpsertEntryRowRequest::roomId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        java.util.Set<UUID> teacherIds = rows.stream().map(BulkUpsertEntryRowRequest::teacherId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        java.util.Set<UUID> subjectIds = rows.stream().map(BulkUpsertEntryRowRequest::subjectId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        java.util.Set<UUID> teacherAssignmentIds = rows.stream().map(BulkUpsertEntryRowRequest::teacherAssignmentId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        java.util.Set<UUID> classSubjectAssignmentIds = rows.stream().map(BulkUpsertEntryRowRequest::classSubjectAssignmentId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());

        Map<UUID, BellSchedulePeriod> periodMap = bellSchedulePeriodRepository.findAllById(periodIds).stream()
                .filter(p -> !p.isDeleted())
                .collect(Collectors.toMap(BellSchedulePeriod::getId, p -> p));

        Map<UUID, Room> roomMap = roomRepository.findAllById(roomIds).stream()
                .filter(r -> !r.isDeleted())
                .collect(Collectors.toMap(Room::getId, r -> r));

        Map<UUID, Staff> staffMap = staffRepository.findAllById(teacherIds).stream()
                .filter(s -> !s.isDeleted())
                .collect(Collectors.toMap(Staff::getId, s -> s));

        Map<UUID, Subject> subjectMap = subjectRepository.findAllById(subjectIds).stream()
                .filter(s -> !s.isDeleted())
                .collect(Collectors.toMap(Subject::getId, s -> s));

        Map<UUID, TeacherSubjectAssignment> teacherAssignmentMap = teacherSubjectAssignmentRepository.findAllById(teacherAssignmentIds).stream()
                .filter(t -> !t.isDeleted())
                .collect(Collectors.toMap(TeacherSubjectAssignment::getId, t -> t));

        Map<UUID, ClassSubjectAssignment> classSubjectAssignmentMap = classSubjectAssignmentRepository.findAllById(classSubjectAssignmentIds).stream()
                .filter(c -> !c.isDeleted())
                .collect(Collectors.toMap(ClassSubjectAssignment::getId, c -> c));

        List<TimetableEntry> existingSectionEntries = timetableEntryRepository.findByTimetableAndClassAndSection(timetableId, academicClass.getId(), section.getId());
        java.util.Set<UUID> existingSectionEntryIds = existingSectionEntries.stream().map(TimetableEntry::getId).collect(Collectors.toSet());

        // Phase 1: Full validation collecting all errors with row index
        List<String> validationErrors = new ArrayList<>();
        java.util.Set<String> naturalKeySet = new java.util.HashSet<>();
        java.util.Set<String> payloadTeacherSlots = new java.util.HashSet<>();
        java.util.Set<String> payloadRoomSlots = new java.util.HashSet<>();
        List<PreparedEntryData> preparedRows = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            BulkUpsertEntryRowRequest row = rows.get(i);
            int rowNum = i + 1;

            if (row.dayOfWeek() == null) {
                validationErrors.add(String.format("Row %d: dayOfWeek is required", rowNum));
                continue;
            }
            if (row.periodId() == null) {
                validationErrors.add(String.format("Row %d: periodId is required", rowNum));
                continue;
            }

            // Duplicate natural keys within payload
            String naturalKey = row.dayOfWeek() + ":" + row.periodId();
            if (!naturalKeySet.add(naturalKey)) {
                validationErrors.add(String.format("Row %d: Duplicate natural key for day [%s] and period [%s]", rowNum, row.dayOfWeek(), row.periodId()));
            }

            try {
                PreparedEntryData data = validateRowData(
                        rowNum,
                        timetable,
                        academicClass,
                        section,
                        row,
                        periodMap,
                        roomMap,
                        staffMap,
                        subjectMap,
                        teacherAssignmentMap,
                        classSubjectAssignmentMap
                );
                preparedRows.add(data);

                // Intra-payload teacher clash
                if (data.teacher() != null) {
                    String teacherSlotKey = row.dayOfWeek() + ":" + row.periodId() + ":" + data.teacher().getId();
                    if (!payloadTeacherSlots.add(teacherSlotKey)) {
                        validationErrors.add(String.format("Row %d: Intra-payload teacher clash for teacher [%s] on %s period [%s]",
                                rowNum, data.teacher().getStaffCode(), row.dayOfWeek(), data.period().getName()));
                    }

                    // Outside DB clash check (excluding this section's existing entries)
                    List<TimetableEntry> teacherClashes = timetableEntryRepository.findTeacherClashes(timetableId, data.teacher().getId(), row.dayOfWeek(), row.periodId(), null);
                    for (TimetableEntry clash : teacherClashes) {
                        if (!existingSectionEntryIds.contains(clash.getId())) {
                            validationErrors.add(String.format("Row %d: Teacher clash with existing entry [%s]: teacher is already scheduled on %s period %s.",
                                    rowNum, clash.getEntryCode(), row.dayOfWeek(), data.period().getName()));
                            break;
                        }
                    }
                }

                // Intra-payload room clash
                if (data.room() != null) {
                    String roomSlotKey = row.dayOfWeek() + ":" + row.periodId() + ":" + data.room().getId();
                    if (!payloadRoomSlots.add(roomSlotKey)) {
                        validationErrors.add(String.format("Row %d: Intra-payload room clash for room [%s] on %s period [%s]",
                                rowNum, data.room().getName(), row.dayOfWeek(), data.period().getName()));
                    }

                    // Outside DB clash check (excluding this section's existing entries)
                    List<TimetableEntry> roomClashes = timetableEntryRepository.findRoomClashes(timetableId, data.room().getId(), row.dayOfWeek(), row.periodId(), null);
                    for (TimetableEntry clash : roomClashes) {
                        if (!existingSectionEntryIds.contains(clash.getId())) {
                            validationErrors.add(String.format("Row %d: Room clash with existing entry [%s]: room is already occupied on %s period %s.",
                                    rowNum, clash.getEntryCode(), row.dayOfWeek(), data.period().getName()));
                            break;
                        }
                    }
                }

            } catch (Exception e) {
                validationErrors.add(String.format("Row %d: %s", rowNum, e.getMessage()));
            }
        }

        if (!validationErrors.isEmpty()) {
            throw new BusinessException("Bulk upsert validation failed with " + validationErrors.size() + " error(s): " + String.join("; ", validationErrors));
        }

        // Phase 2: Atomic Execution
        UUID currentUserId = getCurrentUserId();
        Map<String, TimetableEntry> existingMap = existingSectionEntries.stream()
                .collect(Collectors.toMap(e -> e.getDayOfWeek() + ":" + e.getPeriod().getId(), e -> e, (e1, e2) -> e1));

        java.util.Set<String> payloadKeys = rows.stream().map(r -> r.dayOfWeek() + ":" + r.periodId()).collect(Collectors.toSet());

        int createdCount = 0;
        int updatedCount = 0;
        int deletedCount = 0;

        if (replaceAll) {
            for (TimetableEntry existing : existingSectionEntries) {
                String key = existing.getDayOfWeek() + ":" + existing.getPeriod().getId();
                if (!payloadKeys.contains(key)) {
                    existing.softDelete();
                    existing.setUpdatedBy(currentUserId);
                    timetableEntryRepository.save(existing);
                    deletedCount++;
                }
            }
        }

        // Determine which existing rows changed vs untouched
        boolean hasDeletions = deletedCount > 0;
        List<Integer> rowsToInsertIndices = new ArrayList<>();
        List<TimetableEntry> resultingEntries = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            BulkUpsertEntryRowRequest row = rows.get(i);
            PreparedEntryData data = preparedRows.get(i);
            String key = row.dayOfWeek() + ":" + row.periodId();
            TimetableEntry existing = existingMap.get(key);

            if (existing != null) {
                if (isEntryUnchanged(existing, data)) {
                    // Untouched: preserve existing row with same ID and entryCode
                    resultingEntries.add(existing);
                } else {
                    // Changed: soft-delete old entry and insert replacement
                    existing.softDelete();
                    existing.setUpdatedBy(currentUserId);
                    timetableEntryRepository.save(existing);
                    updatedCount++;
                    hasDeletions = true;
                    rowsToInsertIndices.add(i);
                }
            } else {
                // New slot: create entry
                createdCount++;
                rowsToInsertIndices.add(i);
            }
        }

        if (hasDeletions) {
            timetableEntryRepository.flush();
        }

        // Insert replacements / new entries
        List<TimetableEntry> newEntries = new ArrayList<>();
        for (int idx : rowsToInsertIndices) {
            BulkUpsertEntryRowRequest row = rows.get(idx);
            PreparedEntryData data = preparedRows.get(idx);

            String entryCode = CodeGenerator.timetableEntryCode();
            TimetableEntry entry = new TimetableEntry(
                    entryCode,
                    timetable,
                    timetable.getAcademicYear(),
                    data.period(),
                    row.dayOfWeek(),
                    academicClass,
                    section,
                    data.subject(),
                    data.teacherAssignment(),
                    data.teacher(),
                    data.classSubjectAssignment(),
                    data.room(),
                    data.entryType()
            );
            entry.setCreatedBy(currentUserId);
            entry.setUpdatedBy(currentUserId);
            newEntries.add(entry);
        }

        if (!newEntries.isEmpty()) {
            List<TimetableEntry> savedNew = timetableEntryRepository.saveAll(newEntries);
            resultingEntries.addAll(savedNew);
        }

        resultingEntries.sort(java.util.Comparator.comparing(TimetableEntry::getDayOfWeek)
                .thenComparing(e -> e.getPeriod().getPeriodSequence()));

        List<TimetableEntryResponse> entryResponses = resultingEntries.stream()
                .map(timetableEntryMapper::toResponse)
                .toList();

        return new BulkUpsertEntriesResponse(createdCount, updatedCount, deletedCount, entryResponses);
    }

    private boolean isEntryUnchanged(TimetableEntry existing, PreparedEntryData data) {
        if (existing == null || data == null) {
            return false;
        }
        if (existing.getEntryType() != data.entryType()) {
            return false;
        }
        UUID existingRoomId = existing.getRoom() != null ? existing.getRoom().getId() : null;
        UUID newRoomId = data.room() != null ? data.room().getId() : null;
        if (!java.util.Objects.equals(existingRoomId, newRoomId)) {
            return false;
        }
        UUID existingSubId = existing.getSubject() != null ? existing.getSubject().getId() : null;
        UUID newSubId = data.subject() != null ? data.subject().getId() : null;
        if (!java.util.Objects.equals(existingSubId, newSubId)) {
            return false;
        }
        UUID existingTeacherId = existing.getTeacher() != null ? existing.getTeacher().getId() : null;
        UUID newTeacherId = data.teacher() != null ? data.teacher().getId() : null;
        if (!java.util.Objects.equals(existingTeacherId, newTeacherId)) {
            return false;
        }
        UUID existingTsaId = existing.getTeacherAssignment() != null ? existing.getTeacherAssignment().getId() : null;
        UUID newTsaId = data.teacherAssignment() != null ? data.teacherAssignment().getId() : null;
        if (!java.util.Objects.equals(existingTsaId, newTsaId)) {
            return false;
        }
        UUID existingCsaId = existing.getClassSubjectAssignment() != null ? existing.getClassSubjectAssignment().getId() : null;
        UUID newCsaId = data.classSubjectAssignment() != null ? data.classSubjectAssignment().getId() : null;
        if (!java.util.Objects.equals(existingCsaId, newCsaId)) {
            return false;
        }
        return true;
    }

    private PreparedEntryData validateRowData(
            int rowNum,
            Timetable timetable,
            AcademicClass academicClass,
            Section section,
            BulkUpsertEntryRowRequest row,
            Map<UUID, BellSchedulePeriod> periodMap,
            Map<UUID, Room> roomMap,
            Map<UUID, Staff> staffMap,
            Map<UUID, Subject> subjectMap,
            Map<UUID, TeacherSubjectAssignment> teacherAssignmentMap,
            Map<UUID, ClassSubjectAssignment> classSubjectAssignmentMap
    ) {
        BellSchedulePeriod period = periodMap.get(row.periodId());
        if (period == null) {
            period = bellSchedulePeriodRepository.findByIdAndNotDeleted(row.periodId())
                    .orElseThrow(() -> new ResourceNotFoundException("BellSchedulePeriod", row.periodId()));
        }

        if (!period.getBellSchedule().getId().equals(timetable.getBellSchedule().getId())) {
            throw new BusinessException("Period does not belong to the timetable's bell schedule.");
        }

        TimetableEntryType entryType = row.entryType() != null ? row.entryType() : TimetableEntryType.REGULAR;
        if (entryType == TimetableEntryType.SUBSTITUTE) {
            throw new BusinessException("SUBSTITUTE entry type is reserved for substitution management and cannot be created directly.");
        }

        if (period.getPeriodType() == PeriodType.FREE) {
            throw new BusinessException("FREE periods do not accept any timetable entries.");
        } else if (period.getPeriodType() == PeriodType.TEACHING) {
            if (entryType == TimetableEntryType.BREAK || entryType == TimetableEntryType.ASSEMBLY) {
                throw new BusinessException("TEACHING periods accept only REGULAR, LAB, ACTIVITY, and SPECIAL entry types.");
            }
        } else if (period.getPeriodType() == PeriodType.BREAK || period.getPeriodType() == PeriodType.LUNCH) {
            if (entryType != TimetableEntryType.BREAK) {
                throw new BusinessException("BREAK and LUNCH periods accept only BREAK entry type.");
            }
        } else if (period.getPeriodType() == PeriodType.ASSEMBLY) {
            if (entryType != TimetableEntryType.ASSEMBLY) {
                throw new BusinessException("ASSEMBLY periods accept only ASSEMBLY entry type.");
            }
        } else if (period.getPeriodType() == PeriodType.ACTIVITY) {
            if (entryType != TimetableEntryType.ACTIVITY && entryType != TimetableEntryType.SPECIAL) {
                throw new BusinessException("ACTIVITY periods accept only ACTIVITY and SPECIAL entry types.");
            }
        } else if (period.getPeriodType() == PeriodType.OTHER) {
            if (entryType == TimetableEntryType.BREAK || entryType == TimetableEntryType.ASSEMBLY) {
                throw new BusinessException("OTHER periods accept only REGULAR, LAB, ACTIVITY, and SPECIAL entry types.");
            }
        }

        ClassSubjectAssignment classSubjectAssignment = null;
        Subject subject = null;
        if (row.classSubjectAssignmentId() != null) {
            classSubjectAssignment = classSubjectAssignmentMap.get(row.classSubjectAssignmentId());
            if (classSubjectAssignment == null) {
                classSubjectAssignment = classSubjectAssignmentRepository.findByIdAndNotDeleted(row.classSubjectAssignmentId())
                        .orElseThrow(() -> new ResourceNotFoundException("ClassSubjectAssignment", row.classSubjectAssignmentId()));
            }
            if (!classSubjectAssignment.getAcademicClass().getId().equals(academicClass.getId())) {
                throw new BusinessException("Class subject assignment does not belong to specified academic class.");
            }
            if (!classSubjectAssignment.getAcademicYear().getId().equals(timetable.getAcademicYear().getId())) {
                throw new BusinessException("Class subject assignment does not belong to timetable's academic year.");
            }
            if (classSubjectAssignment.getStatus() != com.vidya.tenantdata.assignment.enums.ClassSubjectAssignmentStatus.ACTIVE) {
                throw new BusinessException("Class subject assignment is not ACTIVE.");
            }
            subject = classSubjectAssignment.getSubject();
        }

        if (row.subjectId() != null) {
            Subject directSub = subjectMap.get(row.subjectId());
            if (directSub == null) {
                directSub = subjectRepository.findByIdAndNotDeleted(row.subjectId())
                        .orElseThrow(() -> new ResourceNotFoundException("Subject", row.subjectId()));
            }
            if (subject != null && !subject.getId().equals(directSub.getId())) {
                throw new BusinessException("Specified subject does not match subject in class subject assignment.");
            }
            subject = directSub;
        }

        TeacherSubjectAssignment teacherAssignment = null;
        Staff teacher = null;
        if (row.teacherAssignmentId() != null) {
            teacherAssignment = teacherAssignmentMap.get(row.teacherAssignmentId());
            if (teacherAssignment == null) {
                teacherAssignment = teacherSubjectAssignmentRepository.findByIdAndNotDeleted(row.teacherAssignmentId())
                        .orElseThrow(() -> new ResourceNotFoundException("TeacherSubjectAssignment", row.teacherAssignmentId()));
            }
            if (teacherAssignment.getStatus() != com.vidya.tenantdata.assignment.enums.TeacherAssignmentStatus.ACTIVE) {
                throw new BusinessException("Teacher subject assignment is not ACTIVE.");
            }
            if (!teacherAssignment.getAcademicYear().getId().equals(timetable.getAcademicYear().getId())) {
                throw new BusinessException("Teacher assignment does not belong to timetable's academic year.");
            }
            if (!teacherAssignment.getAcademicClass().getId().equals(academicClass.getId())) {
                throw new BusinessException("Teacher assignment does not belong to specified academic class.");
            }
            if (section != null && teacherAssignment.getSection() != null && !teacherAssignment.getSection().getId().equals(section.getId())) {
                throw new BusinessException("Teacher assignment does not match specified section.");
            }
            if (subject != null && !teacherAssignment.getSubject().getId().equals(subject.getId())) {
                throw new BusinessException("Teacher assignment does not match specified subject.");
            }
            if (subject == null) {
                subject = teacherAssignment.getSubject();
            }
            teacher = teacherAssignment.getTeacher();
            if (teacher.getEmploymentStatus() != com.vidya.tenantdata.staff.enums.EmploymentStatus.ACTIVE) {
                throw new BusinessException("Teacher is not in ACTIVE employment status.");
            }
        } else if (row.teacherId() != null) {
            teacher = staffMap.get(row.teacherId());
            if (teacher == null) {
                teacher = staffRepository.findByIdAndNotDeleted(row.teacherId())
                        .orElseThrow(() -> new ResourceNotFoundException("Staff", row.teacherId()));
            }
            if (teacher.getEmploymentStatus() != com.vidya.tenantdata.staff.enums.EmploymentStatus.ACTIVE) {
                throw new BusinessException("Teacher is not in ACTIVE employment status.");
            }
        }

        // Entry-Type Field Rules:
        if (entryType == TimetableEntryType.REGULAR || entryType == TimetableEntryType.LAB) {
            if (subject == null || teacherAssignment == null || teacher == null) {
                throw new BusinessException("REGULAR and LAB entries require subject, teacher assignment, and teacher.");
            }
        } else if (entryType == TimetableEntryType.BREAK || entryType == TimetableEntryType.ASSEMBLY) {
            if (subject != null || teacherAssignment != null || teacher != null || classSubjectAssignment != null) {
                throw new BusinessException(entryType + " entries must not have subject, teacher assignment, or teacher.");
            }
        }

        Room room = null;
        if (row.roomId() != null) {
            room = roomMap.get(row.roomId());
            if (room == null) {
                room = roomRepository.findByIdAndNotDeleted(row.roomId())
                        .orElseThrow(() -> new ResourceNotFoundException("Room", row.roomId()));
            }
            if (room.getStatus() != RoomStatus.ACTIVE) {
                throw new BusinessException("Assigned room must be in ACTIVE status.");
            }
            if (entryType == TimetableEntryType.LAB &&
                room.getRoomType() != RoomType.LAB &&
                room.getRoomType() != RoomType.SCIENCE_LAB &&
                room.getRoomType() != RoomType.COMPUTER_LAB) {
                throw new BusinessException("LAB entries require a lab-type room (LAB, SCIENCE_LAB, or COMPUTER_LAB).");
            }
        }
        if (entryType == TimetableEntryType.LAB && room == null) {
            throw new BusinessException("LAB entries require an assigned lab room.");
        }

        String warning = null;
        if (room != null && section != null && room.getCapacity() != null && section.getCapacity() != null && room.getCapacity() < section.getCapacity()) {
            warning = String.format("Room capacity (%d) is below section student count (%d).", room.getCapacity(), section.getCapacity());
        }

        return new PreparedEntryData(period, academicClass, section, subject, teacherAssignment, teacher, classSubjectAssignment, room, entryType, warning);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public CloneTimetableResponse cloneTimetable(UUID id, CloneTimetableRequest request) {
        log.info("Cloning timetable [{}]", id);

        Timetable source = timetableRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", id));

        String targetName;
        if (request != null && request.name() != null && !request.name().isBlank()) {
            targetName = request.name().trim();
            if (timetableRepository.existsByNameIgnoreCaseAndAcademicYearIdAndNotDeleted(targetName, source.getAcademicYear().getId(), null)) {
                throw new DuplicateResourceException("Timetable", "name", "Timetable with name [" + targetName + "] already exists in this academic year.");
            }
        } else {
            targetName = generateClonedTimetableName(source.getName(), source.getAcademicYear().getId());
        }

        java.time.LocalDate effectiveFrom = (request != null && request.effectiveFrom() != null) ? request.effectiveFrom() : source.getEffectiveFrom();
        java.time.LocalDate effectiveTo = (request != null && request.effectiveTo() != null) ? request.effectiveTo() : source.getEffectiveTo();

        UUID currentUserId = getCurrentUserId();
        String timetableCode = CodeGenerator.timetableCode();
        Timetable cloned = new Timetable(
                timetableCode,
                source.getAcademicYear(),
                source.getBellSchedule(),
                targetName,
                source.getDescription(),
                effectiveFrom,
                effectiveTo
        );
        cloned.setStatus(TimetableStatus.DRAFT);
        cloned.setCreatedBy(currentUserId);
        cloned.setUpdatedBy(currentUserId);

        Timetable savedTimetable = timetableRepository.save(cloned);

        boolean strict = request != null && Boolean.TRUE.equals(request.strict());
        List<TimetableEntry> sourceEntries = timetableEntryRepository.findAllByTimetableIdWithDetails(source.getId());

        List<SkippedEntryResponse> skipped = new ArrayList<>();
        List<TimetableEntry> validNewEntries = new ArrayList<>();

        for (TimetableEntry entry : sourceEntries) {
            String invalidReason = validateCloneEntry(entry);
            if (invalidReason != null) {
                if (strict) {
                    throw new BusinessException("Strict clone failed: Entry [" + entry.getEntryCode() + "] is invalid: " + invalidReason);
                } else {
                    skipped.add(new SkippedEntryResponse(entry.getId(), entry.getEntryCode(), invalidReason));
                }
            } else {
                TimetableEntry newEntry = new TimetableEntry(
                        CodeGenerator.timetableEntryCode(),
                        savedTimetable,
                        savedTimetable.getAcademicYear(),
                        entry.getPeriod(),
                        entry.getDayOfWeek(),
                        entry.getAcademicClass(),
                        entry.getSection(),
                        entry.getSubject(),
                        entry.getTeacherAssignment(),
                        entry.getTeacher(),
                        entry.getClassSubjectAssignment(),
                        entry.getRoom(),
                        entry.getEntryType()
                );
                newEntry.setCreatedBy(currentUserId);
                newEntry.setUpdatedBy(currentUserId);
                validNewEntries.add(newEntry);
            }
        }

        timetableEntryRepository.saveAll(validNewEntries);

        return new CloneTimetableResponse(
                timetableMapper.toResponse(savedTimetable),
                validNewEntries.size(),
                skipped.size(),
                skipped
        );
    }

    private String generateClonedTimetableName(String originalName, UUID academicYearId) {
        String baseName = originalName + " (Copy)";
        String candidate = baseName;
        int suffix = 2;
        while (timetableRepository.existsByNameIgnoreCaseAndAcademicYearIdAndNotDeleted(candidate, academicYearId, null)) {
            candidate = originalName + " (Copy " + suffix + ")";
            suffix++;
        }
        return candidate;
    }

    private String validateCloneEntry(TimetableEntry entry) {
        if (entry.getPeriod() != null && entry.getPeriod().isDeleted()) {
            return "Referenced period is deleted.";
        }
        if (entry.getAcademicClass() != null && entry.getAcademicClass().isDeleted()) {
            return "Referenced academic class is deleted.";
        }
        if (entry.getSection() != null && entry.getSection().isDeleted()) {
            return "Referenced section is deleted.";
        }
        if (entry.getSubject() != null && entry.getSubject().isDeleted()) {
            return "Referenced subject is deleted.";
        }
        if (entry.getTeacher() != null) {
            if (entry.getTeacher().isDeleted() || entry.getTeacher().getEmploymentStatus() != com.vidya.tenantdata.staff.enums.EmploymentStatus.ACTIVE) {
                return "Assigned teacher is not active.";
            }
        }
        if (entry.getTeacherAssignment() != null) {
            if (entry.getTeacherAssignment().isDeleted() || entry.getTeacherAssignment().getStatus() != com.vidya.tenantdata.assignment.enums.TeacherAssignmentStatus.ACTIVE) {
                return "Assigned teacher subject assignment is not active.";
            }
        }
        if (entry.getRoom() != null) {
            if (entry.getRoom().isDeleted() || entry.getRoom().getStatus() != RoomStatus.ACTIVE) {
                return "Assigned room is not active.";
            }
            if (entry.getEntryType() == TimetableEntryType.LAB &&
                entry.getRoom().getRoomType() != RoomType.LAB &&
                entry.getRoom().getRoomType() != RoomType.SCIENCE_LAB &&
                entry.getRoom().getRoomType() != RoomType.COMPUTER_LAB) {
                return "LAB entry requires a lab room.";
            }
        }
        return null;
    }

    private String getTeacherName(Staff teacher) {
        if (teacher == null) {
            return null;
        }
        if (teacher.getTenantUser() != null && teacher.getTenantUser().getProfile() != null) {
            return teacher.getTenantUser().getProfile().getFullName();
        }
        return teacher.getStaffCode();
    }

}
