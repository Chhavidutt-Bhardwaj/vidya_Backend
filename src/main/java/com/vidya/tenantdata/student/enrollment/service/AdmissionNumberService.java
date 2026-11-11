package com.vidya.tenantdata.student.enrollment.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.AdmissionSequenceRequest;
import com.vidya.tenantdata.student.enrollment.dto.StudentSetupDtos.AdmissionSequenceResponse;
import com.vidya.tenantdata.student.enrollment.entity.AdmissionSequence;
import com.vidya.tenantdata.student.enrollment.repository.AdmissionSequenceRepository;
import com.vidya.tenantdata.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Admission numbers. Schools number admissions per academic year ("2026/0001") or school-wide ("GPS-00123");
 * a sequence per year wins over the school-wide one. With no sequence configured, a per-year one is created on
 * first use with prefix {@code <start year>/} and 4-digit padding.
 */
@Service
@RequiredArgsConstructor
public class AdmissionNumberService {

    private final AdmissionSequenceRepository sequenceRepository;
    private final AcademicYearRepository academicYearRepository;
    private final StudentRepository studentRepository;

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public List<AdmissionSequenceResponse> list() {
        return sequenceRepository.findAllByOrderByPrefixAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public AdmissionSequenceResponse configure(AdmissionSequenceRequest req) {
        AcademicYear year = req.academicYearId() == null ? null : academicYearRepository.findByIdAndNotDeleted(req.academicYearId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", req.academicYearId()));
        Optional<AdmissionSequence> existing = year != null
                ? sequenceRepository.findByAcademicYearId(year.getId()) : sequenceRepository.findSchoolWide();
        AdmissionSequence s = existing.orElseGet(() -> new AdmissionSequence(req.academicYearId(), defaultPrefix(year), 1, 4));
        if (req.prefix() != null) {
            s.setPrefix(req.prefix().trim());
        }
        if (req.nextNumber() != null) {
            s.setNextNumber(req.nextNumber());
        }
        if (req.padding() != null) {
            s.setPadding(req.padding());
        }
        if (studentRepository.existsByAdmissionNumber(s.peek())) {
            throw new BusinessException("Admission number " + s.peek() + " is already in use; choose a higher nextNumber");
        }
        return toResponse(sequenceRepository.save(s));
    }

    /** The number the next admission in this year would get, without consuming it. */
    public String peek(AcademicYear year) {
        AdmissionSequence s = sequenceRepository.findByAcademicYearId(year.getId())
                .or(sequenceRepository::findSchoolWide)
                .orElseGet(() -> new AdmissionSequence(year.getId(), defaultPrefix(year), 1, 4));
        int n = s.getNextNumber();
        String candidate = s.format(n);
        while (studentRepository.existsByAdmissionNumber(candidate)) {
            candidate = s.format(++n);
        }
        return candidate;
    }

    /** Takes the next free number; the sequence row is locked for the rest of the transaction. */
    public String next(AcademicYear year) {
        AdmissionSequence s = sequenceRepository.lockByYear(year.getId())
                .or(sequenceRepository::lockSchoolWide)
                .orElseGet(() -> sequenceRepository.saveAndFlush(new AdmissionSequence(year.getId(), defaultPrefix(year), 1, 4)));
        String candidate = s.take();
        while (studentRepository.existsByAdmissionNumber(candidate)) {
            candidate = s.take();
        }
        sequenceRepository.save(s);
        return candidate;
    }

    static String defaultPrefix(AcademicYear year) {
        return year != null && year.getStartDate() != null ? year.getStartDate().getYear() + "/" : "";
    }

    private AdmissionSequenceResponse toResponse(AdmissionSequence s) {
        String yearName = null;
        if (s.getAcademicYearId() != null) {
            yearName = academicYearRepository.findByIdAndNotDeleted(s.getAcademicYearId()).map(AcademicYear::getCode).orElse(null);
        }
        return new AdmissionSequenceResponse(s.getId(), s.getAcademicYearId(), yearName, s.getPrefix(), s.getNextNumber(), s.getPadding(), s.peek());
    }
}
