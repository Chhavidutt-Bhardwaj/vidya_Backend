package com.vidya.tenantdata.calendar.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.SecurityUtils;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.calendar.dto.DayStatusResponse;
import com.vidya.tenantdata.calendar.dto.WorkingDayConfigRequest;
import com.vidya.tenantdata.calendar.dto.WorkingDayConfigResponse;
import com.vidya.tenantdata.calendar.entity.SchoolHoliday;
import com.vidya.tenantdata.calendar.entity.WorkingDayConfig;
import com.vidya.tenantdata.calendar.enums.DayStatus;
import com.vidya.tenantdata.calendar.enums.SaturdayPattern;
import com.vidya.tenantdata.calendar.mapper.WorkingDayConfigMapper;
import com.vidya.tenantdata.calendar.repository.SchoolHolidayRepository;
import com.vidya.tenantdata.calendar.repository.WorkingDayConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarServiceImpl implements CalendarService {

    private final AcademicYearRepository academicYearRepository;
    private final WorkingDayConfigRepository workingDayConfigRepository;
    private final SchoolHolidayRepository schoolHolidayRepository;
    private final WorkingDayConfigMapper workingDayConfigMapper;

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public boolean isWorkingDay(LocalDate date) {
        DayStatusResponse status = getDayStatus(date);
        return status.status() == DayStatus.WORKING;
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public DayStatusResponse getDayStatus(LocalDate date) {
        if (date == null) {
            throw new BusinessException("Date must not be null.");
        }

        List<AcademicYear> academicYears = academicYearRepository.findAcademicYearsForDate(date);
        if (academicYears.isEmpty()) {
            throw new ResourceNotFoundException("AcademicYear for date", date);
        }

        AcademicYear academicYear = academicYears.getFirst();
        return computeDayStatusForAcademicYear(academicYear, date);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<DayStatusResponse> getDaysStatus(LocalDate from, LocalDate to) {
        validateDateRange(from, to);

        List<DayStatusResponse> responses = new ArrayList<>();
        Map<UUID, WorkingDayConfig> configCache = new HashMap<>();
        Map<UUID, List<SchoolHoliday>> holidayCache = new HashMap<>();

        LocalDate current = from;
        while (!current.isAfter(to)) {
            List<AcademicYear> academicYears = academicYearRepository.findAcademicYearsForDate(current);
            if (academicYears.isEmpty()) {
                throw new ResourceNotFoundException("AcademicYear for date", current);
            }

            AcademicYear academicYear = academicYears.getFirst();
            UUID ayId = academicYear.getId();

            WorkingDayConfig config = configCache.computeIfAbsent(ayId, id ->
                    workingDayConfigRepository.findByAcademicYearIdAndNotDeleted(id).orElse(null)
            );

            List<SchoolHoliday> holidays = holidayCache.computeIfAbsent(ayId, id ->
                    schoolHolidayRepository.findActiveHolidaysInRange(id, from, to)
            );

            responses.add(computeDayStatusWithPreloaded(academicYear, config, holidays, current));
            current = current.plusDays(1);
        }

        return responses;
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public long countWorkingDays(LocalDate from, LocalDate to) {
        return getDaysStatus(from, to).stream()
                .filter(d -> d.status() == DayStatus.WORKING)
                .count();
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public WorkingDayConfigResponse getWorkingDayConfig(UUID academicYearId) {
        AcademicYear academicYear = academicYearRepository.findByIdAndNotDeleted(academicYearId)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", academicYearId));

        return workingDayConfigRepository.findByAcademicYearIdAndNotDeleted(academicYearId)
                .map(workingDayConfigMapper::toResponse)
                .orElseGet(() -> workingDayConfigMapper.toDefaultResponse(academicYear));
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public WorkingDayConfigResponse updateWorkingDayConfig(UUID academicYearId, WorkingDayConfigRequest request) {
        log.info("Updating working day config for academic year [{}]", academicYearId);

        AcademicYear academicYear = academicYearRepository.findByIdAndNotDeleted(academicYearId)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", academicYearId));

        Optional<WorkingDayConfig> existing = workingDayConfigRepository.findByAcademicYearIdAndNotDeleted(academicYearId);
        WorkingDayConfig config;

        UUID currentUserId = SecurityUtils.getCurrentUserId().orElse(null);

        if (existing.isPresent()) {
            config = existing.get();
            config.setMonday(request.monday());
            config.setTuesday(request.tuesday());
            config.setWednesday(request.wednesday());
            config.setThursday(request.thursday());
            config.setFriday(request.friday());
            config.setSaturday(request.saturday());
            config.setSunday(request.sunday());
            config.setSaturdayPattern(request.saturdayPattern());
            config.setUpdatedBy(currentUserId);
        } else {
            config = new WorkingDayConfig(
                    academicYear,
                    request.monday(),
                    request.tuesday(),
                    request.wednesday(),
                    request.thursday(),
                    request.friday(),
                    request.saturday(),
                    request.sunday(),
                    request.saturdayPattern()
            );
            config.setCreatedBy(currentUserId);
            config.setUpdatedBy(currentUserId);
        }

        WorkingDayConfig saved = workingDayConfigRepository.save(config);
        return workingDayConfigMapper.toResponse(saved);
    }

    private DayStatusResponse computeDayStatusForAcademicYear(AcademicYear academicYear, LocalDate date) {
        WorkingDayConfig config = workingDayConfigRepository.findByAcademicYearIdAndNotDeleted(academicYear.getId())
                .orElse(null);

        List<SchoolHoliday> holidays = schoolHolidayRepository.findHolidaysForDate(academicYear.getId(), date);
        return computeDayStatusWithPreloaded(academicYear, config, holidays, date);
    }

    private DayStatusResponse computeDayStatusWithPreloaded(
            AcademicYear academicYear,
            WorkingDayConfig config,
            List<SchoolHoliday> holidays,
            LocalDate date
    ) {
        // 1. Holiday check (highest precedence for non-working designation)
        if (holidays != null) {
            for (SchoolHoliday holiday : holidays) {
                if (!date.isBefore(holiday.getStartDate()) && !date.isAfter(holiday.getEndDate())) {
                    return new DayStatusResponse(
                            date,
                            date.getDayOfWeek(),
                            DayStatus.HOLIDAY,
                            holiday.getName(),
                            holiday.getHolidayType()
                    );
                }
            }
        }

        // 2. Weekly off evaluation against working_day_config
        boolean isWorkingWeekday = isConfiguredWorkingDay(config, date);
        if (isWorkingWeekday) {
            return new DayStatusResponse(date, date.getDayOfWeek(), DayStatus.WORKING, null, null);
        } else {
            return new DayStatusResponse(date, date.getDayOfWeek(), DayStatus.WEEKLY_OFF, null, null);
        }
    }

    private boolean isConfiguredWorkingDay(WorkingDayConfig config, LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();

        // If no config persisted, apply system virtual defaults
        if (config == null) {
            return dow != DayOfWeek.SUNDAY;
        }

        return switch (dow) {
            case MONDAY -> config.isMonday();
            case TUESDAY -> config.isTuesday();
            case WEDNESDAY -> config.isWednesday();
            case THURSDAY -> config.isThursday();
            case FRIDAY -> config.isFriday();
            case SUNDAY -> config.isSunday();
            case SATURDAY -> {
                if (!config.isSaturday()) {
                    yield false;
                }
                SaturdayPattern pattern = config.getSaturdayPattern() != null ? config.getSaturdayPattern() : SaturdayPattern.ALL;
                yield isSaturdayWorking(pattern, date.getDayOfMonth());
            }
        };
    }

    private boolean isSaturdayWorking(SaturdayPattern pattern, int dayOfMonth) {
        int saturdayOrdinal = (dayOfMonth - 1) / 7 + 1;
        return switch (pattern) {
            case ALL -> true;
            case FIRST_THIRD_FIFTH -> (saturdayOrdinal == 1 || saturdayOrdinal == 3 || saturdayOrdinal == 5);
            case SECOND_FOURTH -> (saturdayOrdinal == 2 || saturdayOrdinal == 4);
        };
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public java.util.Set<DayOfWeek> getEverWorkingWeekdays(UUID academicYearId) {
        if (academicYearId == null) {
            throw new BusinessException("academicYearId must not be null.");
        }

        academicYearRepository.findByIdAndNotDeleted(academicYearId)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", academicYearId));

        WorkingDayConfig config = workingDayConfigRepository.findByAcademicYearIdAndNotDeleted(academicYearId)
                .orElse(null);

        java.util.Set<DayOfWeek> everWorking = java.util.EnumSet.noneOf(DayOfWeek.class);

        if (config == null) {
            everWorking.add(DayOfWeek.MONDAY);
            everWorking.add(DayOfWeek.TUESDAY);
            everWorking.add(DayOfWeek.WEDNESDAY);
            everWorking.add(DayOfWeek.THURSDAY);
            everWorking.add(DayOfWeek.FRIDAY);
            everWorking.add(DayOfWeek.SATURDAY);
        } else {
            if (config.isMonday()) everWorking.add(DayOfWeek.MONDAY);
            if (config.isTuesday()) everWorking.add(DayOfWeek.TUESDAY);
            if (config.isWednesday()) everWorking.add(DayOfWeek.WEDNESDAY);
            if (config.isThursday()) everWorking.add(DayOfWeek.THURSDAY);
            if (config.isFriday()) everWorking.add(DayOfWeek.FRIDAY);
            if (config.isSaturday()) everWorking.add(DayOfWeek.SATURDAY);
            if (config.isSunday()) everWorking.add(DayOfWeek.SUNDAY);
        }

        return everWorking;
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessException("from and to dates are required.");
        }
        if (from.isAfter(to)) {
            throw new BusinessException("from date cannot be after to date.");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > 366) {
            throw new BusinessException("Date range cannot exceed 366 days. Requested: " + days + " days.");
        }
    }
}
