package com.vidya.master.school.service;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.CodeGenerator;
import com.vidya.master.school.dto.CompleteSchoolProfileRequest;
import com.vidya.master.school.dto.CompleteSchoolProfileResponse;
import com.vidya.master.school.dto.CreateBranchRequest;
import com.vidya.master.school.dto.SchoolAddressRequest;
import com.vidya.master.school.dto.SchoolAddressResponse;
import com.vidya.master.school.dto.SchoolBoardAffiliationRequest;
import com.vidya.master.school.dto.SchoolBoardAffiliationResponse;
import com.vidya.master.school.dto.SchoolBranchResponse;
import com.vidya.master.school.dto.SchoolContactRequest;
import com.vidya.master.school.dto.SchoolContactResponse;
import com.vidya.master.school.dto.SchoolDocumentRequest;
import com.vidya.master.school.dto.SchoolDocumentResponse;
import com.vidya.master.school.dto.SchoolRelationshipResponse;
import com.vidya.master.school.dto.SchoolResponse;
import com.vidya.master.school.entity.BranchStatus;
import com.vidya.master.school.entity.School;
import com.vidya.master.school.entity.SchoolAddress;
import com.vidya.master.school.entity.SchoolBoardAffiliation;
import com.vidya.master.school.entity.SchoolBranch;
import com.vidya.master.school.entity.SchoolContactDetail;
import com.vidya.master.school.entity.SchoolDocument;
import com.vidya.master.school.mapper.SchoolMapper;
import com.vidya.master.school.repository.SchoolAddressRepository;
import com.vidya.master.school.repository.SchoolBoardAffiliationRepository;
import com.vidya.master.school.repository.SchoolBranchRepository;
import com.vidya.master.school.repository.SchoolContactDetailRepository;
import com.vidya.master.school.repository.SchoolDocumentRepository;
import com.vidya.master.school.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchoolDetailServiceImpl implements SchoolDetailService {

    private final SchoolRepository schoolRepository;
    private final SchoolBranchRepository branchRepository;
    private final SchoolContactDetailRepository contactDetailRepository;
    private final SchoolAddressRepository addressRepository;
    private final SchoolBoardAffiliationRepository boardAffiliationRepository;
    private final SchoolDocumentRepository documentRepository;
    private final SchoolRelationshipService relationshipService;
    private final SchoolMapper schoolMapper;

    private School getSchoolOrThrow(UUID schoolId) {
        return schoolRepository.findById(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("School", schoolId));
    }

    // ================= CONTACTS =================

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public SchoolContactResponse addContact(UUID schoolId, SchoolContactRequest request) {
        log.info("Adding contact [{}] for school [{}]", request.fullName(), schoolId);
        School school = getSchoolOrThrow(schoolId);
        SchoolBranch branch = request.branchId() != null
                ? branchRepository.findById(request.branchId()).orElse(null)
                : null;

        boolean isPrimary = Boolean.TRUE.equals(request.isPrimary());
        if (isPrimary) {
            contactDetailRepository.findBySchoolIdAndPrimaryContactTrue(schoolId)
                    .ifPresent(existing -> {
                        existing.setPrimaryContact(false);
                        contactDetailRepository.save(existing);
                    });
        }

        SchoolContactDetail contact = new SchoolContactDetail(
                school,
                branch,
                request.contactType(),
                request.fullName(),
                request.designation(),
                request.email().trim().toLowerCase(),
                request.phone().trim(),
                request.alternatePhone(),
                isPrimary
        );

        SchoolContactDetail saved = contactDetailRepository.save(contact);
        return SchoolContactResponse.fromEntity(saved);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public List<SchoolContactResponse> getContacts(UUID schoolId) {
        getSchoolOrThrow(schoolId);
        return contactDetailRepository.findBySchoolId(schoolId).stream()
                .map(SchoolContactResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public SchoolContactResponse updateContact(UUID schoolId, UUID contactId, SchoolContactRequest request) {
        getSchoolOrThrow(schoolId);
        SchoolContactDetail contact = contactDetailRepository.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException("SchoolContactDetail", contactId));

        if (Boolean.TRUE.equals(request.isPrimary()) && !contact.isPrimaryContact()) {
            contactDetailRepository.findBySchoolIdAndPrimaryContactTrue(schoolId)
                    .ifPresent(existing -> {
                        existing.setPrimaryContact(false);
                        contactDetailRepository.save(existing);
                    });
            contact.setPrimaryContact(true);
        }

        contact.setContactType(request.contactType());
        contact.setFullName(request.fullName());
        contact.setDesignation(request.designation());
        contact.setEmail(request.email().trim().toLowerCase());
        contact.setPhone(request.phone().trim());
        contact.setAlternatePhone(request.alternatePhone());

        if (request.branchId() != null) {
            contact.setBranch(branchRepository.findById(request.branchId()).orElse(null));
        }

        SchoolContactDetail updated = contactDetailRepository.save(contact);
        return SchoolContactResponse.fromEntity(updated);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public void deleteContact(UUID schoolId, UUID contactId) {
        getSchoolOrThrow(schoolId);
        SchoolContactDetail contact = contactDetailRepository.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException("SchoolContactDetail", contactId));
        contactDetailRepository.delete(contact);
    }

    // ================= ADDRESSES =================

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public SchoolAddressResponse addAddress(UUID schoolId, SchoolAddressRequest request) {
        log.info("Adding address for school [{}] in city [{}]", schoolId, request.city());
        School school = getSchoolOrThrow(schoolId);
        SchoolBranch branch = request.branchId() != null
                ? branchRepository.findById(request.branchId()).orElse(null)
                : null;

        boolean isPrimary = Boolean.TRUE.equals(request.isPrimary());
        if (isPrimary) {
            addressRepository.findBySchoolIdAndPrimaryAddressTrue(schoolId)
                    .ifPresent(existing -> {
                        existing.setPrimaryAddress(false);
                        addressRepository.save(existing);
                    });
        }

        SchoolAddress address = new SchoolAddress(
                school,
                branch,
                request.addressType(),
                request.addressLine1(),
                request.addressLine2(),
                request.city(),
                request.state(),
                request.pincode(),
                request.country() != null ? request.country() : "India",
                request.landmark(),
                request.latitude(),
                request.longitude(),
                isPrimary
        );

        SchoolAddress saved = addressRepository.save(address);
        return SchoolAddressResponse.fromEntity(saved);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public List<SchoolAddressResponse> getAddresses(UUID schoolId) {
        getSchoolOrThrow(schoolId);
        return addressRepository.findBySchoolId(schoolId).stream()
                .map(SchoolAddressResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public SchoolAddressResponse updateAddress(UUID schoolId, UUID addressId, SchoolAddressRequest request) {
        getSchoolOrThrow(schoolId);
        SchoolAddress address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("SchoolAddress", addressId));

        if (Boolean.TRUE.equals(request.isPrimary()) && !address.isPrimaryAddress()) {
            addressRepository.findBySchoolIdAndPrimaryAddressTrue(schoolId)
                    .ifPresent(existing -> {
                        existing.setPrimaryAddress(false);
                        addressRepository.save(existing);
                    });
            address.setPrimaryAddress(true);
        }

        if (request.addressType() != null) address.setAddressType(request.addressType());
        address.setAddressLine1(request.addressLine1());
        address.setAddressLine2(request.addressLine2());
        address.setCity(request.city());
        address.setState(request.state());
        address.setPincode(request.pincode());
        if (request.country() != null) address.setCountry(request.country());
        address.setLandmark(request.landmark());
        address.setLatitude(request.latitude());
        address.setLongitude(request.longitude());

        if (request.branchId() != null) {
            address.setBranch(branchRepository.findById(request.branchId()).orElse(null));
        }

        SchoolAddress updated = addressRepository.save(address);
        return SchoolAddressResponse.fromEntity(updated);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public void deleteAddress(UUID schoolId, UUID addressId) {
        getSchoolOrThrow(schoolId);
        SchoolAddress address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("SchoolAddress", addressId));
        addressRepository.delete(address);
    }

    // ================= BOARD AFFILIATIONS (CBSE, HBSE, etc.) =================

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public SchoolBoardAffiliationResponse addBoardAffiliation(UUID schoolId, SchoolBoardAffiliationRequest request) {
        log.info("Adding board affiliation [{}] for school [{}]", request.boardType(), schoolId);
        School school = getSchoolOrThrow(schoolId);

        boolean isPrimary = Boolean.TRUE.equals(request.isPrimaryBoard());
        if (isPrimary) {
            boardAffiliationRepository.findBySchoolIdAndPrimaryBoardTrue(schoolId)
                    .ifPresent(existing -> {
                        existing.setPrimaryBoard(false);
                        boardAffiliationRepository.save(existing);
                    });
        }

        SchoolBoardAffiliation affiliation = new SchoolBoardAffiliation(
                school,
                request.boardType(),
                request.boardName(),
                request.affiliationNumber(),
                request.affiliationLevel(),
                request.validFrom(),
                request.validTo(),
                isPrimary,
                request.affiliationLetterUrl(),
                request.status()
        );

        SchoolBoardAffiliation saved = boardAffiliationRepository.save(affiliation);
        return SchoolBoardAffiliationResponse.fromEntity(saved);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public List<SchoolBoardAffiliationResponse> getBoardAffiliations(UUID schoolId) {
        getSchoolOrThrow(schoolId);
        return boardAffiliationRepository.findBySchoolId(schoolId).stream()
                .map(SchoolBoardAffiliationResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public SchoolBoardAffiliationResponse updateBoardAffiliation(UUID schoolId, UUID affiliationId, SchoolBoardAffiliationRequest request) {
        getSchoolOrThrow(schoolId);
        SchoolBoardAffiliation affiliation = boardAffiliationRepository.findById(affiliationId)
                .orElseThrow(() -> new ResourceNotFoundException("SchoolBoardAffiliation", affiliationId));

        if (Boolean.TRUE.equals(request.isPrimaryBoard()) && !affiliation.isPrimaryBoard()) {
            boardAffiliationRepository.findBySchoolIdAndPrimaryBoardTrue(schoolId)
                    .ifPresent(existing -> {
                        existing.setPrimaryBoard(false);
                        boardAffiliationRepository.save(existing);
                    });
            affiliation.setPrimaryBoard(true);
        }

        affiliation.setBoardType(request.boardType());
        affiliation.setBoardName(request.boardName());
        affiliation.setAffiliationNumber(request.affiliationNumber());
        if (request.affiliationLevel() != null) affiliation.setAffiliationLevel(request.affiliationLevel());
        affiliation.setValidFrom(request.validFrom());
        affiliation.setValidTo(request.validTo());
        affiliation.setAffiliationLetterUrl(request.affiliationLetterUrl());
        if (request.status() != null) affiliation.setStatus(request.status());

        SchoolBoardAffiliation updated = boardAffiliationRepository.save(affiliation);
        return SchoolBoardAffiliationResponse.fromEntity(updated);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public void deleteBoardAffiliation(UUID schoolId, UUID affiliationId) {
        getSchoolOrThrow(schoolId);
        SchoolBoardAffiliation affiliation = boardAffiliationRepository.findById(affiliationId)
                .orElseThrow(() -> new ResourceNotFoundException("SchoolBoardAffiliation", affiliationId));
        boardAffiliationRepository.delete(affiliation);
    }

    // ================= DOCUMENTS & CERTIFICATES =================

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public SchoolDocumentResponse addDocument(UUID schoolId, SchoolDocumentRequest request) {
        log.info("Adding compliance document [{}] for school [{}]", request.documentType(), schoolId);
        School school = getSchoolOrThrow(schoolId);

        SchoolDocument document = new SchoolDocument(
                school,
                request.documentType(),
                request.documentName(),
                request.documentNumber(),
                request.fileUrl(),
                request.fileSizeBytes(),
                request.mimeType(),
                request.issuedBy(),
                request.issuedDate(),
                request.expiryDate()
        );

        SchoolDocument saved = documentRepository.save(document);
        return SchoolDocumentResponse.fromEntity(saved);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public List<SchoolDocumentResponse> getDocuments(UUID schoolId) {
        getSchoolOrThrow(schoolId);
        return documentRepository.findBySchoolId(schoolId).stream()
                .map(SchoolDocumentResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public SchoolDocumentResponse verifyDocument(UUID schoolId, UUID documentId, UUID verifiedByUserId) {
        getSchoolOrThrow(schoolId);
        SchoolDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("SchoolDocument", documentId));

        document.verify(verifiedByUserId);
        SchoolDocument updated = documentRepository.save(document);
        return SchoolDocumentResponse.fromEntity(updated);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public SchoolDocumentResponse rejectDocument(UUID schoolId, UUID documentId, UUID rejectedByUserId, String reason) {
        getSchoolOrThrow(schoolId);
        SchoolDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("SchoolDocument", documentId));

        document.reject(rejectedByUserId, reason);
        SchoolDocument updated = documentRepository.save(document);
        return SchoolDocumentResponse.fromEntity(updated);
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public void deleteDocument(UUID schoolId, UUID documentId) {
        getSchoolOrThrow(schoolId);
        SchoolDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("SchoolDocument", documentId));
        documentRepository.delete(document);
    }

    // ================= AGGREGATED 360 PROFILE =================

    @Override
    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public CompleteSchoolProfileResponse getCompleteSchoolProfile(UUID schoolId) {
        School school = getSchoolOrThrow(schoolId);

        List<SchoolBranch> branches = branchRepository.findBySchoolId(schoolId);
        long branchCount = branches.size();
        SchoolBranch mainBranch = branches.stream()
                .filter(SchoolBranch::isMainBranch)
                .findFirst()
                .orElse(branches.isEmpty() ? null : branches.get(0));

        SchoolResponse schoolResponse = schoolMapper.toResponse(school, branchCount, mainBranch);
        SchoolRelationshipResponse relationship = relationshipService.getSchoolRelationshipDetails(schoolId);

        List<SchoolBranchResponse> branchResponses = branches.stream()
                .map(schoolMapper::toBranchResponse)
                .toList();

        List<SchoolContactResponse> contacts = contactDetailRepository.findBySchoolId(schoolId).stream()
                .map(SchoolContactResponse::fromEntity)
                .toList();

        List<SchoolAddressResponse> addresses = addressRepository.findBySchoolId(schoolId).stream()
                .map(SchoolAddressResponse::fromEntity)
                .toList();

        List<SchoolBoardAffiliationResponse> boards = boardAffiliationRepository.findBySchoolId(schoolId).stream()
                .map(SchoolBoardAffiliationResponse::fromEntity)
                .toList();

        List<SchoolDocumentResponse> documents = documentRepository.findBySchoolId(schoolId).stream()
                .map(SchoolDocumentResponse::fromEntity)
                .toList();

        return new CompleteSchoolProfileResponse(
                schoolResponse,
                relationship,
                branchResponses,
                contacts,
                addresses,
                boards,
                documents
        );
    }

    @Override
    @Transactional(transactionManager = "masterTransactionManager")
    public CompleteSchoolProfileResponse saveCompleteSchoolProfile(UUID schoolId, CompleteSchoolProfileRequest request) {
        log.info("Saving complete post-approval profile for school [{}]", schoolId);
        School school = getSchoolOrThrow(schoolId);

        // 1. Update Core Information
        if (request.schoolName() != null && !request.schoolName().isBlank()) school.setSchoolName(request.schoolName().trim());
        if (request.legalName() != null) school.setLegalName(request.legalName().trim());
        if (request.registrationNumber() != null) school.setRegistrationNumber(request.registrationNumber().trim());
        if (request.primaryBoard() != null) school.setBoard(request.primaryBoard());
        if (request.schoolType() != null) school.setSchoolType(request.schoolType());
        if (request.email() != null) school.setEmail(request.email().trim().toLowerCase());
        if (request.phone() != null) school.setPhone(request.phone().trim());
        if (request.website() != null) school.setWebsite(request.website().trim());
        schoolRepository.save(school);

        // 2. Add Branches
        if (request.branches() != null && !request.branches().isEmpty()) {
            for (CreateBranchRequest bReq : request.branches()) {
                SchoolBranch branch = new SchoolBranch(
                        school,
                        CodeGenerator.branchCode(),
                        bReq.branchName(),
                        bReq.isMainBranch(),
                        bReq.addressLine1(),
                        bReq.addressLine2(),
                        bReq.city(),
                        bReq.state(),
                        bReq.pincode(),
                        bReq.country() != null ? bReq.country() : "India",
                        bReq.contactEmail(),
                        bReq.contactPhone()
                );
                branchRepository.save(branch);
            }
        }

        // 3. Add Contacts
        if (request.contacts() != null && !request.contacts().isEmpty()) {
            for (SchoolContactRequest cReq : request.contacts()) {
                addContact(schoolId, cReq);
            }
        }

        // 4. Add Addresses
        if (request.addresses() != null && !request.addresses().isEmpty()) {
            for (SchoolAddressRequest aReq : request.addresses()) {
                addAddress(schoolId, aReq);
            }
        }

        // 5. Add Boards
        if (request.boards() != null && !request.boards().isEmpty()) {
            for (SchoolBoardAffiliationRequest bReq : request.boards()) {
                addBoardAffiliation(schoolId, bReq);
            }
        }

        // 6. Add Documents
        if (request.documents() != null && !request.documents().isEmpty()) {
            for (SchoolDocumentRequest dReq : request.documents()) {
                addDocument(schoolId, dReq);
            }
        }

        return getCompleteSchoolProfile(schoolId);
    }
}
