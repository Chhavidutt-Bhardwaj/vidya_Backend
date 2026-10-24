package com.vidya.master.school.dto;

import com.vidya.master.school.entity.SchoolContactType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SchoolContactRequest(
        UUID branchId,

        @NotNull(message = "Contact type is required")
        SchoolContactType contactType,

        @NotBlank(message = "Full name is required")
        @Size(max = 200, message = "Full name must not exceed 200 characters")
        String fullName,

        @Size(max = 100, message = "Designation must not exceed 100 characters")
        String designation,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @NotBlank(message = "Phone number is required")
        @Size(max = 20, message = "Phone must not exceed 20 characters")
        String phone,

        @Size(max = 20, message = "Alternate phone must not exceed 20 characters")
        String alternatePhone,

        Boolean isPrimary
) {}
