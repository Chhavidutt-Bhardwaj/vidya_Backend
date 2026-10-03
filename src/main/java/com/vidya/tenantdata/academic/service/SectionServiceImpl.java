package com.vidya.tenantdata.academic.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.dto.CreateSectionRequest;
import com.vidya.tenantdata.academic.dto.SectionResponse;
import com.vidya.tenantdata.academic.dto.UpdateSectionRequest;
import com.vidya.tenantdata.academic.entity.AcademicClass;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.entity.Section;
import com.vidya.tenantdata.academic.enums.SectionStatus;
import com.vidya.tenantdata.academic.mapper.AcademicMapper;
import com.vidya.tenantdata.academic.repository.AcademicClassRepository;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.academic.repository.SectionRepository;
import com.vidya.tenantdata.academic.repository.StudentAcademicAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SectionServiceImpl implements SectionService {

    private final SectionRepository sectionRepository;
    private final AcademicYearRepository academicYearRepository;
    private final AcademicClassRepository academicClassRepository;
    private final StudentAcademicAssignmentRepository assignmentRepository;

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public SectionResponse createSection(CreateSectionRequest request) {
        log.info("Creating section with code: [{}] for class: [{}] in year: [{}]",
                request.getCode(), request.getAcademicClassId(), request.getAcademicYearId());

        AcademicYear year = academicYearRepository.findByIdAndNotDeleted(request.getAcademicYearId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", request.getAcademicYearId()));

        AcademicClass cls = academicClassRepository.findByIdAndNotDeleted(request.getAcademicClassId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicClass", request.getAcademicClassId()));

        String code = request.getCode().trim();
        if (sectionRepository.existsByAcademicYearIdAndAcademicClassIdAndCode(year.getId(), cls.getId(), code)) {
            throw new DuplicateResourceException("Section", "code", code);
        }

        Section section = AcademicMapper.toEntity(request, year, cls);
        Section saved = sectionRepository.save(section);

        log.info("Created section ID: [{}]", saved.getId());
        return AcademicMapper.toResponse(saved, 0L);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public SectionResponse getSectionById(UUID id) {
        Section section = sectionRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Section", id));
        long count = assignmentRepository.countActiveAssignmentsBySectionId(id);
        return AcademicMapper.toResponse(section, count);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Page<SectionResponse> searchSections(UUID academicYearId, UUID academicClassId, SectionStatus status, Pageable pageable) {
        return sectionRepository.searchSections(academicYearId, academicClassId, status, pageable)
                .map(s -> AcademicMapper.toResponse(s, assignmentRepository.countActiveAssignmentsBySectionId(s.getId())));
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public SectionResponse updateSection(UUID id, UpdateSectionRequest request) {
        log.info("Updating section ID: [{}]", id);

        Section section = sectionRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Section", id));

        if (request.getName() != null && !request.getName().isBlank()) {
            section.setName(request.getName().trim());
        }
        if (request.getCapacity() != null) {
            long currentCount = assignmentRepository.countActiveAssignmentsBySectionId(id);
            if (request.getCapacity() < currentCount) {
                throw new BusinessException("Cannot set capacity lower than current active student count: " + currentCount);
            }
            section.setCapacity(request.getCapacity());
        }

        Section saved = sectionRepository.save(section);
        long count = assignmentRepository.countActiveAssignmentsBySectionId(id);
        log.info("Updated section ID: [{}]", saved.getId());
        return AcademicMapper.toResponse(saved, count);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteSection(UUID id) {
        log.info("Soft-deleting section ID: [{}]", id);

        Section section = sectionRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Section", id));

        if (assignmentRepository.existsBySectionIdAndDeletedAtIsNull(id)) {
            throw new BusinessException("Cannot delete section with existing student academic assignments");
        }

        section.softDelete();
        sectionRepository.save(section);
        log.info("Successfully soft-deleted section ID: [{}]", id);
    }
}
