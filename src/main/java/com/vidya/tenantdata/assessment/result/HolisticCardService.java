package com.vidya.tenantdata.assessment.result;

import com.vidya.tenantdata.assessment.analytics.AnalyticsDtos.OutcomeAttainment;
import com.vidya.tenantdata.assessment.analytics.AssessmentAnalyticsService;
import com.vidya.tenantdata.assessment.competency.CompetencyDtos.CoScholasticResponse;
import com.vidya.tenantdata.assessment.competency.CompetencyDtos.RatingResponse;
import com.vidya.tenantdata.assessment.competency.CompetencyService;
import com.vidya.tenantdata.assessment.entity.AssessmentScheme;
import com.vidya.tenantdata.assessment.enums.Board;
import com.vidya.tenantdata.assessment.repository.AssessmentSchemeRepository;
import com.vidya.tenantdata.reportcard.dto.StudentReportCardResponse;
import com.vidya.tenantdata.reportcard.entity.StudentReportCard;
import com.vidya.tenantdata.reportcard.mapper.ReportCardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The complete progress card of a term: scholastic results with their formative / summative breakdown, co-scholastic
 * grades, competency and outcome ratings, outcome attainment from question-level marks, attendance and remarks
 * (CBSE holistic progress card; the same shape serves the other boards).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
public class HolisticCardService {

    private final ReportCardEngine engine;
    private final ReportCardMapper mapper;
    private final CompetencyService competencies;
    private final AssessmentAnalyticsService analytics;
    private final AssessmentSchemeRepository schemeRepository;

    public record SchemeRef(UUID id, String name, Board board) {
    }

    public record HolisticCard(StudentReportCardResponse reportCard, SchemeRef scheme, List<CoScholasticResponse> coScholastic,
                               List<RatingResponse> competencyRatings, List<OutcomeAttainment> outcomeAttainment) {
    }

    public HolisticCard card(UUID reportCardId) {
        StudentReportCard card = engine.card(reportCardId);
        engine.requireVisible(card);
        UUID termId = card.getExamTerm().getId();
        UUID studentId = card.getStudent().getId();
        AssessmentScheme scheme = card.getAssessmentSchemeId() == null ? null : schemeRepository.findById(card.getAssessmentSchemeId()).orElse(null);
        return new HolisticCard(mapper.toResponse(card), scheme == null ? null : new SchemeRef(scheme.getId(), scheme.getName(), scheme.getBoard()),
                competencies.coScholastic(termId, studentId, null, null), competencies.ratings(termId, studentId, null, null),
                analytics.studentOutcomes(studentId, termId));
    }
}
