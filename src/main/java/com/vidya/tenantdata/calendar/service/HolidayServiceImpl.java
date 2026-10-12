package com.vidya.tenantdata.calendar.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.DuplicateResourceException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.SecurityUtils;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.calendar.dto.CreateHolidayRequest;
import com.vidya.tenantdata.calendar.dto.HolidayResponse;
import com.vidya.tenantdata.calendar.dto.UpdateHolidayRequest;
import com.vidya.tenantdata.calendar.entity.SchoolHoliday;
import com.vidya.tenantdata.calendar.enums.HolidayType;
import com.vidya.tenantdata.calendar.mapper.HolidayMapper;
import com.vidya.tenantdata.calendar.repository.SchoolHolidayRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HolidayServiceImpl implements HolidayService {

    private final SchoolHolidayRepository holidayRepository;
    private final AcademicYearRepository academicYearRepository;
    private final HolidayMapper holidayMapper;

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public HolidayResponse createHoliday(CreateHolidayRequest request) {
        log.info("Creating holiday [{}] for academic year [{}]", request.name(), request.academicYearId());

        if (request.startDate().isAfter(request.endDate())) {
            throw new BusinessException("Holiday start date cannot be after end date.");
        }

        AcademicYear academicYear = academicYearRepository.findByIdAndNotDeleted(request.academicYearId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", request.academicYearId()));

        validateHolidayWithinAcademicYear(academicYear, request.startDate(), request.endDate());

        List<SchoolHoliday> overlapping = holidayRepository.findOverlappingHolidays(
                academicYear.getId(), request.startDate(), request.endDate(), null
        );
        if (!overlapping.isEmpty()) {
            SchoolHoliday existing = overlapping.getFirst();
            throw new DuplicateResourceException(
                    "SchoolHoliday",
                    "dates",
                    String.format("Holiday overlaps with existing holiday '%s' (%s to %s)",
                            existing.getName(), existing.getStartDate(), existing.getEndDate())
            );
        }

        String holidayCode = "HOL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        SchoolHoliday holiday = new SchoolHoliday(
                holidayCode,
                academicYear,
                request.name(),
                request.holidayType(),
                request.startDate(),
                request.endDate(),
                request.description()
        );

        UUID currentUserId = SecurityUtils.getCurrentUserId().orElse(null);
        holiday.setCreatedBy(currentUserId);
        holiday.setUpdatedBy(currentUserId);

        try {
            SchoolHoliday saved = holidayRepository.saveAndFlush(holiday);
            return holidayMapper.toResponse(saved);
        } catch (Exception e) {
            log.warn("Database constraint violation during holiday creation: {}", e.getMessage());
            throw new DuplicateResourceException(
                    "SchoolHoliday",
                    "name",
                    String.format("A holiday with name '%s' on %s already exists in this academic year.",
                            request.name(), request.startDate())
            );
        }
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public HolidayResponse getHolidayById(UUID id) {
        SchoolHoliday holiday = holidayRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("SchoolHoliday", id));
        return holidayMapper.toResponse(holiday);
    }

    @Override
    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Page<HolidayResponse> searchHolidays(
            UUID academicYearId,
            HolidayType type,
            LocalDate dateFrom,
            LocalDate dateTo,
            Pageable pageable
    ) {
        return holidayRepository.searchHolidays(academicYearId, type, dateFrom, dateTo, pageable)
                .map(holidayMapper::toResponse);
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public HolidayResponse updateHoliday(UUID id, UpdateHolidayRequest request) {
        log.info("Updating holiday [{}]", id);

        if (request.startDate().isAfter(request.endDate())) {
            throw new BusinessException("Holiday start date cannot be after end date.");
        }

        SchoolHoliday holiday = holidayRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("SchoolHoliday", id));

        AcademicYear academicYear = holiday.getAcademicYear();
        validateHolidayWithinAcademicYear(academicYear, request.startDate(), request.endDate());

        List<SchoolHoliday> overlapping = holidayRepository.findOverlappingHolidays(
                academicYear.getId(), request.startDate(), request.endDate(), id
        );
        if (!overlapping.isEmpty()) {
            SchoolHoliday existing = overlapping.getFirst();
            throw new DuplicateResourceException(
                    "SchoolHoliday",
                    "dates",
                    String.format("Holiday overlaps with existing holiday '%s' (%s to %s)",
                            existing.getName(), existing.getStartDate(), existing.getEndDate())
            );
        }

        holiday.setName(request.name().trim());
        holiday.setHolidayType(request.holidayType());
        holiday.setStartDate(request.startDate());
        holiday.setEndDate(request.endDate());
        holiday.setDescription(request.description() != null ? request.description().trim() : null);
        holiday.setUpdatedBy(SecurityUtils.getCurrentUserId().orElse(null));

        try {
            SchoolHoliday saved = holidayRepository.saveAndFlush(holiday);
            return holidayMapper.toResponse(saved);
        } catch (Exception e) {
            log.warn("Database constraint violation during holiday update: {}", e.getMessage());
            throw new DuplicateResourceException(
                    "SchoolHoliday",
                    "name",
                    String.format("A holiday with name '%s' on %s already exists in this academic year.",
                            request.name(), request.startDate())
            );
        }
    }

    @Override
    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteHoliday(UUID id) {
        log.info("Soft deleting holiday [{}]", id);

        SchoolHoliday holiday = holidayRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("SchoolHoliday", id));

        holiday.softDelete();
        holiday.setUpdatedBy(SecurityUtils.getCurrentUserId().orElse(null));
        holidayRepository.save(holiday);
    }

    private void validateHolidayWithinAcademicYear(AcademicYear academicYear, LocalDate start, LocalDate end) {
        if (academicYear.getStartDate() != null && start.isBefore(academicYear.getStartDate())) {
            throw new BusinessException(String.format(
                    "Holiday start date [%s] cannot be before academic year start date [%s].",
                    start, academicYear.getStartDate()
            ));
        }
        if (academicYear.getEndDate() != null && end.isAfter(academicYear.getEndDate())) {
            throw new BusinessException(String.format(
                    "Holiday end date [%s] cannot be after academic year end date [%s].",
                    end, academicYear.getEndDate()
            ));
        }
    }
}
