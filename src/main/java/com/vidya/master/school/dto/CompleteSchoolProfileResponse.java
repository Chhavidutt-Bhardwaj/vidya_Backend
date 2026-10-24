package com.vidya.master.school.dto;

import java.util.List;

public record CompleteSchoolProfileResponse(
        SchoolResponse school,
        SchoolRelationshipResponse relationship,
        List<SchoolBranchResponse> branches,
        List<SchoolContactResponse> contacts,
        List<SchoolAddressResponse> addresses,
        List<SchoolBoardAffiliationResponse> boards,
        List<SchoolDocumentResponse> documents
) {}
