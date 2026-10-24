package com.vidya.master.community.dto;

import com.vidya.master.community.entity.Gender;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for community personal details response.
 */
public record CommunityPersonalDetailResponse(
        UUID id,
        UUID communityId,
        String contactPersonName,
        String designation,
        String alternateEmail,
        String alternatePhone,
        LocalDate dateOfBirth,
        Gender gender,
        String panNumber,
        String aadhaarNumber,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy
) {}
