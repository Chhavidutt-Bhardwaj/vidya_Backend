package com.vidya.master.support.dto;

import jakarta.validation.constraints.NotBlank;

public record AddQueryCommentRequest(
        @NotBlank(message = "Message is required")
        String message,

        boolean internalNote
) {}
