package com.vidya.tenantdata.assessment;

import com.vidya.common.exception.BusinessException;
import com.vidya.tenantdata.assessment.bank.QuestionFormats;
import com.vidya.tenantdata.assessment.bank.QuestionFormats.Grade;
import com.vidya.tenantdata.assessment.enums.QuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionFormatsTest {

    private static final Map<String, Object> CHOICES = Map.of("choices", List.of(Map.of("key", "A", "text", "1"), Map.of("key", "B", "text", "2"),
            Map.of("key", "C", "text", "3"), Map.of("key", "D", "text", "4")));
    private static final BigDecimal FOUR = BigDecimal.valueOf(4);
    private static final BigDecimal ONE = BigDecimal.ONE;

    private static Grade g(QuestionType t, Map<String, Object> key, Map<String, Object> response) {
        return QuestionFormats.grade(t, key, response, FOUR, ONE);
    }

    @Test
    @DisplayName("validation of answer keys")
    void validate() {
        assertThatCode(() -> QuestionFormats.validate(QuestionType.MCQ_SINGLE, CHOICES, Map.of("keys", List.of("B")))).doesNotThrowAnyException();
        assertThatThrownBy(() -> QuestionFormats.validate(QuestionType.MCQ_SINGLE, CHOICES, Map.of("keys", List.of("B", "C"))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("exactly one");
        assertThatThrownBy(() -> QuestionFormats.validate(QuestionType.MCQ_MULTI, CHOICES, Map.of("keys", List.of("E"))))
                .hasMessageContaining("keys of the choices");
        assertThatThrownBy(() -> QuestionFormats.validate(QuestionType.TRUE_FALSE, null, Map.of("value", "yes"))).hasMessageContaining("true or false");
        assertThatThrownBy(() -> QuestionFormats.validate(QuestionType.NUMERICAL, null, Map.of("value", 9.8, "tolerance", -1)))
                .hasMessageContaining("tolerance");
        assertThatThrownBy(() -> QuestionFormats.validate(QuestionType.MATCH_FOLLOWING,
                Map.of("left", List.of(Map.of("key", "1"), Map.of("key", "2")), "right", List.of(Map.of("key", "a"), Map.of("key", "b"))),
                Map.of("pairs", Map.of("1", "a")))).hasMessageContaining("every left key");
        assertThatCode(() -> QuestionFormats.validate(QuestionType.LONG_ANSWER, null, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("single answer: full marks or the negative marks; blank scores zero")
    void single() {
        Map<String, Object> key = Map.of("keys", List.of("B"));
        assertThat(g(QuestionType.MCQ_SINGLE, key, Map.of("keys", List.of("b"))).marks()).isEqualByComparingTo("4");
        Grade wrong = g(QuestionType.MCQ_SINGLE, key, Map.of("keys", List.of("C")));
        assertThat(wrong.correct()).isFalse();
        assertThat(wrong.marks()).isEqualByComparingTo("-1");
        Grade blank = g(QuestionType.MCQ_SINGLE, key, Map.of());
        assertThat(blank.blank()).isTrue();
        assertThat(blank.marks()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("multi-select: proportional for a correct subset, negative for any wrong pick")
    void multi() {
        Map<String, Object> key = Map.of("keys", List.of("A", "C"));
        assertThat(g(QuestionType.MCQ_MULTI, key, Map.of("keys", List.of("A", "C"))).marks()).isEqualByComparingTo("4");
        assertThat(g(QuestionType.MCQ_MULTI, key, Map.of("keys", List.of("A"))).marks()).isEqualByComparingTo("2");
        assertThat(g(QuestionType.MCQ_MULTI, key, Map.of("keys", List.of("A", "B"))).marks()).isEqualByComparingTo("-1");
    }

    @Test
    @DisplayName("true/false, numerical with tolerance, fill in the blanks, matching")
    void others() {
        assertThat(g(QuestionType.TRUE_FALSE, Map.of("value", true), Map.of("value", "true")).correct()).isTrue();
        Map<String, Object> num = Map.of("value", 9.8, "tolerance", 0.05);
        assertThat(g(QuestionType.NUMERICAL, num, Map.of("value", "9.84")).correct()).isTrue();
        assertThat(g(QuestionType.NUMERICAL, num, Map.of("value", 9.9)).correct()).isFalse();
        Map<String, Object> blanks = Map.of("blanks", List.of(List.of("mitochondria"), List.of("ATP", "adenosine triphosphate")));
        assertThat(g(QuestionType.FILL_BLANK, blanks, Map.of("blanks", List.of(" Mitochondria ", "atp"))).marks()).isEqualByComparingTo("4");
        assertThat(g(QuestionType.FILL_BLANK, blanks, Map.of("blanks", List.of("nucleus", "atp"))).marks()).isEqualByComparingTo("2");
        Map<String, Object> pairs = Map.of("pairs", Map.of("1", "c", "2", "a", "3", "b", "4", "d"));
        assertThat(g(QuestionType.MATCH_FOLLOWING, pairs, Map.of("pairs", Map.of("1", "C", "2", "a", "3", "d"))).marks()).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("written answers wait for a teacher")
    void manual() {
        Grade gr = g(QuestionType.LONG_ANSWER, Map.of(), Map.of("text", "Photosynthesis is ..."));
        assertThat(gr.manual()).isTrue();
        assertThat(gr.marks()).isNull();
        assertThat(QuestionFormats.choiceKeys(CHOICES)).containsExactly("A", "B", "C", "D");
    }
}
