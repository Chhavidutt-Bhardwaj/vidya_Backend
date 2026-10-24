package com.vidya.master.school.repository;

import com.vidya.master.school.entity.DocumentVerificationStatus;
import com.vidya.master.school.entity.SchoolDocument;
import com.vidya.master.school.entity.SchoolDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SchoolDocumentRepository extends JpaRepository<SchoolDocument, UUID> {

    List<SchoolDocument> findBySchoolId(UUID schoolId);

    List<SchoolDocument> findBySchoolIdAndDocumentType(UUID schoolId, SchoolDocumentType documentType);

    List<SchoolDocument> findBySchoolIdAndVerificationStatus(UUID schoolId, DocumentVerificationStatus verificationStatus);
}
