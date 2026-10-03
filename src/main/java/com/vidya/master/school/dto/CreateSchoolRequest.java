package com.vidya.master.school.dto;

import com.vidya.master.school.entity.SchoolBoard;
import com.vidya.master.school.entity.SchoolType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload for registering a new School entity.
 */
public record CreateSchoolRequest(
        @NotBlank(message = "School name is required")
        @Size(max = 300, message = "School name must not exceed 300 characters")
        String schoolName,

        @Size(max = 300, message = "Legal name must not exceed 300 characters")
        String legalName,

        @Size(max = 100, message = "Registration number must not exceed 100 characters")
        String registrationNumber,

        SchoolBoard board,

        SchoolType schoolType,

        @NotBlank(message = "School email is required")
        @Email(message = "School email must be valid")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @Size(max = 20, message = "Phone must not exceed 20 characters")
        String phone,

        @Size(max = 500, message = "Website must not exceed 500 characters")
        String website,

        @Valid
        CreateBranchRequest mainBranch
) {}
