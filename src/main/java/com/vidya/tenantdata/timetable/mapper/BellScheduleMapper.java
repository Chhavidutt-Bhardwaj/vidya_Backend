package com.vidya.tenantdata.timetable.mapper;

import com.vidya.tenantdata.timetable.dto.BellScheduleResponse;
import com.vidya.tenantdata.timetable.dto.PeriodResponse;
import com.vidya.tenantdata.timetable.entity.BellSchedule;
import com.vidya.tenantdata.timetable.entity.BellSchedulePeriod;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class BellScheduleMapper {

    public BellScheduleResponse toResponse(BellSchedule schedule) {
        if (schedule == null) {
            return null;
        }
        List<PeriodResponse> periods = schedule.getPeriods() != null
                ? schedule.getPeriods().stream()
                .filter(p -> !p.isDeleted())
                .map(this::toPeriodResponse)
                .toList()
                : Collections.emptyList();

        return new BellScheduleResponse(
                schedule.getId(),
                schedule.getScheduleCode(),
                schedule.getName(),
                schedule.getDescription(),
                schedule.isDefault(),
                schedule.getStatus(),
                periods,
                schedule.getCreatedAt(),
                schedule.getUpdatedAt(),
                schedule.getVersion()
        );
    }

    public PeriodResponse toPeriodResponse(BellSchedulePeriod period) {
        if (period == null) {
            return null;
        }
        return new PeriodResponse(
                period.getId(),
                period.getPeriodCode(),
                period.getName(),
                period.getPeriodSequence(),
                period.getStartTime(),
                period.getEndTime(),
                period.getPeriodType(),
                period.getCreatedAt(),
                period.getUpdatedAt(),
                period.getVersion()
        );
    }
}
