package com.vidya.master.support.dto;

import com.vidya.master.support.entity.QueryCategory;
import com.vidya.master.support.entity.QueryPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateSupportQueryRequest(
        @NotNull(message = "School ID is required")
        UUID schoolId,

        @NotNull(message = "Category is required")
        QueryCategory category,

        QueryPriority priority,

        @NotBlank(message = "Subject is required")
        @Size(max = 300, message = "Subject cannot exceed 300 characters")
        String subject,

        @NotBlank(message = "Description is required")
        String description
) {}
