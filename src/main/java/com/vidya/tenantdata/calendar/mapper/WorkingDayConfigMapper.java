package com.vidya.tenantdata.calendar.mapper;

import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.calendar.dto.WorkingDayConfigResponse;
import com.vidya.tenantdata.calendar.entity.WorkingDayConfig;
import com.vidya.tenantdata.calendar.enums.SaturdayPattern;
import org.springframework.stereotype.Component;

@Component
public class WorkingDayConfigMapper {

    public WorkingDayConfigResponse toResponse(WorkingDayConfig config) {
        if (config == null) {
            return null;
        }

        return new WorkingDayConfigResponse(
                config.getId(),
                config.getAcademicYear() != null ? config.getAcademicYear().getId() : null,
                config.getAcademicYear() != null ? config.getAcademicYear().getName() : null,
                config.isMonday(),
                config.isTuesday(),
                config.isWednesday(),
                config.isThursday(),
                config.isFriday(),
                config.isSaturday(),
                config.isSunday(),
                config.getSaturdayPattern(),
                false
        );
    }

    public WorkingDayConfigResponse toDefaultResponse(AcademicYear academicYear) {
        return new WorkingDayConfigResponse(
                null,
                academicYear != null ? academicYear.getId() : null,
                academicYear != null ? academicYear.getName() : null,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                SaturdayPattern.ALL,
                true
        );
    }
}
