package com.vidya.tenantdata.academic.service;

import com.vidya.tenantdata.academic.dto.AcademicClassResponse;
import com.vidya.tenantdata.academic.dto.CreateAcademicClassRequest;
import com.vidya.tenantdata.academic.dto.UpdateAcademicClassRequest;
import com.vidya.tenantdata.academic.enums.AcademicClassStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AcademicClassService {

    AcademicClassResponse createAcademicClass(CreateAcademicClassRequest request);

    AcademicClassResponse getAcademicClassById(UUID id);

    AcademicClassResponse getAcademicClassByCode(String code);

    Page<AcademicClassResponse> searchAcademicClasses(AcademicClassStatus status, Pageable pageable);

    AcademicClassResponse updateAcademicClass(UUID id, UpdateAcademicClassRequest request);

    void deleteAcademicClass(UUID id);
}
