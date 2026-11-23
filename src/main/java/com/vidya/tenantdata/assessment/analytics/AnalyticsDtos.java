package com.vidya.tenantdata.assessment.analytics;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record Distribution(BigDecimal average, BigDecimal median, BigDecimal highest, BigDecimal lowest, BigDecimal stdDev) {
    }

    public record SectionAverage(UUID sectionId, String sectionName, int students, BigDecimal average, BigDecimal passRate) {
    }

    public record SubjectPerformance(UUID subjectId, String subjectName, String category, int students, int absent, int passed,
                                     BigDecimal passRate, Distribution percent, Map<String, Integer> grades, String topStudent,
                                     List<SectionAverage> sections) {
    }

    public record ClassPerformance(UUID examTermId, UUID academicClassId, UUID sectionId, int students, Distribution percent,
                                   Map<String, Integer> results, Map<String, Integer> overallGrades, List<SubjectPerformance> subjects) {
    }

    public record YearPoint(UUID academicYearId, String academicYearName, UUID examTermId, String examTermName, int students,
                            BigDecimal average, BigDecimal passRate, Map<String, BigDecimal> subjectAverages) {
    }

    public record YearComparison(UUID academicClassId, String className, UUID subjectId, List<YearPoint> years, BigDecimal changeSinceFirst) {
    }

    public record TermPoint(UUID examTermId, String examTermName, String academicYearName, String className, BigDecimal percentage,
                            String grade, Integer classRank, Integer sectionRank, String result, BigDecimal attendancePercentage) {
    }

    public record SubjectTrend(UUID subjectId, String subjectName, List<BigDecimal> percentages, BigDecimal slopePerTerm,
                               Stats.Trend trend, BigDecimal latest) {
    }

    public record RatingPoint(String examTermName, String target, String level) {
    }

    public record OutcomeAttainment(UUID learningOutcomeId, String code, String statement, UUID subjectId, String subjectName, int questions,
                                    int students, BigDecimal attainment, String level, int studentsBelowHalf) {
    }

    public record StudentProgress(UUID studentId, String studentName, List<TermPoint> terms, Stats.Trend overallTrend,
                                  List<SubjectTrend> subjects, List<String> strengths, List<String> needsAttention,
                                  List<RatingPoint> competencyHistory, List<OutcomeAttainment> outcomes) {
    }

    public record ItemStat(int position, UUID questionId, String questionCode, String questionType, String difficulty, BigDecimal marks,
                           int responses, BigDecimal facility, BigDecimal discrimination, String flag) {
    }

    public record ItemAnalysis(UUID questionPaperId, int students, BigDecimal averagePercent, List<ItemStat> items) {
    }
}
