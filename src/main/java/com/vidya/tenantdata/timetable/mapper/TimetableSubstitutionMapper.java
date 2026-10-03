package com.vidya.tenantdata.timetable.mapper;

import com.vidya.tenantdata.timetable.dto.SubstitutionResponse;
import com.vidya.tenantdata.timetable.entity.TimetableSubstitution;
import org.springframework.stereotype.Component;

@Component
public class TimetableSubstitutionMapper {

    public SubstitutionResponse toResponse(TimetableSubstitution sub, Boolean sameSubjectQualified) {
        if (sub == null) {
            return null;
        }

        String origTeacherName = null;
        if (sub.getOriginalTeacher() != null) {
            origTeacherName = sub.getOriginalTeacher().getStaffCode();
        }

        String origSubjectName = null;
        if (sub.getOriginalSubject() != null) {
            origSubjectName = sub.getOriginalSubject().getName();
        }

        String subTeacherName = null;
        if (sub.getSubstituteTeacher() != null) {
            subTeacherName = sub.getSubstituteTeacher().getStaffCode();
        }

        String subRoomName = null;
        if (sub.getSubstituteRoom() != null) {
            subRoomName = sub.getSubstituteRoom().getName();
        }

        return new SubstitutionResponse(
                sub.getId(),
                sub.getSubstitutionCode(),
                sub.getTimetable() != null ? sub.getTimetable().getId() : null,
                sub.getTimetable() != null ? sub.getTimetable().getTimetableCode() : null,
                sub.getSubstitutionDate(),
                sub.getPeriod() != null ? sub.getPeriod().getId() : null,
                sub.getPeriod() != null ? sub.getPeriod().getName() : null,
                sub.getPeriod() != null ? sub.getPeriod().getPeriodSequence() : null,
                sub.getAcademicClass() != null ? sub.getAcademicClass().getId() : null,
                sub.getAcademicClass() != null ? sub.getAcademicClass().getName() : null,
                sub.getSection() != null ? sub.getSection().getId() : null,
                sub.getSection() != null ? sub.getSection().getName() : null,
                sub.getSubstitutionType(),
                sub.getOriginalTeacher() != null ? sub.getOriginalTeacher().getId() : null,
                origTeacherName,
                sub.getOriginalSubject() != null ? sub.getOriginalSubject().getId() : null,
                origSubjectName,
                sub.getSubstituteTeacher() != null ? sub.getSubstituteTeacher().getId() : null,
                subTeacherName,
                sub.getSubstituteRoom() != null ? sub.getSubstituteRoom().getId() : null,
                subRoomName,
                sub.getReason(),
                sub.getStatus(),
                sameSubjectQualified,
                sub.getCreatedAt(),
                sub.getUpdatedAt()
        );
    }

    public com.vidya.tenantdata.timetable.dto.TimetableGridSubstitutionResponse toGridSubstitution(TimetableSubstitution sub) {
        if (sub == null) {
            return null;
        }

        String subTeacherName = null;
        if (sub.getSubstituteTeacher() != null) {
            if (sub.getSubstituteTeacher().getTenantUser() != null && sub.getSubstituteTeacher().getTenantUser().getProfile() != null) {
                subTeacherName = sub.getSubstituteTeacher().getTenantUser().getProfile().getFullName();
            } else {
                subTeacherName = sub.getSubstituteTeacher().getStaffCode();
            }
        }

        String subRoomName = null;
        if (sub.getSubstituteRoom() != null) {
            subRoomName = sub.getSubstituteRoom().getName();
        }

        return new com.vidya.tenantdata.timetable.dto.TimetableGridSubstitutionResponse(
                sub.getId(),
                sub.getSubstitutionCode(),
                sub.getSubstitutionType(),
                sub.getSubstitutionDate(),
                sub.getSubstituteTeacher() != null ? sub.getSubstituteTeacher().getId() : null,
                subTeacherName,
                sub.getSubstituteRoom() != null ? sub.getSubstituteRoom().getId() : null,
                subRoomName,
                sub.getReason()
        );
    }
}
