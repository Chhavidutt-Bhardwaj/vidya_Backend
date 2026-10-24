package com.vidya.master.school.dto;

import com.vidya.master.school.entity.BoardAffiliationLevel;
import com.vidya.master.school.entity.BoardAffiliationStatus;
import com.vidya.master.school.entity.BoardAffiliationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SchoolBoardAffiliationRequest(
        @NotNull(message = "Board type is required")
        BoardAffiliationType boardType,

        @Size(max = 150, message = "Board name must not exceed 150 characters")
        String boardName,

        @NotBlank(message = "Affiliation number is required")
        @Size(max = 100, message = "Affiliation number must not exceed 100 characters")
        String affiliationNumber,

        BoardAffiliationLevel affiliationLevel,

        LocalDate validFrom,

        LocalDate validTo,

        Boolean isPrimaryBoard,

        @Size(max = 500, message = "Affiliation letter URL must not exceed 500 characters")
        String affiliationLetterUrl,

        BoardAffiliationStatus status
) {}
