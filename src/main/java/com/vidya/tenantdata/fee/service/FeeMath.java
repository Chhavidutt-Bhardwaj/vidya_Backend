package com.vidya.tenantdata.fee.service;

import com.vidya.tenantdata.fee.entity.FeeConcessionRequest;
import com.vidya.tenantdata.fee.entity.FeeLedgerLine;
import com.vidya.tenantdata.fee.enums.LineSource;
import com.vidya.tenantdata.fee.enums.LineStatus;
import com.vidya.tenantdata.operations.entity.FeeInstallment;
import com.vidya.tenantdata.operations.entity.FeeSettings;
import com.vidya.tenantdata.operations.enums.ConcessionKind;
import com.vidya.tenantdata.operations.enums.LateFineType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The arithmetic of fee collection, free of the database. Money is paise. */
public final class FeeMath {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private FeeMath() {
    }

    public static long toPaise(BigDecimal rupees) {
        return rupees == null ? 0 : rupees.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /**
     * A year's amount split over the instalment plan by percent, the last instalment taking the rounding. Returns one amount per
     * instalment, in plan order.
     */
    public static long[] split(long annualPaise, List<FeeInstallment> plan) {
        long[] out = new long[plan.size()];
        long given = 0;
        for (int k = 0; k < plan.size(); k++) {
            if (k == plan.size() - 1) {
                out[k] = annualPaise - given;
            } else {
                out[k] = BigDecimal.valueOf(annualPaise).multiply(plan.get(k).getPercent()).divide(HUNDRED, 0, RoundingMode.HALF_UP).longValue();
                given += out[k];
            }
        }
        return out;
    }

    /**
     * The school's late fine on what is left of a line today (for display: it is not owed until added as a charge). Counted from the
     * due date plus the grace days; PER_DAY counts each late day, PERCENT is a share of the overdue amount; capped by {@code lateFineCap}.
     */
    public static long lateFine(long duePaise, LocalDate dueDate, LocalDate today, FeeSettings s) {
        if (s == null || duePaise <= 0 || s.getLateFineType() == null || s.getLateFineType() == LateFineType.NONE) {
            return 0;
        }
        long lateDays = ChronoUnit.DAYS.between(dueDate.plusDays(s.getGraceDays()), today);
        if (lateDays <= 0) {
            return 0;
        }
        BigDecimal value = s.getLateFineValue() == null ? BigDecimal.ZERO : s.getLateFineValue();
        long fine = switch (s.getLateFineType()) {
            case FIXED -> toPaise(value);
            case PER_DAY -> toPaise(value) * lateDays;
            case PERCENT -> BigDecimal.valueOf(duePaise).multiply(value).divide(HUNDRED, 0, RoundingMode.HALF_UP).longValue();
            case NONE -> 0;
        };
        if (s.getLateFineCap() != null && s.getLateFineCap().signum() > 0) {
            fine = Math.min(fine, toPaise(s.getLateFineCap()));
        }
        return Math.max(fine, 0);
    }

    /**
     * What an approved concession takes off each line, on top of what it has already given ({@code given}: line → paise of this
     * request). Only active structure lines of the request's head (or every head) with something left to pay are touched, and never
     * more than is left. PERCENT gives each line its share of the line amount; FIXED spends what is left of the request on the
     * latest dues first.
     */
    public static Map<FeeLedgerLine, Long> concessionShares(FeeConcessionRequest r, List<FeeLedgerLine> lines, Map<FeeLedgerLine, Long> given) {
        Map<FeeLedgerLine, Long> out = new IdentityHashMap<>();
        List<FeeLedgerLine> eligible = new ArrayList<>(lines.stream()
                .filter(l -> l.getStatus() == LineStatus.ACTIVE && l.getSource() == LineSource.STRUCTURE)
                .filter(l -> r.getFeeHeadId() == null || r.getFeeHeadId().equals(l.getFeeHeadId()))
                .filter(l -> l.duePaise() > 0)
                .toList());
        if (r.getKind() == ConcessionKind.PERCENT) {
            for (FeeLedgerLine l : eligible) {
                long want = BigDecimal.valueOf(l.getAmountPaise()).multiply(r.getPercent()).divide(HUNDRED, 0, RoundingMode.HALF_UP).longValue()
                        - given.getOrDefault(l, 0L);
                long take = Math.min(want, l.duePaise());
                if (take > 0) {
                    out.put(l, take);
                }
            }
            return out;
        }
        long left = r.getAmountPaise() - r.getAppliedPaise();
        eligible.sort(Comparator.comparing(FeeLedgerLine::getDueDate).reversed());
        for (FeeLedgerLine l : eligible) {
            if (left <= 0) {
                break;
            }
            long take = Math.min(left, l.duePaise());
            out.put(l, take);
            left -= take;
        }
        return out;
    }

    /** ₹1,23,456.50 (Indian digit grouping). */
    public static String rupees(long paise) {
        boolean negative = paise < 0;
        long abs = Math.abs(paise);
        String whole = Long.toString(abs / 100);
        StringBuilder grouped = new StringBuilder();
        if (whole.length() > 3) {
            String head = whole.substring(0, whole.length() - 3);
            for (int i = 0; i < head.length(); i++) {
                if (i > 0 && (head.length() - i) % 2 == 0) {
                    grouped.append(',');
                }
                grouped.append(head.charAt(i));
            }
            grouped.append(',').append(whole.substring(whole.length() - 3));
        } else {
            grouped.append(whole);
        }
        return (negative ? "-" : "") + "₹" + grouped + "." + String.format("%02d", abs % 100);
    }

    public static String date(LocalDate d) {
        return d == null ? "" : DATE.format(d);
    }

    private static final String[] ONES = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve",
            "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

    /** "Rupees Twelve Thousand Five Hundred and Fifty Paise Only" (Indian system: thousand, lakh, crore). */
    public static String inWords(long paise) {
        long rupees = Math.abs(paise) / 100;
        long p = Math.abs(paise) % 100;
        String r = rupees == 0 ? "Zero" : words(rupees);
        return "Rupees " + r + (p > 0 ? " and " + below100((int) p) + " Paise" : "") + " Only";
    }

    private static String words(long n) {
        StringBuilder sb = new StringBuilder();
        long crore = n / 10_000_000;
        n %= 10_000_000;
        if (crore > 0) {
            sb.append(words(crore)).append(" Crore ");
        }
        long lakh = n / 100_000;
        n %= 100_000;
        if (lakh > 0) {
            sb.append(below100((int) lakh)).append(" Lakh ");
        }
        long thousand = n / 1000;
        n %= 1000;
        if (thousand > 0) {
            sb.append(below100((int) thousand)).append(" Thousand ");
        }
        long hundred = n / 100;
        n %= 100;
        if (hundred > 0) {
            sb.append(ONES[(int) hundred]).append(" Hundred ");
        }
        if (n > 0) {
            sb.append(below100((int) n));
        }
        return sb.toString().trim();
    }

    private static String below100(int n) {
        if (n < 20) {
            return ONES[n];
        }
        return TENS[n / 10] + (n % 10 > 0 ? " " + ONES[n % 10] : "");
    }

    /** INV/26/00042: the year part is the academic year's start year. */
    public static String number(String prefix, LocalDate yearStart, long seq) {
        return prefix + "/" + String.format("%02d", yearStart.getYear() % 100) + "/" + String.format("%05d", seq);
    }
}
