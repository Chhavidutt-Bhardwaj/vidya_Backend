package com.vidya.tenantdata.timetable.mapper;

import com.vidya.tenantdata.timetable.dto.TimetableDetailResponse;
import com.vidya.tenantdata.timetable.dto.TimetableEntryResponse;
import com.vidya.tenantdata.timetable.dto.TimetableResponse;
import com.vidya.tenantdata.timetable.entity.Timetable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TimetableMapper {

    private final BellScheduleMapper bellScheduleMapper;
    private final TimetableEntryMapper timetableEntryMapper;

    public TimetableResponse toResponse(Timetable timetable) {
        if (timetable == null) {
            return null;
        }
        int totalEntries = timetable.getEntries() != null
                ? (int) timetable.getEntries().stream().filter(e -> !e.isDeleted()).count()
                : 0;

        return new TimetableResponse(
                timetable.getId(),
                timetable.getTimetableCode(),
                timetable.getAcademicYear() != null ? timetable.getAcademicYear().getId() : null,
                timetable.getAcademicYear() != null ? timetable.getAcademicYear().getCode() : null,
                timetable.getAcademicYear() != null ? timetable.getAcademicYear().getName() : null,
                timetable.getBellSchedule() != null ? timetable.getBellSchedule().getId() : null,
                timetable.getBellSchedule() != null ? timetable.getBellSchedule().getScheduleCode() : null,
                timetable.getBellSchedule() != null ? timetable.getBellSchedule().getName() : null,
                timetable.getName(),
                timetable.getDescription(),
                timetable.getStatus(),
                timetable.getEffectiveFrom(),
                timetable.getEffectiveTo(),
                totalEntries,
                timetable.getCreatedAt(),
                timetable.getUpdatedAt(),
                timetable.getVersion()
        );
    }

    public TimetableDetailResponse toDetailResponse(Timetable timetable) {
        if (timetable == null) {
            return null;
        }
        List<TimetableEntryResponse> entries = timetable.getEntries() != null
                ? timetable.getEntries().stream()
                .filter(e -> !e.isDeleted())
                .map(timetableEntryMapper::toResponse)
                .toList()
                : Collections.emptyList();

        return new TimetableDetailResponse(
                timetable.getId(),
                timetable.getTimetableCode(),
                timetable.getAcademicYear() != null ? timetable.getAcademicYear().getId() : null,
                timetable.getAcademicYear() != null ? timetable.getAcademicYear().getCode() : null,
                timetable.getAcademicYear() != null ? timetable.getAcademicYear().getName() : null,
                bellScheduleMapper.toResponse(timetable.getBellSchedule()),
                timetable.getName(),
                timetable.getDescription(),
                timetable.getStatus(),
                timetable.getEffectiveFrom(),
                timetable.getEffectiveTo(),
                entries,
                timetable.getCreatedAt(),
                timetable.getUpdatedAt(),
                timetable.getVersion()
        );
    }
}
