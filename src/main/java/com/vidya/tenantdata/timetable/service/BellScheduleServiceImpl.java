package com.vidya.tenantdata.timetable.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.timetable.dto.BellScheduleResponse;
import com.vidya.tenantdata.timetable.dto.CreateBellScheduleRequest;
import com.vidya.tenantdata.timetable.dto.CreatePeriodRequest;
import com.vidya.tenantdata.timetable.dto.PeriodResponse;
import com.vidya.tenantdata.timetable.dto.UpdateBellScheduleRequest;
import com.vidya.tenantdata.timetable.dto.UpdateBellScheduleStatusRequest;
import com.vidya.tenantdata.timetable.dto.UpdatePeriodRequest;
import com.vidya.tenantdata.timetable.entity.BellSchedule;
import com.vidya.tenantdata.timetable.entity.BellSchedulePeriod;
import com.vidya.tenantdata.timetable.enums.BellScheduleStatus;
import com.vidya.tenantdata.timetable.mapper.BellScheduleMapper;
import com.vidya.tenantdata.timetable.repository.BellSchedulePeriodRepository;
import com.vidya.tenantdata.timetable.repository.BellScheduleRepository;
import com.vidya.tenantdata.timetable.repository.TimetableEntryRepository;
import com.vidya.tenantdata.timetable.repository.TimetableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BellScheduleServiceImpl implements BellScheduleService {

    private final BellScheduleRepository bellScheduleRepository;
    private final BellSchedulePeriodRepository bellSchedulePeriodRepository;
    private final TimetableRepository timetableRepository;
    private final TimetableEntryRepository timetableEntryRepository;
    private final BellScheduleMapper bellScheduleMapper;

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public BellScheduleResponse createBellSchedule(CreateBellScheduleRequest request) {
        log.info("Creating bell schedule: [{}]", request.name());

        String name = request.name().trim();
        if (bellScheduleRepository.existsByNameIgnoreCaseAndNotDeleted(name, null)) {
            throw new DuplicateResourceException("BellSchedule", "name", name);
        }

        validatePeriods(request.periods());

        if (request.isDefault()) {
            clearPreviousDefaultAndFlush();
        }

        String scheduleCode = CodeGenerator.bellScheduleCode();
        while (bellScheduleRepository.existsByScheduleCode(scheduleCode)) {
            scheduleCode = CodeGenerator.bellScheduleCode();
        }

        BellSchedule schedule = new BellSchedule(
                scheduleCode,
                name,
                request.description() != null ? request.description().trim() : null,
                request.isDefault()
        );
        UUID currentUserId = com.vidya.common.util.SecurityUtils.getCurrentUserId().orElse(null);
        schedule.setCreatedBy(currentUserId);
        schedule.setUpdatedBy(currentUserId);

        if (request.periods() != null) {
            for (CreatePeriodRequest pReq : request.periods()) {
                String periodCode = CodeGenerator.periodCode();
                while (bellSchedulePeriodRepository.existsByPeriodCode(periodCode)) {
                    periodCode = CodeGenerator.periodCode();
                }
                BellSchedulePeriod period = new BellSchedulePeriod(
                        periodCode,
                        schedule,
                        pReq.name().trim(),
                        pReq.periodSequence(),
                        pReq.startTime(),
                        pReq.endTime(),
                        pReq.periodType()
                );
                period.setCreatedBy(currentUserId);
                period.setUpdatedBy(currentUserId);
                schedule.addPeriod(period);
            }
        }

        try {
            BellSchedule saved = bellScheduleRepository.save(schedule);
            log.info("Bell schedule created successfully: [{}]", saved.getScheduleCode());
            return bellScheduleMapper.toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            log.warn("Database constraint violation creating bell schedule: {}", e.getMessage());
            throw new DuplicateResourceException("BellSchedule", "name", name);
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public BellScheduleResponse updateBellSchedule(UUID id, UpdateBellScheduleRequest request) {
        log.info("Updating bell schedule: [{}]", id);

        BellSchedule schedule = bellScheduleRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("BellSchedule", id));

        String name = request.name().trim();
        if (bellScheduleRepository.existsByNameIgnoreCaseAndNotDeleted(name, id)) {
            throw new DuplicateResourceException("BellSchedule", "name", name);
        }

        if (request.status() == BellScheduleStatus.DELETED) {
            throw new InvalidStateTransitionException("BellSchedule", schedule.getStatus().name(), "DELETED (Use soft delete endpoint)");
        }

        if (request.status() != BellScheduleStatus.ACTIVE && request.status() != schedule.getStatus()) {
            validateScheduleNotInActiveTimetable(id, schedule.getScheduleCode(), "change status of");
        }

        if (request.periods() != null && !request.periods().isEmpty()) {
            validatePeriods(request.periods());
        }

        if (request.isDefault() && !schedule.isDefault()) {
            clearPreviousDefaultAndFlush();
        }

        UUID currentUserId = com.vidya.common.util.SecurityUtils.getCurrentUserId().orElse(null);
        schedule.setName(name);
        schedule.setDescription(request.description() != null ? request.description().trim() : null);
        schedule.setDefault(request.isDefault());
        schedule.updateStatus(request.status());
        schedule.setUpdatedBy(currentUserId);

        if (request.periods() != null && !request.periods().isEmpty()) {
            // Check if any existing periods used in active timetables are being modified incompatibly
            for (BellSchedulePeriod existing : schedule.getPeriods()) {
                if (!existing.isDeleted() && timetableEntryRepository.isPeriodUsedInActiveTimetables(existing.getId())) {
                    boolean stillMatchesTiming = request.periods().stream().anyMatch(p ->
                            p.startTime().equals(existing.getStartTime()) && p.endTime().equals(existing.getEndTime())
                    );
                    if (!stillMatchesTiming) {
                        throw new BusinessException(String.format(
                                "Cannot replace periods because period [%s] (%s) is assigned in active timetable entries.",
                                existing.getPeriodCode(), existing.getName()
                        ));
                    }
                }
            }

            schedule.getPeriods().clear();
            for (CreatePeriodRequest pReq : request.periods()) {
                String periodCode = CodeGenerator.periodCode();
                while (bellSchedulePeriodRepository.existsByPeriodCode(periodCode)) {
                    periodCode = CodeGenerator.periodCode();
                }
                BellSchedulePeriod period = new BellSchedulePeriod(
                        periodCode,
                        schedule,
                        pReq.name().trim(),
                        pReq.periodSequence(),
                        pReq.startTime(),
                        pReq.endTime(),
                        pReq.periodType()
                );
                schedule.addPeriod(period);
            }
        }

        try {
            BellSchedule updated = bellScheduleRepository.save(schedule);
            log.info("Bell schedule updated successfully: [{}]", updated.getScheduleCode());
            return bellScheduleMapper.toResponse(updated);
        } catch (DataIntegrityViolationException e) {
            log.warn("Database constraint violation updating bell schedule: {}", e.getMessage());
            throw new DuplicateResourceException("BellSchedule", "name", name);
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public BellScheduleResponse updateBellScheduleStatus(UUID id, UpdateBellScheduleStatusRequest request) {
        log.info("Updating status of bell schedule [{}] to [{}]", id, request.status());

        BellSchedule schedule = bellScheduleRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("BellSchedule", id));

        if (request.status() == BellScheduleStatus.DELETED) {
            throw new InvalidStateTransitionException("BellSchedule", schedule.getStatus().name(), "DELETED (Use soft delete endpoint)");
        }

        if (request.status() != BellScheduleStatus.ACTIVE && request.status() != schedule.getStatus()) {
            validateScheduleNotInActiveTimetable(id, schedule.getScheduleCode(), "change status of");
        }

        UUID currentUserId = com.vidya.common.util.SecurityUtils.getCurrentUserId().orElse(null);
        schedule.updateStatus(request.status());
        schedule.setUpdatedBy(currentUserId);
        BellSchedule updated = bellScheduleRepository.save(schedule);
        log.info("Bell schedule status updated successfully: [{}] -> [{}]", updated.getScheduleCode(), updated.getStatus());
        return bellScheduleMapper.toResponse(updated);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public BellScheduleResponse getBellScheduleById(UUID id) {
        BellSchedule schedule = bellScheduleRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("BellSchedule", id));
        return bellScheduleMapper.toResponse(schedule);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public BellScheduleResponse getBellScheduleByCode(String scheduleCode) {
        BellSchedule schedule = bellScheduleRepository.findByScheduleCodeAndNotDeleted(scheduleCode)
                .orElseThrow(() -> new ResourceNotFoundException("BellSchedule", scheduleCode));
        return bellScheduleMapper.toResponse(schedule);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Page<BellScheduleResponse> searchBellSchedules(String search, BellScheduleStatus status, Pageable pageable) {
        String query = (search != null && !search.isBlank()) ? search.trim() : null;
        return bellScheduleRepository.searchSchedules(query, status, pageable)
                .map(bellScheduleMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public BellScheduleResponse getDefaultSchedule() {
        BellSchedule schedule = bellScheduleRepository.findDefaultSchedule()
                .orElseThrow(() -> new ResourceNotFoundException("DefaultBellSchedule", "DEFAULT"));
        return bellScheduleMapper.toResponse(schedule);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public BellScheduleResponse setDefaultSchedule(UUID id) {
        log.info("Setting bell schedule [{}] as default", id);
        BellSchedule target = bellScheduleRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("BellSchedule", id));

        if (target.isDefault()) {
            return bellScheduleMapper.toResponse(target);
        }

        if (target.getStatus() != BellScheduleStatus.ACTIVE) {
            throw new BusinessException("Only active bell schedules can be set as default.");
        }

        clearPreviousDefaultAndFlush();

        target.setDefault(true);
        target.setUpdatedBy(com.vidya.common.util.SecurityUtils.getCurrentUserId().orElse(null));
        try {
            BellSchedule saved = bellScheduleRepository.saveAndFlush(target);
            log.info("Bell schedule [{}] set as default successfully", saved.getScheduleCode());
            return bellScheduleMapper.toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            log.warn("Constraint violation setting default schedule: {}", e.getMessage());
            throw new DuplicateResourceException("BellSchedule", "isDefault", true);
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public BellScheduleResponse cloneSchedule(UUID id) {
        log.info("Cloning bell schedule [{}]", id);
        BellSchedule source = bellScheduleRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("BellSchedule", id));

        String baseName = source.getName() + " (Copy)";
        String clonedName = baseName;
        int suffix = 2;
        while (bellScheduleRepository.existsByNameIgnoreCaseAndNotDeleted(clonedName, null)) {
            clonedName = source.getName() + " (Copy " + suffix + ")";
            suffix++;
        }

        String scheduleCode = CodeGenerator.bellScheduleCode();
        while (bellScheduleRepository.existsByScheduleCode(scheduleCode)) {
            scheduleCode = CodeGenerator.bellScheduleCode();
        }

        UUID currentUserId = com.vidya.common.util.SecurityUtils.getCurrentUserId().orElse(null);
        BellSchedule cloned = new BellSchedule(
                scheduleCode,
                clonedName,
                source.getDescription(),
                false // cloned is never default
        );
        cloned.setCreatedBy(currentUserId);
        cloned.setUpdatedBy(currentUserId);

        for (BellSchedulePeriod srcPeriod : source.getPeriods()) {
            if (!srcPeriod.isDeleted()) {
                String periodCode = CodeGenerator.periodCode();
                while (bellSchedulePeriodRepository.existsByPeriodCode(periodCode)) {
                    periodCode = CodeGenerator.periodCode();
                }
                BellSchedulePeriod clonedPeriod = new BellSchedulePeriod(
                        periodCode,
                        cloned,
                        srcPeriod.getName(),
                        srcPeriod.getPeriodSequence(),
                        srcPeriod.getStartTime(),
                        srcPeriod.getEndTime(),
                        srcPeriod.getPeriodType()
                );
                clonedPeriod.setCreatedBy(currentUserId);
                clonedPeriod.setUpdatedBy(currentUserId);
                cloned.addPeriod(clonedPeriod);
            }
        }

        BellSchedule saved = bellScheduleRepository.save(cloned);
        log.info("Bell schedule cloned successfully: [{}] -> [{}] ({})", source.getScheduleCode(), saved.getScheduleCode(), saved.getName());
        return bellScheduleMapper.toResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteBellSchedule(UUID id) {
        log.info("Deleting bell schedule: [{}]", id);
        BellSchedule schedule = bellScheduleRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("BellSchedule", id));

        validateScheduleNotInActiveTimetable(id, schedule.getScheduleCode(), "delete");

        schedule.softDelete();
        schedule.setUpdatedBy(com.vidya.common.util.SecurityUtils.getCurrentUserId().orElse(null));
        bellScheduleRepository.save(schedule);
        log.info("Bell schedule deleted successfully: [{}]", id);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public PeriodResponse addPeriod(UUID scheduleId, CreatePeriodRequest request) {
        log.info("Adding period [{}] to schedule [{}]", request.name(), scheduleId);
        BellSchedule schedule = bellScheduleRepository.findByIdAndNotDeleted(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("BellSchedule", scheduleId));

        if (!request.startTime().isBefore(request.endTime())) {
            throw new BusinessException("Period start time (" + request.startTime() + ") must be before end time (" + request.endTime() + ")");
        }

        List<BellSchedulePeriod> existingPeriods = bellSchedulePeriodRepository.findByBellScheduleIdAndNotDeletedOrderBySequence(scheduleId);

        // Check sequence clash & time overlap
        for (BellSchedulePeriod existing : existingPeriods) {
            if (existing.getPeriodSequence() == request.periodSequence()) {
                throw new DuplicateResourceException("BellSchedulePeriod", "periodSequence", request.periodSequence());
            }

            // Check overlap
            if (isTimeOverlapping(request.startTime(), request.endTime(), existing.getStartTime(), existing.getEndTime())) {
                throw new BusinessException(String.format(
                        "Overlapping period timing: new period (%s - %s) overlaps with '%s' (%s - %s)",
                        request.startTime(), request.endTime(), existing.getName(), existing.getStartTime(), existing.getEndTime()
                ));
            }

            // Check sequence consistency with time
            if (request.startTime().isAfter(existing.getStartTime()) && request.periodSequence() <= existing.getPeriodSequence()) {
                throw new BusinessException(String.format(
                        "Period sequence [%d] must be greater than previous period sequence [%d]",
                        request.periodSequence(), existing.getPeriodSequence()
                ));
            }
            if (request.startTime().isBefore(existing.getStartTime()) && request.periodSequence() >= existing.getPeriodSequence()) {
                throw new BusinessException(String.format(
                        "Period sequence [%d] must be less than subsequent period sequence [%d]",
                        request.periodSequence(), existing.getPeriodSequence()
                ));
            }
        }

        String periodCode = CodeGenerator.periodCode();
        while (bellSchedulePeriodRepository.existsByPeriodCode(periodCode)) {
            periodCode = CodeGenerator.periodCode();
        }

        UUID currentUserId = com.vidya.common.util.SecurityUtils.getCurrentUserId().orElse(null);
        BellSchedulePeriod period = new BellSchedulePeriod(
                periodCode,
                schedule,
                request.name().trim(),
                request.periodSequence(),
                request.startTime(),
                request.endTime(),
                request.periodType()
        );
        period.setCreatedBy(currentUserId);
        period.setUpdatedBy(currentUserId);

        try {
            BellSchedulePeriod saved = bellSchedulePeriodRepository.save(period);
            log.info("Period added successfully: [{}]", saved.getPeriodCode());
            return bellScheduleMapper.toPeriodResponse(saved);
        } catch (DataIntegrityViolationException e) {
            log.warn("Constraint violation adding period: {}", e.getMessage());
            throw new DuplicateResourceException("BellSchedulePeriod", "periodSequence", request.periodSequence());
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public PeriodResponse updatePeriod(UUID scheduleId, UUID periodId, UpdatePeriodRequest request) {
        log.info("Updating period [{}] in schedule [{}]", periodId, scheduleId);

        BellSchedulePeriod period = bellSchedulePeriodRepository.findByIdAndNotDeleted(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("BellSchedulePeriod", periodId));

        if (!period.getBellSchedule().getId().equals(scheduleId)) {
            throw new BusinessException("Period does not belong to specified schedule.");
        }

        if (!request.startTime().isBefore(request.endTime())) {
            throw new BusinessException("Period start time (" + request.startTime() + ") must be before end time (" + request.endTime() + ")");
        }

        boolean timeChanged = !request.startTime().equals(period.getStartTime()) || !request.endTime().equals(period.getEndTime());
        if (timeChanged && timetableEntryRepository.isPeriodUsedInActiveTimetables(periodId)) {
            throw new BusinessException(String.format(
                    "Cannot modify start/end times of period [%s] because it is currently assigned in active timetable entries.",
                    period.getPeriodCode()
            ));
        }

        List<BellSchedulePeriod> otherPeriods = bellSchedulePeriodRepository.findByBellScheduleIdAndNotDeletedOrderBySequence(scheduleId)
                .stream()
                .filter(p -> !p.getId().equals(periodId))
                .toList();

        for (BellSchedulePeriod other : otherPeriods) {
            if (other.getPeriodSequence() == request.periodSequence()) {
                throw new DuplicateResourceException("BellSchedulePeriod", "periodSequence", request.periodSequence());
            }
            if (isTimeOverlapping(request.startTime(), request.endTime(), other.getStartTime(), other.getEndTime())) {
                throw new BusinessException(String.format(
                        "Overlapping period timing: (%s - %s) overlaps with '%s' (%s - %s)",
                        request.startTime(), request.endTime(), other.getName(), other.getStartTime(), other.getEndTime()
                ));
            }
            if (request.startTime().isAfter(other.getStartTime()) && request.periodSequence() <= other.getPeriodSequence()) {
                throw new BusinessException(String.format(
                        "Period sequence [%d] must be greater than previous period sequence [%d]",
                        request.periodSequence(), other.getPeriodSequence()
                ));
            }
            if (request.startTime().isBefore(other.getStartTime()) && request.periodSequence() >= other.getPeriodSequence()) {
                throw new BusinessException(String.format(
                        "Period sequence [%d] must be less than subsequent period sequence [%d]",
                        request.periodSequence(), other.getPeriodSequence()
                ));
            }
        }

        period.setName(request.name().trim());
        period.setPeriodSequence(request.periodSequence());
        period.setStartTime(request.startTime());
        period.setEndTime(request.endTime());
        period.setPeriodType(request.periodType());
        period.setUpdatedBy(com.vidya.common.util.SecurityUtils.getCurrentUserId().orElse(null));

        try {
            BellSchedulePeriod updated = bellSchedulePeriodRepository.save(period);
            log.info("Period updated successfully: [{}]", updated.getPeriodCode());
            return bellScheduleMapper.toPeriodResponse(updated);
        } catch (DataIntegrityViolationException e) {
            log.warn("Constraint violation updating period: {}", e.getMessage());
            throw new DuplicateResourceException("BellSchedulePeriod", "periodSequence", request.periodSequence());
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void deletePeriod(UUID scheduleId, UUID periodId) {
        log.info("Deleting period [{}] from schedule [{}]", periodId, scheduleId);
        BellSchedulePeriod period = bellSchedulePeriodRepository.findByIdAndNotDeleted(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("BellSchedulePeriod", periodId));

        if (!period.getBellSchedule().getId().equals(scheduleId)) {
            throw new BusinessException("Period does not belong to specified schedule.");
        }

        if (timetableEntryRepository.isPeriodUsedInActiveTimetables(periodId)) {
            throw new BusinessException(String.format(
                    "Cannot delete period [%s] because it is currently assigned in active timetable entries.",
                    period.getPeriodCode()
            ));
        }

        period.softDelete();
        period.setUpdatedBy(com.vidya.common.util.SecurityUtils.getCurrentUserId().orElse(null));
        bellSchedulePeriodRepository.save(period);
        log.info("Period deleted successfully: [{}]", periodId);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<PeriodResponse> getPeriodsByScheduleId(UUID scheduleId) {
        if (!bellScheduleRepository.existsById(scheduleId)) {
            throw new ResourceNotFoundException("BellSchedule", scheduleId);
        }
        return bellSchedulePeriodRepository.findByBellScheduleIdAndNotDeletedOrderBySequence(scheduleId)
                .stream()
                .map(bellScheduleMapper::toPeriodResponse)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public List<PeriodResponse> bulkReplacePeriods(UUID scheduleId, List<CreatePeriodRequest> periods) {
        log.info("Bulk replacing periods for schedule [{}] (count: {})", scheduleId, periods != null ? periods.size() : 0);
        BellSchedule schedule = bellScheduleRepository.findByIdAndNotDeleted(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("BellSchedule", scheduleId));

        validatePeriods(periods);

        // Check if any existing periods used in active timetables are being deleted/modified
        List<BellSchedulePeriod> existingPeriods = bellSchedulePeriodRepository.findByBellScheduleIdAndNotDeletedOrderBySequence(scheduleId);
        for (BellSchedulePeriod existing : existingPeriods) {
            if (timetableEntryRepository.isPeriodUsedInActiveTimetables(existing.getId())) {
                boolean stillMatchesTiming = periods.stream().anyMatch(p ->
                        p.startTime().equals(existing.getStartTime()) && p.endTime().equals(existing.getEndTime())
                );
                if (!stillMatchesTiming) {
                    throw new BusinessException(String.format(
                            "Cannot bulk replace periods because period [%s] (%s) is assigned in active timetable entries.",
                            existing.getPeriodCode(), existing.getName()
                    ));
                }
            }
        }

        UUID currentUserId = com.vidya.common.util.SecurityUtils.getCurrentUserId().orElse(null);

        // Soft delete old periods
        for (BellSchedulePeriod existing : existingPeriods) {
            existing.softDelete();
            existing.setUpdatedBy(currentUserId);
            bellSchedulePeriodRepository.save(existing);
        }

        // Add new periods
        List<BellSchedulePeriod> newPeriods = new ArrayList<>();
        for (CreatePeriodRequest pReq : periods) {
            String periodCode = CodeGenerator.periodCode();
            while (bellSchedulePeriodRepository.existsByPeriodCode(periodCode)) {
                periodCode = CodeGenerator.periodCode();
            }
            BellSchedulePeriod period = new BellSchedulePeriod(
                    periodCode,
                    schedule,
                    pReq.name().trim(),
                    pReq.periodSequence(),
                    pReq.startTime(),
                    pReq.endTime(),
                    pReq.periodType()
            );
            period.setCreatedBy(currentUserId);
            period.setUpdatedBy(currentUserId);
            newPeriods.add(period);
        }

        List<BellSchedulePeriod> saved = bellSchedulePeriodRepository.saveAll(newPeriods);
        log.info("Bulk replaced periods successfully for schedule [{}]", scheduleId);
        return saved.stream().map(bellScheduleMapper::toPeriodResponse).toList();
    }

    private void clearPreviousDefaultAndFlush() {
        bellScheduleRepository.findDefaultSchedule().ifPresent(prev -> {
            prev.setDefault(false);
            bellScheduleRepository.saveAndFlush(prev);
        });
    }

    private void validateScheduleNotInActiveTimetable(UUID scheduleId, String scheduleCode, String action) {
        if (timetableRepository.isBellScheduleUsedInActiveTimetables(scheduleId)) {
            throw new BusinessException(String.format(
                    "Cannot %s bell schedule [%s] because it is referenced by active timetable(s).",
                    action, scheduleCode
            ));
        }
    }

    private boolean isTimeOverlapping(java.time.LocalTime start1, java.time.LocalTime end1,
                                     java.time.LocalTime start2, java.time.LocalTime end2) {
        return start1.isBefore(end2) && end1.isAfter(start2);
    }

    private void validatePeriods(List<CreatePeriodRequest> periods) {
        if (periods == null || periods.isEmpty()) {
            throw new BusinessException("A bell schedule must contain at least one period");
        }

        Set<Integer> sequences = new HashSet<>();
        List<CreatePeriodRequest> sortedPeriods = new ArrayList<>(periods);
        sortedPeriods.sort(Comparator.comparing(CreatePeriodRequest::startTime));

        for (int i = 0; i < sortedPeriods.size(); i++) {
            CreatePeriodRequest current = sortedPeriods.get(i);

            if (current.startTime() == null || current.endTime() == null) {
                throw new BusinessException("Period start time and end time must not be null");
            }
            if (!current.startTime().isBefore(current.endTime())) {
                throw new BusinessException("Period start time (" + current.startTime() +
                        ") must be before end time (" + current.endTime() + ") for period '" + current.name() + "'");
            }

            if (!sequences.add(current.periodSequence())) {
                throw new DuplicateResourceException("BellSchedulePeriod", "periodSequence", current.periodSequence());
            }

            if (i > 0) {
                CreatePeriodRequest previous = sortedPeriods.get(i - 1);
                if (current.startTime().isBefore(previous.endTime())) {
                    throw new BusinessException("Overlapping periods detected between '" +
                            previous.name() + "' (" + previous.startTime() + "-" + previous.endTime() + ") and '" +
                            current.name() + "' (" + current.startTime() + "-" + current.endTime() + ")");
                }
                if (current.periodSequence() <= previous.periodSequence()) {
                    throw new BusinessException(String.format(
                            "Period sequence numbers must increase with time: period '%s' (sequence %d) is after '%s' (sequence %d)",
                            current.name(), current.periodSequence(), previous.name(), previous.periodSequence()
                    ));
                }
            }
        }
    }
}
