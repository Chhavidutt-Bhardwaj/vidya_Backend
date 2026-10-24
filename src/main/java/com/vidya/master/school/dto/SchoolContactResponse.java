package com.vidya.master.school.dto;

import com.vidya.master.school.entity.SchoolContactDetail;
import com.vidya.master.school.entity.SchoolContactType;

import java.util.UUID;

public record SchoolContactResponse(
        UUID id,
        UUID schoolId,
        UUID branchId,
        SchoolContactType contactType,
        String fullName,
        String designation,
        String email,
        String phone,
        String alternatePhone,
        boolean isPrimary
) {
    public static SchoolContactResponse fromEntity(SchoolContactDetail entity) {
        return new SchoolContactResponse(
                entity.getId(),
                entity.getSchool().getId(),
                entity.getBranch() != null ? entity.getBranch().getId() : null,
                entity.getContactType(),
                entity.getFullName(),
                entity.getDesignation(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getAlternatePhone(),
                entity.isPrimaryContact()
        );
    }
}
