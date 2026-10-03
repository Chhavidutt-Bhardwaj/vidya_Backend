package com.vidya.tenantdata.timetable.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.InvalidStateTransitionException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.timetable.dto.BellScheduleResponse;
import com.vidya.tenantdata.timetable.dto.CreateBellScheduleRequest;
import com.vidya.tenantdata.timetable.dto.CreatePeriodRequest;
import com.vidya.tenantdata.timetable.dto.PeriodResponse;
import com.vidya.tenantdata.timetable.dto.UpdateBellScheduleRequest;
import com.vidya.tenantdata.timetable.dto.UpdateBellScheduleStatusRequest;
import com.vidya.tenantdata.timetable.dto.UpdatePeriodRequest;
import com.vidya.tenantdata.timetable.entity.BellSchedule;
import com.vidya.tenantdata.timetable.entity.BellSchedulePeriod;
import com.vidya.tenantdata.timetable.enums.BellScheduleStatus;
import com.vidya.tenantdata.timetable.enums.PeriodType;
import com.vidya.tenantdata.timetable.mapper.BellScheduleMapper;
import com.vidya.tenantdata.timetable.repository.BellSchedulePeriodRepository;
import com.vidya.tenantdata.timetable.repository.BellScheduleRepository;
import com.vidya.tenantdata.timetable.repository.TimetableEntryRepository;
import com.vidya.tenantdata.timetable.repository.TimetableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BellScheduleService — Unit Tests")
class BellScheduleServiceTest {

    @Mock
    private BellScheduleRepository bellScheduleRepository;

    @Mock
    private BellSchedulePeriodRepository bellSchedulePeriodRepository;

    @Mock
    private TimetableRepository timetableRepository;

    @Mock
    private TimetableEntryRepository timetableEntryRepository;

    @Spy
    private BellScheduleMapper bellScheduleMapper = new BellScheduleMapper();

    @InjectMocks
    private BellScheduleServiceImpl bellScheduleService;

    private BellSchedule testSchedule;
    private UUID scheduleId;
    private BellSchedulePeriod period1;
    private BellSchedulePeriod period2;
    private UUID period1Id;
    private UUID period2Id;

