package com.vidya.tenantdata.subject.mapper;

import com.vidya.tenantdata.subject.dto.CurriculumDetailResponse;
import com.vidya.tenantdata.subject.dto.CurriculumResponse;
import com.vidya.tenantdata.subject.dto.CurriculumSubjectResponse;
import com.vidya.tenantdata.subject.entity.Curriculum;
import com.vidya.tenantdata.subject.entity.CurriculumSubject;

import java.util.List;

/**
 * Mapping utility between Curriculum entities and DTOs.
 */
public final class CurriculumMapper {

    private CurriculumMapper() {}

    public static CurriculumResponse toResponse(Curriculum entity) {
        if (entity == null) {
            return null;
        }
        return CurriculumResponse.builder()
                .id(entity.getId())
                .curriculumCode(entity.getCurriculumCode())
                .academicYearId(entity.getAcademicYear() != null ? entity.getAcademicYear().getId() : null)
                .academicYearCode(entity.getAcademicYear() != null ? entity.getAcademicYear().getCode() : null)
                .academicYearName(entity.getAcademicYear() != null ? entity.getAcademicYear().getName() : null)
                .name(entity.getName())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static CurriculumDetailResponse toDetailResponse(Curriculum entity, List<CurriculumSubjectResponse> subjects) {
        if (entity == null) {
            return null;
        }
        return CurriculumDetailResponse.builder()
                .id(entity.getId())
                .curriculumCode(entity.getCurriculumCode())
                .academicYearId(entity.getAcademicYear() != null ? entity.getAcademicYear().getId() : null)
                .academicYearCode(entity.getAcademicYear() != null ? entity.getAcademicYear().getCode() : null)
                .academicYearName(entity.getAcademicYear() != null ? entity.getAcademicYear().getName() : null)
                .name(entity.getName())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .subjects(subjects != null ? subjects : List.of())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .version(entity.getVersion())
                .build();
    }

    public static CurriculumSubjectResponse toCurriculumSubjectResponse(CurriculumSubject entity) {
        if (entity == null) {
            return null;
        }
        return CurriculumSubjectResponse.builder()
                .id(entity.getId())
                .curriculumId(entity.getCurriculum() != null ? entity.getCurriculum().getId() : null)
                .curriculumCode(entity.getCurriculum() != null ? entity.getCurriculum().getCurriculumCode() : null)
                .subjectId(entity.getSubject() != null ? entity.getSubject().getId() : null)
                .subjectCode(entity.getSubject() != null ? entity.getSubject().getSubjectCode() : null)
                .subjectName(entity.getSubject() != null ? entity.getSubject().getName() : null)
                .subjectShortName(entity.getSubject() != null ? entity.getSubject().getShortName() : null)
                .subjectType(entity.getSubject() != null ? entity.getSubject().getSubjectType() : null)
                .academicClassId(entity.getAcademicClass() != null ? entity.getAcademicClass().getId() : null)
                .academicClassCode(entity.getAcademicClass() != null ? entity.getAcademicClass().getCode() : null)
                .academicClassName(entity.getAcademicClass() != null ? entity.getAcademicClass().getName() : null)
                .isCompulsory(entity.isCompulsory())
                .displayOrder(entity.getDisplayOrder())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
