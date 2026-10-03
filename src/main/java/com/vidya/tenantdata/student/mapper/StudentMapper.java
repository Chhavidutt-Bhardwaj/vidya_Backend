package com.vidya.tenantdata.student.mapper;

import com.vidya.tenantdata.student.dto.CreateStudentRequest;
import com.vidya.tenantdata.student.dto.StudentDetailResponse;
import com.vidya.tenantdata.student.dto.StudentResponse;
import com.vidya.tenantdata.student.entity.Student;
import com.vidya.tenantdata.user.entity.TenantUser;
import com.vidya.tenantdata.user.mapper.TenantUserMapper;

/**
 * Mapper for converting between Student entities and DTOs.
 */
public final class StudentMapper {

    private StudentMapper() {}

    public static Student toEntity(CreateStudentRequest request, TenantUser tenantUser, String studentCode) {
        return new Student(
                studentCode,
                tenantUser,
                request.getAdmissionNumber(),
                request.getAdmissionDate()
        );
    }

    public static StudentResponse toResponse(Student student) {
        if (student == null) {
            return null;
        }

        String fullName = null;
        String email = null;
        String phone = null;
        java.util.UUID tenantUserId = null;

        if (student.getTenantUser() != null) {
            tenantUserId = student.getTenantUser().getId();
            email = student.getTenantUser().getEmail();
            phone = student.getTenantUser().getPhone();
            if (student.getTenantUser().getProfile() != null) {
                fullName = student.getTenantUser().getProfile().getFullName();
            }
        }

        return StudentResponse.builder()
                .id(student.getId())
                .studentCode(student.getStudentCode())
                .tenantUserId(tenantUserId)
                .admissionNumber(student.getAdmissionNumber())
                .admissionDate(student.getAdmissionDate())
                .status(student.getStatus())
                .fullName(fullName)
                .email(email)
                .phone(phone)
                .createdAt(student.getCreatedAt())
                .updatedAt(student.getUpdatedAt())
                .build();
    }

    public static StudentDetailResponse toDetailResponse(Student student) {
        if (student == null) {
            return null;
        }

        return StudentDetailResponse.builder()
                .id(student.getId())
                .studentCode(student.getStudentCode())
                .admissionNumber(student.getAdmissionNumber())
                .admissionDate(student.getAdmissionDate())
                .status(student.getStatus())
                .user(student.getTenantUser() != null ? TenantUserMapper.toDetailResponse(student.getTenantUser()) : null)
                .createdAt(student.getCreatedAt())
                .updatedAt(student.getUpdatedAt())
                .version(student.getVersion())
                .build();
    }
}
