package com.vidya.tenantdata.timetable.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.SecurityUtils;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.assignment.repository.TeacherSubjectAssignmentRepository;
import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.staff.repository.StaffRepository;
import com.vidya.tenantdata.timetable.dto.CreateSubstitutionRequest;
import com.vidya.tenantdata.timetable.dto.SubstitutionResponse;
import com.vidya.tenantdata.timetable.dto.UpdateSubstitutionRequest;
import com.vidya.tenantdata.timetable.entity.BellSchedulePeriod;
import com.vidya.tenantdata.timetable.entity.Room;
import com.vidya.tenantdata.timetable.entity.Timetable;
import com.vidya.tenantdata.timetable.entity.TimetableEntry;
import com.vidya.tenantdata.timetable.entity.TimetableSubstitution;
import com.vidya.tenantdata.timetable.enums.RoomStatus;
import com.vidya.tenantdata.timetable.enums.SubstitutionStatus;
import com.vidya.tenantdata.timetable.enums.SubstitutionType;
import com.vidya.tenantdata.timetable.enums.TimetableStatus;
import com.vidya.tenantdata.timetable.mapper.TimetableSubstitutionMapper;
import com.vidya.tenantdata.timetable.repository.BellSchedulePeriodRepository;
import com.vidya.tenantdata.timetable.repository.RoomRepository;
import com.vidya.tenantdata.timetable.repository.TimetableEntryRepository;
import com.vidya.tenantdata.timetable.repository.TimetableRepository;
import com.vidya.tenantdata.timetable.repository.TimetableSubstitutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimetableSubstitutionServiceImpl implements TimetableSubstitutionService {

    private final TimetableSubstitutionRepository substitutionRepository;
    private final TimetableRepository timetableRepository;
    private final TimetableEntryRepository timetableEntryRepository;
    private final BellSchedulePeriodRepository bellSchedulePeriodRepository;
    private final AcademicClassRepository academicClassRepository;
    private final SectionRepository sectionRepository;
    private final StaffRepository staffRepository;
    private final RoomRepository roomRepository;
    private final TeacherSubjectAssignmentRepository teacherSubjectAssignmentRepository;
    private final TimetableSubstitutionMapper substitutionMapper;

    private Clock clock = Clock.systemDefaultZone();

    @Autowired(required = false)
    public void setClock(Clock clock) {
        if (clock != null) {
            this.clock = clock;
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public SubstitutionResponse createSubstitution(CreateSubstitutionRequest request) {
        log.info("Creating substitution for timetable [{}] on date [{}]", request.timetableId(), request.substitutionDate());

        // 1. Date validation (cannot be in the past)
        LocalDate today = LocalDate.now(clock);
        if (request.substitutionDate().isBefore(today)) {
            throw new BusinessException("Substitution date cannot be in the past. Date requested: " + request.substitutionDate());
        }

        // 2. Timetable validation (must be ACTIVE)
        Timetable timetable = timetableRepository.findByIdAndNotDeleted(request.timetableId())
                .orElseThrow(() -> new ResourceNotFoundException("Timetable", request.timetableId()));

        if (timetable.getStatus() != TimetableStatus.ACTIVE) {
            throw new BusinessException("Substitutions can only be created for ACTIVE timetables. Current status: " + timetable.getStatus());
        }

        // Effective date bounds validation
        if (timetable.getEffectiveFrom() != null && request.substitutionDate().isBefore(timetable.getEffectiveFrom())) {
            throw new BusinessException("Substitution date is before timetable effective start date: " + timetable.getEffectiveFrom());
        }
        if (timetable.getEffectiveTo() != null && request.substitutionDate().isAfter(timetable.getEffectiveTo())) {
            throw new BusinessException("Substitution date is after timetable effective end date: " + timetable.getEffectiveTo());
        }

        AcademicYear academicYear = timetable.getAcademicYear();
        if (academicYear != null) {
            if (academicYear.getStartDate() != null && request.substitutionDate().isBefore(academicYear.getStartDate())) {
                throw new BusinessException("Substitution date is before academic year start date: " + academicYear.getStartDate());
            }
            if (academicYear.getEndDate() != null && request.substitutionDate().isAfter(academicYear.getEndDate())) {
                throw new BusinessException("Substitution date is after academic year end date: " + academicYear.getEndDate());
            }
        }

        // 3. Period & Class/Section validation
        BellSchedulePeriod period = bellSchedulePeriodRepository.findByIdAndNotDeleted(request.periodId())
                .orElseThrow(() -> new ResourceNotFoundException("BellSchedulePeriod", request.periodId()));

        AcademicClass academicClass = academicClassRepository.findByIdAndNotDeleted(request.academicClassId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicClass", request.academicClassId()));

        Section section = null;
        if (request.sectionId() != null) {
            section = sectionRepository.findByIdAndNotDeleted(request.sectionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Section", request.sectionId()));
            if (!section.getAcademicClass().getId().equals(academicClass.getId())) {
                throw new BusinessException("Section does not belong to specified academic class.");
            }
        }

        // 4. Verify slot matching active timetable entry
        DayOfWeek dayOfWeek = request.substitutionDate().getDayOfWeek();
        UUID sectionId = section != null ? section.getId() : null;
        List<TimetableEntry> matchingEntries = timetableEntryRepository.findActiveSlotEntries(
                timetable.getId(), dayOfWeek, period.getId(), academicClass.getId(), sectionId
        );

        if (matchingEntries.isEmpty()) {
            throw new BusinessException(String.format(
                    "No active timetable entry exists for timetable [%s] on %s period [%s] for class [%s]%s",
                    timetable.getTimetableCode(), dayOfWeek, period.getName(), academicClass.getName(),
                    section != null ? " section [" + section.getName() + "]" : ""
            ));
        }

        TimetableEntry baseEntry = matchingEntries.getFirst();

        // 5. Existing active substitution for this slot check
        List<TimetableSubstitution> existingSlotSubs = substitutionRepository.findActiveSlotSubstitutions(
                timetable.getId(), request.substitutionDate(), period.getId(), academicClass.getId(), sectionId
        );
        if (!existingSlotSubs.isEmpty()) {
            throw new DuplicateResourceException(String.format(
                    "Active substitution [%s] already exists for slot on %s period [%s].",
                    existingSlotSubs.getFirst().getSubstitutionCode(), request.substitutionDate(), period.getName()
            ));
        }

        // 6. Substitution type rules & clash validation
        Staff substituteTeacher = null;
        Room substituteRoom = null;

        if (request.substitutionType() == SubstitutionType.TEACHER_SUBSTITUTE) {
            if (request.substituteTeacherId() == null) {
                throw new BusinessException("TEACHER_SUBSTITUTE substitution requires substituteTeacherId.");
            }
            substituteTeacher = staffRepository.findByIdAndNotDeleted(request.substituteTeacherId())
                    .orElseThrow(() -> new ResourceNotFoundException("Staff", request.substituteTeacherId()));

            if (substituteTeacher.getEmploymentStatus() != com.vidya.tenantdata.staff.enums.EmploymentStatus.ACTIVE) {
                throw new BusinessException("Substitute teacher must be ACTIVE staff.");
            }

            if (baseEntry.getTeacher() != null && baseEntry.getTeacher().getId().equals(substituteTeacher.getId())) {
                throw new BusinessException("Substitute teacher cannot be the same as the original teacher.");
            }

            // Check if substitute teacher is scheduled to teach regular entry at that weekday and period
            List<TimetableEntry> teacherClashes = timetableEntryRepository.findTeacherClashes(
                    timetable.getId(), substituteTeacher.getId(), dayOfWeek, period.getId(), null
            );
            if (!teacherClashes.isEmpty()) {
                // Check if those regular entries are freed on this date by CLASS_CANCELLED or TEACHER_SUBSTITUTE
                List<TimetableSubstitution> freed = substitutionRepository.findActiveFreedTeacherEntries(
                        timetable.getId(), request.substitutionDate(), period.getId(), substituteTeacher.getId()
                );
                if (freed.size() < teacherClashes.size()) {
                    throw new DuplicateResourceException(String.format(
                            "Substitute teacher [%s] is already scheduled to teach in entry [%s] on %s period [%s].",
                            substituteTeacher.getStaffCode(), teacherClashes.getFirst().getEntryCode(), dayOfWeek, period.getName()
                    ));
                }
            }

            // Check if substitute teacher already has an active substitution on that date and period
            List<TimetableSubstitution> activeTeacherSubs = substitutionRepository.findActiveTeacherSubstitutions(
                    request.substitutionDate(), period.getId(), substituteTeacher.getId(), null
            );
            if (!activeTeacherSubs.isEmpty()) {
                throw new DuplicateResourceException(String.format(
                        "Substitute teacher [%s] is already assigned as a substitute on %s period [%s] in substitution [%s].",
                        substituteTeacher.getStaffCode(), request.substitutionDate(), period.getName(), activeTeacherSubs.getFirst().getSubstitutionCode()
                ));
            }

        } else if (request.substitutionType() == SubstitutionType.ROOM_CHANGE) {
            if (request.substituteRoomId() == null) {
                throw new BusinessException("ROOM_CHANGE substitution requires substituteRoomId.");
            }
            substituteRoom = roomRepository.findByIdAndNotDeleted(request.substituteRoomId())
                    .orElseThrow(() -> new ResourceNotFoundException("Room", request.substituteRoomId()));

            if (substituteRoom.getStatus() != RoomStatus.ACTIVE) {
                throw new BusinessException("Substitute room must be in ACTIVE status.");
            }

            // Check regular entry clash
            List<TimetableEntry> roomClashes = timetableEntryRepository.findRoomClashes(
                    timetable.getId(), substituteRoom.getId(), dayOfWeek, period.getId(), null
            );
            if (!roomClashes.isEmpty()) {
                throw new DuplicateResourceException(String.format(
                        "Substitute room [%s] is already booked by entry [%s] on %s period [%s].",
                        substituteRoom.getName(), roomClashes.getFirst().getEntryCode(), dayOfWeek, period.getName()
                ));
            }

            // Check substitution room clash
            List<TimetableSubstitution> activeRoomSubs = substitutionRepository.findActiveRoomSubstitutions(
                    request.substitutionDate(), period.getId(), substituteRoom.getId(), null
            );
            if (!activeRoomSubs.isEmpty()) {
                throw new DuplicateResourceException(String.format(
                        "Substitute room [%s] is already booked by substitution [%s] on %s period [%s].",
                        substituteRoom.getName(), activeRoomSubs.getFirst().getSubstitutionCode(), request.substitutionDate(), period.getName()
                ));
            }

        } else if (request.substitutionType() == SubstitutionType.CLASS_CANCELLED) {
            if (request.substituteTeacherId() != null || request.substituteRoomId() != null) {
                throw new BusinessException("CLASS_CANCELLED substitution must not have substitute teacher or room specified.");
            }
        }

        // 7. Check subject qualification (informational)
        boolean sameSubjectQualified = false;
        if (substituteTeacher != null && baseEntry.getSubject() != null && academicYear != null) {
            sameSubjectQualified = teacherSubjectAssignmentRepository.isTeacherQualifiedForSubject(
                    substituteTeacher.getId(), baseEntry.getSubject().getId(), academicYear.getId()
            );
        }

        // 8. Construct and persist
        String subCode = "SUB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        TimetableSubstitution substitution = new TimetableSubstitution(
                subCode,
                timetable,
                request.substitutionDate(),
                period,
                academicClass,
                section,
                request.substitutionType(),
                baseEntry.getTeacher(),
                baseEntry.getSubject(),
                substituteTeacher,
                substituteRoom,
                request.reason()
        );

        UUID currentUserId = SecurityUtils.getCurrentUserId().orElse(null);
        substitution.setCreatedBy(currentUserId);
        substitution.setUpdatedBy(currentUserId);

        try {
            TimetableSubstitution saved = substitutionRepository.saveAndFlush(substitution);
            return substitutionMapper.toResponse(saved, sameSubjectQualified);
        } catch (DataIntegrityViolationException e) {
            log.warn("Database constraint violation during substitution creation: {}", e.getMessage());
            throw new DuplicateResourceException("Substitution conflict detected: a substitution already exists for this slot, teacher, or room on " + request.substitutionDate());
        }
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public SubstitutionResponse getSubstitutionById(UUID id) {
        TimetableSubstitution sub = substitutionRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("TimetableSubstitution", id));

        boolean sameSubjectQualified = false;
        if (sub.getSubstituteTeacher() != null && sub.getOriginalSubject() != null && sub.getTimetable() != null && sub.getTimetable().getAcademicYear() != null) {
            sameSubjectQualified = teacherSubjectAssignmentRepository.isTeacherQualifiedForSubject(
                    sub.getSubstituteTeacher().getId(), sub.getOriginalSubject().getId(), sub.getTimetable().getAcademicYear().getId()
            );
        }

        return substitutionMapper.toResponse(sub, sameSubjectQualified);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Page<SubstitutionResponse> searchSubstitutions(
            LocalDate dateFrom,
            LocalDate dateTo,
            UUID sectionId,
            UUID teacherId,
            SubstitutionStatus status,
            Pageable pageable
    ) {
        return substitutionRepository.searchSubstitutions(dateFrom, dateTo, sectionId, teacherId, status, pageable)
                .map(sub -> {
                    boolean sameSubjectQualified = false;
                    if (sub.getSubstituteTeacher() != null && sub.getOriginalSubject() != null && sub.getTimetable() != null && sub.getTimetable().getAcademicYear() != null) {
                        sameSubjectQualified = teacherSubjectAssignmentRepository.isTeacherQualifiedForSubject(
                                sub.getSubstituteTeacher().getId(), sub.getOriginalSubject().getId(), sub.getTimetable().getAcademicYear().getId()
                        );
                    }
                    return substitutionMapper.toResponse(sub, sameSubjectQualified);
                });
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public SubstitutionResponse updateSubstitution(UUID id, UpdateSubstitutionRequest request) {
        log.info("Updating substitution [{}]", id);

        TimetableSubstitution sub = substitutionRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("TimetableSubstitution", id));

        if (sub.getStatus() != SubstitutionStatus.ACTIVE) {
            throw new BusinessException("Only ACTIVE substitutions can be updated. Current status: " + sub.getStatus());
        }

        DayOfWeek dayOfWeek = sub.getSubstitutionDate().getDayOfWeek();

        if (sub.getSubstitutionType() == SubstitutionType.TEACHER_SUBSTITUTE) {
            if (request.substituteTeacherId() != null && (sub.getSubstituteTeacher() == null || !sub.getSubstituteTeacher().getId().equals(request.substituteTeacherId()))) {
                Staff newTeacher = staffRepository.findByIdAndNotDeleted(request.substituteTeacherId())
                        .orElseThrow(() -> new ResourceNotFoundException("Staff", request.substituteTeacherId()));

                if (newTeacher.getEmploymentStatus() != com.vidya.tenantdata.staff.enums.EmploymentStatus.ACTIVE) {
                    throw new BusinessException("Substitute teacher must be ACTIVE staff.");
                }

                // Check regular entry clash
                List<TimetableEntry> teacherClashes = timetableEntryRepository.findTeacherClashes(
                        sub.getTimetable().getId(), newTeacher.getId(), dayOfWeek, sub.getPeriod().getId(), null
                );
                if (!teacherClashes.isEmpty()) {
                    List<TimetableSubstitution> freed = substitutionRepository.findActiveFreedTeacherEntries(
                            sub.getTimetable().getId(), sub.getSubstitutionDate(), sub.getPeriod().getId(), newTeacher.getId()
                    );
                    if (freed.size() < teacherClashes.size()) {
                        throw new DuplicateResourceException(String.format(
                                "Substitute teacher [%s] is already scheduled to teach on %s period [%s].",
                                newTeacher.getStaffCode(), dayOfWeek, sub.getPeriod().getName()
                        ));
                    }
                }

                // Check active substitution clash
                List<TimetableSubstitution> activeTeacherSubs = substitutionRepository.findActiveTeacherSubstitutions(
                        sub.getSubstitutionDate(), sub.getPeriod().getId(), newTeacher.getId(), sub.getId()
                );
                if (!activeTeacherSubs.isEmpty()) {
                    throw new DuplicateResourceException(String.format(
                            "Substitute teacher [%s] is already assigned as substitute on %s period [%s].",
                            newTeacher.getStaffCode(), sub.getSubstitutionDate(), sub.getPeriod().getName()
                    ));
                }

                sub.setSubstituteTeacher(newTeacher);
            }
        } else if (sub.getSubstitutionType() == SubstitutionType.ROOM_CHANGE) {
            if (request.substituteRoomId() != null && (sub.getSubstituteRoom() == null || !sub.getSubstituteRoom().getId().equals(request.substituteRoomId()))) {
                Room newRoom = roomRepository.findByIdAndNotDeleted(request.substituteRoomId())
                        .orElseThrow(() -> new ResourceNotFoundException("Room", request.substituteRoomId()));

                if (newRoom.getStatus() != RoomStatus.ACTIVE) {
                    throw new BusinessException("Substitute room must be in ACTIVE status.");
                }

                List<TimetableEntry> roomClashes = timetableEntryRepository.findRoomClashes(
                        sub.getTimetable().getId(), newRoom.getId(), dayOfWeek, sub.getPeriod().getId(), null
                );
                if (!roomClashes.isEmpty()) {
                    throw new DuplicateResourceException(String.format(
                            "Substitute room [%s] is already booked on %s period [%s].",
                            newRoom.getName(), dayOfWeek, sub.getPeriod().getName()
                    ));
                }

                List<TimetableSubstitution> activeRoomSubs = substitutionRepository.findActiveRoomSubstitutions(
                        sub.getSubstitutionDate(), sub.getPeriod().getId(), newRoom.getId(), sub.getId()
                );
                if (!activeRoomSubs.isEmpty()) {
                    throw new DuplicateResourceException(String.format(
                            "Substitute room [%s] is already booked on %s period [%s].",
                            newRoom.getName(), sub.getSubstitutionDate(), sub.getPeriod().getName()
                    ));
                }

                sub.setSubstituteRoom(newRoom);
            }
        }

        if (request.reason() != null) {
            sub.setReason(request.reason());
        }

        sub.setUpdatedBy(SecurityUtils.getCurrentUserId().orElse(null));

        try {
            TimetableSubstitution saved = substitutionRepository.saveAndFlush(sub);
            boolean sameSubjectQualified = false;
            if (saved.getSubstituteTeacher() != null && saved.getOriginalSubject() != null && saved.getTimetable() != null && saved.getTimetable().getAcademicYear() != null) {
                sameSubjectQualified = teacherSubjectAssignmentRepository.isTeacherQualifiedForSubject(
                        saved.getSubstituteTeacher().getId(), saved.getOriginalSubject().getId(), saved.getTimetable().getAcademicYear().getId()
                );
            }
            return substitutionMapper.toResponse(saved, sameSubjectQualified);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException("Substitution conflict detected: a substitution already exists for this slot, teacher, or room.");
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public SubstitutionResponse cancelSubstitution(UUID id) {
        log.info("Cancelling substitution [{}]", id);

        TimetableSubstitution sub = substitutionRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("TimetableSubstitution", id));

        if (sub.getStatus() != SubstitutionStatus.ACTIVE) {
            throw new BusinessException("Only ACTIVE substitutions can be cancelled. Current status: " + sub.getStatus());
        }

        sub.cancel();
        sub.setUpdatedBy(SecurityUtils.getCurrentUserId().orElse(null));
        TimetableSubstitution saved = substitutionRepository.save(sub);

        return substitutionMapper.toResponse(saved, false);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteSubstitution(UUID id) {
        log.info("Soft deleting substitution [{}]", id);

        TimetableSubstitution sub = substitutionRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("TimetableSubstitution", id));

        sub.softDelete();
        sub.setUpdatedBy(SecurityUtils.getCurrentUserId().orElse(null));
        substitutionRepository.save(sub);
    }
}
