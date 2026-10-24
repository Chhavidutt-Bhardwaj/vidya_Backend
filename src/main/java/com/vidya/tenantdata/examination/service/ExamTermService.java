package com.vidya.tenantdata.examination.service;

import com.vidya.tenantdata.examination.dto.CreateExamTermRequest;
import com.vidya.tenantdata.examination.dto.ExamTermResponse;
import com.vidya.tenantdata.examination.dto.UpdateExamTermRequest;
import com.vidya.tenantdata.examination.enums.ExamTermStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ExamTermService {

    ExamTermResponse createExamTerm(CreateExamTermRequest request);

    ExamTermResponse getExamTermById(UUID id);

    List<ExamTermResponse> getExamTermsByAcademicYear(UUID academicYearId);

    Page<ExamTermResponse> searchExamTerms(UUID academicYearId, ExamTermStatus status, Pageable pageable);

    ExamTermResponse updateExamTerm(UUID id, UpdateExamTermRequest request);

    ExamTermResponse updateTermStatus(UUID id, ExamTermStatus status);

    void deleteExamTerm(UUID id);
}
