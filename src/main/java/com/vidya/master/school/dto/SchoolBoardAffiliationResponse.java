package com.vidya.master.school.dto;

import com.vidya.master.school.entity.BoardAffiliationLevel;
import com.vidya.master.school.entity.BoardAffiliationStatus;
import com.vidya.master.school.entity.BoardAffiliationType;
import com.vidya.master.school.entity.SchoolBoardAffiliation;

import java.time.LocalDate;
import java.util.UUID;

public record SchoolBoardAffiliationResponse(
        UUID id,
        UUID schoolId,
        BoardAffiliationType boardType,
        String boardName,
        String affiliationNumber,
        BoardAffiliationLevel affiliationLevel,
        LocalDate validFrom,
        LocalDate validTo,
        boolean isPrimaryBoard,
        String affiliationLetterUrl,
        BoardAffiliationStatus status
) {
    public static SchoolBoardAffiliationResponse fromEntity(SchoolBoardAffiliation entity) {
        return new SchoolBoardAffiliationResponse(
                entity.getId(),
                entity.getSchool().getId(),
                entity.getBoardType(),
                entity.getBoardName(),
                entity.getAffiliationNumber(),
                entity.getAffiliationLevel(),
                entity.getValidFrom(),
                entity.getValidTo(),
                entity.isPrimaryBoard(),
                entity.getAffiliationLetterUrl(),
                entity.getStatus()
        );
    }
}
