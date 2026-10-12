package com.vidya.tenantdata.attendance.mapper;

import com.vidya.tenantdata.attendance.dto.AttendanceDetailResponse;
import com.vidya.tenantdata.attendance.dto.AttendanceResponse;
import com.vidya.tenantdata.attendance.dto.DailyAttendanceEntryResponse;
import com.vidya.tenantdata.attendance.entity.StudentAttendance;
import com.vidya.tenantdata.student.entity.StudentAcademicEnrollment;

import java.util.UUID;

/**
 * Mapping utility between StudentAttendance entities and DTOs.
 *
 * <p>No database queries are performed here — only field projections.
 */
public final class StudentAttendanceMapper {

    private StudentAttendanceMapper() {}

    // ── Summary response (list/paginated views) ───────────────────────────────

    public static AttendanceResponse toResponse(StudentAttendance entity) {
        if (entity == null) {
            return null;
        }

        String studentCode = null;
        String studentName = null;
        UUID studentId = null;

        if (entity.getStudent() != null) {
            studentId = entity.getStudent().getId();
            studentCode = entity.getStudent().getStudentCode();
            if (entity.getStudent().getTenantUser() != null
                    && entity.getStudent().getTenantUser().getProfile() != null) {
                studentName = entity.getStudent().getTenantUser().getProfile().getFullName();
            }
        }

        UUID markedByStaffId = entity.getMarkedByStaff() != null ? entity.getMarkedByStaff().getId() : null;

        return AttendanceResponse.builder()
                .id(entity.getId())
                .attendanceCode(entity.getAttendanceCode())
                .studentId(studentId)
                .studentCode(studentCode)
                .studentName(studentName)
                .academicYearId(entity.getAcademicYear() != null ? entity.getAcademicYear().getId() : null)
                .academicYearCode(entity.getAcademicYear() != null ? entity.getAcademicYear().getCode() : null)
                .academicClassId(entity.getAcademicClass() != null ? entity.getAcademicClass().getId() : null)
                .academicClassName(entity.getAcademicClass() != null ? entity.getAcademicClass().getName() : null)
                .sectionId(entity.getSection() != null ? entity.getSection().getId() : null)
                .sectionName(entity.getSection() != null ? entity.getSection().getName() : null)
                .attendanceDate(entity.getAttendanceDate())
                .status(entity.getStatus())
                .remarks(entity.getRemarks())
                .nonWorkingDayOverride(entity.isNonWorkingDayOverride())
                .overrideReason(entity.getOverrideReason())
                .markedByStaffId(markedByStaffId)
                .markedAt(entity.getMarkedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    // ── Detail response (single GET / create / update) ────────────────────────

    public static AttendanceDetailResponse toDetailResponse(StudentAttendance entity) {
        if (entity == null) {
            return null;
        }

        String studentCode = null;
        String studentName = null;
        UUID studentId = null;

        if (entity.getStudent() != null) {
            studentId = entity.getStudent().getId();
            studentCode = entity.getStudent().getStudentCode();
            if (entity.getStudent().getTenantUser() != null
                    && entity.getStudent().getTenantUser().getProfile() != null) {
                studentName = entity.getStudent().getTenantUser().getProfile().getFullName();
            }
        }

        UUID markedByStaffId = null;
        String markedByStaffCode = null;
        String markedByStaffName = null;

        if (entity.getMarkedByStaff() != null) {
            markedByStaffId = entity.getMarkedByStaff().getId();
            markedByStaffCode = entity.getMarkedByStaff().getStaffCode();
            if (entity.getMarkedByStaff().getTenantUser() != null
                    && entity.getMarkedByStaff().getTenantUser().getProfile() != null) {
                markedByStaffName = entity.getMarkedByStaff().getTenantUser().getProfile().getFullName();
            }
        }

        return AttendanceDetailResponse.builder()
                .id(entity.getId())
                .attendanceCode(entity.getAttendanceCode())
                .studentId(studentId)
                .studentCode(studentCode)
                .studentName(studentName)
                .academicYearId(entity.getAcademicYear() != null ? entity.getAcademicYear().getId() : null)
                .academicYearCode(entity.getAcademicYear() != null ? entity.getAcademicYear().getCode() : null)
                .academicYearName(entity.getAcademicYear() != null ? entity.getAcademicYear().getName() : null)
                .academicClassId(entity.getAcademicClass() != null ? entity.getAcademicClass().getId() : null)
                .academicClassCode(entity.getAcademicClass() != null ? entity.getAcademicClass().getCode() : null)
                .academicClassName(entity.getAcademicClass() != null ? entity.getAcademicClass().getName() : null)
                .sectionId(entity.getSection() != null ? entity.getSection().getId() : null)
                .sectionCode(entity.getSection() != null ? entity.getSection().getCode() : null)
                .sectionName(entity.getSection() != null ? entity.getSection().getName() : null)
                .attendanceDate(entity.getAttendanceDate())
                .status(entity.getStatus())
                .remarks(entity.getRemarks())
                .nonWorkingDayOverride(entity.isNonWorkingDayOverride())
                .overrideReason(entity.getOverrideReason())
                .markedByStaffId(markedByStaffId)
                .markedByStaffCode(markedByStaffCode)
                .markedByStaffName(markedByStaffName)
                .markedAt(entity.getMarkedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .version(entity.getVersion())
                .build();
    }

    // ── Daily attendance sheet entry ──────────────────────────────────────────

    /**
     * Maps a StudentAttendance to a daily entry. Used for the daily section attendance view.
     *
     * @param entity     the attendance record (may be null if student has no record yet)
     * @param enrollment the student's enrollment in this section (for student info + roll number)
     */
    public static DailyAttendanceEntryResponse toDailyEntry(StudentAttendance entity,
                                                             StudentAcademicEnrollment enrollment) {
        UUID studentId = enrollment.getStudent().getId();
        String studentCode = enrollment.getStudent().getStudentCode();
        String studentName = null;
        if (enrollment.getStudent().getTenantUser() != null
                && enrollment.getStudent().getTenantUser().getProfile() != null) {
            studentName = enrollment.getStudent().getTenantUser().getProfile().getFullName();
        }

        if (entity == null) {
            // Student has no attendance record yet for this date
            return DailyAttendanceEntryResponse.builder()
                    .attendanceId(null)
                    .attendanceCode(null)
                    .studentId(studentId)
                    .studentCode(studentCode)
                    .studentName(studentName)
                    .rollNumber(enrollment.getRollNumber())
                    .attendanceDate(null)
                    .status(null)
                    .remarks(null)
                    .nonWorkingDayOverride(false)
                    .overrideReason(null)
                    .build();
        }

        return DailyAttendanceEntryResponse.builder()
                .attendanceId(entity.getId())
                .attendanceCode(entity.getAttendanceCode())
                .studentId(studentId)
                .studentCode(studentCode)
                .studentName(studentName)
                .rollNumber(enrollment.getRollNumber())
                .attendanceDate(entity.getAttendanceDate())
                .status(entity.getStatus())
                .remarks(entity.getRemarks())
                .nonWorkingDayOverride(entity.isNonWorkingDayOverride())
                .overrideReason(entity.getOverrideReason())
                .build();
    }
}
