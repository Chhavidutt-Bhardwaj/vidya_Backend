package com.vidya.tenantdata.calendar.service;

import com.vidya.tenantdata.calendar.dto.DayStatusResponse;
import com.vidya.tenantdata.calendar.dto.WorkingDayConfigRequest;
import com.vidya.tenantdata.calendar.dto.WorkingDayConfigResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CalendarService {

    boolean isWorkingDay(LocalDate date);

    DayStatusResponse getDayStatus(LocalDate date);

    List<DayStatusResponse> getDaysStatus(LocalDate from, LocalDate to);

    long countWorkingDays(LocalDate from, LocalDate to);

    WorkingDayConfigResponse getWorkingDayConfig(UUID academicYearId);

    WorkingDayConfigResponse updateWorkingDayConfig(UUID academicYearId, WorkingDayConfigRequest request);

    java.util.Set<java.time.DayOfWeek> getEverWorkingWeekdays(UUID academicYearId);
}
