package com.vidya.tenantdata.subject.service;

import com.vidya.tenantdata.subject.dto.CreateCurriculumRequest;
import com.vidya.tenantdata.subject.dto.CurriculumDetailResponse;
import com.vidya.tenantdata.subject.dto.CurriculumResponse;
import com.vidya.tenantdata.subject.dto.UpdateCurriculumRequest;
import com.vidya.tenantdata.subject.dto.UpdateCurriculumStatusRequest;
import com.vidya.tenantdata.subject.enums.CurriculumStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service contract for Curriculum business operations.
 */
public interface CurriculumService {

    CurriculumDetailResponse createCurriculum(CreateCurriculumRequest request);

    CurriculumDetailResponse getCurriculum(UUID id);

    CurriculumDetailResponse getCurriculumByCode(String curriculumCode);

    Page<CurriculumResponse> searchCurricula(UUID academicYearId, CurriculumStatus status, String query, Pageable pageable);

    CurriculumDetailResponse updateCurriculum(UUID id, UpdateCurriculumRequest request);

    CurriculumDetailResponse updateStatus(UUID id, UpdateCurriculumStatusRequest request);

    void deleteCurriculum(UUID id);
}
