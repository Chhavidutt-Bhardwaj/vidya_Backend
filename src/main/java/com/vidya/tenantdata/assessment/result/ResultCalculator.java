package com.vidya.tenantdata.assessment.result;

import com.vidya.tenantdata.assessment.enums.AssessmentType;
import com.vidya.tenantdata.assessment.enums.ComponentAggregation;
import com.vidya.tenantdata.assessment.enums.MarkRounding;
import com.vidya.tenantdata.assessment.enums.ModerationMethod;
import com.vidya.tenantdata.assessment.enums.SubjectCategory;
import com.vidya.tenantdata.assessment.enums.TermAggregation;
import com.vidya.tenantdata.reportcard.enums.ResultStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Board-neutral result rules. Everything a board does differently is data in {@link Rules} and the components,
 * so CBSE, ICSE/ISC, state boards, IB and IGCSE share one calculation:
 *
 * <ol>
 *   <li>Each component turns its marks into a percentage (sum, average, best-of-n, latest or highest) and scores
 *       {@code percentage × weight / 100}. A component not conducted for the student's class is left out and the
 *       subject is out of the remaining weights. Exempt marks are ignored; absence counts as zero.</li>
 *   <li>Subject marks = rounded sum of component scores; it passes at the pass percentage (and, if the scheme says so,
 *       each pass-required component must pass on its own).</li>
 *   <li>Grace: failed subjects short by at most the per-subject limit pass, smallest shortfall first, within the
 *       subject-count and total limits.</li>
 *   <li>An additional subject that passed replaces a failed main subject (lowest first).</li>
 *   <li>Totals use the best N main subjects when the scheme sets best-of.</li>
 *   <li>No failures → PASSED (or FAILED under the overall pass percentage); up to the compartment limit → COMPARTMENT.</li>
 * </ol>
 */
public final class ResultCalculator {

    static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ResultCalculator() {
    }

