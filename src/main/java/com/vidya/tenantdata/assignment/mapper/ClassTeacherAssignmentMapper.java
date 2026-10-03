package com.vidya.tenantdata.assignment.mapper;

import com.vidya.tenantdata.assignment.dto.ClassTeacherAssignmentDetailResponse;
import com.vidya.tenantdata.assignment.dto.ClassTeacherAssignmentResponse;
import com.vidya.tenantdata.assignment.entity.ClassTeacherAssignment;

/**
 * Mapping utility between ClassTeacherAssignment entities and DTOs.
 *
 * <p>Mapping is kept separate from business logic; no database queries here.
 */
public final class ClassTeacherAssignmentMapper {

    private ClassTeacherAssignmentMapper() {}

    public static ClassTeacherAssignmentResponse toResponse(ClassTeacherAssignment entity) {
        if (entity == null) {
            return null;
        }

        String staffName = null;
        String staffEmail = null;
        String staffCode = null;
        java.util.UUID staffId = null;

        if (entity.getStaff() != null) {
            staffId = entity.getStaff().getId();
            staffCode = entity.getStaff().getStaffCode();
            if (entity.getStaff().getTenantUser() != null) {
                staffEmail = entity.getStaff().getTenantUser().getEmail();
                if (entity.getStaff().getTenantUser().getProfile() != null) {
                    staffName = entity.getStaff().getTenantUser().getProfile().getFullName();
                }
            }
        }

        return ClassTeacherAssignmentResponse.builder()
                .id(entity.getId())
                .assignmentCode(entity.getAssignmentCode())
                .staffId(staffId)
                .staffCode(staffCode)
                .staffName(staffName)
                .staffEmail(staffEmail)
                .academicYearId(entity.getAcademicYear() != null ? entity.getAcademicYear().getId() : null)
                .academicYearCode(entity.getAcademicYear() != null ? entity.getAcademicYear().getCode() : null)
                .academicClassId(entity.getAcademicClass() != null ? entity.getAcademicClass().getId() : null)
                .academicClassCode(entity.getAcademicClass() != null ? entity.getAcademicClass().getCode() : null)
                .academicClassName(entity.getAcademicClass() != null ? entity.getAcademicClass().getName() : null)
                .sectionId(entity.getSection() != null ? entity.getSection().getId() : null)
                .sectionCode(entity.getSection() != null ? entity.getSection().getCode() : null)
                .sectionName(entity.getSection() != null ? entity.getSection().getName() : null)
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .notes(entity.getNotes())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static ClassTeacherAssignmentDetailResponse toDetailResponse(ClassTeacherAssignment entity) {
        if (entity == null) {
            return null;
        }

        String staffName = null;
        String staffEmail = null;
        String staffCode = null;
        java.util.UUID staffId = null;

        if (entity.getStaff() != null) {
            staffId = entity.getStaff().getId();
            staffCode = entity.getStaff().getStaffCode();
            if (entity.getStaff().getTenantUser() != null) {
                staffEmail = entity.getStaff().getTenantUser().getEmail();
                if (entity.getStaff().getTenantUser().getProfile() != null) {
                    staffName = entity.getStaff().getTenantUser().getProfile().getFullName();
                }
            }
        }

        return ClassTeacherAssignmentDetailResponse.builder()
                .id(entity.getId())
                .assignmentCode(entity.getAssignmentCode())
                .staffId(staffId)
                .staffCode(staffCode)
                .staffName(staffName)
                .staffEmail(staffEmail)
                .academicYearId(entity.getAcademicYear() != null ? entity.getAcademicYear().getId() : null)
                .academicYearCode(entity.getAcademicYear() != null ? entity.getAcademicYear().getCode() : null)
                .academicClassId(entity.getAcademicClass() != null ? entity.getAcademicClass().getId() : null)
                .academicClassCode(entity.getAcademicClass() != null ? entity.getAcademicClass().getCode() : null)
                .academicClassName(entity.getAcademicClass() != null ? entity.getAcademicClass().getName() : null)
                .sectionId(entity.getSection() != null ? entity.getSection().getId() : null)
                .sectionCode(entity.getSection() != null ? entity.getSection().getCode() : null)
                .sectionName(entity.getSection() != null ? entity.getSection().getName() : null)
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .notes(entity.getNotes())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .version(entity.getVersion())
                .build();
    }
}
