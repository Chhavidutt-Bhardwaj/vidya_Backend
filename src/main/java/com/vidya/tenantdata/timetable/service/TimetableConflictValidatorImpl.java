package com.vidya.tenantdata.timetable.service;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.assignment.entity.ClassSubjectAssignment;
import com.vidya.tenantdata.assignment.entity.TeacherSubjectAssignment;
import com.vidya.tenantdata.assignment.enums.ClassSubjectAssignmentStatus;
import com.vidya.tenantdata.assignment.enums.TeacherAssignmentStatus;
import com.vidya.tenantdata.assignment.repository.ClassSubjectAssignmentRepository;
import com.vidya.tenantdata.assignment.repository.TeacherSubjectAssignmentRepository;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.subject.repository.SubjectRepository;
import com.vidya.tenantdata.timetable.dto.CreateTimetableEntryRequest;
import com.vidya.tenantdata.timetable.dto.ScheduleConflictResponse;
import com.vidya.tenantdata.timetable.dto.ScheduleValidationResponse;
import com.vidya.tenantdata.timetable.entity.BellSchedulePeriod;
import com.vidya.tenantdata.timetable.entity.Room;
import com.vidya.tenantdata.timetable.entity.Timetable;
import com.vidya.tenantdata.timetable.entity.TimetableEntry;
import com.vidya.tenantdata.timetable.enums.ConflictSeverity;
import com.vidya.tenantdata.timetable.enums.PeriodType;
import com.vidya.tenantdata.timetable.enums.RoomStatus;
import com.vidya.tenantdata.timetable.enums.ScheduleConflictType;
import com.vidya.tenantdata.timetable.repository.BellSchedulePeriodRepository;
import com.vidya.tenantdata.timetable.repository.RoomRepository;
import com.vidya.tenantdata.timetable.repository.TimetableEntryRepository;
import com.vidya.tenantdata.timetable.repository.TimetableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimetableConflictValidatorImpl implements TimetableConflictValidator {

    private final TimetableRepository timetableRepository;
    private final TimetableEntryRepository timetableEntryRepository;
    private final BellSchedulePeriodRepository bellSchedulePeriodRepository;
    private final RoomRepository roomRepository;
    private final StaffRepository staffRepository;
    private final AcademicClassRepository academicClassRepository;
    private final SectionRepository sectionRepository;
    private final SubjectRepository subjectRepository;
    private final TeacherSubjectAssignmentRepository teacherSubjectAssignmentRepository;
    private final ClassSubjectAssignmentRepository classSubjectAssignmentRepository;

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public ScheduleValidationResponse validateEntry(UUID timetableId, CreateTimetableEntryRequest request, UUID excludeEntryId) {
        Timetable timetable = timetableRepository.findByIdAndNotDeleted(timetableId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", timetableId));

        List<ScheduleConflictResponse> conflicts = new ArrayList<>();

        // 1. Validate Period belongs to Timetable's BellSchedule
        BellSchedulePeriod period = bellSchedulePeriodRepository.findByIdAndNotDeleted(request.periodId()).orElse(null);
        if (period == null) {
            conflicts.add(new ScheduleConflictResponse(
                    ScheduleConflictType.PERIOD_CONFLICT,
                    ConflictSeverity.ERROR,
                    "Period not found with ID: " + request.periodId(),
                    excludeEntryId, null, request.periodId(), request.dayOfWeek(), null
            ));
        } else if (!period.getBellSchedule().getId().equals(timetable.getBellSchedule().getId())) {
            conflicts.add(new ScheduleConflictResponse(
                    ScheduleConflictType.PERIOD_CONFLICT,
                    ConflictSeverity.ERROR,
                    "Period '" + period.getName() + "' does not belong to Timetable's Bell Schedule '" + timetable.getBellSchedule().getName() + "'",
                    excludeEntryId, null, period.getId(), request.dayOfWeek(), period.getId()
            ));
        }

        // 2. Validate Academic Class and Section
        AcademicClass academicClass = academicClassRepository.findByIdAndNotDeleted(request.academicClassId()).orElse(null);
        if (academicClass == null) {
            conflicts.add(new ScheduleConflictResponse(
                    ScheduleConflictType.CLASS_CONFLICT,
                    ConflictSeverity.ERROR,
                    "Academic class not found with ID: " + request.academicClassId(),
                    excludeEntryId, null, request.periodId(), request.dayOfWeek(), request.academicClassId()
            ));
        }

        if (request.sectionId() != null) {
            Section section = sectionRepository.findByIdAndNotDeleted(request.sectionId()).orElse(null);
            if (section == null) {
                conflicts.add(new ScheduleConflictResponse(
                        ScheduleConflictType.SECTION_CONFLICT,
                        ConflictSeverity.ERROR,
                        "Section not found with ID: " + request.sectionId(),
                        excludeEntryId, null, request.periodId(), request.dayOfWeek(), request.sectionId()
                ));
            } else if (academicClass != null && !section.getAcademicClass().getId().equals(academicClass.getId())) {
                conflicts.add(new ScheduleConflictResponse(
                        ScheduleConflictType.SECTION_CONFLICT,
                        ConflictSeverity.ERROR,
                        "Section '" + section.getName() + "' does not belong to Academic Class '" + academicClass.getName() + "'",
                        excludeEntryId, null, request.periodId(), request.dayOfWeek(), section.getId()
                ));
            }
        }

        // 3. Resolve Teacher ID from TeacherAssignment if assignment provided
        UUID effectiveTeacherId = request.teacherId();
        if (request.teacherAssignmentId() != null) {
            TeacherSubjectAssignment tsa = teacherSubjectAssignmentRepository.findByIdAndNotDeleted(request.teacherAssignmentId()).orElse(null);
            if (tsa == null) {
                conflicts.add(new ScheduleConflictResponse(
                        ScheduleConflictType.TEACHER_ASSIGNMENT_CONFLICT,
                        ConflictSeverity.ERROR,
                        "Teacher subject assignment not found with ID: " + request.teacherAssignmentId(),
                        excludeEntryId, null, request.periodId(), request.dayOfWeek(), request.teacherAssignmentId()
                ));
            } else {
                if (tsa.getStatus() != TeacherAssignmentStatus.ACTIVE) {
                    conflicts.add(new ScheduleConflictResponse(
                            ScheduleConflictType.TEACHER_ASSIGNMENT_CONFLICT,
                            ConflictSeverity.ERROR,
                            "Teacher assignment is not ACTIVE (current status: " + tsa.getStatus() + ")",
                            excludeEntryId, null, request.periodId(), request.dayOfWeek(), tsa.getId()
                    ));
                }
                if (!tsa.getAcademicYear().getId().equals(timetable.getAcademicYear().getId())) {
                    conflicts.add(new ScheduleConflictResponse(
                            ScheduleConflictType.ACADEMIC_YEAR_CONFLICT,
                            ConflictSeverity.ERROR,
                            "Teacher assignment belongs to a different academic year",
                            excludeEntryId, null, request.periodId(), request.dayOfWeek(), tsa.getId()
                    ));
                }
                if (effectiveTeacherId == null) {
                    effectiveTeacherId = tsa.getTeacher().getId();
                } else if (!effectiveTeacherId.equals(tsa.getTeacher().getId())) {
                    conflicts.add(new ScheduleConflictResponse(
                            ScheduleConflictType.TEACHER_ASSIGNMENT_CONFLICT,
                            ConflictSeverity.ERROR,
                            "Specified teacher does not match the teacher in the teacher assignment",
                            excludeEntryId, null, request.periodId(), request.dayOfWeek(), effectiveTeacherId
                    ));
                }
            }
        }

        // 4. Validate ClassSubjectAssignment if provided
        if (request.classSubjectAssignmentId() != null) {
            ClassSubjectAssignment csa = classSubjectAssignmentRepository.findByIdAndNotDeleted(request.classSubjectAssignmentId()).orElse(null);
            if (csa == null) {
                conflicts.add(new ScheduleConflictResponse(
                        ScheduleConflictType.CLASS_SUBJECT_CONFLICT,
                        ConflictSeverity.ERROR,
                        "Class subject assignment not found with ID: " + request.classSubjectAssignmentId(),
                        excludeEntryId, null, request.periodId(), request.dayOfWeek(), request.classSubjectAssignmentId()
                ));
            } else {
                if (csa.getStatus() != ClassSubjectAssignmentStatus.ACTIVE) {
                    conflicts.add(new ScheduleConflictResponse(
                            ScheduleConflictType.CLASS_SUBJECT_CONFLICT,
                            ConflictSeverity.ERROR,
                            "Class subject assignment is not ACTIVE (current status: " + csa.getStatus() + ")",
                            excludeEntryId, null, request.periodId(), request.dayOfWeek(), csa.getId()
                    ));
                }
                if (!csa.getAcademicYear().getId().equals(timetable.getAcademicYear().getId())) {
                    conflicts.add(new ScheduleConflictResponse(
                            ScheduleConflictType.ACADEMIC_YEAR_CONFLICT,
                            ConflictSeverity.ERROR,
                            "Class subject assignment belongs to a different academic year",
                            excludeEntryId, null, request.periodId(), request.dayOfWeek(), csa.getId()
                    ));
                }
            }
        }

        // 5. Check Room status
        if (request.roomId() != null) {
            Room room = roomRepository.findByIdAndNotDeleted(request.roomId()).orElse(null);
            if (room == null) {
                conflicts.add(new ScheduleConflictResponse(
                        ScheduleConflictType.ROOM_CONFLICT,
                        ConflictSeverity.ERROR,
                        "Room not found with ID: " + request.roomId(),
                        excludeEntryId, null, request.periodId(), request.dayOfWeek(), request.roomId()
                ));
            } else if (room.getStatus() != RoomStatus.ACTIVE) {
                conflicts.add(new ScheduleConflictResponse(
                        ScheduleConflictType.ROOM_CONFLICT,
                        ConflictSeverity.ERROR,
                        "Room '" + room.getName() + "' is not ACTIVE (status: " + room.getStatus() + ")",
                        excludeEntryId, null, request.periodId(), request.dayOfWeek(), room.getId()
                ));
            }
        }

        // 6. Teacher Conflict Check (Teacher double-booking)
        if (effectiveTeacherId != null && period != null && period.getPeriodType() == PeriodType.TEACHING) {
            List<TimetableEntry> teacherClashes = timetableEntryRepository.findTeacherClashes(
                    timetableId, effectiveTeacherId, request.dayOfWeek(), request.periodId(), excludeEntryId
            );
            for (TimetableEntry clash : teacherClashes) {
                String teacherName = getTeacherName(clash.getTeacher());
                conflicts.add(new ScheduleConflictResponse(
                        ScheduleConflictType.TEACHER_CONFLICT,
                        ConflictSeverity.ERROR,
                        teacherName + " is already scheduled for " + clash.getAcademicClass().getName() +
                                (clash.getSection() != null ? " - " + clash.getSection().getName() : "") +
                                " on " + request.dayOfWeek() + " in period " + period.getName(),
                        excludeEntryId, clash.getId(), request.periodId(), request.dayOfWeek(), effectiveTeacherId
                ));
            }
        }

        // 7. Class/Section Conflict Check (Class double-booking)
        if (request.academicClassId() != null && period != null && period.getPeriodType() == PeriodType.TEACHING) {
            List<TimetableEntry> classClashes = timetableEntryRepository.findClassClashes(
                    timetableId, request.academicClassId(), request.sectionId(), request.dayOfWeek(), request.periodId(), excludeEntryId
            );
            for (TimetableEntry clash : classClashes) {
                conflicts.add(new ScheduleConflictResponse(
                        ScheduleConflictType.CLASS_CONFLICT,
                        ConflictSeverity.ERROR,
                        "Class " + clash.getAcademicClass().getName() +
                                (clash.getSection() != null ? " - " + clash.getSection().getName() : "") +
                                " already has a session scheduled on " + request.dayOfWeek() + " in period " + period.getName(),
                        excludeEntryId, clash.getId(), request.periodId(), request.dayOfWeek(), request.academicClassId()
                ));
            }
        }

        // 8. Room Conflict Check (Room double-booking)
        if (request.roomId() != null && period != null) {
            List<TimetableEntry> roomClashes = timetableEntryRepository.findRoomClashes(
                    timetableId, request.roomId(), request.dayOfWeek(), request.periodId(), excludeEntryId
            );
            for (TimetableEntry clash : roomClashes) {
                conflicts.add(new ScheduleConflictResponse(
                        ScheduleConflictType.ROOM_CONFLICT,
                        ConflictSeverity.ERROR,
                        "Room is already occupied by " + clash.getAcademicClass().getName() +
                                (clash.getSection() != null ? " - " + clash.getSection().getName() : "") +
                                " on " + request.dayOfWeek() + " in period " + period.getName(),
                        excludeEntryId, clash.getId(), request.periodId(), request.dayOfWeek(), request.roomId()
                ));
            }
        }

        boolean valid = conflicts.stream().noneMatch(c -> c.severity() == ConflictSeverity.ERROR);
        return new ScheduleValidationResponse(valid, conflicts.size(), conflicts, 1);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public ScheduleValidationResponse validateTimetable(UUID timetableId) {
        Timetable timetable = timetableRepository.findByIdAndNotDeleted(timetableId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", timetableId));
        return validateTimetable(timetable);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public ScheduleValidationResponse validateTimetable(Timetable timetable) {
        List<TimetableEntry> entries = timetableEntryRepository.findAllByTimetableIdWithDetails(timetable.getId());
        List<ScheduleConflictResponse> allConflicts = new ArrayList<>();

        Set<String> teacherSlots = new HashSet<>();
        Set<String> classSlots = new HashSet<>();
        Set<String> roomSlots = new HashSet<>();

        for (TimetableEntry entry : entries) {
            if (entry.isDeleted()) {
                continue;
            }

            // 1. Check Period belonging
            if (entry.getPeriod() == null || !entry.getPeriod().getBellSchedule().getId().equals(timetable.getBellSchedule().getId())) {
                allConflicts.add(new ScheduleConflictResponse(
                        ScheduleConflictType.PERIOD_CONFLICT,
                        ConflictSeverity.ERROR,
                        "Entry " + entry.getEntryCode() + " references period not belonging to bell schedule",
                        entry.getId(), null, entry.getPeriod() != null ? entry.getPeriod().getId() : null, entry.getDayOfWeek(), null
                ));
            }

            // 2. Teacher Conflict
            if (entry.getTeacher() != null && entry.getPeriod() != null && entry.getPeriod().getPeriodType() == PeriodType.TEACHING) {
                String teacherKey = entry.getTeacher().getId() + ":" + entry.getDayOfWeek() + ":" + entry.getPeriod().getId();
                if (!teacherSlots.add(teacherKey)) {
                    allConflicts.add(new ScheduleConflictResponse(
                            ScheduleConflictType.TEACHER_CONFLICT,
                            ConflictSeverity.ERROR,
                            "Teacher " + getTeacherName(entry.getTeacher()) + " has multiple bookings on " + entry.getDayOfWeek() + " during period " + entry.getPeriod().getName(),
                            entry.getId(), null, entry.getPeriod().getId(), entry.getDayOfWeek(), entry.getTeacher().getId()
                    ));
                }
            }

            // 3. Class Conflict
            if (entry.getAcademicClass() != null && entry.getPeriod() != null && entry.getPeriod().getPeriodType() == PeriodType.TEACHING) {
                String classKey = entry.getAcademicClass().getId() + ":" +
                        (entry.getSection() != null ? entry.getSection().getId() : "ALL") + ":" +
                        entry.getDayOfWeek() + ":" + entry.getPeriod().getId();
                if (!classSlots.add(classKey)) {
                    allConflicts.add(new ScheduleConflictResponse(
                            ScheduleConflictType.CLASS_CONFLICT,
                            ConflictSeverity.ERROR,
                            "Class " + entry.getAcademicClass().getName() +
                                    (entry.getSection() != null ? " - " + entry.getSection().getName() : "") +
                                    " has multiple entries on " + entry.getDayOfWeek() + " during period " + entry.getPeriod().getName(),
                            entry.getId(), null, entry.getPeriod().getId(), entry.getDayOfWeek(), entry.getAcademicClass().getId()
                    ));
                }
            }

            // 4. Room Conflict
            if (entry.getRoom() != null && entry.getPeriod() != null) {
                String roomKey = entry.getRoom().getId() + ":" + entry.getDayOfWeek() + ":" + entry.getPeriod().getId();
                if (!roomSlots.add(roomKey)) {
                    allConflicts.add(new ScheduleConflictResponse(
                            ScheduleConflictType.ROOM_CONFLICT,
                            ConflictSeverity.ERROR,
                            "Room " + entry.getRoom().getName() + " is double-booked on " + entry.getDayOfWeek() + " during period " + entry.getPeriod().getName(),
                            entry.getId(), null, entry.getPeriod().getId(), entry.getDayOfWeek(), entry.getRoom().getId()
                    ));
                }
            }
        }

        boolean valid = allConflicts.stream().noneMatch(c -> c.severity() == ConflictSeverity.ERROR);
        return new ScheduleValidationResponse(valid, allConflicts.size(), allConflicts, entries.size());
    }

    private String getTeacherName(Staff teacher) {
        if (teacher == null) {
            return "Teacher";
        }
        if (teacher.getTenantUser() != null && teacher.getTenantUser().getProfile() != null) {
            return teacher.getTenantUser().getProfile().getFullName();
        }
        return teacher.getStaffCode();
    }
}
