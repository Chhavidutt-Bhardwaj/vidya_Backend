package com.vidya.tenantdata.timetable.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.tenantdata.timetable.dto.BellScheduleResponse;
import com.vidya.tenantdata.timetable.dto.CreateBellScheduleRequest;
import com.vidya.tenantdata.timetable.dto.CreatePeriodRequest;
import com.vidya.tenantdata.timetable.dto.UpdateBellScheduleRequest;
import com.vidya.tenantdata.timetable.entity.BellSchedule;
import com.vidya.tenantdata.timetable.entity.BellSchedulePeriod;
import com.vidya.tenantdata.timetable.enums.BellScheduleStatus;
import com.vidya.tenantdata.timetable.mapper.BellScheduleMapper;
import com.vidya.tenantdata.timetable.repository.BellSchedulePeriodRepository;
import com.vidya.tenantdata.timetable.repository.BellScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final BellScheduleMapper bellScheduleMapper;

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public BellScheduleResponse createBellSchedule(CreateBellScheduleRequest request) {
        log.info("Creating bell schedule: [{}]", request.name());

        if (bellScheduleRepository.existsByNameIgnoreCaseAndNotDeleted(request.name().trim(), null)) {
            throw new DuplicateResourceException("BellSchedule", "name", request.name().trim());
        }

        validatePeriods(request.periods());

        if (request.isDefault()) {
            clearPreviousDefault();
        }

        String scheduleCode = CodeGenerator.bellScheduleCode();
        while (bellScheduleRepository.existsByScheduleCode(scheduleCode)) {
            scheduleCode = CodeGenerator.bellScheduleCode();
        }

        BellSchedule schedule = new BellSchedule(
                scheduleCode,
                request.name(),
                request.description(),
                request.isDefault()
        );

        if (request.periods() != null) {
            for (CreatePeriodRequest pReq : request.periods()) {
                String periodCode = CodeGenerator.periodCode();
                while (bellSchedulePeriodRepository.existsByPeriodCode(periodCode)) {
                    periodCode = CodeGenerator.periodCode();
                }
                BellSchedulePeriod period = new BellSchedulePeriod(
                        periodCode,
                        schedule,
                        pReq.name(),
                        pReq.periodSequence(),
                        pReq.startTime(),
                        pReq.endTime(),
                        pReq.periodType()
                );
                schedule.addPeriod(period);
            }
        }

        BellSchedule saved = bellScheduleRepository.save(schedule);
        log.info("Bell schedule created successfully: [{}]", saved.getScheduleCode());
        return bellScheduleMapper.toResponse(saved);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public BellScheduleResponse updateBellSchedule(UUID id, UpdateBellScheduleRequest request) {
        log.info("Updating bell schedule: [{}]", id);

        BellSchedule schedule = bellScheduleRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("BellSchedule", id));

        if (bellScheduleRepository.existsByNameIgnoreCaseAndNotDeleted(request.name().trim(), id)) {
            throw new DuplicateResourceException("BellSchedule", "name", request.name().trim());
        }

        if (request.periods() != null && !request.periods().isEmpty()) {
            validatePeriods(request.periods());
        }

        if (request.isDefault() && !schedule.isDefault()) {
            clearPreviousDefault();
        }

        schedule.setName(request.name().trim());
        schedule.setDescription(request.description() != null ? request.description().trim() : null);
        schedule.setDefault(request.isDefault());
        schedule.updateStatus(request.status());

        if (request.periods() != null && !request.periods().isEmpty()) {
            schedule.getPeriods().clear();
            for (CreatePeriodRequest pReq : request.periods()) {
                String periodCode = CodeGenerator.periodCode();
                while (bellSchedulePeriodRepository.existsByPeriodCode(periodCode)) {
                    periodCode = CodeGenerator.periodCode();
                }
                BellSchedulePeriod period = new BellSchedulePeriod(
                        periodCode,
                        schedule,
                        pReq.name(),
                        pReq.periodSequence(),
                        pReq.startTime(),
                        pReq.endTime(),
                        pReq.periodType()
                );
                schedule.addPeriod(period);
            }
        }

        BellSchedule updated = bellScheduleRepository.save(schedule);
        log.info("Bell schedule updated successfully: [{}]", updated.getScheduleCode());
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
    public void deleteBellSchedule(UUID id) {
        log.info("Deleting bell schedule: [{}]", id);
        BellSchedule schedule = bellScheduleRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("BellSchedule", id));
        schedule.softDelete();
        bellScheduleRepository.save(schedule);
        log.info("Bell schedule deleted successfully: [{}]", id);
    }

    private void clearPreviousDefault() {
        bellScheduleRepository.findDefaultSchedule().ifPresent(prev -> {
            prev.setDefault(false);
            bellScheduleRepository.save(prev);
        });
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
                throw new BusinessException("Duplicate period sequence number: " + current.periodSequence());
            }

            if (i > 0) {
                CreatePeriodRequest previous = sortedPeriods.get(i - 1);
                if (current.startTime().isBefore(previous.endTime())) {
                    throw new BusinessException("Overlapping periods detected between '" +
                            previous.name() + "' (" + previous.startTime() + "-" + previous.endTime() + ") and '" +
                            current.name() + "' (" + current.startTime() + "-" + current.endTime() + ")");
                }
            }
        }
    }
}
