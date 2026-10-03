package com.vidya.tenantdata.assignment.mapper;

import com.vidya.tenantdata.assignment.dto.ClassSubjectAssignmentDetailResponse;
import com.vidya.tenantdata.assignment.dto.ClassSubjectAssignmentResponse;
import com.vidya.tenantdata.assignment.entity.ClassSubjectAssignment;

/**
 * Mapping utility between ClassSubjectAssignment entities and DTOs.
 */
public final class ClassSubjectAssignmentMapper {

    private ClassSubjectAssignmentMapper() {}

    public static ClassSubjectAssignmentResponse toResponse(ClassSubjectAssignment entity) {
        if (entity == null) {
            return null;
        }

        return ClassSubjectAssignmentResponse.builder()
                .id(entity.getId())
                .assignmentCode(entity.getAssignmentCode())
                .academicYearId(entity.getAcademicYear() != null ? entity.getAcademicYear().getId() : null)
                .academicYearCode(entity.getAcademicYear() != null ? entity.getAcademicYear().getCode() : null)
                .academicClassId(entity.getAcademicClass() != null ? entity.getAcademicClass().getId() : null)
                .academicClassCode(entity.getAcademicClass() != null ? entity.getAcademicClass().getCode() : null)
                .academicClassName(entity.getAcademicClass() != null ? entity.getAcademicClass().getName() : null)
                .sectionId(entity.getSection() != null ? entity.getSection().getId() : null)
                .sectionCode(entity.getSection() != null ? entity.getSection().getCode() : null)
                .sectionName(entity.getSection() != null ? entity.getSection().getName() : null)
                .subjectId(entity.getSubject() != null ? entity.getSubject().getId() : null)
                .subjectCode(entity.getSubject() != null ? entity.getSubject().getSubjectCode() : null)
                .subjectName(entity.getSubject() != null ? entity.getSubject().getName() : null)
                .curriculumId(entity.getCurriculum() != null ? entity.getCurriculum().getId() : null)
                .curriculumCode(entity.getCurriculum() != null ? entity.getCurriculum().getCurriculumCode() : null)
                .isCompulsory(entity.isCompulsory())
                .displayOrder(entity.getDisplayOrder())
                .periodsPerWeek(entity.getPeriodsPerWeek())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static ClassSubjectAssignmentDetailResponse toDetailResponse(ClassSubjectAssignment entity) {
        if (entity == null) {
            return null;
        }

        return ClassSubjectAssignmentDetailResponse.builder()
                .id(entity.getId())
                .assignmentCode(entity.getAssignmentCode())
                .academicYearId(entity.getAcademicYear() != null ? entity.getAcademicYear().getId() : null)
                .academicYearCode(entity.getAcademicYear() != null ? entity.getAcademicYear().getCode() : null)
                .academicClassId(entity.getAcademicClass() != null ? entity.getAcademicClass().getId() : null)
                .academicClassCode(entity.getAcademicClass() != null ? entity.getAcademicClass().getCode() : null)
                .academicClassName(entity.getAcademicClass() != null ? entity.getAcademicClass().getName() : null)
                .sectionId(entity.getSection() != null ? entity.getSection().getId() : null)
                .sectionCode(entity.getSection() != null ? entity.getSection().getCode() : null)
                .sectionName(entity.getSection() != null ? entity.getSection().getName() : null)
                .subjectId(entity.getSubject() != null ? entity.getSubject().getId() : null)
                .subjectCode(entity.getSubject() != null ? entity.getSubject().getSubjectCode() : null)
                .subjectName(entity.getSubject() != null ? entity.getSubject().getName() : null)
                .curriculumId(entity.getCurriculum() != null ? entity.getCurriculum().getId() : null)
                .curriculumCode(entity.getCurriculum() != null ? entity.getCurriculum().getCurriculumCode() : null)
                .isCompulsory(entity.isCompulsory())
                .displayOrder(entity.getDisplayOrder())
                .periodsPerWeek(entity.getPeriodsPerWeek())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .version(entity.getVersion())
                .build();
    }
}
