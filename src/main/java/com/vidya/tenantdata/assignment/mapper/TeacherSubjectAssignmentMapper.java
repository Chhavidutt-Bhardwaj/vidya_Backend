package com.vidya.tenantdata.assignment.mapper;

import com.vidya.tenantdata.assignment.dto.TeacherAssignmentDetailResponse;
import com.vidya.tenantdata.assignment.dto.TeacherAssignmentResponse;
import com.vidya.tenantdata.assignment.entity.TeacherSubjectAssignment;

/**
 * Mapping utility between TeacherSubjectAssignment entities and DTOs.
 */
public final class TeacherSubjectAssignmentMapper {

    private TeacherSubjectAssignmentMapper() {}

    public static TeacherAssignmentResponse toResponse(TeacherSubjectAssignment entity) {
        if (entity == null) {
            return null;
        }

        String teacherName = null;
        String teacherEmail = null;
        String teacherStaffCode = null;
        java.util.UUID teacherId = null;

        if (entity.getTeacher() != null) {
            teacherId = entity.getTeacher().getId();
            teacherStaffCode = entity.getTeacher().getStaffCode();
            if (entity.getTeacher().getTenantUser() != null) {
                teacherEmail = entity.getTeacher().getTenantUser().getEmail();
                if (entity.getTeacher().getTenantUser().getProfile() != null) {
                    teacherName = entity.getTeacher().getTenantUser().getProfile().getFullName();
                }
            }
        }

        return TeacherAssignmentResponse.builder()
                .id(entity.getId())
                .assignmentCode(entity.getAssignmentCode())
                .teacherId(teacherId)
                .teacherStaffCode(teacherStaffCode)
                .teacherName(teacherName)
                .teacherEmail(teacherEmail)
                .subjectId(entity.getSubject() != null ? entity.getSubject().getId() : null)
                .subjectCode(entity.getSubject() != null ? entity.getSubject().getSubjectCode() : null)
                .subjectName(entity.getSubject() != null ? entity.getSubject().getName() : null)
                .academicYearId(entity.getAcademicYear() != null ? entity.getAcademicYear().getId() : null)
                .academicYearCode(entity.getAcademicYear() != null ? entity.getAcademicYear().getCode() : null)
                .academicClassId(entity.getAcademicClass() != null ? entity.getAcademicClass().getId() : null)
                .academicClassCode(entity.getAcademicClass() != null ? entity.getAcademicClass().getCode() : null)
                .academicClassName(entity.getAcademicClass() != null ? entity.getAcademicClass().getName() : null)
                .sectionId(entity.getSection() != null ? entity.getSection().getId() : null)
                .sectionCode(entity.getSection() != null ? entity.getSection().getCode() : null)
                .sectionName(entity.getSection() != null ? entity.getSection().getName() : null)
                .curriculumId(entity.getCurriculum() != null ? entity.getCurriculum().getId() : null)
                .curriculumCode(entity.getCurriculum() != null ? entity.getCurriculum().getCurriculumCode() : null)
                .isPrimary(entity.isPrimary())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static TeacherAssignmentDetailResponse toDetailResponse(TeacherSubjectAssignment entity) {
        if (entity == null) {
            return null;
        }

        String teacherName = null;
        String teacherEmail = null;
        String teacherStaffCode = null;
        java.util.UUID teacherId = null;

        if (entity.getTeacher() != null) {
            teacherId = entity.getTeacher().getId();
            teacherStaffCode = entity.getTeacher().getStaffCode();
            if (entity.getTeacher().getTenantUser() != null) {
                teacherEmail = entity.getTeacher().getTenantUser().getEmail();
                if (entity.getTeacher().getTenantUser().getProfile() != null) {
                    teacherName = entity.getTeacher().getTenantUser().getProfile().getFullName();
                }
            }
        }

        return TeacherAssignmentDetailResponse.builder()
                .id(entity.getId())
                .assignmentCode(entity.getAssignmentCode())
                .teacherId(teacherId)
                .teacherStaffCode(teacherStaffCode)
                .teacherName(teacherName)
                .teacherEmail(teacherEmail)
                .subjectId(entity.getSubject() != null ? entity.getSubject().getId() : null)
                .subjectCode(entity.getSubject() != null ? entity.getSubject().getSubjectCode() : null)
                .subjectName(entity.getSubject() != null ? entity.getSubject().getName() : null)
                .academicYearId(entity.getAcademicYear() != null ? entity.getAcademicYear().getId() : null)
                .academicYearCode(entity.getAcademicYear() != null ? entity.getAcademicYear().getCode() : null)
                .academicClassId(entity.getAcademicClass() != null ? entity.getAcademicClass().getId() : null)
                .academicClassCode(entity.getAcademicClass() != null ? entity.getAcademicClass().getCode() : null)
                .academicClassName(entity.getAcademicClass() != null ? entity.getAcademicClass().getName() : null)
                .sectionId(entity.getSection() != null ? entity.getSection().getId() : null)
                .sectionCode(entity.getSection() != null ? entity.getSection().getCode() : null)
                .sectionName(entity.getSection() != null ? entity.getSection().getName() : null)
                .curriculumId(entity.getCurriculum() != null ? entity.getCurriculum().getId() : null)
                .curriculumCode(entity.getCurriculum() != null ? entity.getCurriculum().getCurriculumCode() : null)
                .isPrimary(entity.isPrimary())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .version(entity.getVersion())
                .build();
    }
}
