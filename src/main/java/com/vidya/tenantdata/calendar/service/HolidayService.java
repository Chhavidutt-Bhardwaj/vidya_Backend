package com.vidya.tenantdata.calendar.service;

import com.vidya.tenantdata.calendar.dto.CreateHolidayRequest;
import com.vidya.tenantdata.calendar.dto.HolidayResponse;
import com.vidya.tenantdata.calendar.dto.UpdateHolidayRequest;
import com.vidya.tenantdata.calendar.enums.HolidayType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

public interface HolidayService {

    HolidayResponse createHoliday(CreateHolidayRequest request);

    HolidayResponse getHolidayById(UUID id);

    Page<HolidayResponse> searchHolidays(
            UUID academicYearId,
            HolidayType type,
            LocalDate dateFrom,
            LocalDate dateTo,
            Pageable pageable
    );

    HolidayResponse updateHoliday(UUID id, UpdateHolidayRequest request);

    void deleteHoliday(UUID id);
}
