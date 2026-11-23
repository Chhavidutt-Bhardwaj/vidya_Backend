package com.vidya.tenantdata.fee;

import com.vidya.common.exception.BusinessException;
import com.vidya.tenantdata.fee.dto.FeeDtos.Allocation;
import com.vidya.tenantdata.fee.entity.FeeConcessionRequest;
import com.vidya.tenantdata.fee.entity.FeeLedgerLine;
import com.vidya.tenantdata.fee.enums.DueStatus;
import com.vidya.tenantdata.fee.enums.LineSource;
import com.vidya.tenantdata.fee.enums.LineStatus;
import com.vidya.tenantdata.fee.service.FeeMath;
import com.vidya.tenantdata.fee.service.FeeTestAccess;
import com.vidya.tenantdata.operations.entity.FeeInstallment;
import com.vidya.tenantdata.operations.entity.FeeSettings;
import com.vidya.tenantdata.operations.enums.ConcessionKind;
import com.vidya.tenantdata.operations.enums.LateFineType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Fee arithmetic")
class FeeMathTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 20);
    private static final UUID TUITION = UUID.randomUUID();
    private static final UUID BUS = UUID.randomUUID();

    @Nested
    @DisplayName("splitting a year's amount over instalments")
    class Split {

        @Test
        @DisplayName("by percent, the last instalment taking the rounding")
        void lastTakesRounding() {
            long[] parts = FeeMath.split(100_001, List.of(inst("33.33"), inst("33.33"), inst("33.34")));
            assertThat(parts).containsExactly(33_330, 33_330, 33_341);
            assertThat(parts[0] + parts[1] + parts[2]).isEqualTo(100_001);
        }

        @Test
        @DisplayName("rupees become paise exactly")
        void toPaise() {
            assertThat(FeeMath.toPaise(new BigDecimal("1234.56"))).isEqualTo(123_456);
            assertThat(FeeMath.toPaise(null)).isZero();
        }
    }

    @Nested
    @DisplayName("late fine (shown, not charged)")
    class LateFine {

        @Test
        @DisplayName("nothing within the grace days or when the rule is NONE")
        void grace() {
            FeeSettings s = settings(LateFineType.FIXED, "100", null, 5);
            assertThat(FeeMath.lateFine(50_000, TODAY.minusDays(5), TODAY, s)).isZero();
            assertThat(FeeMath.lateFine(50_000, TODAY.minusDays(6), TODAY, s)).isEqualTo(10_000);
            assertThat(FeeMath.lateFine(50_000, TODAY.minusDays(60), TODAY, settings(LateFineType.NONE, "100", null, 0))).isZero();
        }

        @Test
        @DisplayName("per day late, capped")
        void perDayCapped() {
            FeeSettings s = settings(LateFineType.PER_DAY, "10", "250", 0);
            assertThat(FeeMath.lateFine(50_000, TODAY.minusDays(7), TODAY, s)).isEqualTo(7_000);
            assertThat(FeeMath.lateFine(50_000, TODAY.minusDays(40), TODAY, s)).isEqualTo(25_000);
        }

        @Test
        @DisplayName("percent of what is overdue; nothing once paid")
        void percent() {
            FeeSettings s = settings(LateFineType.PERCENT, "2", null, 0);
            assertThat(FeeMath.lateFine(150_000, TODAY.minusDays(1), TODAY, s)).isEqualTo(3_000);
            assertThat(FeeMath.lateFine(0, TODAY.minusDays(1), TODAY, s)).isZero();
        }
    }

    @Nested
    @DisplayName("concession shares")
    class Concessions {

        @Test
        @DisplayName("PERCENT takes its share of each matching structure line, never more than is left")
        void percent() {
            FeeLedgerLine paidMost = line(TUITION, LineSource.STRUCTURE, 100_000, TODAY.minusDays(30));
            paidMost.setPaidPaise(95_000);
            FeeLedgerLine open = line(TUITION, LineSource.STRUCTURE, 100_000, TODAY.plusDays(30));
            FeeLedgerLine bus = line(BUS, LineSource.STRUCTURE, 40_000, TODAY.plusDays(30));
            FeeLedgerLine charge = line(TUITION, LineSource.CHARGE, 10_000, TODAY);

            Map<FeeLedgerLine, Long> shares = FeeMath.concessionShares(percentRequest("10", TUITION), List.of(paidMost, open, bus, charge), new IdentityHashMap<>());

            assertThat(shares).containsOnlyKeys(paidMost, open);
            assertThat(shares.get(paidMost)).isEqualTo(5_000);
            assertThat(shares.get(open)).isEqualTo(10_000);
        }

        @Test
        @DisplayName("PERCENT does not give a line twice")
        void percentIdempotent() {
            FeeLedgerLine l = line(TUITION, LineSource.STRUCTURE, 100_000, TODAY);
            Map<FeeLedgerLine, Long> given = new IdentityHashMap<>();
            given.put(l, 10_000L);
            l.setConcessionPaise(10_000);
            assertThat(FeeMath.concessionShares(percentRequest("10", null), List.of(l), given)).isEmpty();
        }

        @Test
        @DisplayName("FIXED spends what is left of it on the latest dues first")
        void fixedLatestFirst() {
            FeeLedgerLine q1 = line(TUITION, LineSource.STRUCTURE, 30_000, TODAY.minusDays(60));
            FeeLedgerLine q2 = line(TUITION, LineSource.STRUCTURE, 30_000, TODAY.plusDays(30));
            FeeLedgerLine q3 = line(TUITION, LineSource.STRUCTURE, 30_000, TODAY.plusDays(120));
            FeeConcessionRequest r = new FeeConcessionRequest();
            r.setKind(ConcessionKind.FIXED);
            r.setAmountPaise(50_000L);
            r.setAppliedPaise(5_000);

            Map<FeeLedgerLine, Long> shares = FeeMath.concessionShares(r, List.of(q1, q2, q3), new IdentityHashMap<>());

            assertThat(shares).containsOnlyKeys(q3, q2);
            assertThat(shares.get(q3)).isEqualTo(30_000);
            assertThat(shares.get(q2)).isEqualTo(15_000);
        }
    }

    @Nested
    @DisplayName("line status")
    class Status {

        @Test
        @DisplayName("PAID, OVERDUE, PARTIAL, UPCOMING; a cheque waiting to clear is not due, a cancelled line owes nothing")
        void statuses() {
            FeeLedgerLine l = line(TUITION, LineSource.STRUCTURE, 10_000, TODAY.minusDays(1));
            assertThat(l.dueStatus(TODAY)).isEqualTo(DueStatus.OVERDUE);
            l.setDueDate(TODAY);
            assertThat(l.dueStatus(TODAY)).isEqualTo(DueStatus.UPCOMING);
            l.setPaidPaise(4_000);
            assertThat(l.dueStatus(TODAY)).isEqualTo(DueStatus.PARTIAL);
            l.setPendingPaise(6_000);
            assertThat(l.duePaise()).isZero();
            assertThat(l.dueStatus(TODAY)).isEqualTo(DueStatus.PAID);
            l.setPendingPaise(0);
            l.setStatus(LineStatus.CANCELLED);
            assertThat(l.duePaise()).isZero();
        }
    }

    @Nested
    @DisplayName("allocating a payment")
    class Allocate {

        private final FeeLedgerLine old = withId(line(TUITION, LineSource.STRUCTURE, 30_000, TODAY.minusDays(40)));
        private final FeeLedgerLine next = withId(line(TUITION, LineSource.STRUCTURE, 30_000, TODAY.plusDays(50)));

        @Test
        @DisplayName("oldest due first, the rest on the next line")
        void oldestFirst() {
            Map<FeeLedgerLine, Long> plan = FeeTestAccess.allocate(List.of(old, next), 40_000, null, true);
            assertThat(plan).containsEntry(old, 30_000L).containsEntry(next, 10_000L);
        }

        @Test
        @DisplayName("more than is due is refused")
        void overpayment() {
            assertThatThrownBy(() -> FeeTestAccess.allocate(List.of(old, next), 60_001, null, true))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("more than what is due");
        }

        @Test
        @DisplayName("without part payments a line must be paid off whole")
        void noPartial() {
            assertThatThrownBy(() -> FeeTestAccess.allocate(List.of(old, next), 40_000, null, false)).isInstanceOf(BusinessException.class);
            assertThat(FeeTestAccess.allocate(List.of(old, next), 30_000, null, false)).containsOnlyKeys(old);
        }

        @Test
        @DisplayName("an explicit allocation must add up, stay within each due and name lines of this ledger")
        void explicit() {
            assertThat(FeeTestAccess.allocate(List.of(old, next), 25_000, List.of(new Allocation(next.getId(), 25_000L)), true))
                    .containsOnlyKeys(next);
            assertThatThrownBy(() -> FeeTestAccess.allocate(List.of(old, next), 25_000, List.of(new Allocation(next.getId(), 20_000L)), true))
                    .hasMessageContaining("adds up to");
            assertThatThrownBy(() -> FeeTestAccess.allocate(List.of(old, next), 35_000, List.of(new Allocation(next.getId(), 35_000L)), true))
                    .hasMessageContaining("more than its due");
            assertThatThrownBy(() -> FeeTestAccess.allocate(List.of(old, next), 5_000, List.of(new Allocation(UUID.randomUUID(), 5_000L)), true))
                    .hasMessageContaining("not an open line");
        }
    }

    @Nested
    @DisplayName("formatting")
    class Formatting {

        @Test
        @DisplayName("Indian digit grouping")
        void rupees() {
            assertThat(FeeMath.rupees(12_345_678_950L)).isEqualTo("₹12,34,56,789.50");
            assertThat(FeeMath.rupees(99_900)).isEqualTo("₹999.00");
            assertThat(FeeMath.rupees(100_000)).isEqualTo("₹1,000.00");
        }

        @Test
        @DisplayName("amount in words with lakh and crore")
        void words() {
            assertThat(FeeMath.inWords(1_250_050)).isEqualTo("Rupees Twelve Thousand Five Hundred and Fifty Paise Only");
            assertThat(FeeMath.inWords(2_15_00_000_00L)).isEqualTo("Rupees Two Crore Fifteen Lakh Only");
            assertThat(FeeMath.inWords(0)).isEqualTo("Rupees Zero Only");
        }

        @Test
        @DisplayName("document numbers carry the year's start")
        void number() {
            assertThat(FeeMath.number("INV", LocalDate.of(2026, 4, 1), 42)).isEqualTo("INV/26/00042");
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────

    private static FeeInstallment inst(String percent) {
        FeeInstallment i = new FeeInstallment();
        ReflectionTestUtils.setField(i, "percent", new BigDecimal(percent));
        return i;
    }

    private static FeeSettings settings(LateFineType type, String value, String cap, int grace) {
        FeeSettings s = new FeeSettings(UUID.randomUUID());
        ReflectionTestUtils.setField(s, "lateFineType", type);
        ReflectionTestUtils.setField(s, "lateFineValue", new BigDecimal(value));
        ReflectionTestUtils.setField(s, "lateFineCap", cap == null ? null : new BigDecimal(cap));
        ReflectionTestUtils.setField(s, "graceDays", grace);
        return s;
    }

    private static FeeLedgerLine line(UUID head, LineSource source, long amount, LocalDate due) {
        FeeLedgerLine l = new FeeLedgerLine();
        l.setFeeHeadId(head);
        l.setSource(source);
        l.setAmountPaise(amount);
        l.setDueDate(due);
        l.setLabel("line");
        return l;
    }

    private static FeeLedgerLine withId(FeeLedgerLine l) {
        l.setId(UUID.randomUUID());
        return l;
    }

    private static FeeConcessionRequest percentRequest(String pct, UUID head) {
        FeeConcessionRequest r = new FeeConcessionRequest();
        r.setKind(ConcessionKind.PERCENT);
        r.setPercent(new BigDecimal(pct));
        r.setFeeHeadId(head);
        return r;
    }
}
