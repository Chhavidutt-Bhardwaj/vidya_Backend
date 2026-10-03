package com.vidya.tenantdata.timetable.service;

import com.vidya.tenantdata.timetable.dto.BellScheduleResponse;
import com.vidya.tenantdata.timetable.dto.CreateBellScheduleRequest;
import com.vidya.tenantdata.timetable.dto.CreatePeriodRequest;
import com.vidya.tenantdata.timetable.dto.PeriodResponse;
import com.vidya.tenantdata.timetable.dto.UpdateBellScheduleRequest;
import com.vidya.tenantdata.timetable.dto.UpdateBellScheduleStatusRequest;
import com.vidya.tenantdata.timetable.dto.UpdatePeriodRequest;
import com.vidya.tenantdata.timetable.enums.BellScheduleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface BellScheduleService {
    BellScheduleResponse createBellSchedule(CreateBellScheduleRequest request);
    BellScheduleResponse updateBellSchedule(UUID id, UpdateBellScheduleRequest request);
    BellScheduleResponse updateBellScheduleStatus(UUID id, UpdateBellScheduleStatusRequest request);
    BellScheduleResponse getBellScheduleById(UUID id);
    BellScheduleResponse getBellScheduleByCode(String scheduleCode);
    Page<BellScheduleResponse> searchBellSchedules(String search, BellScheduleStatus status, Pageable pageable);
    BellScheduleResponse getDefaultSchedule();
    BellScheduleResponse setDefaultSchedule(UUID id);
    BellScheduleResponse cloneSchedule(UUID id);
    void deleteBellSchedule(UUID id);

    PeriodResponse addPeriod(UUID scheduleId, CreatePeriodRequest request);
    PeriodResponse updatePeriod(UUID scheduleId, UUID periodId, UpdatePeriodRequest request);
    void deletePeriod(UUID scheduleId, UUID periodId);
    List<PeriodResponse> getPeriodsByScheduleId(UUID scheduleId);
    List<PeriodResponse> bulkReplacePeriods(UUID scheduleId, List<CreatePeriodRequest> periods);
}
