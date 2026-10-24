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
import com.vidya.master.school.dto.SchoolRelationshipResponse;
import com.vidya.master.school.dto.SchoolResponse;
import com.vidya.master.school.entity.BoardAffiliationLevel;
import com.vidya.master.school.entity.BoardAffiliationStatus;
import com.vidya.master.school.entity.BoardAffiliationType;
import com.vidya.master.school.entity.DocumentVerificationStatus;
import com.vidya.master.school.entity.School;
import com.vidya.master.school.entity.SchoolAddress;
import com.vidya.master.school.entity.SchoolAddressType;
import com.vidya.master.school.entity.SchoolBoardAffiliation;
import com.vidya.master.school.entity.SchoolContactDetail;
import com.vidya.master.school.entity.SchoolContactType;
import com.vidya.master.school.entity.SchoolDocument;
import com.vidya.master.school.entity.SchoolDocumentType;
import com.vidya.master.school.entity.SchoolStatus;
import com.vidya.master.school.mapper.SchoolMapper;
import com.vidya.master.school.repository.SchoolAddressRepository;
import com.vidya.master.school.repository.SchoolBoardAffiliationRepository;
import com.vidya.master.school.repository.SchoolBranchRepository;
import com.vidya.master.school.repository.SchoolContactDetailRepository;
import com.vidya.master.school.repository.SchoolDocumentRepository;
import com.vidya.master.school.repository.SchoolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SchoolDetailService — Normalized Sub-Tables & Onboarding Profile Tests")
class SchoolDetailServiceTest {

    @Mock
    private SchoolRepository schoolRepository;
    @Mock
    private SchoolBranchRepository branchRepository;
    @Mock
    private SchoolContactDetailRepository contactDetailRepository;
    @Mock
    private SchoolAddressRepository addressRepository;
    @Mock
    private SchoolBoardAffiliationRepository boardAffiliationRepository;
    @Mock
    private SchoolDocumentRepository documentRepository;
    @Mock
    private SchoolRelationshipService relationshipService;
    @Mock
    private SchoolMapper schoolMapper;

    @InjectMocks
    private SchoolDetailServiceImpl detailService;

