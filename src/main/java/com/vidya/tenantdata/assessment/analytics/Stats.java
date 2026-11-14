package com.vidya.tenantdata.assessment.analytics;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/** Descriptive statistics on percentages, rounded to two decimals. */
public final class Stats {

    /** Change per term (in percentage points) beyond which a trend counts as improving / declining. */
    static final BigDecimal TREND_THRESHOLD = BigDecimal.valueOf(2);

    private Stats() {
    }

    public enum Trend { IMPROVING, STEADY, DECLINING, NOT_ENOUGH_DATA }

    public static BigDecimal mean(List<BigDecimal> v) {
        if (v.isEmpty()) {
            return null;
        }
        return v.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(v.size()), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal median(List<BigDecimal> v) {
        if (v.isEmpty()) {
            return null;
        }
        List<BigDecimal> s = v.stream().sorted().toList();
        int n = s.size();
        BigDecimal m = n % 2 == 1 ? s.get(n / 2) : s.get(n / 2 - 1).add(s.get(n / 2)).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        return m.setScale(2, RoundingMode.HALF_UP);
    }

    /** Population standard deviation. */
    public static BigDecimal stdDev(List<BigDecimal> v) {
        if (v.size() < 2) {
            return v.isEmpty() ? null : BigDecimal.ZERO.setScale(2);
        }
        BigDecimal mean = v.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(v.size()), 8, RoundingMode.HALF_UP);
        BigDecimal var = v.stream().map(x -> x.subtract(mean).pow(2)).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(v.size()), 8, RoundingMode.HALF_UP);
        return var.sqrt(MathContext.DECIMAL64).setScale(2, RoundingMode.HALF_UP);
    }

    /** Least-squares slope of the values over their position (0, 1, 2 ...): average change per step. */
    public static BigDecimal slope(List<BigDecimal> v) {
        int n = v.size();
        if (n < 2) {
            return null;
        }
        double meanX = (n - 1) / 2.0;
        double meanY = v.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        double num = 0;
        double den = 0;
        for (int i = 0; i < n; i++) {
            num += (i - meanX) * (v.get(i).doubleValue() - meanY);
            den += (i - meanX) * (i - meanX);
        }
        return BigDecimal.valueOf(num / den).setScale(2, RoundingMode.HALF_UP);
    }

    public static Trend trend(List<BigDecimal> v) {
        BigDecimal s = slope(v);
        if (s == null) {
            return Trend.NOT_ENOUGH_DATA;
        }
        if (s.compareTo(TREND_THRESHOLD) >= 0) {
            return Trend.IMPROVING;
        }
        return s.compareTo(TREND_THRESHOLD.negate()) <= 0 ? Trend.DECLINING : Trend.STEADY;
    }

    /** Level for an attainment percentage, splitting 0–100 evenly across the levels (lowest first). */
    public static String level(BigDecimal percent, List<String> levels) {
        if (percent == null || levels.isEmpty()) {
            return null;
        }
        int n = levels.size();
        int idx = percent.multiply(BigDecimal.valueOf(n)).divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR).intValue();
        return levels.get(Math.max(0, Math.min(n - 1, idx)));
    }
}
