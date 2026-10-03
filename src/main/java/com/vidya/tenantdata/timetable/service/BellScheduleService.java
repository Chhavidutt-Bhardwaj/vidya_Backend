package com.vidya.tenantdata.timetable.service;

import com.vidya.tenantdata.timetable.dto.BellScheduleResponse;
import com.vidya.tenantdata.timetable.dto.CreateBellScheduleRequest;
import com.vidya.tenantdata.timetable.dto.UpdateBellScheduleRequest;
import com.vidya.tenantdata.timetable.enums.BellScheduleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface BellScheduleService {
    BellScheduleResponse createBellSchedule(CreateBellScheduleRequest request);
    BellScheduleResponse updateBellSchedule(UUID id, UpdateBellScheduleRequest request);
    BellScheduleResponse getBellScheduleById(UUID id);
    BellScheduleResponse getBellScheduleByCode(String scheduleCode);
    Page<BellScheduleResponse> searchBellSchedules(String search, BellScheduleStatus status, Pageable pageable);
    BellScheduleResponse getDefaultSchedule();
    void deleteBellSchedule(UUID id);
}
