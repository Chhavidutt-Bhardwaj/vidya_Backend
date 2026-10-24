package com.vidya.tenantdata.examination.mapper;

import com.vidya.tenantdata.examination.dto.ExamTermResponse;
import com.vidya.tenantdata.examination.entity.ExamTerm;
import org.springframework.stereotype.Component;

@Component
public class ExamTermMapper {

    public ExamTermResponse toResponse(ExamTerm term) {
        if (term == null) return null;

        return ExamTermResponse.builder()
                .id(term.getId())
                .termCode(term.getTermCode())
                .academicYearId(term.getAcademicYear() != null ? term.getAcademicYear().getId() : null)
                .academicYearName(term.getAcademicYear() != null ? term.getAcademicYear().getName() : null)
                .name(term.getName())
                .startDate(term.getStartDate())
                .endDate(term.getEndDate())
                .weightagePercentage(term.getWeightagePercentage())
                .status(term.getStatus())
                .createdAt(term.getCreatedAt())
                .updatedAt(term.getUpdatedAt())
                .build();
    }
}
