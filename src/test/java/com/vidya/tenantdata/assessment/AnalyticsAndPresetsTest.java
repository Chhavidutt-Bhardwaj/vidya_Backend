package com.vidya.tenantdata.assessment;

import com.vidya.tenantdata.assessment.analytics.Stats;
import com.vidya.tenantdata.assessment.enums.Board;
import com.vidya.tenantdata.assessment.scheme.BoardPresets;
import com.vidya.tenantdata.examination.grading.GradingPresets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsAndPresetsTest {

    private static List<BigDecimal> v(double... values) {
        return Arrays.stream(values).mapToObj(BigDecimal::valueOf).toList();
    }

    @Test
    @DisplayName("mean, median, standard deviation, slope and trend")
    void stats() {
        assertThat(Stats.mean(v(60, 70, 80))).isEqualByComparingTo("70");
        assertThat(Stats.median(v(10, 40, 20, 30))).isEqualByComparingTo("25");
        assertThat(Stats.stdDev(v(2, 4, 4, 4, 5, 5, 7, 9))).isEqualByComparingTo("2");
        assertThat(Stats.slope(v(50, 55, 60))).isEqualByComparingTo("5");
        assertThat(Stats.trend(v(50, 55, 60))).isEqualTo(Stats.Trend.IMPROVING);
        assertThat(Stats.trend(v(70, 69, 71))).isEqualTo(Stats.Trend.STEADY);
        assertThat(Stats.trend(v(80, 70))).isEqualTo(Stats.Trend.DECLINING);
        assertThat(Stats.trend(v(80))).isEqualTo(Stats.Trend.NOT_ENOUGH_DATA);
        assertThat(Stats.mean(List.of())).isNull();
    }

    @Test
    @DisplayName("attainment levels split 0–100 evenly")
    void levels() {
        List<String> levels = List.of("BEGINNING", "DEVELOPING", "PROFICIENT", "ADVANCED");
        assertThat(Stats.level(BigDecimal.valueOf(10), levels)).isEqualTo("BEGINNING");
        assertThat(Stats.level(BigDecimal.valueOf(50), levels)).isEqualTo("PROFICIENT");
        assertThat(Stats.level(BigDecimal.valueOf(100), levels)).isEqualTo("ADVANCED");
        assertThat(Stats.level(null, levels)).isNull();
    }

    @Test
    @DisplayName("every board preset is consistent: unique key, known grading scale, components, unique codes")
    void presets() {
        List<BoardPresets.Preset> all = BoardPresets.all();
        assertThat(all.stream().map(BoardPresets.Preset::key).collect(Collectors.toSet())).hasSize(all.size());
        Set<Board> boards = all.stream().map(BoardPresets.Preset::board).collect(Collectors.toSet());
        assertThat(boards).contains(Board.CBSE, Board.ICSE, Board.ISC, Board.STATE, Board.IB, Board.IGCSE, Board.CUSTOM);
        for (BoardPresets.Preset p : all) {
            assertThat(p.components()).as(p.key()).isNotEmpty();
            assertThat(p.components().stream().mapToInt(BoardPresets.Component::weight).sum()).as(p.key() + " weights").isPositive();
            assertThat(new HashSet<>(p.components().stream().map(BoardPresets.Component::code).toList())).as(p.key()).hasSize(p.components().size());
            if (p.gradingPresetKey() != null) {
                assertThat(GradingPresets.find(p.gradingPresetKey())).as(p.key() + " grading " + p.gradingPresetKey()).isPresent();
            }
            assertThat(BoardPresets.find(p.key())).contains(p);
        }
    }
}
