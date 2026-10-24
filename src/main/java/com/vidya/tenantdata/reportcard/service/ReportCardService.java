package com.vidya.tenantdata.reportcard.service;

import com.vidya.tenantdata.reportcard.dto.AddReportCardRemarkRequest;
import com.vidya.tenantdata.reportcard.dto.BatchReportCardGenerationResponse;
import com.vidya.tenantdata.reportcard.dto.GenerateReportCardsRequest;
import com.vidya.tenantdata.reportcard.dto.PublishReportCardsRequest;
import com.vidya.tenantdata.reportcard.dto.ReportCardRemarkResponse;
import com.vidya.tenantdata.reportcard.dto.StudentReportCardResponse;
import com.vidya.tenantdata.reportcard.enums.ReportCardStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ReportCardService {

    BatchReportCardGenerationResponse generateReportCards(GenerateReportCardsRequest request, UUID generatedByStaffId);

    StudentReportCardResponse getReportCardById(UUID id);

    List<StudentReportCardResponse> getReportCardsByStudent(UUID studentId);

    Page<StudentReportCardResponse> searchReportCards(UUID termId, UUID classId, UUID sectionId,
                                                      ReportCardStatus status, Pageable pageable);

    ReportCardRemarkResponse addOrUpdateRemark(UUID reportCardId, AddReportCardRemarkRequest request, UUID staffId);

    int publishReportCards(PublishReportCardsRequest request);

    StudentReportCardResponse revokeReportCard(UUID id);
}