    @BeforeEach
    void setUp() {
        scheduleId = UUID.randomUUID();
        period1Id = UUID.randomUUID();
        period2Id = UUID.randomUUID();

        testSchedule = new BellSchedule("BSC-12345678", "Regular Schedule", "Standard timing", true);
        testSchedule.setId(scheduleId);

        period1 = new BellSchedulePeriod("PRD-1", testSchedule, "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45), PeriodType.TEACHING);
        period1.setId(period1Id);
        period2 = new BellSchedulePeriod("PRD-2", testSchedule, "Period 2", 2, LocalTime.of(8, 45), LocalTime.of(9, 30), PeriodType.TEACHING);
        period2.setId(period2Id);

        testSchedule.addPeriod(period1);
        testSchedule.addPeriod(period2);
    }

    // ── 1. Create Bell Schedule ───────────────────────────────────────────────

    @Test
    @DisplayName("Create bell schedule successfully with valid periods")
    void createBellSchedule_success() {
        List<CreatePeriodRequest> periods = List.of(
                new CreatePeriodRequest("Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45), PeriodType.TEACHING),
                new CreatePeriodRequest("Period 2", 2, LocalTime.of(8, 45), LocalTime.of(9, 30), PeriodType.TEACHING)
        );
        CreateBellScheduleRequest request = new CreateBellScheduleRequest("Regular Schedule", "Standard", true, periods);

        when(bellScheduleRepository.existsByNameIgnoreCaseAndNotDeleted("Regular Schedule", null)).thenReturn(false);
        when(bellScheduleRepository.findDefaultSchedule()).thenReturn(Optional.empty());
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
    @DisplayName("Create bell schedule fails when name is duplicate")
    void createBellSchedule_duplicateName_throwsDuplicateResourceException() {
        List<CreatePeriodRequest> periods = List.of(
                new CreatePeriodRequest("Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45), PeriodType.TEACHING)
        );
        CreateBellScheduleRequest request = new CreateBellScheduleRequest("Regular Schedule", "Standard", false, periods);

        when(bellScheduleRepository.existsByNameIgnoreCaseAndNotDeleted("Regular Schedule", null)).thenReturn(true);

        assertThatThrownBy(() -> bellScheduleService.createBellSchedule(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Regular Schedule");

        verify(bellScheduleRepository, never()).save(any());
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
    @DisplayName("Create bell schedule fails when period sequence is duplicate")
    void createBellSchedule_duplicateSequence_throwsException() {
        List<CreatePeriodRequest> periods = List.of(
                new CreatePeriodRequest("Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 45), PeriodType.TEACHING),
                new CreatePeriodRequest("Period 2", 1, LocalTime.of(8, 45), LocalTime.of(9, 30), PeriodType.TEACHING)
        );
        CreateBellScheduleRequest request = new CreateBellScheduleRequest("Dup Seq Schedule", null, false, periods);

        assertThatThrownBy(() -> bellScheduleService.createBellSchedule(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("periodSequence");
    }

    @Test
    @DisplayName("Create bell schedule fails when sequence numbers do not increase chronologically")
    void createBellSchedule_outOfOrderSequence_throwsException() {
        List<CreatePeriodRequest> periods = List.of(
                new CreatePeriodRequest("Period 1", 2, LocalTime.of(8, 0), LocalTime.of(8, 45), PeriodType.TEACHING),
                new CreatePeriodRequest("Period 2", 1, LocalTime.of(8, 45), LocalTime.of(9, 30), PeriodType.TEACHING)
        );
        CreateBellScheduleRequest request = new CreateBellScheduleRequest("Bad Seq Order", null, false, periods);

        assertThatThrownBy(() -> bellScheduleService.createBellSchedule(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Period sequence numbers must increase with time");
    }

    // ── 2. Get & Search ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Get bell schedule by ID returns details")
    void getBellScheduleById_success() {
        when(bellScheduleRepository.findByIdAndNotDeleted(scheduleId)).thenReturn(Optional.of(testSchedule));

        BellScheduleResponse response = bellScheduleService.getBellScheduleById(scheduleId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(scheduleId);
        assertThat(response.periods()).hasSize(2);
    }

    @Test
    @DisplayName("Get bell schedule by ID not found throws ResourceNotFoundException")
    void getBellScheduleById_notFound_throwsResourceNotFoundException() {
        when(bellScheduleRepository.findByIdAndNotDeleted(scheduleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bellScheduleService.getBellScheduleById(scheduleId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Get bell schedule by code returns details")
    void getBellScheduleByCode_success() {
        when(bellScheduleRepository.findByScheduleCodeAndNotDeleted("BSC-12345678")).thenReturn(Optional.of(testSchedule));

        BellScheduleResponse response = bellScheduleService.getBellScheduleByCode("BSC-12345678");

        assertThat(response).isNotNull();
        assertThat(response.scheduleCode()).isEqualTo("BSC-12345678");
    }

    @Test
    @DisplayName("Search bell schedules returns paginated list")
    void searchBellSchedules_paged() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<BellSchedule> page = new PageImpl<>(List.of(testSchedule), pageable, 1);

        when(bellScheduleRepository.searchSchedules("Regular", BellScheduleStatus.ACTIVE, pageable)).thenReturn(page);

        Page<BellScheduleResponse> result = bellScheduleService.searchBellSchedules("Regular", BellScheduleStatus.ACTIVE, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ── 3. Update & Status ────────────────────────────────────────────────────

    @Test
    @DisplayName("Update bell schedule successfully")
    void updateBellSchedule_success() {
        UpdateBellScheduleRequest request = new UpdateBellScheduleRequest("Updated Schedule", "Updated desc", false, BellScheduleStatus.ACTIVE, null);

        when(bellScheduleRepository.findByIdAndNotDeleted(scheduleId)).thenReturn(Optional.of(testSchedule));
        when(bellScheduleRepository.existsByNameIgnoreCaseAndNotDeleted("Updated Schedule", scheduleId)).thenReturn(false);
        when(bellScheduleRepository.save(any(BellSchedule.class))).thenReturn(testSchedule);

        BellScheduleResponse response = bellScheduleService.updateBellSchedule(scheduleId, request);

        assertThat(response).isNotNull();
        verify(bellScheduleRepository).save(testSchedule);
    }

    @Test
    @DisplayName("Update bell schedule fails when setting DELETED status via update")
    void updateBellSchedule_deletedTarget_throwsInvalidStateTransitionException() {
        UpdateBellScheduleRequest request = new UpdateBellScheduleRequest("Regular Schedule", "Standard", false, BellScheduleStatus.DELETED, null);

        when(bellScheduleRepository.findByIdAndNotDeleted(scheduleId)).thenReturn(Optional.of(testSchedule));

        assertThatThrownBy(() -> bellScheduleService.updateBellSchedule(scheduleId, request))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("Update bell schedule status fails when referenced by active timetables")
    void updateBellScheduleStatus_inUse_blocked() {
        UpdateBellScheduleStatusRequest request = new UpdateBellScheduleStatusRequest(BellScheduleStatus.INACTIVE);

        when(bellScheduleRepository.findByIdAndNotDeleted(scheduleId)).thenReturn(Optional.of(testSchedule));
        when(timetableRepository.isBellScheduleUsedInActiveTimetables(scheduleId)).thenReturn(true);

        assertThatThrownBy(() -> bellScheduleService.updateBellScheduleStatus(scheduleId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("referenced by active timetable");
    }

    // ── 4. Set Default ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Set default schedule clears previous default and flushes")
    void setDefaultSchedule_success() {
        testSchedule.setDefault(false);
        BellSchedule prevDefault = new BellSchedule("BSC-PREV", "Prev Default", null, true);
        prevDefault.setId(UUID.randomUUID());

        when(bellScheduleRepository.findByIdAndNotDeleted(scheduleId)).thenReturn(Optional.of(testSchedule));
        when(bellScheduleRepository.findDefaultSchedule()).thenReturn(Optional.of(prevDefault));
        when(bellScheduleRepository.saveAndFlush(any(BellSchedule.class))).thenReturn(testSchedule);

        BellScheduleResponse response = bellScheduleService.setDefaultSchedule(scheduleId);

        assertThat(response).isNotNull();
        assertThat(prevDefault.isDefault()).isFalse();
        assertThat(testSchedule.isDefault()).isTrue();
        verify(bellScheduleRepository).saveAndFlush(prevDefault);
        verify(bellScheduleRepository).saveAndFlush(testSchedule);
    }

    // ── 5. Clone ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Clone schedule duplicates periods and creates unique name")
    void cloneSchedule_success() {
        when(bellScheduleRepository.findByIdAndNotDeleted(scheduleId)).thenReturn(Optional.of(testSchedule));
        when(bellScheduleRepository.existsByNameIgnoreCaseAndNotDeleted("Regular Schedule (Copy)", null)).thenReturn(false);
        when(bellScheduleRepository.save(any(BellSchedule.class))).thenAnswer(i -> {
            BellSchedule b = i.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });

        BellScheduleResponse response = bellScheduleService.cloneSchedule(scheduleId);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Regular Schedule (Copy)");
        assertThat(response.isDefault()).isFalse();
        assertThat(response.periods()).hasSize(2);
    }

    // ── 6. Delete Schedule ────────────────────────────────────────────────────

    @Test
    @DisplayName("Delete bell schedule soft deletes when not in use")
    void deleteBellSchedule_success() {
        when(bellScheduleRepository.findByIdAndNotDeleted(scheduleId)).thenReturn(Optional.of(testSchedule));
        when(timetableRepository.isBellScheduleUsedInActiveTimetables(scheduleId)).thenReturn(false);

        bellScheduleService.deleteBellSchedule(scheduleId);

        assertThat(testSchedule.isDeleted()).isTrue();
        verify(bellScheduleRepository).save(testSchedule);
    }

    @Test
    @DisplayName("Delete bell schedule throws BusinessException when in use")
    void deleteBellSchedule_inUse_throwsBusinessException() {
        when(bellScheduleRepository.findByIdAndNotDeleted(scheduleId)).thenReturn(Optional.of(testSchedule));
        when(timetableRepository.isBellScheduleUsedInActiveTimetables(scheduleId)).thenReturn(true);

        assertThatThrownBy(() -> bellScheduleService.deleteBellSchedule(scheduleId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("referenced by active timetable");

        verify(bellScheduleRepository, never()).save(testSchedule);
    }

    // ── 7. Period Operations ──────────────────────────────────────────────────

    @Test
    @DisplayName("Add period to schedule successfully")
    void addPeriod_success() {
        CreatePeriodRequest request = new CreatePeriodRequest("Period 3", 3, LocalTime.of(9, 30), LocalTime.of(10, 15), PeriodType.TEACHING);

        when(bellScheduleRepository.findByIdAndNotDeleted(scheduleId)).thenReturn(Optional.of(testSchedule));
        when(bellSchedulePeriodRepository.findByBellScheduleIdAndNotDeletedOrderBySequence(scheduleId))
                .thenReturn(List.of(period1, period2));
        when(bellSchedulePeriodRepository.save(any(BellSchedulePeriod.class))).thenAnswer(i -> {
            BellSchedulePeriod p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        PeriodResponse response = bellScheduleService.addPeriod(scheduleId, request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Period 3");
        assertThat(response.periodSequence()).isEqualTo(3);
    }

    @Test
    @DisplayName("Update period timings fails when period is in use in active timetable entries")
    void updatePeriod_timingChangeInUse_throwsBusinessException() {
        UpdatePeriodRequest request = new UpdatePeriodRequest("Renamed Period", 1, LocalTime.of(8, 10), LocalTime.of(8, 55), PeriodType.TEACHING);

        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(period1Id)).thenReturn(Optional.of(period1));
        when(timetableEntryRepository.isPeriodUsedInActiveTimetables(period1Id)).thenReturn(true);

        assertThatThrownBy(() -> bellScheduleService.updatePeriod(scheduleId, period1Id, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot modify start/end times");
    }

    @Test
    @DisplayName("Delete period fails when period is in use in active timetable entries")
    void deletePeriod_inUse_throwsBusinessException() {
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(period1Id)).thenReturn(Optional.of(period1));
        when(timetableEntryRepository.isPeriodUsedInActiveTimetables(period1Id)).thenReturn(true);

        assertThatThrownBy(() -> bellScheduleService.deletePeriod(scheduleId, period1Id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot delete period");
    }

    @Test
    @DisplayName("Delete period succeeds when not in use")
    void deletePeriod_success() {
        when(bellSchedulePeriodRepository.findByIdAndNotDeleted(period1Id)).thenReturn(Optional.of(period1));
        when(timetableEntryRepository.isPeriodUsedInActiveTimetables(period1Id)).thenReturn(false);

        bellScheduleService.deletePeriod(scheduleId, period1Id);

        assertThat(period1.isDeleted()).isTrue();
        verify(bellSchedulePeriodRepository).save(period1);
    }
}
