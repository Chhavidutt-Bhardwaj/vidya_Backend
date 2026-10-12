package com.vidya.tenantdata.calendar.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.calendar.dto.CreateHolidayRequest;
import com.vidya.tenantdata.calendar.dto.HolidayResponse;
import com.vidya.tenantdata.calendar.dto.UpdateHolidayRequest;
import com.vidya.tenantdata.calendar.entity.SchoolHoliday;
import com.vidya.tenantdata.calendar.enums.HolidayType;
import com.vidya.tenantdata.calendar.mapper.HolidayMapper;
import com.vidya.tenantdata.calendar.repository.SchoolHolidayRepository;
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

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HolidayService — Unit Tests")
class HolidayServiceTest {

    @Mock
    private SchoolHolidayRepository holidayRepository;

    @Mock
    private AcademicYearRepository academicYearRepository;

    @Spy
    private HolidayMapper holidayMapper = new HolidayMapper();

    @InjectMocks
    private HolidayServiceImpl holidayService;

    private UUID yearId;
    private AcademicYear academicYear;
    private UUID holidayId;
    private SchoolHoliday holiday;

    @BeforeEach
    void setUp() {
        yearId = UUID.randomUUID();
        academicYear = new AcademicYear("AY-2026", "2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 5, 31), true, AcademicYearStatus.ACTIVE);
        academicYear.setId(yearId);

        holidayId = UUID.randomUUID();
        holiday = new SchoolHoliday(
                "HOL-001",
                academicYear,
                "Independence Day",
                HolidayType.PUBLIC_HOLIDAY,
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 8, 15),
                "National holiday"
        );
        holiday.setId(holidayId);
    }

    @Test
    @DisplayName("Create Holiday — Successfully creates holiday within academic year bounds")
    void createHoliday_success() {
        CreateHolidayRequest req = new CreateHolidayRequest(
                yearId, "Independence Day", HolidayType.PUBLIC_HOLIDAY,
                LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 15), "National holiday"
        );

        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(academicYear));
        when(holidayRepository.findOverlappingHolidays(yearId, req.startDate(), req.endDate(), null))
                .thenReturn(Collections.emptyList());
        when(holidayRepository.saveAndFlush(any(SchoolHoliday.class))).thenAnswer(inv -> inv.getArgument(0));

        HolidayResponse response = holidayService.createHoliday(req);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Independence Day");
        assertThat(response.holidayType()).isEqualTo(HolidayType.PUBLIC_HOLIDAY);
        assertThat(response.startDate()).isEqualTo(LocalDate.of(2026, 8, 15));
    }

    @Test
    @DisplayName("Create Holiday — Fails when startDate is after endDate")
    void createHoliday_invalidDates_throwsBusinessException() {
        CreateHolidayRequest req = new CreateHolidayRequest(
                yearId, "Invalid Holiday", HolidayType.SCHOOL_HOLIDAY,
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 15), "Invalid"
        );

        assertThatThrownBy(() -> holidayService.createHoliday(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("start date cannot be after end date");
    }

    @Test
    @DisplayName("Create Holiday — Fails when date is outside academic year bounds")
    void createHoliday_outsideAcademicYear_throwsBusinessException() {
        CreateHolidayRequest req = new CreateHolidayRequest(
                yearId, "Early Break", HolidayType.VACATION,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10), "Outside bounds"
        );

        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(academicYear));

        assertThatThrownBy(() -> holidayService.createHoliday(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be before academic year start date");
    }

    @Test
    @DisplayName("Create Holiday — Fails when overlapping with existing active holiday (409)")
    void createHoliday_overlapping_throwsDuplicateResourceException() {
        CreateHolidayRequest req = new CreateHolidayRequest(
                yearId, "Independence Week", HolidayType.VACATION,
                LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 16), "Overlaps"
        );

        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(academicYear));
        when(holidayRepository.findOverlappingHolidays(yearId, req.startDate(), req.endDate(), null))
                .thenReturn(List.of(holiday));

        assertThatThrownBy(() -> holidayService.createHoliday(req))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Holiday overlaps with existing holiday 'Independence Day'");
    }

    @Test
    @DisplayName("Create Holiday — Database unique constraint violation maps to DuplicateResourceException")
    void createHoliday_dbUniqueConstraint_mapsToDuplicateResourceException() {
        CreateHolidayRequest req = new CreateHolidayRequest(
                yearId, "Independence Day", HolidayType.PUBLIC_HOLIDAY,
                LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 15), "National holiday"
        );

        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(academicYear));
        when(holidayRepository.findOverlappingHolidays(yearId, req.startDate(), req.endDate(), null))
                .thenReturn(Collections.emptyList());
        when(holidayRepository.saveAndFlush(any(SchoolHoliday.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint idx_school_holiday_unique"));

        assertThatThrownBy(() -> holidayService.createHoliday(req))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists in this academic year");
    }

    @Test
    @DisplayName("Get Holiday By ID — Success")
    void getHolidayById_success() {
        when(holidayRepository.findByIdAndNotDeleted(holidayId)).thenReturn(Optional.of(holiday));

        HolidayResponse response = holidayService.getHolidayById(holidayId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(holidayId);
        assertThat(response.name()).isEqualTo("Independence Day");
    }

    @Test
    @DisplayName("Get Holiday By ID — Not found throws ResourceNotFoundException (404)")
    void getHolidayById_notFound_throwsResourceNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(holidayRepository.findByIdAndNotDeleted(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> holidayService.getHolidayById(unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Search Holidays — Paged list success")
    void searchHolidays_success() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(holidayRepository.searchHolidays(yearId, HolidayType.PUBLIC_HOLIDAY, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(holiday), pageable, 1));

        Page<HolidayResponse> page = holidayService.searchHolidays(yearId, HolidayType.PUBLIC_HOLIDAY, null, null, pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().name()).isEqualTo("Independence Day");
    }

    @Test
    @DisplayName("Update Holiday — Success")
    void updateHoliday_success() {
        UpdateHolidayRequest req = new UpdateHolidayRequest(
                "Updated Name", HolidayType.PUBLIC_HOLIDAY,
                LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 15), "Updated desc"
        );

        when(holidayRepository.findByIdAndNotDeleted(holidayId)).thenReturn(Optional.of(holiday));
        when(holidayRepository.findOverlappingHolidays(yearId, req.startDate(), req.endDate(), holidayId))
                .thenReturn(Collections.emptyList());
        when(holidayRepository.saveAndFlush(any(SchoolHoliday.class))).thenAnswer(inv -> inv.getArgument(0));

        HolidayResponse response = holidayService.updateHoliday(holidayId, req);

        assertThat(response.name()).isEqualTo("Updated Name");
    }

    @Test
    @DisplayName("Delete Holiday — Soft deletes successfully")
    void deleteHoliday_success() {
        when(holidayRepository.findByIdAndNotDeleted(holidayId)).thenReturn(Optional.of(holiday));

        holidayService.deleteHoliday(holidayId);

        assertThat(holiday.isDeleted()).isTrue();
        verify(holidayRepository).save(holiday);
    }
}
