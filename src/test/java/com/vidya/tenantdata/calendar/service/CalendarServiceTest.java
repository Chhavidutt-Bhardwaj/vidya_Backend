package com.vidya.tenantdata.calendar.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.calendar.dto.DayStatusResponse;
import com.vidya.tenantdata.calendar.dto.WorkingDayConfigRequest;
import com.vidya.tenantdata.calendar.dto.WorkingDayConfigResponse;
import com.vidya.tenantdata.calendar.entity.SchoolHoliday;
import com.vidya.tenantdata.calendar.entity.WorkingDayConfig;
import com.vidya.tenantdata.calendar.enums.DayStatus;
import com.vidya.tenantdata.calendar.enums.HolidayType;
import com.vidya.tenantdata.calendar.enums.SaturdayPattern;
import com.vidya.tenantdata.calendar.mapper.WorkingDayConfigMapper;
import com.vidya.tenantdata.calendar.repository.SchoolHolidayRepository;
import com.vidya.tenantdata.calendar.repository.WorkingDayConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CalendarService — Unit Tests")
class CalendarServiceTest {

    @Mock
    private AcademicYearRepository academicYearRepository;

    @Mock
    private WorkingDayConfigRepository workingDayConfigRepository;

    @Mock
    private SchoolHolidayRepository schoolHolidayRepository;

    @Spy
    private WorkingDayConfigMapper workingDayConfigMapper = new WorkingDayConfigMapper();

    @InjectMocks
    private CalendarServiceImpl calendarService;

    private UUID yearId;
    private AcademicYear academicYear;

