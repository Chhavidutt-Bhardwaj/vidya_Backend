package com.vidya.master.school.service;

import com.vidya.master.school.dto.CompleteSchoolProfileRequest;
import com.vidya.master.school.dto.CompleteSchoolProfileResponse;
import com.vidya.master.school.dto.SchoolAddressRequest;
import com.vidya.master.school.dto.SchoolAddressResponse;
import com.vidya.master.school.dto.SchoolBoardAffiliationRequest;
import com.vidya.master.school.dto.SchoolBoardAffiliationResponse;
import com.vidya.master.school.dto.SchoolContactRequest;
import com.vidya.master.school.dto.SchoolContactResponse;
import com.vidya.master.school.dto.SchoolDocumentRequest;
import com.vidya.master.school.dto.SchoolDocumentResponse;

import java.util.List;
import java.util.UUID;

public interface SchoolDetailService {

    // Contacts
    SchoolContactResponse addContact(UUID schoolId, SchoolContactRequest request);
    List<SchoolContactResponse> getContacts(UUID schoolId);
    SchoolContactResponse updateContact(UUID schoolId, UUID contactId, SchoolContactRequest request);
    void deleteContact(UUID schoolId, UUID contactId);

    // Addresses
    SchoolAddressResponse addAddress(UUID schoolId, SchoolAddressRequest request);
    List<SchoolAddressResponse> getAddresses(UUID schoolId);
    SchoolAddressResponse updateAddress(UUID schoolId, UUID addressId, SchoolAddressRequest request);
    void deleteAddress(UUID schoolId, UUID addressId);

    // Educational Boards (CBSE, HBSE, ICSE, IB, etc.)
    SchoolBoardAffiliationResponse addBoardAffiliation(UUID schoolId, SchoolBoardAffiliationRequest request);
    List<SchoolBoardAffiliationResponse> getBoardAffiliations(UUID schoolId);
    SchoolBoardAffiliationResponse updateBoardAffiliation(UUID schoolId, UUID affiliationId, SchoolBoardAffiliationRequest request);
    void deleteBoardAffiliation(UUID schoolId, UUID affiliationId);

    // Compliance Documents & Certificates
    SchoolDocumentResponse addDocument(UUID schoolId, SchoolDocumentRequest request);
    List<SchoolDocumentResponse> getDocuments(UUID schoolId);
    SchoolDocumentResponse verifyDocument(UUID schoolId, UUID documentId, UUID verifiedByUserId);
    SchoolDocumentResponse rejectDocument(UUID schoolId, UUID documentId, UUID rejectedByUserId, String reason);
    void deleteDocument(UUID schoolId, UUID documentId);

    // Complete Aggregated 360 Profile
    CompleteSchoolProfileResponse getCompleteSchoolProfile(UUID schoolId);
    CompleteSchoolProfileResponse saveCompleteSchoolProfile(UUID schoolId, CompleteSchoolProfileRequest request);
}
