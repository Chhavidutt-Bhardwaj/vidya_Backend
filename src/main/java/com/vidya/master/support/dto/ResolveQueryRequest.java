package com.vidya.master.support.dto;

import jakarta.validation.constraints.NotBlank;

public record ResolveQueryRequest(
        @NotBlank(message = "Resolution summary is required")
        String resolutionSummary,

        boolean closeImmediately
) {}
