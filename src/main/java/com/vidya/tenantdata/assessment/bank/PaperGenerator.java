package com.vidya.tenantdata.assessment.bank;

import com.vidya.tenantdata.assessment.enums.BloomLevel;
import com.vidya.tenantdata.assessment.enums.Difficulty;
import com.vidya.tenantdata.assessment.enums.QuestionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Picks questions for a blueprint from the approved pool.
 *
 * <p>For every section: questions must match the section's type, Bloom levels, units and learning outcomes (each filter
 * only when set; a case-based passage must carry exactly the section's marks). The section's easy / medium / hard counts
 * are filled first, the rest from any difficulty. Within those rules the generator prefers questions whose own marks
 * equal the section's, that were used less often and longer ago, and spreads picks across units so the paper covers the
 * syllabus. A question appears once per paper; {@code exclude} keeps questions of other sets or recent papers out.
 * The same seed and pool give the same paper.</p>
 *
 * <p>When the pool is too small the result lists every shortfall (section, difficulty, wanted, available) instead of
 * returning a short paper.</p>
 */
public final class PaperGenerator {

    private PaperGenerator() {
    }

    public record Candidate(UUID id, QuestionType type, Difficulty difficulty, BloomLevel bloom, BigDecimal marks, UUID unitId,
                            Set<UUID> outcomeIds, int timesUsed, Instant lastUsedAt) {
    }

    public record SectionSpec(int position, String title, QuestionType type, BigDecimal marksPerQuestion, int questionCount,
                              int easy, int medium, int hard, Set<BloomLevel> blooms, Set<UUID> unitIds, Set<UUID> outcomeIds) {
    }

    public record Shortfall(int sectionPosition, String sectionTitle, String difficulty, int wanted, int available) {
        public String message() {
            return "Section " + sectionPosition + " (" + sectionTitle + "): needs " + wanted + " " + difficulty
                    + " question(s), " + available + " available";
        }
    }

    /** @param sections question ids per section (in section order), easiest first */
    public record Result(List<List<UUID>> sections, List<Shortfall> shortfalls) {
        public boolean complete() {
            return shortfalls.isEmpty();
        }
    }

    public static Result generate(List<SectionSpec> specs, Collection<Candidate> pool, Set<UUID> exclude, long seed) {
        Random random = new Random(seed);
        Map<UUID, Double> tieBreak = new HashMap<>();
        pool.forEach(c -> tieBreak.put(c.id(), random.nextDouble()));
        Set<UUID> used = new HashSet<>(exclude == null ? Set.of() : exclude);
        List<List<UUID>> out = new ArrayList<>();
        List<Shortfall> shortfalls = new ArrayList<>();
        Map<UUID, Candidate> byId = new HashMap<>();
        pool.forEach(c -> byId.put(c.id(), c));

        for (SectionSpec spec : specs) {
            List<Candidate> eligible = pool.stream().filter(c -> matches(spec, c)).toList();
            List<UUID> picked = new ArrayList<>();
            Map<UUID, Integer> unitUse = new HashMap<>();
            int[] quotas = {spec.easy(), spec.medium(), spec.hard()};
            Difficulty[] levels = {Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD};
            for (int i = 0; i < 3; i++) {
                Difficulty level = levels[i];
                int got = pick(spec, eligible.stream().filter(c -> c.difficulty() == level).toList(), quotas[i], used, picked, unitUse, tieBreak);
                if (got < quotas[i]) {
                    shortfalls.add(new Shortfall(spec.position(), spec.title(), level.name(), quotas[i], got));
                }
            }
            int rest = spec.questionCount() - spec.easy() - spec.medium() - spec.hard();
            if (rest > 0) {
                int got = pick(spec, eligible, rest, used, picked, unitUse, tieBreak);
                if (got < rest) {
                    shortfalls.add(new Shortfall(spec.position(), spec.title(), "any-difficulty", rest, got));
                }
            }
            picked.sort(Comparator.comparing((UUID id) -> byId.get(id).difficulty()).thenComparing(tieBreak::get));
            out.add(picked);
        }
        return new Result(out, shortfalls);
    }

    private static int pick(SectionSpec spec, List<Candidate> from, int wanted, Set<UUID> used, List<UUID> picked,
                            Map<UUID, Integer> unitUse, Map<UUID, Double> tieBreak) {
        int got = 0;
        while (got < wanted) {
            Candidate best = null;
            for (Candidate c : from) {
                if (!used.contains(c.id()) && (best == null || better(spec, c, best, unitUse, tieBreak))) {
                    best = c;
                }
            }
            if (best == null) {
                break;
            }
            used.add(best.id());
            picked.add(best.id());
            if (best.unitId() != null) {
                unitUse.merge(best.unitId(), 1, Integer::sum);
            }
            got++;
        }
        return got;
    }

    /** Is {@code a} a better pick than {@code b}? */
    private static boolean better(SectionSpec spec, Candidate a, Candidate b, Map<UUID, Integer> unitUse, Map<UUID, Double> tieBreak) {
        int ua = a.unitId() == null ? 0 : unitUse.getOrDefault(a.unitId(), 0);
        int ub = b.unitId() == null ? 0 : unitUse.getOrDefault(b.unitId(), 0);
        if (ua != ub) {
            return ua < ub;
        }
        boolean ma = a.marks() != null && a.marks().compareTo(spec.marksPerQuestion()) == 0;
        boolean mb = b.marks() != null && b.marks().compareTo(spec.marksPerQuestion()) == 0;
        if (ma != mb) {
            return ma;
        }
        if (a.timesUsed() != b.timesUsed()) {
            return a.timesUsed() < b.timesUsed();
        }
        Instant la = a.lastUsedAt() == null ? Instant.EPOCH : a.lastUsedAt();
        Instant lb = b.lastUsedAt() == null ? Instant.EPOCH : b.lastUsedAt();
        if (!la.equals(lb)) {
            return la.isBefore(lb);
        }
        return tieBreak.get(a.id()) < tieBreak.get(b.id());
    }

    static boolean matches(SectionSpec spec, Candidate c) {
        if (spec.type() != null && c.type() != spec.type()) {
            return false;
        }
        if (spec.type() == null && c.type() == QuestionType.CASE_BASED) {
            return false;
        }
        if (c.type() == QuestionType.CASE_BASED && (c.marks() == null || c.marks().compareTo(spec.marksPerQuestion()) != 0)) {
            return false;
        }
        if (spec.blooms() != null && !spec.blooms().isEmpty() && (c.bloom() == null || !spec.blooms().contains(c.bloom()))) {
            return false;
        }
        if (spec.unitIds() != null && !spec.unitIds().isEmpty() && (c.unitId() == null || !spec.unitIds().contains(c.unitId()))) {
            return false;
        }
        return spec.outcomeIds() == null || spec.outcomeIds().isEmpty()
                || (c.outcomeIds() != null && c.outcomeIds().stream().anyMatch(spec.outcomeIds()::contains));
    }
}
