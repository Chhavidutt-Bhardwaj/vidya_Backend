package com.vidya.tenantdata.assessment;

import com.vidya.tenantdata.assessment.bank.PaperGenerator;
import com.vidya.tenantdata.assessment.bank.PaperGenerator.Candidate;
import com.vidya.tenantdata.assessment.bank.PaperGenerator.Result;
import com.vidya.tenantdata.assessment.bank.PaperGenerator.SectionSpec;
import com.vidya.tenantdata.assessment.enums.Difficulty;
import com.vidya.tenantdata.assessment.enums.QuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PaperGeneratorTest {

    private static final UUID UNIT_1 = UUID.randomUUID();
    private static final UUID UNIT_2 = UUID.randomUUID();

    private static List<Candidate> pool() {
        List<Candidate> out = new ArrayList<>();
        Difficulty[] levels = {Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD};
        for (int i = 0; i < 12; i++) {
            out.add(new Candidate(UUID.randomUUID(), QuestionType.MCQ_SINGLE, levels[i % 3], null, BigDecimal.ONE, i % 2 == 0 ? UNIT_1 : UNIT_2,
                    Set.of(), 0, null));
        }
        for (int i = 0; i < 4; i++) {
            out.add(new Candidate(UUID.randomUUID(), QuestionType.LONG_ANSWER, Difficulty.HARD, null, BigDecimal.valueOf(5), UNIT_1, Set.of(), i, null));
        }
        return out;
    }

    private static List<SectionSpec> specs() {
        return List.of(
                new SectionSpec(1, "A", QuestionType.MCQ_SINGLE, BigDecimal.ONE, 6, 2, 2, 2, Set.of(), Set.of(), Set.of()),
                new SectionSpec(2, "B", QuestionType.LONG_ANSWER, BigDecimal.valueOf(5), 2, 0, 0, 2, Set.of(), Set.of(), Set.of()));
    }

    @Test
    @DisplayName("meets difficulty quotas, spreads units, no repeats, deterministic per seed")
    void generates() {
        List<Candidate> pool = pool();
        Map<UUID, Candidate> byId = pool.stream().collect(Collectors.toMap(Candidate::id, Function.identity()));
        Result r = PaperGenerator.generate(specs(), pool, Set.of(), 42L);
        assertThat(r.complete()).isTrue();
        List<UUID> a = r.sections().get(0);
        assertThat(a).hasSize(6).doesNotHaveDuplicates();
        assertThat(a.stream().filter(id -> byId.get(id).difficulty() == Difficulty.HARD)).hasSize(2);
        assertThat(a.stream().filter(id -> byId.get(id).unitId().equals(UNIT_1))).hasSize(3);
        // least used long answers first
        assertThat(r.sections().get(1).stream().map(id -> byId.get(id).timesUsed())).containsExactlyInAnyOrder(0, 1);
        assertThat(PaperGenerator.generate(specs(), pool, Set.of(), 42L).sections()).isEqualTo(r.sections());
    }

    @Test
    @DisplayName("a second set excludes the first set's questions; shortfalls are reported")
    void shortfall() {
        List<Candidate> pool = pool();
        Result first = PaperGenerator.generate(specs(), pool, Set.of(), 1L);
        Set<UUID> used = new HashSet<>();
        first.sections().forEach(used::addAll);
        Result second = PaperGenerator.generate(specs(), pool, used, 2L);
        assertThat(second.sections().get(0)).doesNotContainAnyElementsOf(first.sections().get(0));
        assertThat(second.complete()).isTrue();
        Result third = PaperGenerator.generate(specs(), pool, new HashSet<>(pool.stream().map(Candidate::id).toList()), 3L);
        assertThat(third.complete()).isFalse();
        assertThat(third.shortfalls()).extracting(PaperGenerator.Shortfall::difficulty).contains("EASY", "HARD");
    }

    @Test
    @DisplayName("recently used questions are avoided when fresh ones exist")
    void prefersFresh() {
        UUID fresh = UUID.randomUUID();
        UUID used = UUID.randomUUID();
        List<Candidate> pool = List.of(
                new Candidate(used, QuestionType.TRUE_FALSE, Difficulty.EASY, null, BigDecimal.ONE, null, Set.of(), 3, Instant.now()),
                new Candidate(fresh, QuestionType.TRUE_FALSE, Difficulty.EASY, null, BigDecimal.ONE, null, Set.of(), 0, null));
        Result r = PaperGenerator.generate(List.of(new SectionSpec(1, "TF", QuestionType.TRUE_FALSE, BigDecimal.ONE, 1, 0, 0, 0, Set.of(), Set.of(),
                Set.of())), pool, Set.of(), 9L);
        assertThat(r.sections().get(0)).containsExactly(fresh);
    }
}
