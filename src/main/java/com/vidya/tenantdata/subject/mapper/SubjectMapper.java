package com.vidya.tenantdata.subject.mapper;

import com.vidya.tenantdata.subject.dto.SubjectDetailResponse;
import com.vidya.tenantdata.subject.dto.SubjectResponse;
import com.vidya.tenantdata.subject.entity.Subject;

/**
 * Mapping utility between Subject entities and DTOs.
 */
public final class SubjectMapper {

    private SubjectMapper() {}

    public static SubjectResponse toResponse(Subject entity) {
        if (entity == null) {
            return null;
        }
        return SubjectResponse.builder()
                .id(entity.getId())
                .subjectCode(entity.getSubjectCode())
                .name(entity.getName())
                .shortName(entity.getShortName())
                .description(entity.getDescription())
                .subjectType(entity.getSubjectType())
                .status(entity.getStatus())
                .displayOrder(entity.getDisplayOrder())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static SubjectDetailResponse toDetailResponse(Subject entity) {
        if (entity == null) {
            return null;
        }
        return SubjectDetailResponse.builder()
                .id(entity.getId())
                .subjectCode(entity.getSubjectCode())
                .name(entity.getName())
                .shortName(entity.getShortName())
                .description(entity.getDescription())
                .subjectType(entity.getSubjectType())
                .status(entity.getStatus())
                .displayOrder(entity.getDisplayOrder())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .version(entity.getVersion())
                .build();
    }
}
