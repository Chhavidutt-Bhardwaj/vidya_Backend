package com.vidya.tenantdata.assessment;

import com.vidya.common.exception.BusinessException;
import com.vidya.tenantdata.assessment.bank.PaperScoring;
import com.vidya.tenantdata.assessment.bank.PaperScoring.ItemMark;
import com.vidya.tenantdata.assessment.enums.QuestionType;
import com.vidya.tenantdata.assessment.omr.OmrParser;
import com.vidya.tenantdata.assessment.omr.OmrParser.Row;
import com.vidya.tenantdata.assessment.omr.OmrParser.StudentKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OmrAndScoringTest {

    @Test
    @DisplayName("CSV with an answers column, a set column and a BOM")
    void csvAnswers() {
        List<Row> rows = OmrParser.parseCsv("﻿Roll_No,Set,Answers\n12,b,AB-C*\n13,,\"A|B|C\"\n");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).keyType()).isEqualTo(StudentKey.ROLL_NUMBER);
        assertThat(rows.get(0).setLabel()).isEqualTo("B");
        assertThat(rows.get(0).tokens()).containsExactly("A", "B", "-", "C", "*");
        assertThat(rows.get(1).setLabel()).isNull();
    }

    @Test
    @DisplayName("CSV with q1..qN columns; header errors")
    void csvColumns() {
        List<Row> rows = OmrParser.parseCsv("student_code,q1,q2,q3\nSTU-1,A,,AC\n");
        assertThat(rows.get(0).keyType()).isEqualTo(StudentKey.STUDENT_CODE);
        assertThat(rows.get(0).tokens()).containsExactly("A", "", "AC");
        assertThatThrownBy(() -> OmrParser.parseCsv("name,answers\nx,AB\n")).isInstanceOf(BusinessException.class).hasMessageContaining("student_code");
        assertThatThrownBy(() -> OmrParser.parseCsv("roll\n1\n")).hasMessageContaining("answers column");
    }

    @Test
    @DisplayName("tokens become responses: letters or numbers, multi-select, true/false, blank and invalid")
    void tokens() {
        List<String> keys = List.of("A", "B", "C", "D");
        assertThat(OmrParser.toResponse(QuestionType.MCQ_SINGLE, keys, "c").response()).isEqualTo(Map.of("keys", List.of("C")));
        assertThat(OmrParser.toResponse(QuestionType.MCQ_SINGLE, keys, "2").response()).isEqualTo(Map.of("keys", List.of("B")));
        assertThat(OmrParser.toResponse(QuestionType.MCQ_MULTI, keys, "AD").response()).isEqualTo(Map.of("keys", List.of("A", "D")));
        assertThat(OmrParser.toResponse(QuestionType.TRUE_FALSE, keys, "F").response()).isEqualTo(Map.of("value", false));
        assertThat(OmrParser.toResponse(QuestionType.MCQ_SINGLE, keys, "-").blank()).isTrue();
        assertThat(OmrParser.toResponse(QuestionType.MCQ_SINGLE, keys, "*").invalid()).isTrue();
        assertThat(OmrParser.toResponse(QuestionType.MCQ_SINGLE, keys, "E").invalid()).isTrue();
    }

    @Test
    @DisplayName("paper total: 'attempt any 2 of 3' counts the best two; a case study counts once as the sum of its parts")
    void paperTotal() {
        UUID q1 = UUID.randomUUID();
        UUID q2 = UUID.randomUUID();
        UUID q3 = UUID.randomUUID();
        UUID caseStudy = UUID.randomUUID();
        List<ItemMark> items = List.of(
                new ItemMark(1, 2, q1, BigDecimal.valueOf(3)), new ItemMark(1, 2, q2, BigDecimal.valueOf(5)), new ItemMark(1, 2, q3, BigDecimal.valueOf(4)),
                new ItemMark(2, null, caseStudy, BigDecimal.valueOf(1)), new ItemMark(2, null, caseStudy, BigDecimal.valueOf(2)),
                new ItemMark(3, null, UUID.randomUUID(), BigDecimal.valueOf(-1)));
        assertThat(PaperScoring.total(items)).isEqualByComparingTo("11");
    }
}