    /**
     * @param graceMaxPerSubject 0 = no grace
     * @param graceMaxTotal      0 = no limit on the total grace
     * @param graceMaxSubjects   0 = no limit on the number of subjects
     */
    public record Rules(BigDecimal subjectPassPercent, BigDecimal overallPassPercent, boolean separateComponentPass,
                        MarkRounding rounding, int roundingScale, Integer bestOfSubjects,
                        BigDecimal graceMaxPerSubject, BigDecimal graceMaxTotal, int graceMaxSubjects, int compartmentMaxSubjects) {

        /** Used when a class has no active scheme: exam paper marks as entered, no grace, no compartment. */
        public static Rules plain(BigDecimal passPercent) {
            return new Rules(passPercent != null ? passPercent : BigDecimal.valueOf(33), null, false, MarkRounding.NONE, 2, null,
                    BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
        }
    }

    /** One mark feeding a component: an assessment score or the exam paper (after approved adjustments). */
    public record Mark(BigDecimal obtained, BigDecimal max, boolean absent, boolean exempt, LocalDate date) {
    }

    /**
     * @param conducted at least one assessment / exam paper of this component exists for the student's class or section
     */
    public record ComponentInput(UUID componentId, String code, String name, AssessmentType type, BigDecimal weight,
                                 ComponentAggregation aggregation, Integer bestOf, boolean passRequired, boolean conducted,
                                 List<Mark> marks) {
    }

    /** @param passPercent subject-specific pass percentage, null = the scheme's */
    public record SubjectInput(UUID subjectId, String name, String code, SubjectCategory category, BigDecimal passPercent,
                               List<ComponentInput> components) {
    }

    public record ComponentResult(String code, String name, AssessmentType type, BigDecimal weight, boolean included,
                                  BigDecimal percentage, BigDecimal score, int marksCounted, String note) {
    }

    /**
     * @param evaluated       counts for pass / fail (main subjects, or an additional subject that replaced one)
     * @param countedInTotal  part of the total and percentage (evaluated and within best-of)
     */
    public record SubjectResult(UUID subjectId, String name, String code, SubjectCategory category, BigDecimal maxMarks,
                                BigDecimal passMarks, BigDecimal rawMarks, BigDecimal marks, BigDecimal percentage,
                                BigDecimal graceMarks, boolean passing, boolean absent, boolean exempt, boolean evaluated,
                                boolean countedInTotal, List<ComponentResult> components, String note) {

        SubjectResult with(BigDecimal newMarks, BigDecimal grace, boolean pass, boolean eval, boolean inTotal, String newNote) {
            BigDecimal pct = percent(newMarks, maxMarks);
            return new SubjectResult(subjectId, name, code, category, maxMarks, passMarks, rawMarks, newMarks, pct, grace, pass,
                    absent, exempt, eval, inTotal, components, newNote);
        }
    }

    public record Outcome(List<SubjectResult> subjects, BigDecimal totalMax, BigDecimal totalObtained, BigDecimal percentage,
                          ResultStatus status, int failedSubjects, boolean graceApplied, String note) {
    }

    public record TermScore(BigDecimal percentage, BigDecimal weight) {
    }

    // ───────────────────────────── calculation ─────────────────────────────

    public static Outcome compute(Rules rules, List<SubjectInput> inputs) {
        List<SubjectResult> subjects = new ArrayList<>();
        for (SubjectInput in : inputs) {
            if (in.category() == SubjectCategory.EXCLUDED) {
                continue;
            }
            SubjectResult r = subject(rules, in);
            if (r != null) {
                subjects.add(r);
            }
        }
        List<String> notes = new ArrayList<>();
        boolean graceApplied = applyGrace(rules, subjects, notes);
        replaceWithAdditional(subjects, notes);
        applyBestOf(rules, subjects);

        List<SubjectResult> evaluated = subjects.stream().filter(SubjectResult::evaluated).toList();
        if (evaluated.isEmpty()) {
            return new Outcome(subjects, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, ResultStatus.WITHHELD, 0, false,
                    "No marks recorded for any main subject");
        }
        BigDecimal totalMax = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (SubjectResult s : subjects) {
            if (s.countedInTotal()) {
                totalMax = totalMax.add(s.maxMarks());
                total = total.add(s.marks());
            }
        }
        BigDecimal percentage = percent(total, totalMax);
        int failed = (int) evaluated.stream().filter(s -> !s.passing()).count();
        ResultStatus status;
        if (failed == 0) {
            status = rules.overallPassPercent() != null && percentage.compareTo(rules.overallPassPercent()) < 0 ? ResultStatus.FAILED : ResultStatus.PASSED;
            if (status == ResultStatus.FAILED) {
                notes.add("Overall percentage below " + plain(rules.overallPassPercent()) + "%");
            }
        } else if (failed <= rules.compartmentMaxSubjects()) {
            status = ResultStatus.COMPARTMENT;
            notes.add("Compartment in " + String.join(", ", evaluated.stream().filter(s -> !s.passing()).map(SubjectResult::name).toList()));
        } else {
            status = ResultStatus.FAILED;
        }
        return new Outcome(subjects, totalMax, total, percentage, status, failed, graceApplied, notes.isEmpty() ? null : String.join("; ", notes));
    }

    static SubjectResult subject(Rules rules, SubjectInput in) {
        List<ComponentResult> components = in.components().stream().map(ResultCalculator::component).filter(Objects::nonNull).toList();
        List<ComponentResult> included = components.stream().filter(ComponentResult::included).toList();
        if (components.isEmpty()) {
            return null;
        }
        boolean main = in.category() == SubjectCategory.SCHOLASTIC;
        BigDecimal passPct = in.passPercent() != null ? in.passPercent() : rules.subjectPassPercent();
        if (included.isEmpty()) {
            return new SubjectResult(in.subjectId(), in.name(), in.code(), in.category(), BigDecimal.ZERO, BigDecimal.ZERO, null, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, true, false, true, false, false, components, "Exempt");
        }
        BigDecimal max = included.stream().map(ComponentResult::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal raw = included.stream().map(ComponentResult::score).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal marks = round(raw, rules.rounding(), rules.roundingScale());
        BigDecimal passMarks = max.multiply(passPct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        boolean componentsPass = !rules.separateComponentPass() || included.stream()
                .filter(c -> passRequired(in, c)).allMatch(c -> c.percentage().compareTo(passPct) >= 0);
        boolean passing = marks.compareTo(passMarks) >= 0 && componentsPass;
        boolean absent = in.components().stream().filter(c -> c.conducted() && !c.marks().isEmpty())
                .allMatch(c -> c.marks().stream().allMatch(m -> m.absent() || m.exempt()));
        String note = componentsPass ? null : "Below the pass mark in a component that must be passed separately";
        return new SubjectResult(in.subjectId(), in.name(), in.code(), in.category(), max, passMarks, raw.setScale(2, RoundingMode.HALF_UP),
                marks, percent(marks, max), BigDecimal.ZERO, passing, absent, false, main, main, components, note);
    }

    private static boolean passRequired(SubjectInput in, ComponentResult c) {
        return in.components().stream().anyMatch(i -> i.code().equals(c.code()) && i.passRequired());
    }

    /** Percentage and weighted score of one component; null if it was not conducted for the class. */
    static ComponentResult component(ComponentInput c) {
        if (!c.conducted()) {
            return null;
        }
        List<Mark> counted = c.marks().stream().filter(m -> !m.exempt() && m.max() != null && m.max().signum() > 0).toList();
        if (counted.isEmpty()) {
            if (!c.marks().isEmpty()) {
                return new ComponentResult(c.code(), c.name(), c.type(), c.weight(), false, null, null, 0, "Exempt");
            }
            return new ComponentResult(c.code(), c.name(), c.type(), c.weight(), true, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), 0, "Not assessed");
        }
        BigDecimal pct = switch (c.aggregation()) {
            case SUM -> {
                BigDecimal got = counted.stream().map(ResultCalculator::obtained).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal max = counted.stream().map(Mark::max).reduce(BigDecimal.ZERO, BigDecimal::add);
                yield got.multiply(HUNDRED).divide(max, 6, RoundingMode.HALF_UP);
            }
            case AVERAGE -> mean(counted.stream().map(ResultCalculator::pct).toList());
            case BEST_OF -> mean(counted.stream().map(ResultCalculator::pct).sorted(Comparator.reverseOrder())
                    .limit(c.bestOf() != null && c.bestOf() > 0 ? c.bestOf() : counted.size()).toList());
            case LATEST -> {
                Mark last = counted.get(0);
                for (Mark m : counted) {
                    if (m.date() != null && (last.date() == null || !m.date().isBefore(last.date()))) {
                        last = m;
                    }
                }
                yield pct(last);
            }
            case MAX -> counted.stream().map(ResultCalculator::pct).max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        };
        pct = pct.min(HUNDRED).max(BigDecimal.ZERO);
        BigDecimal score = pct.multiply(c.weight()).divide(HUNDRED, 6, RoundingMode.HALF_UP);
        return new ComponentResult(c.code(), c.name(), c.type(), c.weight(), true, pct.setScale(2, RoundingMode.HALF_UP), score,
                counted.size(), null);
    }

    private static boolean applyGrace(Rules rules, List<SubjectResult> subjects, List<String> notes) {
        BigDecimal perSubject = rules.graceMaxPerSubject();
        if (perSubject == null || perSubject.signum() <= 0) {
            return false;
        }
        record Candidate(int index, BigDecimal deficit) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < subjects.size(); i++) {
            SubjectResult s = subjects.get(i);
            if (s.evaluated() && !s.passing() && !s.absent() && s.note() == null) {
                BigDecimal deficit = roundUp(s.passMarks().subtract(s.marks()), rules);
                if (deficit.signum() > 0 && deficit.compareTo(perSubject) <= 0) {
                    candidates.add(new Candidate(i, deficit));
                }
            }
        }
        candidates.sort(Comparator.comparing(Candidate::deficit));
        BigDecimal used = BigDecimal.ZERO;
        int graced = 0;
        for (Candidate c : candidates) {
            if (rules.graceMaxSubjects() > 0 && graced >= rules.graceMaxSubjects()) {
                break;
            }
            if (rules.graceMaxTotal() != null && rules.graceMaxTotal().signum() > 0 && used.add(c.deficit()).compareTo(rules.graceMaxTotal()) > 0) {
                continue;
            }
            SubjectResult s = subjects.get(c.index());
            subjects.set(c.index(), s.with(s.marks().add(c.deficit()), c.deficit(), true, true, true, "Grace " + plain(c.deficit())));
            used = used.add(c.deficit());
            graced++;
        }
        if (graced > 0) {
            notes.add("Grace of " + plain(used) + " mark(s) in " + graced + " subject(s)");
        }
        return graced > 0;
    }

    private static void replaceWithAdditional(List<SubjectResult> subjects, List<String> notes) {
        List<Integer> additional = new ArrayList<>();
        for (int i = 0; i < subjects.size(); i++) {
            SubjectResult s = subjects.get(i);
            if (s.category() == SubjectCategory.ADDITIONAL && s.passing() && !s.exempt()) {
                additional.add(i);
            }
        }
        additional.sort(Comparator.comparing((Integer i) -> subjects.get(i).percentage()).reversed());
        for (Integer a : additional) {
            int failed = -1;
            for (int i = 0; i < subjects.size(); i++) {
                SubjectResult s = subjects.get(i);
                if (s.evaluated() && !s.passing() && (failed < 0 || s.percentage().compareTo(subjects.get(failed).percentage()) < 0)) {
                    failed = i;
                }
            }
            if (failed < 0) {
                return;
            }
            SubjectResult f = subjects.get(failed);
            SubjectResult add = subjects.get(a);
            subjects.set(failed, f.with(f.marks(), f.graceMarks(), false, false, false, "Replaced by " + add.name()));
            subjects.set(a, add.with(add.marks(), add.graceMarks(), true, true, true, "Replaces " + f.name()));
            notes.add(add.name() + " replaces " + f.name());
        }
    }

    private static void applyBestOf(Rules rules, List<SubjectResult> subjects) {
        Integer n = rules.bestOfSubjects();
        if (n == null || n <= 0) {
            return;
        }
        List<Integer> counted = new ArrayList<>();
        for (int i = 0; i < subjects.size(); i++) {
            if (subjects.get(i).countedInTotal()) {
                counted.add(i);
            }
        }
        if (counted.size() <= n) {
            return;
        }
        counted.sort(Comparator.comparing((Integer i) -> subjects.get(i).percentage()).reversed());
        for (Integer i : counted.subList(n, counted.size())) {
            SubjectResult s = subjects.get(i);
            subjects.set(i, s.with(s.marks(), s.graceMarks(), s.passing(), true, false,
                    s.note() == null ? "Not in best " + n : s.note() + "; not in best " + n));
        }
    }

    // ───────────────────────────── cumulative and moderation ─────────────────────────────

    /** Year-to-date percentage from term percentages (oldest first). */
    public static BigDecimal cumulative(TermAggregation aggregation, List<TermScore> terms) {
        List<TermScore> known = terms.stream().filter(t -> t.percentage() != null).toList();
        if (known.isEmpty()) {
            return null;
        }
        if (aggregation == TermAggregation.LATEST) {
            return known.get(known.size() - 1).percentage();
        }
        BigDecimal weights = known.stream().map(t -> t.weight() != null ? t.weight() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (aggregation == TermAggregation.AVERAGE || weights.signum() <= 0) {
            return mean(known.stream().map(TermScore::percentage).toList()).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (TermScore t : known) {
            sum = sum.add(t.percentage().multiply(t.weight() != null ? t.weight() : BigDecimal.ZERO));
        }
        return sum.divide(weights, 2, RoundingMode.HALF_UP);
    }

    /**
     * Marks a moderation adds for one student. The result never takes marks above the maximum or below zero.
     *
     * @param raw     marks as entered
     * @param current marks after earlier approved adjustments
     */
    public static BigDecimal moderationDelta(ModerationMethod method, BigDecimal value, BigDecimal raw, BigDecimal current,
                                             BigDecimal max, BigDecimal passMarks) {
        BigDecimal delta = switch (method) {
            case ADD_FLAT -> value;
            case ADD_PERCENT_OF_MAX -> max.multiply(value).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            case SCALE_FACTOR -> raw.multiply(value.subtract(BigDecimal.ONE)).setScale(2, RoundingMode.HALF_UP);
            case RAISE_TO_PASS_WITHIN -> {
                BigDecimal shortBy = passMarks != null ? passMarks.subtract(current) : BigDecimal.ZERO;
                yield shortBy.signum() > 0 && shortBy.compareTo(value) <= 0 ? shortBy : BigDecimal.ZERO;
            }
        };
        BigDecimal target = current.add(delta).min(max).max(BigDecimal.ZERO);
        return target.subtract(current).setScale(2, RoundingMode.HALF_UP);
    }

    // ───────────────────────────── helpers ─────────────────────────────

    public static BigDecimal round(BigDecimal v, MarkRounding mode, int scale) {
        BigDecimal clean = v.setScale(4, RoundingMode.HALF_UP);
        return switch (mode == null ? MarkRounding.NONE : mode) {
            case NONE -> clean.setScale(2, RoundingMode.HALF_UP);
            case HALF_UP -> clean.setScale(scale, RoundingMode.HALF_UP);
            case UP -> clean.setScale(scale, RoundingMode.CEILING);
            case DOWN -> clean.setScale(scale, RoundingMode.FLOOR);
        };
    }

    private static BigDecimal roundUp(BigDecimal v, Rules rules) {
        int scale = rules.rounding() == MarkRounding.NONE ? 2 : rules.roundingScale();
        return v.setScale(4, RoundingMode.HALF_UP).setScale(scale, RoundingMode.CEILING);
    }

    public static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (part == null || whole == null || whole.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal obtained(Mark m) {
        return m.absent() || m.obtained() == null ? BigDecimal.ZERO : m.obtained().min(m.max()).max(BigDecimal.ZERO);
    }

    private static BigDecimal pct(Mark m) {
        return obtained(m).multiply(HUNDRED).divide(m.max(), 6, RoundingMode.HALF_UP);
    }

    static BigDecimal mean(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP);
    }

    static String plain(BigDecimal d) {
        return d.stripTrailingZeros().toPlainString();
    }
}
