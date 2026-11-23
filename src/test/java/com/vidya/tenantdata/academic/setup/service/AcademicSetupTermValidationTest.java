package com.vidya.tenantdata.academic.setup.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.tenantdata.academic.setup.dto.AcademicYearSetupRequest.Term;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Academic year setup — term validation")
class AcademicSetupTermValidationTest {

    private static final LocalDate START = LocalDate.of(2027, 4, 1);
    private static final LocalDate END = LocalDate.of(2028, 3, 31);

    private static Term term(String name, String from, String to, int weight) {
        return new Term(name, LocalDate.parse(from), LocalDate.parse(to), BigDecimal.valueOf(weight));
    }

    @Test
    void validTerms() {
        assertThatCode(() -> AcademicSetupService.validateTerms(List.of(
                term("Term 1", "2027-04-01", "2027-09-30", 40),
                term("Term 2", "2027-10-01", "2028-03-31", 60)), START, END)).doesNotThrowAnyException();
    }

    @Test
    void outsideYear() {
        assertThatThrownBy(() -> AcademicSetupService.validateTerms(List.of(term("T1", "2027-03-01", "2027-06-30", 50)), START, END))
                .isInstanceOf(BusinessException.class).hasMessageContaining("within the academic year");
    }

    @Test
    void overlapping() {
        assertThatThrownBy(() -> AcademicSetupService.validateTerms(List.of(
                term("T1", "2027-04-01", "2027-10-15", 50),
                term("T2", "2027-10-01", "2028-03-31", 50)), START, END))
                .isInstanceOf(BusinessException.class).hasMessageContaining("overlap");
    }

    @Test
    void weightOver100() {
        assertThatThrownBy(() -> AcademicSetupService.validateTerms(List.of(
                term("T1", "2027-04-01", "2027-09-30", 60),
                term("T2", "2027-10-01", "2028-03-31", 60)), START, END))
                .isInstanceOf(BusinessException.class).hasMessageContaining("100");
    }

    @Test
    void duplicateNames() {
        assertThatThrownBy(() -> AcademicSetupService.validateTerms(List.of(
                term("Term", "2027-04-01", "2027-09-30", 50),
                term("term", "2027-10-01", "2028-03-31", 50)), START, END))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Duplicate");
    }
}
