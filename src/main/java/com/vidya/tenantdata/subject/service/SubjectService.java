package com.vidya.tenantdata.subject.service;

import com.vidya.tenantdata.subject.dto.CreateSubjectRequest;
import com.vidya.tenantdata.subject.dto.SubjectDetailResponse;
import com.vidya.tenantdata.subject.dto.SubjectResponse;
import com.vidya.tenantdata.subject.dto.UpdateSubjectRequest;
import com.vidya.tenantdata.subject.dto.UpdateSubjectStatusRequest;
import com.vidya.tenantdata.subject.enums.SubjectStatus;
import com.vidya.tenantdata.subject.enums.SubjectType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service contract for academic Subject business operations.
 */
public interface SubjectService {

    SubjectDetailResponse createSubject(CreateSubjectRequest request);

    SubjectDetailResponse getSubject(UUID id);

    SubjectDetailResponse getSubjectByCode(String subjectCode);

    Page<SubjectResponse> searchSubjects(SubjectType subjectType, SubjectStatus status, String query, Pageable pageable);

    SubjectDetailResponse updateSubject(UUID id, UpdateSubjectRequest request);

    SubjectDetailResponse updateStatus(UUID id, UpdateSubjectStatusRequest request);

    void deleteSubject(UUID id);
}