    @BeforeEach
    void setUp() {
        yearId = UUID.randomUUID();
        academicYear = new AcademicYear("AY-2026", "2026-2027", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), true, AcademicYearStatus.ACTIVE);
        academicYear.setId(yearId);
    }

    @Test
    @DisplayName("Virtual Defaults — Monday to Saturday are WORKING, Sunday is WEEKLY_OFF when no config exists")
    void getDayStatus_virtualDefaults() {
        LocalDate monday = LocalDate.of(2026, 8, 3);
        LocalDate saturday = LocalDate.of(2026, 8, 8);
        LocalDate sunday = LocalDate.of(2026, 8, 9);

        when(academicYearRepository.findAcademicYearsForDate(any())).thenReturn(List.of(academicYear));
        when(workingDayConfigRepository.findByAcademicYearIdAndNotDeleted(yearId)).thenReturn(Optional.empty());
        when(schoolHolidayRepository.findHolidaysForDate(any(), any())).thenReturn(Collections.emptyList());

        assertThat(calendarService.getDayStatus(monday).status()).isEqualTo(DayStatus.WORKING);
        assertThat(calendarService.getDayStatus(saturday).status()).isEqualTo(DayStatus.WORKING);
        assertThat(calendarService.getDayStatus(sunday).status()).isEqualTo(DayStatus.WEEKLY_OFF);
    }

    @Test
    @DisplayName("Saturday Pattern ALL — All Saturdays in August 2026 (5 Saturdays) are WORKING")
    void saturdayPattern_ALL_fiveSaturdays() {
        WorkingDayConfig config = new WorkingDayConfig(academicYear, true, true, true, true, true, true, false, SaturdayPattern.ALL);

        when(academicYearRepository.findAcademicYearsForDate(any())).thenReturn(List.of(academicYear));
        when(workingDayConfigRepository.findByAcademicYearIdAndNotDeleted(yearId)).thenReturn(Optional.of(config));
        when(schoolHolidayRepository.findHolidaysForDate(any(), any())).thenReturn(Collections.emptyList());

        LocalDate[] saturdays = {
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 8),
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 8, 22),
                LocalDate.of(2026, 8, 29)
        };

        for (LocalDate sat : saturdays) {
            assertThat(calendarService.getDayStatus(sat).status())
                    .as("Saturday %s should be WORKING for ALL pattern", sat)
                    .isEqualTo(DayStatus.WORKING);
        }
    }

    @Test
    @DisplayName("Saturday Pattern FIRST_THIRD_FIFTH — In August 2026, 1st/3rd/5th are WORKING, 2nd/4th are WEEKLY_OFF")
    void saturdayPattern_FIRST_THIRD_FIFTH_august2026() {
        WorkingDayConfig config = new WorkingDayConfig(academicYear, true, true, true, true, true, true, false, SaturdayPattern.FIRST_THIRD_FIFTH);

        when(academicYearRepository.findAcademicYearsForDate(any())).thenReturn(List.of(academicYear));
        when(workingDayConfigRepository.findByAcademicYearIdAndNotDeleted(yearId)).thenReturn(Optional.of(config));
        when(schoolHolidayRepository.findHolidaysForDate(any(), any())).thenReturn(Collections.emptyList());

        assertThat(calendarService.getDayStatus(LocalDate.of(2026, 8, 1)).status()).isEqualTo(DayStatus.WORKING); // 1st
        assertThat(calendarService.getDayStatus(LocalDate.of(2026, 8, 8)).status()).isEqualTo(DayStatus.WEEKLY_OFF); // 2nd
        assertThat(calendarService.getDayStatus(LocalDate.of(2026, 8, 15)).status()).isEqualTo(DayStatus.WORKING); // 3rd
        assertThat(calendarService.getDayStatus(LocalDate.of(2026, 8, 22)).status()).isEqualTo(DayStatus.WEEKLY_OFF); // 4th
        assertThat(calendarService.getDayStatus(LocalDate.of(2026, 8, 29)).status()).isEqualTo(DayStatus.WORKING); // 5th
    }

    @Test
    @DisplayName("Saturday Pattern SECOND_FOURTH — In October 2026 (5 Saturdays), 2nd/4th are WORKING, 1st/3rd/5th are WEEKLY_OFF")
    void saturdayPattern_SECOND_FOURTH_october2026() {
        WorkingDayConfig config = new WorkingDayConfig(academicYear, true, true, true, true, true, true, false, SaturdayPattern.SECOND_FOURTH);

        when(academicYearRepository.findAcademicYearsForDate(any())).thenReturn(List.of(academicYear));
        when(workingDayConfigRepository.findByAcademicYearIdAndNotDeleted(yearId)).thenReturn(Optional.of(config));
        when(schoolHolidayRepository.findHolidaysForDate(any(), any())).thenReturn(Collections.emptyList());

        assertThat(calendarService.getDayStatus(LocalDate.of(2026, 10, 3)).status()).isEqualTo(DayStatus.WEEKLY_OFF); // 1st
        assertThat(calendarService.getDayStatus(LocalDate.of(2026, 10, 10)).status()).isEqualTo(DayStatus.WORKING);    // 2nd
        assertThat(calendarService.getDayStatus(LocalDate.of(2026, 10, 17)).status()).isEqualTo(DayStatus.WEEKLY_OFF); // 3rd
        assertThat(calendarService.getDayStatus(LocalDate.of(2026, 10, 24)).status()).isEqualTo(DayStatus.WORKING);    // 4th
        assertThat(calendarService.getDayStatus(LocalDate.of(2026, 10, 31)).status()).isEqualTo(DayStatus.WEEKLY_OFF); // 5th
    }

    @Test
    @DisplayName("Saturday Pattern in February 2026 (4 Saturdays) — SECOND_FOURTH correctly evaluates")
    void saturdayPattern_SECOND_FOURTH_february2026() {
        WorkingDayConfig config = new WorkingDayConfig(academicYear, true, true, true, true, true, true, false, SaturdayPattern.SECOND_FOURTH);

        when(academicYearRepository.findAcademicYearsForDate(any())).thenReturn(List.of(academicYear));
        when(workingDayConfigRepository.findByAcademicYearIdAndNotDeleted(yearId)).thenReturn(Optional.of(config));
        when(schoolHolidayRepository.findHolidaysForDate(any(), any())).thenReturn(Collections.emptyList());

        assertThat(calendarService.getDayStatus(LocalDate.of(2026, 2, 7)).status()).isEqualTo(DayStatus.WEEKLY_OFF); // 1st
        assertThat(calendarService.getDayStatus(LocalDate.of(2026, 2, 14)).status()).isEqualTo(DayStatus.WORKING);    // 2nd
        assertThat(calendarService.getDayStatus(LocalDate.of(2026, 2, 21)).status()).isEqualTo(DayStatus.WEEKLY_OFF); // 3rd
        assertThat(calendarService.getDayStatus(LocalDate.of(2026, 2, 28)).status()).isEqualTo(DayStatus.WORKING);    // 4th
    }

    @Test
    @DisplayName("Holiday Status Override — A working day with a holiday returns HOLIDAY and holiday details")
    void getDayStatus_holidayOverridesWorkingDay() {
        LocalDate holidayDate = LocalDate.of(2026, 8, 15);
        SchoolHoliday holiday = new SchoolHoliday("HOL-001", academicYear, "Independence Day", HolidayType.PUBLIC_HOLIDAY, holidayDate, holidayDate, null);

        when(academicYearRepository.findAcademicYearsForDate(holidayDate)).thenReturn(List.of(academicYear));
        when(workingDayConfigRepository.findByAcademicYearIdAndNotDeleted(yearId)).thenReturn(Optional.empty());
        when(schoolHolidayRepository.findHolidaysForDate(yearId, holidayDate)).thenReturn(List.of(holiday));

        DayStatusResponse status = calendarService.getDayStatus(holidayDate);

        assertThat(status.status()).isEqualTo(DayStatus.HOLIDAY);
        assertThat(status.holidayName()).isEqualTo("Independence Day");
        assertThat(status.holidayType()).isEqualTo(HolidayType.PUBLIC_HOLIDAY);
    }

    @Test
    @DisplayName("Count Working Days — Correctly excludes Sundays, weekly-off Saturdays, and holidays")
    void countWorkingDays_rangeCalculation() {
        LocalDate from = LocalDate.of(2026, 8, 10); // Mon
        LocalDate to = LocalDate.of(2026, 8, 16);   // Sun (Mon..Sun: Mon, Tue, Wed, Thu, Fri, Sat, Sun)

        // Aug 15 is Holiday
        SchoolHoliday holiday = new SchoolHoliday("HOL-001", academicYear, "Independence Day", HolidayType.PUBLIC_HOLIDAY, LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 15), null);

        // Virtual defaults: Mon..Sat working, Sun off. Aug 15 is holiday. So working days: Mon, Tue, Wed, Thu, Fri (5 days).
        when(academicYearRepository.findAcademicYearsForDate(any())).thenReturn(List.of(academicYear));
        when(workingDayConfigRepository.findByAcademicYearIdAndNotDeleted(yearId)).thenReturn(Optional.empty());
        when(schoolHolidayRepository.findActiveHolidaysInRange(yearId, from, to)).thenReturn(List.of(holiday));

        long count = calendarService.countWorkingDays(from, to);

        assertThat(count).isEqualTo(5);
    }

    @Test
    @DisplayName("Get Days Status — Date range > 366 days rejected with BusinessException")
    void getDaysStatus_rangeTooLarge_throwsBusinessException() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2027, 2, 1); // > 366 days

        assertThatThrownBy(() -> calendarService.getDaysStatus(from, to))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Date range cannot exceed 366 days");
    }

    @Test
    @DisplayName("Get Day Status — Date outside any academic year returns 404 (ResourceNotFoundException)")
    void getDayStatus_dateOutsideAcademicYear_throwsResourceNotFoundException() {
        LocalDate date = LocalDate.of(2025, 1, 1);
        when(academicYearRepository.findAcademicYearsForDate(date)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> calendarService.getDayStatus(date))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Get Working Day Config — Returns virtual defaults when no config row exists")
    void getWorkingDayConfig_virtualDefaults() {
        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(academicYear));
        when(workingDayConfigRepository.findByAcademicYearIdAndNotDeleted(yearId)).thenReturn(Optional.empty());

        WorkingDayConfigResponse response = calendarService.getWorkingDayConfig(yearId);

        assertThat(response.isDefault()).isTrue();
        assertThat(response.monday()).isTrue();
        assertThat(response.saturday()).isTrue();
        assertThat(response.sunday()).isFalse();
        assertThat(response.saturdayPattern()).isEqualTo(SaturdayPattern.ALL);
    }

    @Test
    @DisplayName("Update Working Day Config — Creates config on first PUT")
    void updateWorkingDayConfig_createsOnFirstPut() {
        WorkingDayConfigRequest req = new WorkingDayConfigRequest(true, true, true, true, true, true, false, SaturdayPattern.SECOND_FOURTH);

        when(academicYearRepository.findByIdAndNotDeleted(yearId)).thenReturn(Optional.of(academicYear));
        when(workingDayConfigRepository.findByAcademicYearIdAndNotDeleted(yearId)).thenReturn(Optional.empty());
        when(workingDayConfigRepository.save(any(WorkingDayConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkingDayConfigResponse response = calendarService.updateWorkingDayConfig(yearId, req);

        assertThat(response.saturdayPattern()).isEqualTo(SaturdayPattern.SECOND_FOURTH);
        assertThat(response.isDefault()).isFalse();
    }
}
