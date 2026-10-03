package com.vidya.tenantdata.timetable.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.timetable.dto.BellScheduleResponse;
import com.vidya.tenantdata.timetable.dto.CreateBellScheduleRequest;
import com.vidya.tenantdata.timetable.dto.CreatePeriodRequest;
import com.vidya.tenantdata.timetable.dto.UpdateBellScheduleRequest;
import com.vidya.tenantdata.timetable.entity.BellSchedule;
import com.vidya.tenantdata.timetable.entity.BellSchedulePeriod;
import com.vidya.tenantdata.timetable.enums.BellScheduleStatus;
import com.vidya.tenantdata.timetable.enums.PeriodType;
import com.vidya.tenantdata.timetable.mapper.BellScheduleMapper;
import com.vidya.tenantdata.timetable.repository.BellSchedulePeriodRepository;
import com.vidya.tenantdata.timetable.repository.BellScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BellScheduleService — Unit Tests")
class BellScheduleServiceTest {

    @Mock
    private BellScheduleRepository bellScheduleRepository;

    @Mock
    private BellSchedulePeriodRepository bellSchedulePeriodRepository;

    @Spy
    private BellScheduleMapper bellScheduleMapper = new BellScheduleMapper();

    @InjectMocks
    private BellScheduleServiceImpl bellScheduleService;

    private BellSchedule testSchedule;
    private UUID scheduleId;

    @BeforeEach
    void setUp() {
        scheduleId = UUID.randomUUID();
        testSchedule = new BellSchedule("BSC-12345678", "Regular Schedule", "Standard timing", true);
        testSchedule.setId(scheduleId);

        BellSchedulePeriod p1 = new BellSchedulePeriod("PRD-1", testSchedule, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45), PeriodType.TEACHING);
        BellSchedulePeriod p2 = new BellSchedulePeriod("PRD-2", testSchedule, "Period 2", 2, LocalTime.of(8, 45), LocalTime.of(9, 30), PeriodType.TEACHING);
        testSchedule.addPeriod(p1);
        testSchedule.addPeriod(p2);
    }

    @Test
    @DisplayName("Create bell schedule successfully with valid periods")
    void createBellSchedule_success() {
        List<CreatePeriodRequest> periods = List.of(
                new CreatePeriodRequest("Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45), PeriodType.TEACHING),
                new CreatePeriodRequest("Period 2", 2, LocalTime.of(8, 45), LocalTime.of(9, 30), PeriodType.TEACHING)
        );
        CreateBellScheduleRequest request = new CreateBellScheduleRequest("Regular Schedule", "Standard", true, periods);

        when(bellScheduleRepository.existsByNameIgnoreCaseAndNotDeleted("Regular Schedule", null)).thenReturn(false);
        when(bellScheduleRepository.save(any(BellSchedule.class))).thenAnswer(i -> {
            BellSchedule b = i.getArgument(0);
            b.setId(scheduleId);
            return b;
        });

        BellScheduleResponse response = bellScheduleService.createBellSchedule(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Regular Schedule");
        assertThat(response.periods()).hasSize(2);
        verify(bellScheduleRepository).save(any(BellSchedule.class));
    }

    @Test
    @DisplayName("Create bell schedule fails when period start time is after end time")
    void createBellSchedule_invalidPeriodTimes_throwsException() {
        List<CreatePeriodRequest> periods = List.of(
                new CreatePeriodRequest("Period 1", 1, LocalTime.of(9, 0), LocalTime.of(8, 0), PeriodType.TEACHING)
        );
        CreateBellScheduleRequest request = new CreateBellScheduleRequest("Bad Schedule", null, false, periods);

        assertThatThrownBy(() -> bellScheduleService.createBellSchedule(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must be before end time");
    }

    @Test
    @DisplayName("Create bell schedule fails when periods overlap")
    void createBellSchedule_overlappingPeriods_throwsException() {
        List<CreatePeriodRequest> periods = List.of(
                new CreatePeriodRequest("Period 1", 1, LocalTime.of(8, 0), LocalTime.of(9, 0), PeriodType.TEACHING),
                new CreatePeriodRequest("Period 2", 2, LocalTime.of(8, 30), LocalTime.of(9, 30), PeriodType.TEACHING)
        );
        CreateBellScheduleRequest request = new CreateBellScheduleRequest("Overlap Schedule", null, false, periods);

        assertThatThrownBy(() -> bellScheduleService.createBellSchedule(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Overlapping periods detected");
    }

    @Test
    @DisplayName("Get bell schedule by ID returns details")
    void getBellScheduleById_success() {
        when(bellScheduleRepository.findByIdAndNotDeleted(scheduleId)).thenReturn(Optional.of(testSchedule));

        BellScheduleResponse response = bellScheduleService.getBellScheduleById(scheduleId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(scheduleId);
        assertThat(response.periods()).hasSize(2);
    }
}
