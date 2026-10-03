package com.vidya.tenantdata.timetable.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
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
import com.vidya.tenantdata.timetable.dto.CreateTimetableEntryRequest;
import com.vidya.tenantdata.timetable.dto.CreateTimetableRequest;
import com.vidya.tenantdata.timetable.dto.ScheduleConflictResponse;
import com.vidya.tenantdata.timetable.dto.ScheduleValidationResponse;
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
import com.vidya.tenantdata.timetable.enums.ConflictSeverity;
import com.vidya.tenantdata.timetable.enums.TimetableStatus;
import com.vidya.tenantdata.timetable.mapper.TimetableEntryMapper;
import com.vidya.tenantdata.timetable.mapper.TimetableMapper;
import com.vidya.tenantdata.timetable.repository.BellSchedulePeriodRepository;
import com.vidya.tenantdata.timetable.repository.BellScheduleRepository;
import com.vidya.tenantdata.timetable.repository.RoomRepository;
import com.vidya.tenantdata.timetable.repository.TimetableEntryRepository;
import com.vidya.tenantdata.timetable.repository.TimetableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public TimetableResponse createTimetable(CreateTimetableRequest request) {
        log.info("Creating timetable: [{}] for academic year [{}]", request.name(), request.academicYearId());

        AcademicYear academicYear = academicYearRepository.findByIdAndNotDeleted(request.academicYearId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", request.academicYearId()));

        BellSchedule bellSchedule = bellScheduleRepository.findByIdAndNotDeleted(request.bellScheduleId())
                .orElseThrow(() -> new ResourceNotFoundException("BellSchedule", request.bellScheduleId()));

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

        if (timetable.getStatus() == TimetableStatus.ARCHIVED || timetable.getStatus() == TimetableStatus.DELETED) {
            throw new BusinessException("Cannot update timetable in status: " + timetable.getStatus());
        }

        BellSchedule bellSchedule = bellScheduleRepository.findByIdAndNotDeleted(request.bellScheduleId())
                .orElseThrow(() -> new ResourceNotFoundException("BellSchedule", request.bellScheduleId()));

        timetable.setName(request.name().trim());
        timetable.setDescription(request.description() != null ? request.description().trim() : null);
        timetable.setBellSchedule(bellSchedule);
        timetable.setEffectiveFrom(request.effectiveFrom());
        timetable.setEffectiveTo(request.effectiveTo());

        Timetable updated = timetableRepository.save(timetable);
        log.info("Timetable updated successfully: [{}]", updated.getTimetableCode());
        return timetableMapper.toResponse(updated);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public TimetableResponse updateTimetableStatus(UUID id, UpdateTimetableStatusRequest request) {
        log.info("Updating timetable status: [{}] to [{}]", id, request.status());

        Timetable timetable = timetableRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", id));

        if (request.status() == TimetableStatus.PUBLISHED || request.status() == TimetableStatus.ACTIVE) {
            ScheduleValidationResponse validation = conflictValidator.validateTimetable(timetable);
            if (!validation.valid()) {
                String errorMessages = validation.conflicts().stream()
                        .filter(c -> c.severity() == ConflictSeverity.ERROR)
                        .map(ScheduleConflictResponse::message)
                        .collect(Collectors.joining("; "));
                throw new BusinessException("Cannot activate/publish timetable with unresolved conflicts: " + errorMessages);
            }
        }

        timetable.updateStatus(request.status());
        Timetable updated = timetableRepository.save(timetable);
        log.info("Timetable status updated to [{}] for [{}]", updated.getStatus(), updated.getTimetableCode());
        return timetableMapper.toResponse(updated);
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
    public Page<TimetableResponse> searchTimetables(UUID academicYearId, TimetableStatus status, String search, Pageable pageable) {
        String query = (search != null && !search.isBlank()) ? search.trim() : null;
        return timetableRepository.searchTimetables(academicYearId, status, query, pageable)
                .map(timetableMapper::toResponse);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteTimetable(UUID id) {
        log.info("Deleting timetable: [{}]", id);
        Timetable timetable = timetableRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", id));
        timetable.softDelete();
        timetableRepository.save(timetable);
        log.info("Timetable soft deleted: [{}]", id);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public ScheduleValidationResponse validateTimetable(UUID id) {
        return conflictValidator.validateTimetable(id);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public TimetableResponse publishTimetable(UUID id) {
        log.info("Publishing timetable: [{}]", id);
        Timetable timetable = timetableRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", id));

        ScheduleValidationResponse validation = conflictValidator.validateTimetable(timetable);
        if (!validation.valid()) {
            String errorMessages = validation.conflicts().stream()
                    .filter(c -> c.severity() == ConflictSeverity.ERROR)
                    .map(ScheduleConflictResponse::message)
                    .collect(Collectors.joining("; "));
            throw new BusinessException("Cannot publish timetable with unresolved conflicts: " + errorMessages);
        }

        timetable.updateStatus(TimetableStatus.PUBLISHED);
        Timetable published = timetableRepository.save(timetable);
        log.info("Timetable published successfully: [{}]", published.getTimetableCode());
        return timetableMapper.toResponse(published);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public TimetableEntryResponse createEntry(UUID timetableId, CreateTimetableEntryRequest request) {
        log.info("Creating timetable entry for timetable [{}] on [{}] period [{}]", timetableId, request.dayOfWeek(), request.periodId());

        Timetable timetable = timetableRepository.findByIdAndNotDeleted(timetableId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", timetableId));

        if (timetable.getStatus() == TimetableStatus.ARCHIVED || timetable.getStatus() == TimetableStatus.DELETED) {
            throw new BusinessException("Cannot add entries to a timetable in status: " + timetable.getStatus());
        }

        ScheduleValidationResponse validation = conflictValidator.validateEntry(timetableId, request, null);
        if (!validation.valid()) {
            String errorMessages = validation.conflicts().stream()
                    .filter(c -> c.severity() == ConflictSeverity.ERROR)
                    .map(ScheduleConflictResponse::message)
                    .collect(Collectors.joining("; "));
            throw new BusinessException("Scheduling conflict detected: " + errorMessages);
        }

        BellSchedulePeriod period = bellSchedulePeriodRepository.findByIdAndNotDeleted(request.periodId())
                .orElseThrow(() -> new ResourceNotFoundException("BellSchedulePeriod", request.periodId()));

        AcademicClass academicClass = academicClassRepository.findByIdAndNotDeleted(request.academicClassId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicClass", request.academicClassId()));

        Section section = null;
        if (request.sectionId() != null) {
            section = sectionRepository.findByIdAndNotDeleted(request.sectionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Section", request.sectionId()));
        }

        Subject subject = null;
        if (request.subjectId() != null) {
            subject = subjectRepository.findByIdAndNotDeleted(request.subjectId())
                    .orElseThrow(() -> new ResourceNotFoundException("Subject", request.subjectId()));
        }

        TeacherSubjectAssignment teacherAssignment = null;
        Staff teacher = null;
        if (request.teacherAssignmentId() != null) {
            teacherAssignment = teacherSubjectAssignmentRepository.findByIdAndNotDeleted(request.teacherAssignmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("TeacherSubjectAssignment", request.teacherAssignmentId()));
            teacher = teacherAssignment.getTeacher();
            if (subject == null) {
                subject = teacherAssignment.getSubject();
            }
        } else if (request.teacherId() != null) {
            teacher = staffRepository.findByIdAndNotDeleted(request.teacherId())
                    .orElseThrow(() -> new ResourceNotFoundException("Staff", request.teacherId()));
        }

        ClassSubjectAssignment classSubjectAssignment = null;
        if (request.classSubjectAssignmentId() != null) {
            classSubjectAssignment = classSubjectAssignmentRepository.findByIdAndNotDeleted(request.classSubjectAssignmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("ClassSubjectAssignment", request.classSubjectAssignmentId()));
            if (subject == null) {
                subject = classSubjectAssignment.getSubject();
            }
        }

        Room room = null;
        if (request.roomId() != null) {
            room = roomRepository.findByIdAndNotDeleted(request.roomId())
                    .orElseThrow(() -> new ResourceNotFoundException("Room", request.roomId()));
        }

        String entryCode = CodeGenerator.timetableEntryCode();
        while (timetableEntryRepository.existsByEntryCode(entryCode)) {
            entryCode = CodeGenerator.timetableEntryCode();
        }

        TimetableEntry entry = new TimetableEntry(
                entryCode,
                timetable,
                timetable.getAcademicYear(),
                period,
                request.dayOfWeek(),
                academicClass,
                section,
                subject,
                teacherAssignment,
                teacher,
                classSubjectAssignment,
                room,
                request.entryType()
        );

        TimetableEntry saved = timetableEntryRepository.save(entry);
        log.info("Timetable entry created successfully: [{}]", saved.getEntryCode());
        return timetableEntryMapper.toResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public TimetableEntryResponse updateEntry(UUID timetableId, UUID entryId, UpdateTimetableEntryRequest request) {
        log.info("Updating timetable entry [{}] for timetable [{}]", entryId, timetableId);

        Timetable timetable = timetableRepository.findByIdAndNotDeleted(timetableId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", timetableId));

        TimetableEntry entry = timetableEntryRepository.findByIdAndNotDeleted(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("TimetableEntry", entryId));

        if (!entry.getTimetable().getId().equals(timetableId)) {
            throw new ResourceNotFoundException("TimetableEntry", entryId);
        }

        CreateTimetableEntryRequest validationReq = new CreateTimetableEntryRequest(
                request.periodId(),
                request.dayOfWeek(),
                request.academicClassId(),
                request.sectionId(),
                request.subjectId(),
                request.teacherAssignmentId(),
                request.teacherId(),
                request.classSubjectAssignmentId(),
                request.roomId(),
                request.entryType()
        );

        ScheduleValidationResponse validation = conflictValidator.validateEntry(timetableId, validationReq, entryId);
        if (!validation.valid()) {
            String errorMessages = validation.conflicts().stream()
                    .filter(c -> c.severity() == ConflictSeverity.ERROR)
                    .map(ScheduleConflictResponse::message)
                    .collect(Collectors.joining("; "));
            throw new BusinessException("Scheduling conflict detected: " + errorMessages);
        }

        BellSchedulePeriod period = bellSchedulePeriodRepository.findByIdAndNotDeleted(request.periodId())
                .orElseThrow(() -> new ResourceNotFoundException("BellSchedulePeriod", request.periodId()));

        AcademicClass academicClass = academicClassRepository.findByIdAndNotDeleted(request.academicClassId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicClass", request.academicClassId()));

        Section section = null;
        if (request.sectionId() != null) {
            section = sectionRepository.findByIdAndNotDeleted(request.sectionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Section", request.sectionId()));
        }

        Subject subject = null;
        if (request.subjectId() != null) {
            subject = subjectRepository.findByIdAndNotDeleted(request.subjectId())
                    .orElseThrow(() -> new ResourceNotFoundException("Subject", request.subjectId()));
        }

        TeacherSubjectAssignment teacherAssignment = null;
        Staff teacher = null;
        if (request.teacherAssignmentId() != null) {
            teacherAssignment = teacherSubjectAssignmentRepository.findByIdAndNotDeleted(request.teacherAssignmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("TeacherSubjectAssignment", request.teacherAssignmentId()));
            teacher = teacherAssignment.getTeacher();
            if (subject == null) {
                subject = teacherAssignment.getSubject();
            }
        } else if (request.teacherId() != null) {
            teacher = staffRepository.findByIdAndNotDeleted(request.teacherId())
                    .orElseThrow(() -> new ResourceNotFoundException("Staff", request.teacherId()));
        }

        ClassSubjectAssignment classSubjectAssignment = null;
        if (request.classSubjectAssignmentId() != null) {
            classSubjectAssignment = classSubjectAssignmentRepository.findByIdAndNotDeleted(request.classSubjectAssignmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("ClassSubjectAssignment", request.classSubjectAssignmentId()));
            if (subject == null) {
                subject = classSubjectAssignment.getSubject();
            }
        }

        Room room = null;
        if (request.roomId() != null) {
            room = roomRepository.findByIdAndNotDeleted(request.roomId())
                    .orElseThrow(() -> new ResourceNotFoundException("Room", request.roomId()));
        }

        entry.setPeriod(period);
        entry.setDayOfWeek(request.dayOfWeek());
        entry.setAcademicClass(academicClass);
        entry.setSection(section);
        entry.setSubject(subject);
        entry.setTeacherAssignment(teacherAssignment);
        entry.setTeacher(teacher);
        entry.setClassSubjectAssignment(classSubjectAssignment);
        entry.setRoom(room);
        if (request.entryType() != null) {
            entry.setEntryType(request.entryType());
        }

        TimetableEntry updated = timetableEntryRepository.save(entry);
        log.info("Timetable entry updated successfully: [{}]", updated.getEntryCode());
        return timetableEntryMapper.toResponse(updated);
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
    public Page<TimetableEntryResponse> searchEntries(UUID timetableId, UUID academicClassId, UUID sectionId, UUID teacherId, UUID subjectId, UUID roomId, DayOfWeek dayOfWeek, Pageable pageable) {
        return timetableEntryRepository.searchEntries(timetableId, academicClassId, sectionId, teacherId, subjectId, roomId, dayOfWeek, pageable)
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

        entry.softDelete();
        timetableEntryRepository.save(entry);
        log.info("Timetable entry soft deleted: [{}]", entryId);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public TimetableGridResponse getClassTimetableGrid(UUID timetableId, UUID academicClassId, UUID sectionId) {
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

        return buildGridResponse(
                timetable,
                "CLASS",
                academicClassId,
                academicClass.getName() + sectionName,
                periods,
                entries
        );
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public TimetableGridResponse getTeacherTimetableGrid(UUID timetableId, UUID teacherId) {
        Timetable timetable = timetableRepository.findByIdAndNotDeleted(timetableId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", timetableId));

        Staff teacher = staffRepository.findByIdAndNotDeleted(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff", teacherId));

        List<TimetableEntry> entries = timetableEntryRepository.findByTimetableAndTeacher(timetableId, teacherId);
        List<BellSchedulePeriod> periods = bellSchedulePeriodRepository.findByBellScheduleIdAndNotDeletedOrderBySequence(timetable.getBellSchedule().getId());

        String teacherFullName = teacher.getStaffCode();
        if (teacher.getTenantUser() != null && teacher.getTenantUser().getProfile() != null) {
            teacherFullName = teacher.getTenantUser().getProfile().getFullName();
        }

        return buildGridResponse(
                timetable,
                "TEACHER",
                teacherId,
                teacherFullName,
                periods,
                entries
        );
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public TimetableGridResponse getRoomTimetableGrid(UUID timetableId, UUID roomId) {
        Timetable timetable = timetableRepository.findByIdAndNotDeleted(timetableId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", timetableId));

        Room room = roomRepository.findByIdAndNotDeleted(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room", roomId));

        List<TimetableEntry> entries = timetableEntryRepository.findByTimetableAndRoom(timetableId, roomId);
        List<BellSchedulePeriod> periods = bellSchedulePeriodRepository.findByBellScheduleIdAndNotDeletedOrderBySequence(timetable.getBellSchedule().getId());

        return buildGridResponse(
                timetable,
                "ROOM",
                roomId,
                room.getName(),
                periods,
                entries
        );
    }

    private TimetableGridResponse buildGridResponse(
            Timetable timetable,
            String filterType,
            UUID filterId,
            String filterName,
            List<BellSchedulePeriod> periods,
            List<TimetableEntry> entries
    ) {
        Map<String, TimetableEntry> entryMap = entries.stream()
                .collect(Collectors.toMap(
                        e -> e.getDayOfWeek() + ":" + e.getPeriod().getId(),
                        e -> e,
                        (existing, replacement) -> existing
                ));

        DayOfWeek[] workingDays = {
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY
        };

        List<TimetableDayGridResponse> dayGrids = new ArrayList<>();
        for (DayOfWeek day : workingDays) {
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
}
