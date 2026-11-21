package com.vidya.tenantdata.assessment;

import com.vidya.tenantdata.assessment.enums.AssessmentType;
import com.vidya.tenantdata.assessment.enums.ComponentAggregation;
import com.vidya.tenantdata.assessment.enums.MarkRounding;
import com.vidya.tenantdata.assessment.enums.ModerationMethod;
import com.vidya.tenantdata.assessment.enums.SubjectCategory;
import com.vidya.tenantdata.assessment.enums.TermAggregation;
import com.vidya.tenantdata.assessment.result.ResultCalculator;
import com.vidya.tenantdata.assessment.result.ResultCalculator.ComponentInput;
import com.vidya.tenantdata.assessment.result.ResultCalculator.Mark;
import com.vidya.tenantdata.assessment.result.ResultCalculator.Outcome;
import com.vidya.tenantdata.assessment.result.ResultCalculator.Rules;
import com.vidya.tenantdata.assessment.result.ResultCalculator.SubjectInput;
import com.vidya.tenantdata.assessment.result.ResultCalculator.SubjectResult;
import com.vidya.tenantdata.assessment.result.ResultCalculator.TermScore;
import com.vidya.tenantdata.reportcard.enums.ResultStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResultCalculatorTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static Mark m(double got, double max) {
        return new Mark(BigDecimal.valueOf(got), BigDecimal.valueOf(max), false, false, null);
    }

    private static ComponentInput comp(String code, int weight, ComponentAggregation agg, Integer bestOf, boolean passRequired, Mark... marks) {
        return new ComponentInput(UUID.randomUUID(), code, code, code.equals("TERM") ? AssessmentType.SUMMATIVE : AssessmentType.FORMATIVE,
                BigDecimal.valueOf(weight), agg, bestOf, passRequired, true, List.of(marks));
    }

    /** CBSE-like subject: PT 10 (best 2 of 3) + NB 5 + SE 5 + term 80. */
    private static SubjectInput cbse(String name, SubjectCategory cat, double term) {
        return new SubjectInput(UUID.randomUUID(), name, name, cat, null, List.of(
                comp("PT", 10, ComponentAggregation.BEST_OF, 2, false, m(8, 20), m(18, 20), m(16, 20)),
                comp("NB", 5, ComponentAggregation.AVERAGE, null, false, m(5, 5)),
                comp("SE", 5, ComponentAggregation.AVERAGE, null, false, m(4, 5)),
                comp("TERM", 80, ComponentAggregation.LATEST, null, false, m(term, 80))));
    }

    private static Rules rules(double grace, int compartment, Integer bestOf) {
        return new Rules(d("33"), null, false, MarkRounding.HALF_UP, 0, bestOf, BigDecimal.valueOf(grace), BigDecimal.ZERO, 0, compartment);
    }

    @Test
    @DisplayName("weighted components: best 2 of 3 periodic tests, averages, term exam; rounded to whole marks")
    void components() {
        Outcome o = ResultCalculator.compute(rules(0, 0, null), List.of(cbse("Maths", SubjectCategory.SCHOLASTIC, 60)));
        SubjectResult s = o.subjects().get(0);
        // PT: (90 + 80) / 2 = 85% of 10 = 8.5; NB 5; SE 4; term 60 → 77.5 → 78
        assertThat(s.rawMarks()).isEqualByComparingTo("77.5");
        assertThat(s.marks()).isEqualByComparingTo("78");
        assertThat(s.maxMarks()).isEqualByComparingTo("100");
        assertThat(o.status()).isEqualTo(ResultStatus.PASSED);
    }

    @Test
    @DisplayName("grace lifts a near miss to the pass mark; a larger deficit stays failed → compartment")
    void graceAndCompartment() {
        // term 13 → 8.5 + 5 + 4 + 13 = 30.5 → 31 (needs 33: deficit 2)
        // term 5  → 22.5 → 23 (deficit 10)
        Outcome o = ResultCalculator.compute(rules(3, 2, null), List.of(
                cbse("Maths", SubjectCategory.SCHOLASTIC, 13), cbse("Science", SubjectCategory.SCHOLASTIC, 5),
                cbse("English", SubjectCategory.SCHOLASTIC, 70)));
        SubjectResult maths = o.subjects().get(0);
        assertThat(maths.passing()).isTrue();
        assertThat(maths.graceMarks()).isEqualByComparingTo("2");
        assertThat(maths.marks()).isEqualByComparingTo("33");
        assertThat(o.subjects().get(1).passing()).isFalse();
        assertThat(o.graceApplied()).isTrue();
        assertThat(o.status()).isEqualTo(ResultStatus.COMPARTMENT);
        assertThat(o.failedSubjects()).isEqualTo(1);
    }

    @Test
    @DisplayName("a passed additional subject replaces a failed main subject")
    void additionalReplaces() {
        Outcome o = ResultCalculator.compute(rules(0, 0, null), List.of(
                cbse("Maths", SubjectCategory.SCHOLASTIC, 5), cbse("English", SubjectCategory.SCHOLASTIC, 70),
                cbse("IT", SubjectCategory.ADDITIONAL, 60)));
        assertThat(o.status()).isEqualTo(ResultStatus.PASSED);
        assertThat(o.subjects().get(0).evaluated()).isFalse();
        assertThat(o.subjects().get(2).countedInTotal()).isTrue();
        assertThat(o.note()).contains("IT replaces Maths");
    }

    @Test
    @DisplayName("best-of subjects: only the best N count in the percentage")
    void bestOf() {
        List<SubjectInput> subjects = new ArrayList<>();
        double[] terms = {70, 60, 50, 40, 30, 20};
        for (int i = 0; i < terms.length; i++) {
            subjects.add(cbse("S" + i, SubjectCategory.SCHOLASTIC, terms[i]));
        }
        Outcome o = ResultCalculator.compute(rules(0, 0, 5), subjects);
        assertThat(o.subjects().stream().filter(SubjectResult::countedInTotal)).hasSize(5);
        assertThat(o.subjects().get(5).countedInTotal()).isFalse();
        assertThat(o.totalMax()).isEqualByComparingTo("500");
    }

    @Test
    @DisplayName("separate component pass: failing the theory paper fails the subject")
    void separateComponentPass() {
        Rules r = new Rules(d("33"), null, true, MarkRounding.NONE, 2, null, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
        SubjectInput physics = new SubjectInput(UUID.randomUUID(), "Physics", "PHY", SubjectCategory.SCHOLASTIC, null, List.of(
                comp("TERM", 70, ComponentAggregation.LATEST, null, true, m(15, 70)),
                comp("PRAC", 30, ComponentAggregation.LATEST, null, true, m(30, 30))));
        Outcome o = ResultCalculator.compute(r, List.of(physics));
        assertThat(o.subjects().get(0).marks()).isEqualByComparingTo("45");
        assertThat(o.subjects().get(0).passing()).isFalse();
        assertThat(o.status()).isEqualTo(ResultStatus.FAILED);
    }

    @Test
    @DisplayName("exempt in every component → subject exempt, not counted; no main subject → withheld")
    void exempt() {
        SubjectInput s = new SubjectInput(UUID.randomUUID(), "Hindi", "HIN", SubjectCategory.SCHOLASTIC, null, List.of(
                comp("TERM", 80, ComponentAggregation.LATEST, null, false, new Mark(null, d("80"), false, true, LocalDate.now()))));
        Outcome o = ResultCalculator.compute(rules(0, 0, null), List.of(s));
        assertThat(o.subjects().get(0).exempt()).isTrue();
        assertThat(o.status()).isEqualTo(ResultStatus.WITHHELD);
    }

    @Test
    @DisplayName("cumulative: weighted by term weight, plain average, or latest")
    void cumulative() {
        List<TermScore> terms = List.of(new TermScore(d("60"), d("40")), new TermScore(d("80"), d("60")));
        assertThat(ResultCalculator.cumulative(TermAggregation.WEIGHTED, terms)).isEqualByComparingTo("72");
        assertThat(ResultCalculator.cumulative(TermAggregation.AVERAGE, terms)).isEqualByComparingTo("70");
        assertThat(ResultCalculator.cumulative(TermAggregation.LATEST, terms)).isEqualByComparingTo("80");
    }

    @Test
    @DisplayName("moderation never goes above the maximum; raise-to-pass only within the limit")
    void moderation() {
        assertThat(ResultCalculator.moderationDelta(ModerationMethod.ADD_FLAT, d("5"), d("78"), d("78"), d("80"), d("26"))).isEqualByComparingTo("2");
        assertThat(ResultCalculator.moderationDelta(ModerationMethod.ADD_PERCENT_OF_MAX, d("5"), d("40"), d("40"), d("80"), null)).isEqualByComparingTo("4");
        assertThat(ResultCalculator.moderationDelta(ModerationMethod.SCALE_FACTOR, d("1.1"), d("50"), d("50"), d("80"), null)).isEqualByComparingTo("5");
        assertThat(ResultCalculator.moderationDelta(ModerationMethod.RAISE_TO_PASS_WITHIN, d("3"), d("24"), d("24"), d("80"), d("26"))).isEqualByComparingTo("2");
        assertThat(ResultCalculator.moderationDelta(ModerationMethod.RAISE_TO_PASS_WITHIN, d("3"), d("20"), d("20"), d("80"), d("26"))).isEqualByComparingTo("0");
    }
}