    private School sampleSchool;
    private UUID schoolId;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        sampleSchool = new School(
                "SCH-20261024",
                "Vidya Heritage Global Academy",
                "Vidya Heritage Trust",
                "REG-987654",
                null,
                null,
                "info@vidyaheritage.edu",
                "+919876543210",
                "https://vidyaheritage.edu",
                SchoolStatus.DRAFT
        );
    }

    @Test
    @DisplayName("Should add designated school contact successfully")
    void shouldAddSchoolContact() {
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(sampleSchool));

        SchoolContactRequest request = new SchoolContactRequest(
                null,
                SchoolContactType.PRINCIPAL,
                "Dr. Rameshwar Sharma",
                "Senior Principal",
                "principal@vidyaheritage.edu",
                "+919811122233",
                null,
                true
        );

        when(contactDetailRepository.save(any(SchoolContactDetail.class))).thenAnswer(invocation -> {
            SchoolContactDetail saved = invocation.getArgument(0);
            return saved;
        });

        SchoolContactResponse response = detailService.addContact(schoolId, request);

        assertThat(response).isNotNull();
        assertThat(response.fullName()).isEqualTo("Dr. Rameshwar Sharma");
        assertThat(response.contactType()).isEqualTo(SchoolContactType.PRINCIPAL);
        assertThat(response.isPrimary()).isTrue();
    }

    @Test
    @DisplayName("Should add campus address successfully")
    void shouldAddCampusAddress() {
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(sampleSchool));

        SchoolAddressRequest request = new SchoolAddressRequest(
                null,
                SchoolAddressType.CAMPUS_PRIMARY,
                "Plot 42, Institutional Area",
                "Sector 62",
                "Gurugram",
                "Haryana",
                "122001",
                "India",
                "Near Cyber Hub",
                null, null,
                true
        );

        when(addressRepository.save(any(SchoolAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SchoolAddressResponse response = detailService.addAddress(schoolId, request);

        assertThat(response).isNotNull();
        assertThat(response.city()).isEqualTo("Gurugram");
        assertThat(response.state()).isEqualTo("Haryana");
        assertThat(response.pincode()).isEqualTo("122001");
        assertThat(response.isPrimary()).isTrue();
    }

    @Test
    @DisplayName("Should add educational board affiliation (CBSE, HBSE) successfully")
    void shouldAddBoardAffiliation() {
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(sampleSchool));

        SchoolBoardAffiliationRequest request = new SchoolBoardAffiliationRequest(
                BoardAffiliationType.HBSE,
                "Haryana Board of School Education",
                "HBSE-AFF-2026-9081",
                BoardAffiliationLevel.SENIOR_SECONDARY,
                LocalDate.of(2025, 4, 1),
                LocalDate.of(2030, 3, 31),
                true,
                "https://storage.vidya.io/affiliations/hbse_letter.pdf",
                BoardAffiliationStatus.ACTIVE
        );

        when(boardAffiliationRepository.save(any(SchoolBoardAffiliation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SchoolBoardAffiliationResponse response = detailService.addBoardAffiliation(schoolId, request);

        assertThat(response).isNotNull();
        assertThat(response.boardType()).isEqualTo(BoardAffiliationType.HBSE);
        assertThat(response.affiliationNumber()).isEqualTo("HBSE-AFF-2026-9081");
        assertThat(response.isPrimaryBoard()).isTrue();
    }

    @Test
    @DisplayName("Should upload compliance document and allow verification")
    void shouldAddAndVerifyComplianceDocument() {
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(sampleSchool));

        UUID docId = UUID.randomUUID();
        SchoolDocument doc = new SchoolDocument(
                sampleSchool,
                SchoolDocumentType.FIRE_SAFETY_NOC,
                "Fire Safety Certificate 2026",
                "NOC-FS-2026-88",
                "https://storage.vidya.io/docs/fire_noc.pdf",
                2048500L,
                "application/pdf",
                "Haryana Fire Department",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1)
        );

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(SchoolDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UUID verifierUserId = UUID.randomUUID();
        SchoolDocumentResponse verifiedRes = detailService.verifyDocument(schoolId, docId, verifierUserId);

        assertThat(verifiedRes.verificationStatus()).isEqualTo(DocumentVerificationStatus.VERIFIED);
        assertThat(verifiedRes.verifiedByUserId()).isEqualTo(verifierUserId);
    }

    @Test
    @DisplayName("Should aggregate complete 360 profile of school")
    void shouldGetCompleteSchoolProfile() {
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(sampleSchool));
        when(branchRepository.findBySchoolId(schoolId)).thenReturn(Collections.emptyList());
        when(contactDetailRepository.findBySchoolId(schoolId)).thenReturn(Collections.emptyList());
        when(addressRepository.findBySchoolId(schoolId)).thenReturn(Collections.emptyList());
        when(boardAffiliationRepository.findBySchoolId(schoolId)).thenReturn(Collections.emptyList());
        when(documentRepository.findBySchoolId(schoolId)).thenReturn(Collections.emptyList());

        SchoolRelationshipResponse relResponse = org.mockito.Mockito.mock(SchoolRelationshipResponse.class);
        when(relationshipService.getSchoolRelationshipDetails(schoolId)).thenReturn(relResponse);

        SchoolResponse schoolRes = new SchoolResponse(
                schoolId, "SCH-20261024", null, "Vidya Heritage Global Academy",
                null, null, null, null, "info@vidyaheritage.edu", null, null,
                SchoolStatus.DRAFT, null, null, 0, null
        );
        when(schoolMapper.toResponse(any(), any(Long.class), any())).thenReturn(schoolRes);

        CompleteSchoolProfileResponse profile = detailService.getCompleteSchoolProfile(schoolId);

        assertThat(profile).isNotNull();
        assertThat(profile.school()).isNotNull();
        assertThat(profile.relationship()).isNotNull();
    }
}
