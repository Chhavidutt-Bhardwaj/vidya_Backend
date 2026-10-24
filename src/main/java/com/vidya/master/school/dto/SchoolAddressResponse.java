package com.vidya.master.school.dto;

import com.vidya.master.school.entity.SchoolAddress;
import com.vidya.master.school.entity.SchoolAddressType;

import java.math.BigDecimal;
import java.util.UUID;

public record SchoolAddressResponse(
        UUID id,
        UUID schoolId,
        UUID branchId,
        SchoolAddressType addressType,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String pincode,
        String country,
        String landmark,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean isPrimary
) {
    public static SchoolAddressResponse fromEntity(SchoolAddress entity) {
        return new SchoolAddressResponse(
                entity.getId(),
                entity.getSchool().getId(),
                entity.getBranch() != null ? entity.getBranch().getId() : null,
                entity.getAddressType(),
                entity.getAddressLine1(),
                entity.getAddressLine2(),
                entity.getCity(),
                entity.getState(),
                entity.getPincode(),
                entity.getCountry(),
                entity.getLandmark(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.isPrimaryAddress()
        );
    }
}
