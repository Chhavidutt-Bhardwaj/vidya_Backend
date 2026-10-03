package com.vidya.tenantdata.timetable.mapper;

import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.timetable.dto.TimetableEntryResponse;
import com.vidya.tenantdata.timetable.dto.TimetableGridSlotResponse;
import com.vidya.tenantdata.timetable.entity.BellSchedulePeriod;
import com.vidya.tenantdata.timetable.entity.TimetableEntry;
import org.springframework.stereotype.Component;

@Component
public class TimetableEntryMapper {

    public TimetableEntryResponse toResponse(TimetableEntry entry) {
        if (entry == null) {
            return null;
        }
        return new TimetableEntryResponse(
                entry.getId(),
                entry.getEntryCode(),
                entry.getTimetable() != null ? entry.getTimetable().getId() : null,
                entry.getAcademicYear() != null ? entry.getAcademicYear().getId() : null,
                entry.getPeriod() != null ? entry.getPeriod().getId() : null,
                entry.getPeriod() != null ? entry.getPeriod().getPeriodCode() : null,
                entry.getPeriod() != null ? entry.getPeriod().getName() : null,
                entry.getPeriod() != null ? entry.getPeriod().getPeriodSequence() : 0,
                entry.getPeriod() != null ? entry.getPeriod().getStartTime() : null,
                entry.getPeriod() != null ? entry.getPeriod().getEndTime() : null,
                entry.getPeriod() != null ? entry.getPeriod().getPeriodType() : null,
                entry.getDayOfWeek(),
                entry.getAcademicClass() != null ? entry.getAcademicClass().getId() : null,
                entry.getAcademicClass() != null ? entry.getAcademicClass().getCode() : null,
                entry.getAcademicClass() != null ? entry.getAcademicClass().getName() : null,
                entry.getSection() != null ? entry.getSection().getId() : null,
                entry.getSection() != null ? entry.getSection().getCode() : null,
                entry.getSection() != null ? entry.getSection().getName() : null,
                entry.getSubject() != null ? entry.getSubject().getId() : null,
                entry.getSubject() != null ? entry.getSubject().getSubjectCode() : null,
                entry.getSubject() != null ? entry.getSubject().getName() : null,
                entry.getTeacherAssignment() != null ? entry.getTeacherAssignment().getId() : null,
                entry.getTeacher() != null ? entry.getTeacher().getId() : null,
                entry.getTeacher() != null ? entry.getTeacher().getStaffCode() : null,
                getTeacherName(entry.getTeacher()),
                entry.getClassSubjectAssignment() != null ? entry.getClassSubjectAssignment().getId() : null,
                entry.getRoom() != null ? entry.getRoom().getId() : null,
                entry.getRoom() != null ? entry.getRoom().getRoomCode() : null,
                entry.getRoom() != null ? entry.getRoom().getName() : null,
                entry.getEntryType(),
                entry.getCreatedAt(),
                entry.getUpdatedAt(),
                entry.getVersion()
        );
    }

    public TimetableGridSlotResponse toGridSlot(BellSchedulePeriod period, TimetableEntry entry) {
        if (period == null) {
            return null;
        }
        if (entry == null) {
            return new TimetableGridSlotResponse(
                    period.getId(),
                    period.getPeriodCode(),
                    period.getName(),
                    period.getPeriodSequence(),
                    period.getStartTime(),
                    period.getEndTime(),
                    period.getPeriodType(),
                    null, null, null, null, null, null, null, null, null, null, null, null, null, null, null
            );
        }

        return new TimetableGridSlotResponse(
                period.getId(),
                period.getPeriodCode(),
                period.getName(),
                period.getPeriodSequence(),
                period.getStartTime(),
                period.getEndTime(),
                period.getPeriodType(),
                entry.getId(),
                entry.getEntryCode(),
                entry.getSubject() != null ? entry.getSubject().getId() : null,
                entry.getSubject() != null ? entry.getSubject().getSubjectCode() : null,
                entry.getSubject() != null ? entry.getSubject().getName() : null,
                entry.getTeacher() != null ? entry.getTeacher().getId() : null,
                getTeacherName(entry.getTeacher()),
                entry.getRoom() != null ? entry.getRoom().getId() : null,
                entry.getRoom() != null ? entry.getRoom().getRoomCode() : null,
                entry.getRoom() != null ? entry.getRoom().getName() : null,
                entry.getAcademicClass() != null ? entry.getAcademicClass().getId() : null,
                entry.getAcademicClass() != null ? entry.getAcademicClass().getName() : null,
                entry.getSection() != null ? entry.getSection().getId() : null,
                entry.getSection() != null ? entry.getSection().getName() : null,
                entry.getEntryType()
        );
    }

    private String getTeacherName(Staff teacher) {
        if (teacher == null) {
            return null;
        }
        if (teacher.getTenantUser() != null && teacher.getTenantUser().getProfile() != null) {
            return teacher.getTenantUser().getProfile().getFullName();
        }
        return teacher.getStaffCode();
    }
}
