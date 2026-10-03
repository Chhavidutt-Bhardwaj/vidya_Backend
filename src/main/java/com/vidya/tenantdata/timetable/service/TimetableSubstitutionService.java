package com.vidya.tenantdata.timetable.service;

import com.vidya.tenantdata.timetable.dto.CreateSubstitutionRequest;
import com.vidya.tenantdata.timetable.dto.SubstitutionResponse;
import com.vidya.tenantdata.timetable.dto.UpdateSubstitutionRequest;
import com.vidya.tenantdata.timetable.enums.SubstitutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

public interface TimetableSubstitutionService {

    SubstitutionResponse createSubstitution(CreateSubstitutionRequest request);

    SubstitutionResponse getSubstitutionById(UUID id);

    Page<SubstitutionResponse> searchSubstitutions(
            LocalDate dateFrom,
            LocalDate dateTo,
            UUID sectionId,
            UUID teacherId,
            SubstitutionStatus status,
            Pageable pageable
    );

    SubstitutionResponse updateSubstitution(UUID id, UpdateSubstitutionRequest request);

    SubstitutionResponse cancelSubstitution(UUID id);

    void deleteSubstitution(UUID id);
}
