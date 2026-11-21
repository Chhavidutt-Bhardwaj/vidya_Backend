package com.vidya.tenantdata.academic.setup.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.service.AcademicYearService;
import com.vidya.tenantdata.academic.setup.dto.SetupStatusResponse;
import com.vidya.tenantdata.academic.setup.dto.SetupStatusResponse.Level;
import com.vidya.tenantdata.academic.setup.dto.SetupStatusResponse.Step;
import com.vidya.tenantdata.academic.setup.dto.YearSetupActions.StartYearRequest;
import com.vidya.tenantdata.academic.setup.dto.YearSetupActions.StartYearResponse;
import com.vidya.tenantdata.assignment.service.ClassTeacherAssignmentService;
import com.vidya.tenantdata.timetable.dto.UpdateTimetableStatusRequest;
import com.vidya.tenantdata.timetable.enums.TimetableStatus;
import com.vidya.tenantdata.timetable.service.TimetableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("YearSetupActionService — starting the year")
class YearSetupActionServiceTest {

    @Mock private AcademicYearRepository yearRepository;
    @Mock private AcademicYearService academicYearService;
    @Mock private AcademicSetupService setupService;
    @Mock private ClassTeacherAssignmentService classTeacherAssignmentService;
    @Mock private TimetableService timetableService;

    private YearSetupActionService service;
    private AcademicYear year;

    @BeforeEach
    void setUp() {
        service = new YearSetupActionService(yearRepository, academicYearService, setupService, classTeacherAssignmentService, timetableService);
        year = new AcademicYear("AY27", "AY27", LocalDate.of(2027, 4, 1), LocalDate.of(2028, 3, 31), false, AcademicYearStatus.DRAFT);
        year.setId(UUID.randomUUID());
        when(yearRepository.findByIdAndNotDeleted(year.getId())).thenReturn(Optional.of(year));
    }

    @Test
    @DisplayName("refuses to start while required steps are missing")
    void incomplete() {
        when(setupService.status(year.getId())).thenReturn(status(false));

        assertThatThrownBy(() -> service.start(year.getId(), null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Sections for this year");
        verify(academicYearService, never()).activateAcademicYear(any());
    }

    @Test
    @DisplayName("activates, makes current and activates the chosen timetable")
    void starts() {
        UUID timetable = UUID.randomUUID();
        when(setupService.status(year.getId())).thenReturn(status(true));

        StartYearResponse r = service.start(year.getId(), new StartYearRequest(null, timetable, null));

        verify(academicYearService).activateAcademicYear(year.getId());
        verify(academicYearService).setCurrentAcademicYear(year.getId());
        verify(timetableService).updateTimetableStatus(timetable, new UpdateTimetableStatusRequest(TimetableStatus.ACTIVE, true));
        assertThat(r.activatedTimetableId()).isEqualTo(timetable);
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    @DisplayName("force starts an incomplete year with a warning")
    void force() {
        when(setupService.status(year.getId())).thenReturn(status(false));

        StartYearResponse r = service.start(year.getId(), new StartYearRequest(false, UUID.randomUUID(), true));

        assertThat(r.warnings()).anyMatch(w -> w.contains("incomplete"));
        verify(academicYearService, never()).setCurrentAcademicYear(any());
    }

    @Test
    @DisplayName("closed years cannot be started")
    void closed() {
        year.setStatus(AcademicYearStatus.CLOSED);
        assertThatThrownBy(() -> service.start(year.getId(), null)).isInstanceOf(BusinessException.class);
    }

    private SetupStatusResponse status(boolean sectionsDone) {
        List<Step> steps = List.of(
                new Step("CLASSES", "Classes (grades)", Level.REQUIRED, true, 10, 0, "", ""),
                new Step("SECTIONS", "Sections for this year", Level.REQUIRED, sectionsDone, sectionsDone ? 20 : 0, 0, "", ""));
        return new SetupStatusResponse(year.getId(), year.getCode(), year.getName(), year.getStartDate(), year.getEndDate(),
                year.getStatus(), false, sectionsDone ? 2 : 1, 2, 50, sectionsDone, steps);
    }
}
