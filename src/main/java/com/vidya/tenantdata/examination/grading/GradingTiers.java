package com.vidya.tenantdata.examination.grading;

import com.vidya.common.exception.BusinessException;
import com.vidya.tenantdata.examination.dto.GradingScaleTierDto;
import com.vidya.tenantdata.examination.entity.GradingScaleTier;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Rules for grade tiers and the lookup used when marks are entered.
 *
 * <p>Boards publish whole-number bands (A1 91–100, A2 81–90 …), so a mark of 90.5 % falls between
 * two tiers. Gaps of up to 1 point are therefore allowed and a percentage inside a gap gets the
 * lower tier.
 */
public final class GradingTiers {

    static final BigDecimal MAX_GAP = BigDecimal.ONE;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private GradingTiers() {
    }

    /**
     * Rejects tiers that cannot grade every percentage exactly once: min above max, duplicate grade
     * labels, overlapping bands, gaps over 1 point, or not covering 0–100.
     */
    public static void validate(List<GradingScaleTierDto> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            throw new BusinessException("At least one grade tier is required");
        }
        List<String> errors = new ArrayList<>();
        Set<String> labels = new HashSet<>();
        for (GradingScaleTierDto t : tiers) {
            if (t.getMinPercentage() == null || t.getMaxPercentage() == null) {
                errors.add(t.getGrade() + ": min and max percentage are required");
                continue;
            }
            if (t.getMinPercentage().compareTo(t.getMaxPercentage()) > 0) {
                errors.add(t.getGrade() + ": min " + plain(t.getMinPercentage()) + " is above max " + plain(t.getMaxPercentage()));
            }
            if (t.getGrade() != null && !labels.add(t.getGrade().trim().toUpperCase(Locale.ROOT))) {
                errors.add("Grade " + t.getGrade() + " appears twice");
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException("Invalid grading tiers: " + String.join("; ", errors));
        }
        List<GradingScaleTierDto> sorted = tiers.stream().sorted(Comparator.comparing(GradingScaleTierDto::getMinPercentage)).toList();
        if (sorted.get(0).getMinPercentage().signum() != 0) {
            errors.add("The lowest tier must start at 0 (it starts at " + plain(sorted.get(0).getMinPercentage()) + ")");
        }
        if (sorted.get(sorted.size() - 1).getMaxPercentage().compareTo(HUNDRED) != 0) {
            errors.add("The highest tier must end at 100");
        }
        for (int i = 1; i < sorted.size(); i++) {
            GradingScaleTierDto lower = sorted.get(i - 1);
            GradingScaleTierDto upper = sorted.get(i);
            int cmp = upper.getMinPercentage().compareTo(lower.getMaxPercentage());
            if (cmp <= 0) {
                errors.add(lower.getGrade() + " (" + plain(lower.getMinPercentage()) + "–" + plain(lower.getMaxPercentage()) + ") overlaps "
                        + upper.getGrade() + " (" + plain(upper.getMinPercentage()) + "–" + plain(upper.getMaxPercentage()) + ")");
            } else if (upper.getMinPercentage().subtract(lower.getMaxPercentage()).compareTo(MAX_GAP) > 0) {
                errors.add("Gap between " + lower.getGrade() + " (ends " + plain(lower.getMaxPercentage()) + ") and "
                        + upper.getGrade() + " (starts " + plain(upper.getMinPercentage()) + ")");
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException("Invalid grading tiers: " + String.join("; ", errors));
        }
    }

    /** Tier for a percentage: the band containing it, else the band below a gap (90.5 → 81–90). */
    public static Optional<GradingScaleTier> match(List<GradingScaleTier> tiers, BigDecimal percentage) {
        if (tiers == null || percentage == null) {
            return Optional.empty();
        }
        GradingScaleTier best = null;
        for (GradingScaleTier t : tiers) {
            if (percentage.compareTo(t.getMinPercentage()) >= 0 && percentage.compareTo(t.getMaxPercentage()) <= 0) {
                return Optional.of(t);
            }
            if (percentage.compareTo(t.getMinPercentage()) >= 0
                    && percentage.subtract(t.getMaxPercentage()).compareTo(MAX_GAP) < 0
                    && (best == null || t.getMinPercentage().compareTo(best.getMinPercentage()) > 0)) {
                best = t;
            }
        }
        return Optional.ofNullable(best);
    }

    private static String plain(BigDecimal d) {
        return d.stripTrailingZeros().toPlainString();
    }
}
