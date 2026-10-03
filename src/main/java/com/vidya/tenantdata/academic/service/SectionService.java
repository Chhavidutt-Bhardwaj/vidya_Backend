package com.vidya.tenantdata.academic.service;

import com.vidya.tenantdata.academic.dto.CreateSectionRequest;
import com.vidya.tenantdata.academic.dto.SectionResponse;
import com.vidya.tenantdata.academic.dto.UpdateSectionRequest;
import com.vidya.tenantdata.academic.enums.SectionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SectionService {

    SectionResponse createSection(CreateSectionRequest request);

    SectionResponse getSectionById(UUID id);

    Page<SectionResponse> searchSections(UUID academicYearId, UUID academicClassId, SectionStatus status, Pageable pageable);

    SectionResponse updateSection(UUID id, UpdateSectionRequest request);

    void deleteSection(UUID id);
}
