package com.vidya.tenantdata.academic.service;

import com.vidya.tenantdata.academic.dto.AcademicYearResponse;
import com.vidya.tenantdata.academic.dto.CreateAcademicYearRequest;
import com.vidya.tenantdata.academic.dto.UpdateAcademicYearRequest;
import com.vidya.tenantdata.academic.enums.AcademicYearStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AcademicYearService {

    AcademicYearResponse createAcademicYear(CreateAcademicYearRequest request);

    AcademicYearResponse getAcademicYearById(UUID id);

    AcademicYearResponse getAcademicYearByCode(String code);

    AcademicYearResponse getCurrentAcademicYear();

    Page<AcademicYearResponse> searchAcademicYears(AcademicYearStatus status, Boolean isCurrent, Pageable pageable);

    AcademicYearResponse updateAcademicYear(UUID id, UpdateAcademicYearRequest request);

    AcademicYearResponse activateAcademicYear(UUID id);

    AcademicYearResponse closeAcademicYear(UUID id);

    AcademicYearResponse setCurrentAcademicYear(UUID id);

    void deleteAcademicYear(UUID id);
}
