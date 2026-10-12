package com.vidya.tenantdata.calendar.mapper;

import com.vidya.tenantdata.calendar.dto.HolidayResponse;
import com.vidya.tenantdata.calendar.entity.SchoolHoliday;
import org.springframework.stereotype.Component;

@Component
public class HolidayMapper {

    public HolidayResponse toResponse(SchoolHoliday holiday) {
        if (holiday == null) {
            return null;
        }

        return new HolidayResponse(
                holiday.getId(),
                holiday.getHolidayCode(),
                holiday.getAcademicYear() != null ? holiday.getAcademicYear().getId() : null,
                holiday.getAcademicYear() != null ? holiday.getAcademicYear().getName() : null,
                holiday.getName(),
                holiday.getHolidayType(),
                holiday.getStartDate(),
                holiday.getEndDate(),
                holiday.getDescription()
        );
    }
}
