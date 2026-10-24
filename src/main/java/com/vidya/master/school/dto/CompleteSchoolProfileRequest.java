package com.vidya.master.school.dto;

import com.vidya.master.school.entity.SchoolBoard;
import com.vidya.master.school.entity.SchoolType;
import jakarta.validation.Valid;

import java.util.List;

public record CompleteSchoolProfileRequest(
        String schoolName,
        String legalName,
        String registrationNumber,
        SchoolBoard primaryBoard,
        SchoolType schoolType,
        String email,
        String phone,
        String website,

        @Valid
        List<CreateBranchRequest> branches,

        @Valid
        List<SchoolContactRequest> contacts,

        @Valid
        List<SchoolAddressRequest> addresses,

        @Valid
        List<SchoolBoardAffiliationRequest> boards,

        @Valid
        List<SchoolDocumentRequest> documents
) {}
