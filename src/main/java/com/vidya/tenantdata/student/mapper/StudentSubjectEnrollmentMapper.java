package com.vidya.tenantdata.student.mapper;

import com.vidya.tenantdata.student.dto.StudentSubjectEnrollmentResponse;
import com.vidya.tenantdata.student.entity.StudentSubjectEnrollment;

public final class StudentSubjectEnrollmentMapper {

    private StudentSubjectEnrollmentMapper() {}

    public static StudentSubjectEnrollmentResponse toResponse(StudentSubjectEnrollment entity) {
        if (entity == null) {
            return null;
        }
        return StudentSubjectEnrollmentResponse.builder()
                .id(entity.getId())
                .subjectEnrollmentCode(entity.getSubjectEnrollmentCode())
                .enrollmentId(entity.getEnrollment() != null ? entity.getEnrollment().getId() : null)
                .subjectId(entity.getSubject() != null ? entity.getSubject().getId() : null)
                .subjectCode(entity.getSubject() != null ? entity.getSubject().getSubjectCode() : null)
                .subjectName(entity.getSubject() != null ? entity.getSubject().getName() : null)
                .subjectType(entity.getSubject() != null ? entity.getSubject().getSubjectType() : null)
                .classSubjectAssignmentId(entity.getClassSubjectAssignment() != null ? entity.getClassSubjectAssignment().getId() : null)
                .classSubjectAssignmentCode(entity.getClassSubjectAssignment() != null ? entity.getClassSubjectAssignment().getAssignmentCode() : null)
                .isCompulsory(entity.getClassSubjectAssignment() != null ? entity.getClassSubjectAssignment().isCompulsory() : null)
                .status(entity.getStatus())
                .effectiveFrom(entity.getEffectiveFrom())
                .effectiveTo(entity.getEffectiveTo())
                .remarks(entity.getRemarks())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .version(entity.getVersion())
                .build();
    }
}
