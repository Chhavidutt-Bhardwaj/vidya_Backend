package com.vidya.tenantdata.student.mapper;

import com.vidya.tenantdata.student.dto.CreateStudentGuardianRequest;
import com.vidya.tenantdata.student.dto.StudentGuardianResponse;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.student.entity.StudentGuardian;

public final class StudentGuardianMapper {

    private StudentGuardianMapper() {}

    public static StudentGuardian toEntity(CreateStudentGuardianRequest request, Student student, String guardianCode) {
        if (request == null) {
            return null;
        }
        return new StudentGuardian(
                guardianCode,
                student,
                request.getGuardianName(),
                request.getRelationshipType(),
                request.getPhone(),
                request.getEmail(),
                request.getOccupation(),
                request.getAnnualIncome(),
                request.getAddressLine1(),
                request.getAddressLine2(),
                request.getCity(),
                request.getState(),
                request.getPostalCode(),
                Boolean.TRUE.equals(request.getIsPrimary()),
                Boolean.TRUE.equals(request.getIsEmergencyContact())
        );
    }

    public static StudentGuardianResponse toResponse(StudentGuardian entity) {
        if (entity == null) {
            return null;
        }
        return StudentGuardianResponse.builder()
                .id(entity.getId())
                .guardianCode(entity.getGuardianCode())
                .studentId(entity.getStudent() != null ? entity.getStudent().getId() : null)
                .guardianName(entity.getGuardianName())
                .relationshipType(entity.getRelationshipType())
                .phone(entity.getPhone())
                .email(entity.getEmail())
                .occupation(entity.getOccupation())
                .annualIncome(entity.getAnnualIncome())
                .addressLine1(entity.getAddressLine1())
                .addressLine2(entity.getAddressLine2())
                .city(entity.getCity())
                .state(entity.getState())
                .postalCode(entity.getPostalCode())
                .isPrimary(entity.isPrimary())
                .isEmergencyContact(entity.isEmergencyContact())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .version(entity.getVersion())
                .build();
    }
}
