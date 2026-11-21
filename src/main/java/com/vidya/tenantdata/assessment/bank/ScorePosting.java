package com.vidya.tenantdata.assessment.bank;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.tenantdata.assessment.AssessmentAccess;
import com.vidya.tenantdata.assessment.repository.AssessmentQueries;
import com.vidya.tenantdata.assessment.repository.AssessmentQueries.ConfigInfo;
import com.vidya.tenantdata.assessment.repository.AssessmentQueries.Enrolled;
import com.vidya.tenantdata.assessment.entity.Assessment;
import com.vidya.tenantdata.assessment.enums.ScoreSource;
import com.vidya.tenantdata.assessment.formative.AssessmentService;
import com.vidya.tenantdata.assessment.repository.AssessmentRepository;
import com.vidya.tenantdata.examination.dto.BulkStudentMarksRequest;
import com.vidya.tenantdata.examination.dto.SubmitStudentMarkRequest;
import com.vidya.tenantdata.examination.service.StudentMarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.vidya.tenantdata.assessment.repository.AssessmentQueries.params;

/**
 * Posts paper totals (online exam or OMR) as the marks of an exam paper or the scores of a formative assessment, scaled
 * to its maximum; enrolled students without a total are marked absent. Also records question-level scores used by
 * outcome attainment and item analysis.
 */
@Component
@RequiredArgsConstructor
public class ScorePosting {

    private final AssessmentQueries queries;
    private final AssessmentAccess access;
    private final AssessmentRepository assessmentRepository;
    private final AssessmentService assessmentService;
    private final StudentMarkService markService;

    public record Target(UUID termId, UUID yearId, UUID classId, UUID sectionId, UUID subjectId, BigDecimal maxMarks, String label) {
    }

    public record PostResult(String target, BigDecimal targetMaxMarks, int posted, int absent, int skippedNotEnrolled) {
    }

    public record QuestionMark(UUID studentId, UUID questionId, UUID paperId, BigDecimal marks, BigDecimal max, LocalDate on) {
    }

    public Target target(UUID configId, UUID assessmentId) {
        if (configId != null) {
            ConfigInfo c = queries.config(configId).orElseThrow(() -> new ResourceNotFoundException("ExamSubjectConfig", configId));
            return new Target(c.termId(), c.yearId(), c.classId(), null, c.subjectId(), c.maxMarks(), "exam paper");
        }
        if (assessmentId != null) {
            Assessment a = assessmentRepository.findByIdAndDeletedAtIsNull(assessmentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Assessment", assessmentId));
            return new Target(a.getExamTermId(), a.getAcademicYearId(), a.getAcademicClassId(), a.getSectionId(), a.getSubjectId(), a.getMaxMarks(),
                    "assessment");
        }
        throw new BusinessException("NO_TARGET", "Link an exam paper or an assessment to post marks to");
    }

    /**
     * @param totals     paper totals of the students who sat (may be below zero with negative marking; posted as 0)
     * @param sectionId  limits "absent" to this section (null: the whole class, or the assessment's section)
     */
    public PostResult post(UUID configId, UUID assessmentId, UUID sectionId, BigDecimal paperMax, Map<UUID, BigDecimal> totals, String remark) {
        Target t = target(configId, assessmentId);
        if (paperMax == null || paperMax.signum() <= 0) {
            throw new BusinessException("INVALID_PAPER", "The paper has no total marks");
        }
        UUID scope = t.sectionId() != null ? t.sectionId() : sectionId;
        List<Enrolled> enrolled = queries.enrolled(t.yearId(), t.classId(), scope);
        Map<UUID, BigDecimal> scaled = new LinkedHashMap<>();
        int skipped = 0;
        java.util.Set<UUID> ids = new java.util.HashSet<>();
        enrolled.forEach(e -> ids.add(e.studentId()));
        for (Map.Entry<UUID, BigDecimal> e : totals.entrySet()) {
            if (!ids.contains(e.getKey())) {
                skipped++;
                continue;
            }
            scaled.put(e.getKey(), scale(e.getValue(), paperMax, t.maxMarks()));
        }
        int absent = 0;
        if (configId != null) {
            List<SubmitStudentMarkRequest> entries = new ArrayList<>();
            for (Enrolled e : enrolled) {
                BigDecimal m = scaled.get(e.studentId());
                if (m == null) {
                    absent++;
                }
                entries.add(SubmitStudentMarkRequest.builder().studentId(e.studentId()).marksObtained(m).isAbsent(m == null).remarks(remark).build());
            }
            if (!entries.isEmpty()) {
                markService.submitBulkMarks(BulkStudentMarksRequest.builder().examSubjectConfigId(configId).entries(entries).build(), access.staffId());
            }
        } else {
            Assessment a = assessmentRepository.findByIdAndDeletedAtIsNull(assessmentId).orElseThrow();
            Map<UUID, AssessmentService.Mark> marks = new LinkedHashMap<>();
            for (Enrolled e : enrolled) {
                BigDecimal m = scaled.get(e.studentId());
                if (m == null) {
                    absent++;
                }
                marks.put(e.studentId(), new AssessmentService.Mark(m, m == null, false, remark));
            }
            assessmentService.store(a, marks);
        }
        return new PostResult(t.label(), t.maxMarks(), scaled.size(), absent, skipped);
    }

    static BigDecimal scale(BigDecimal total, BigDecimal paperMax, BigDecimal targetMax) {
        BigDecimal v = total.max(BigDecimal.ZERO);
        if (paperMax.compareTo(targetMax) != 0) {
            v = v.multiply(targetMax).divide(paperMax, 2, RoundingMode.HALF_UP);
        }
        return v.min(targetMax).setScale(2, RoundingMode.HALF_UP);
    }

    /** Replaces the question-level scores of these students on these papers. Negative (penalty) marks count as zero. */
    public void questionScores(UUID termId, ScoreSource source, List<QuestionMark> marks) {
        if (marks.isEmpty()) {
            return;
        }
        queries.update("DELETE FROM question_score WHERE question_paper_id IN (:p) AND student_id IN (:s)",
                params("p", marks.stream().map(QuestionMark::paperId).distinct().toList(), "s", marks.stream().map(QuestionMark::studentId).distinct().toList()));
        for (QuestionMark m : marks) {
            queries.update("INSERT INTO question_score (student_id, question_id, question_paper_id, exam_term_id, source, marks_awarded, max_marks, "
                            + "assessed_on) VALUES (:s, :q, :p, :t, :src, :m, :max, :on) ON CONFLICT (student_id, question_id, question_paper_id) DO UPDATE "
                            + "SET marks_awarded = EXCLUDED.marks_awarded, max_marks = EXCLUDED.max_marks, assessed_on = EXCLUDED.assessed_on",
                    params("s", m.studentId(), "q", m.questionId(), "p", m.paperId(), "t", termId, "src", source.name(),
                            "m", m.marks() == null ? BigDecimal.ZERO : m.marks().max(BigDecimal.ZERO), "max", m.max(), "on", m.on()));
        }
    }
}
