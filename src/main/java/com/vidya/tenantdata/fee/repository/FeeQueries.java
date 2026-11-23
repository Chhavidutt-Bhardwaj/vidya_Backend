package com.vidya.tenantdata.fee.repository;

import com.vidya.tenantdata.fee.dto.FeeDtos.BucketCount;
import com.vidya.tenantdata.fee.dto.FeeDtos.ConcessionView;
import com.vidya.tenantdata.fee.dto.FeeDtos.DayBookRow;
import com.vidya.tenantdata.fee.dto.FeeDtos.LedgerRow;
import com.vidya.tenantdata.fee.dto.FeeDtos.ModeTotal;
import com.vidya.tenantdata.fee.enums.AgeingBucket;
import com.vidya.tenantdata.fee.enums.ConcessionRequestStatus;
import com.vidya.tenantdata.fee.enums.DueStatus;
import com.vidya.tenantdata.fee.enums.PaymentMode;
import com.vidya.tenantdata.fee.enums.PaymentStatus;
import com.vidya.tenantdata.operations.enums.ConcessionKind;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Native SQL of fee collection: list views, totals, number sequences and concession bookkeeping. Callers own the transaction. */
@Repository
public class FeeQueries {

    private static final String DUE = "(ln.amount_paise - ln.concession_paise - ln.paid_paise - ln.pending_paise)";
    private static final String STUDENT_NAME = "COALESCE(NULLIF(trim(p.display_name), ''), NULLIF(trim(concat_ws(' ', p.first_name, p.last_name)), ''), "
            + "s.admission_number)";

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    // ── Ledger rows ──────────────────────────────────────────────────────────────────

    /**
     * Which ledgers and which of their lines to total. {@code feeHeadId} / {@code installmentId} restrict the lines (a ledger without
     * such lines is left out). {@code overdueOnly} keeps ledgers with something past due.
     */
    public record LedgerFilter(UUID academicYearId, Collection<UUID> ledgerIds, UUID studentId, UUID classId, UUID sectionId, String search,
                               Set<DueStatus> statuses, UUID feeHeadId, UUID installmentId, boolean overdueOnly, AgeingBucket bucket,
                               Long minDuePaise, LocalDate today) {

        public static LedgerFilter ofLedger(UUID ledgerId, LocalDate today) {
            return new LedgerFilter(null, List.of(ledgerId), null, null, null, null, null, null, null, false, null, null, today);
        }
    }

    /** Allowed sort keys → SQL. */
    private static final Map<String, String> SORT = Map.of(
            "duePaise", "x.due", "overduePaise", "x.overdue", "paidPaise", "x.paid", "netPayablePaise", "x.net",
            "studentName", "lower(x.student_name)", "invoiceNumber", "x.invoice_number", "ageingDays", "x.ageing",
            "className", "x.class_sort", "admissionNumber", "x.admission_number");

    public static boolean sortable(String key) {
        return SORT.containsKey(key);
    }

    public List<LedgerRow> ledgerRows(LedgerFilter f, List<String[]> sort, int page, int size) {
        Sql sql = ledgerSql(f);
        StringBuilder order = new StringBuilder();
        for (String[] s : sort) {
            order.append(order.isEmpty() ? " ORDER BY " : ", ").append(SORT.get(s[0])).append("desc".equalsIgnoreCase(s[1]) ? " DESC NULLS LAST" : " ASC NULLS LAST");
        }
        order.append(order.isEmpty() ? " ORDER BY " : ", ").append("x.invoice_number");
        Query q = sql.query(em, "SELECT x.* FROM (" + sql.text + ") x" + order + " LIMIT " + size + " OFFSET " + ((long) page * size));
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        List<LedgerRow> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            out.add(row(r));
        }
        return out;
    }

    public long countLedgers(LedgerFilter f) {
        Sql sql = ledgerSql(f);
        return num(sql.query(em, "SELECT count(*) FROM (" + sql.text + ") x").getSingleResult());
    }

    public Optional<LedgerRow> ledgerRow(UUID ledgerId, LocalDate today) {
        List<LedgerRow> rows = ledgerRows(LedgerFilter.ofLedger(ledgerId, today), List.of(), 0, 1);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Overdue ledgers per ageing bucket, for the same filter (bucket itself ignored). */
    public List<BucketCount> buckets(LedgerFilter f) {
        LedgerFilter all = new LedgerFilter(f.academicYearId(), f.ledgerIds(), f.studentId(), f.classId(), f.sectionId(), f.search(), null,
                f.feeHeadId(), f.installmentId(), true, null, f.minDuePaise(), f.today());
        Sql sql = ledgerSql(all);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = sql.query(em, "SELECT x.bucket, count(*), sum(x.overdue) FROM (" + sql.text + ") x GROUP BY x.bucket").getResultList();
        Map<String, Object[]> byBucket = new HashMap<>();
        rows.forEach(r -> byBucket.put((String) r[0], r));
        List<BucketCount> out = new ArrayList<>();
        for (AgeingBucket b : AgeingBucket.values()) {
            Object[] r = byBucket.get(b.name());
            out.add(new BucketCount(b, r == null ? 0 : num(r[1]), r == null ? 0 : num(r[2])));
        }
        return out;
    }

    private Sql ledgerSql(LedgerFilter f) {
        Sql sql = new Sql();
        sql.param("today", f.today());
        StringBuilder lineFilter = new StringBuilder();
        if (f.feeHeadId() != null) {
            lineFilter.append(" AND ln.fee_head_id = :head");
            sql.param("head", f.feeHeadId());
        }
        if (f.installmentId() != null) {
            lineFilter.append(" AND ln.installment_id = :inst");
            sql.param("inst", f.installmentId());
        }
        boolean restrictLines = !lineFilter.isEmpty();
        String status = "CASE WHEN COALESCE(a.due, 0) <= 0 AND COALESCE(a.pend, 0) = 0 THEN 'PAID' WHEN COALESCE(a.overdue, 0) > 0 THEN 'OVERDUE' "
                + "WHEN COALESCE(a.paid, 0) + COALESCE(a.pend, 0) > 0 THEN 'PARTIAL' ELSE 'UPCOMING' END";
        String ageing = "(CAST(:today AS date) - a.oldest_overdue)";
        StringBuilder t = new StringBuilder("""
                SELECT l.id, l.invoice_number, s.id AS student_id, s.admission_number, en.roll_number, %s AS student_name,
                       c.id AS class_id, COALESCE(c.display_name, c.name) AS class_name, sec.id AS section_id, sec.name AS section_name,
                       g.guardian_name, g.phone,
                       COALESCE(a.total, 0) AS total, COALESCE(a.conc, 0) AS conc, COALESCE(a.paid, 0) AS paid, COALESCE(a.pend, 0) AS pend,
                       COALESCE(a.due, 0) AS due, COALESCE(a.overdue, 0) AS overdue, a.next_due, a.oldest_overdue,
                       %s AS status, %s AS ageing,
                       CASE WHEN a.oldest_overdue IS NULL THEN NULL WHEN %s <= 30 THEN 'D0_30' WHEN %s <= 60 THEN 'D31_60'
                            WHEN %s <= 90 THEN 'D61_90' ELSE 'D90_PLUS' END AS bucket,
                       rm.last_at, COALESCE(rm.n, 0) AS reminders, c.sort_order AS class_sort,
                       COALESCE(a.total, 0) - COALESCE(a.conc, 0) AS net, l.academic_year_id AS year_id
                FROM fee_ledger l
                """.formatted(STUDENT_NAME, status, ageing, ageing, ageing, ageing));
        t.append(restrictLines ? "JOIN" : "LEFT JOIN").append("""
                 (SELECT ln.ledger_id, sum(ln.amount_paise) AS total, sum(ln.concession_paise) AS conc, sum(ln.paid_paise) AS paid,
                         sum(ln.pending_paise) AS pend, sum(%1$s) AS due,
                         sum(CASE WHEN ln.due_date < :today THEN %1$s ELSE 0 END) AS overdue,
                         min(ln.due_date) FILTER (WHERE %1$s > 0 AND ln.due_date < :today) AS oldest_overdue,
                         min(ln.due_date) FILTER (WHERE %1$s > 0 AND ln.due_date >= :today) AS next_due
                  FROM fee_ledger_line ln WHERE ln.status = 'ACTIVE'%2$s GROUP BY ln.ledger_id) a ON a.ledger_id = l.id
                JOIN student s ON s.id = l.student_id
                LEFT JOIN tenant_user_profile p ON p.user_id = s.tenant_user_id
                JOIN academic_class c ON c.id = l.academic_class_id
                LEFT JOIN section sec ON sec.id = l.section_id
                LEFT JOIN LATERAL (SELECT g.guardian_name, g.phone FROM student_guardian g WHERE g.student_id = s.id AND g.deleted_at IS NULL
                                   AND g.status = 'ACTIVE' ORDER BY g.is_primary DESC, g.created_at LIMIT 1) g ON true
                LEFT JOIN LATERAL (SELECT e.roll_number FROM student_academic_enrollment e WHERE e.student_id = s.id
                                   AND e.academic_year_id = l.academic_year_id AND e.deleted_at IS NULL ORDER BY e.enrollment_date DESC LIMIT 1) en ON true
                LEFT JOIN LATERAL (SELECT max(r.sent_at) AS last_at, count(*) AS n FROM fee_reminder r WHERE r.ledger_id = l.id) rm ON true
                WHERE 1 = 1
                """.formatted(DUE, lineFilter));
        if (f.academicYearId() != null) {
            t.append(" AND l.academic_year_id = :y");
            sql.param("y", f.academicYearId());
        }
        if (f.ledgerIds() != null) {
            t.append(" AND l.id IN (:ids)");
            sql.param("ids", f.ledgerIds().isEmpty() ? List.of(new UUID(0, 0)) : f.ledgerIds());
        }
        if (f.studentId() != null) {
            t.append(" AND l.student_id = :st");
            sql.param("st", f.studentId());
        }
        if (f.classId() != null) {
            t.append(" AND l.academic_class_id = :c");
            sql.param("c", f.classId());
        }
        if (f.sectionId() != null) {
            t.append(" AND l.section_id = :sec");
            sql.param("sec", f.sectionId());
        }
        if (f.search() != null && !f.search().isBlank()) {
            t.append(" AND (lower(").append(STUDENT_NAME).append(") LIKE :q OR lower(s.admission_number) LIKE :q OR lower(l.invoice_number) LIKE :q"
                    + " OR lower(COALESCE(en.roll_number, '')) = :qExact"
                    + " OR EXISTS (SELECT 1 FROM student_guardian g2 WHERE g2.student_id = s.id AND g2.deleted_at IS NULL"
                    + " AND (lower(g2.guardian_name) LIKE :q OR g2.phone LIKE :q)))");
            String q = f.search().trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            sql.param("q", "%" + q + "%");
            sql.param("qExact", f.search().trim().toLowerCase(Locale.ROOT));
        }
        if (f.overdueOnly()) {
            t.append(" AND COALESCE(a.overdue, 0) > 0");
        }
        if (f.minDuePaise() != null) {
            t.append(" AND COALESCE(a.").append(f.overdueOnly() ? "overdue" : "due").append(", 0) >= :minDue");
            sql.param("minDue", f.minDuePaise());
        }
        String where = "";
        if (f.statuses() != null && !f.statuses().isEmpty()) {
            where += " AND " + status + " IN (:statuses)";
            sql.param("statuses", f.statuses().stream().map(Enum::name).toList());
        }
        if (f.bucket() != null) {
            where += " AND " + "CASE WHEN a.oldest_overdue IS NULL THEN NULL WHEN %1$s <= 30 THEN 'D0_30' WHEN %1$s <= 60 THEN 'D31_60' WHEN %1$s <= 90 THEN 'D61_90' ELSE 'D90_PLUS' END = :bucket"
                    .formatted(ageing);
            sql.param("bucket", f.bucket().name());
        }
        t.append(where);
        sql.text = t.toString();
        return sql;
    }

    private static LedgerRow row(Object[] r) {
        long total = num(r[12]);
        long conc = num(r[13]);
        return new LedgerRow(uuid(r[0]), (String) r[1], uuid(r[2]), (String) r[3], (String) r[4], (String) r[5], uuid(r[6]), (String) r[7],
                uuid(r[8]), (String) r[9], (String) r[10], (String) r[11], total, conc, total - conc, num(r[14]), num(r[15]), num(r[16]),
                num(r[17]), date(r[18]), date(r[19]), DueStatus.valueOf((String) r[20]), r[21] == null ? null : ((Number) r[21]).intValue(),
                r[22] == null ? null : AgeingBucket.valueOf((String) r[22]), instant(r[23]), (int) num(r[24]), uuid(r[27]));
    }

    // ── Summary ──────────────────────────────────────────────────────────────────────

    public record Totals(long ledgers, long total, long concession, long paid, long pending, long due, long overdue, long overdueLedgers,
                         long netDueByTo, long paidDueByTo) {
    }

    public Totals totals(UUID y, UUID classId, UUID sectionId, LocalDate today, LocalDate to) {
        Sql sql = new Sql();
        sql.param("y", y).param("today", today).param("to", to);
        String scope = scope(sql, classId, sectionId);
        Object[] r = (Object[]) sql.query(em, """
                SELECT count(DISTINCT l.id), COALESCE(sum(ln.amount_paise), 0), COALESCE(sum(ln.concession_paise), 0), COALESCE(sum(ln.paid_paise), 0),
                       COALESCE(sum(ln.pending_paise), 0), COALESCE(sum(%1$s), 0),
                       COALESCE(sum(%1$s) FILTER (WHERE ln.due_date < :today), 0),
                       count(DISTINCT l.id) FILTER (WHERE ln.due_date < :today AND %1$s > 0),
                       COALESCE(sum(ln.amount_paise - ln.concession_paise) FILTER (WHERE ln.due_date <= :to), 0),
                       COALESCE(sum(ln.paid_paise) FILTER (WHERE ln.due_date <= :to), 0)
                FROM fee_ledger l JOIN fee_ledger_line ln ON ln.ledger_id = l.id AND ln.status = 'ACTIVE'
                WHERE l.academic_year_id = :y""".formatted(DUE) + scope).getSingleResult();
        return new Totals(num(r[0]), num(r[1]), num(r[2]), num(r[3]), num(r[4]), num(r[5]), num(r[6]), num(r[7]), num(r[8]), num(r[9]));
    }

    /** Money realized (SUCCESS) between the dates, per mode. */
    public List<ModeTotal> collectedByMode(UUID y, UUID classId, UUID sectionId, LocalDate from, LocalDate to) {
        Sql sql = new Sql();
        sql.param("y", y).param("f", from).param("t", to);
        String scope = scope(sql, classId, sectionId);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = sql.query(em, "SELECT pm.mode, count(*), sum(pm.amount_paise) FROM fee_payment pm JOIN fee_ledger l ON l.id = pm.ledger_id "
                + "WHERE l.academic_year_id = :y AND pm.status = 'SUCCESS' AND pm.realized_on BETWEEN :f AND :t" + scope
                + " GROUP BY pm.mode ORDER BY 3 DESC").getResultList();
        return rows.stream().map(r -> new ModeTotal(PaymentMode.valueOf((String) r[0]), num(r[1]), num(r[2]))).toList();
    }

    /** Net payable (amount − concession) of active lines falling due between the dates: what the school expects to collect then. */
    public long netDueBetween(UUID y, LocalDate from, LocalDate to) {
        return num(em.createNativeQuery("SELECT COALESCE(sum(ln.amount_paise - ln.concession_paise), 0) FROM fee_ledger_line ln "
                        + "JOIN fee_ledger l ON l.id = ln.ledger_id WHERE l.academic_year_id = :y AND ln.status = 'ACTIVE' AND ln.due_date BETWEEN :f AND :t")
                .setParameter("y", y).setParameter("f", from).setParameter("t", to).getSingleResult());
    }

    /** Collected (SUCCESS) per realization day: day → paise. Days without money are absent. */
    public Map<LocalDate, Long> collectedByDay(UUID y, LocalDate from, LocalDate to) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT pm.realized_on, sum(pm.amount_paise) FROM fee_payment pm WHERE pm.academic_year_id = :y "
                        + "AND pm.status = 'SUCCESS' AND pm.realized_on BETWEEN :f AND :t GROUP BY pm.realized_on")
                .setParameter("y", y).setParameter("f", from).setParameter("t", to).getResultList();
        Map<LocalDate, Long> out = new HashMap<>();
        rows.forEach(r -> out.put(date(r[0]), num(r[1])));
        return out;
    }

    /** Year totals of one group (fee head or class): billed, concession, paid, pending, due, overdue. */
    public record GroupTotals(UUID id, String name, long billed, long concession, long paid, long pending, long due, long overdue) {
    }

    public List<GroupTotals> totalsByHead(UUID y, LocalDate today) {
        return groupTotals(y, today, "h.id", "h.name", "JOIN fee_head h ON h.id = ln.fee_head_id", "min(h.sort_order), h.name");
    }

    public List<GroupTotals> totalsByClass(UUID y, LocalDate today) {
        return groupTotals(y, today, "c.id", "COALESCE(c.display_name, c.name)", "JOIN academic_class c ON c.id = l.academic_class_id",
                "min(c.sort_order), 2");
    }

    private List<GroupTotals> groupTotals(UUID y, LocalDate today, String id, String name, String join, String order) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT %1$s, %2$s, sum(ln.amount_paise), sum(ln.concession_paise), sum(ln.paid_paise), sum(ln.pending_paise), sum(%3$s),
                               COALESCE(sum(%3$s) FILTER (WHERE ln.due_date < :today), 0)
                        FROM fee_ledger_line ln JOIN fee_ledger l ON l.id = ln.ledger_id %4$s
                        WHERE l.academic_year_id = :y AND ln.status = 'ACTIVE'
                        GROUP BY %1$s, %2$s ORDER BY %5$s""".formatted(id, name, DUE, join, order))
                .setParameter("y", y).setParameter("today", today).getResultList();
        return rows.stream().map(r -> new GroupTotals(uuid(r[0]), (String) r[1], num(r[2]), num(r[3]), num(r[4]), num(r[5]), num(r[6]), num(r[7])))
                .toList();
    }

    public enum CollectionGroup { DAY, MODE, COLLECTOR, FEE_HEAD }

    /** One row of the collections report: the group key and label, number of payments (allocations for FEE_HEAD) and money. */
    public record CollectionRow(String key, String label, long count, long amountPaise) {
    }

    /** Money realized (SUCCESS) between the dates, grouped. FEE_HEAD splits payments by what they were allocated to. */
    public List<CollectionRow> collections(UUID y, LocalDate from, LocalDate to, CollectionGroup by) {
        String sql = switch (by) {
            case DAY -> "SELECT CAST(pm.realized_on AS varchar), CAST(pm.realized_on AS varchar), count(*), sum(pm.amount_paise) FROM fee_payment pm "
                    + "WHERE %s GROUP BY pm.realized_on ORDER BY pm.realized_on";
            case MODE -> "SELECT pm.mode, pm.mode, count(*), sum(pm.amount_paise) FROM fee_payment pm WHERE %s GROUP BY pm.mode ORDER BY 4 DESC";
            case COLLECTOR -> "SELECT CAST(pm.collected_by AS varchar), max(pm.collected_by_name), count(*), sum(pm.amount_paise) FROM fee_payment pm "
                    + "WHERE %s GROUP BY pm.collected_by ORDER BY 4 DESC";
            case FEE_HEAD -> "SELECT CAST(h.id AS varchar), h.name, count(*), sum(a.amount_paise) FROM fee_payment pm "
                    + "JOIN fee_payment_allocation a ON a.payment_id = pm.id JOIN fee_ledger_line ln ON ln.id = a.ledger_line_id "
                    + "JOIN fee_head h ON h.id = ln.fee_head_id WHERE %s GROUP BY h.id, h.name ORDER BY 4 DESC";
        };
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql.formatted("pm.academic_year_id = :y AND pm.status = 'SUCCESS' AND pm.realized_on BETWEEN :f AND :t"))
                .setParameter("y", y).setParameter("f", from).setParameter("t", to).getResultList();
        return rows.stream().map(r -> new CollectionRow((String) r[0], (String) r[1], num(r[2]), num(r[3]))).toList();
    }

    public long pendingCheques(UUID y) {
        return num(em.createNativeQuery("SELECT count(*) FROM fee_payment WHERE academic_year_id = :y AND status = 'PENDING_CLEARANCE'")
                .setParameter("y", y).getSingleResult());
    }

    public long pendingConcessionRequests(UUID y) {
        return num(em.createNativeQuery("SELECT count(*) FROM fee_concession_request WHERE academic_year_id = :y AND status = 'PENDING'")
                .setParameter("y", y).getSingleResult());
    }

    private static String scope(Sql sql, UUID classId, UUID sectionId) {
        String s = "";
        if (classId != null) {
            s += " AND l.academic_class_id = :c";
            sql.param("c", classId);
        }
        if (sectionId != null) {
            s += " AND l.section_id = :sec";
            sql.param("sec", sectionId);
        }
        return s;
    }

    // ── Day book ─────────────────────────────────────────────────────────────────────

    public record DayBookFilter(LocalDate from, LocalDate to, PaymentMode mode, PaymentStatus status, UUID collectedBy, UUID classId) {
    }

    public List<DayBookRow> dayBook(DayBookFilter f, int page, int size) {
        Sql sql = dayBookSql(f);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = sql.query(em, """
                SELECT pm.id, pm.receipt_number, pm.paid_on, pm.realized_on, s.id, %s, s.admission_number, COALESCE(c.display_name, c.name),
                       sec.name, pm.amount_paise, pm.mode, pm.reference_no, pm.status, pm.collected_by_name
                """.formatted(STUDENT_NAME) + sql.text + " ORDER BY pm.paid_on DESC, pm.created_at DESC LIMIT " + size + " OFFSET " + ((long) page * size))
                .getResultList();
        return rows.stream().map(r -> new DayBookRow(uuid(r[0]), (String) r[1], date(r[2]), date(r[3]), uuid(r[4]), (String) r[5], (String) r[6],
                (String) r[7], (String) r[8], num(r[9]), PaymentMode.valueOf((String) r[10]), (String) r[11], PaymentStatus.valueOf((String) r[12]),
                (String) r[13])).toList();
    }

    public long countDayBook(DayBookFilter f) {
        Sql sql = dayBookSql(f);
        return num(sql.query(em, "SELECT count(*) " + sql.text).getSingleResult());
    }

    /** Received money per mode in the window, bounced and reversed payments left out. */
    public List<ModeTotal> dayBookTotals(DayBookFilter f) {
        Sql sql = dayBookSql(f);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = sql.query(em, "SELECT pm.mode, count(*), sum(pm.amount_paise) " + sql.text
                + " AND pm.status IN ('SUCCESS', 'PENDING_CLEARANCE') GROUP BY pm.mode ORDER BY pm.mode").getResultList();
        return rows.stream().map(r -> new ModeTotal(PaymentMode.valueOf((String) r[0]), num(r[1]), num(r[2]))).toList();
    }

    private static Sql dayBookSql(DayBookFilter f) {
        Sql sql = new Sql();
        StringBuilder t = new StringBuilder("""
                FROM fee_payment pm
                JOIN fee_ledger l ON l.id = pm.ledger_id
                JOIN student s ON s.id = pm.student_id
                LEFT JOIN tenant_user_profile p ON p.user_id = s.tenant_user_id
                JOIN academic_class c ON c.id = l.academic_class_id
                LEFT JOIN section sec ON sec.id = l.section_id
                WHERE pm.paid_on BETWEEN :f AND :t""");
        sql.param("f", f.from()).param("t", f.to());
        if (f.mode() != null) {
            t.append(" AND pm.mode = :mode");
            sql.param("mode", f.mode().name());
        }
        if (f.status() != null) {
            t.append(" AND pm.status = :status");
            sql.param("status", f.status().name());
        }
        if (f.collectedBy() != null) {
            t.append(" AND pm.collected_by = :by");
            sql.param("by", f.collectedBy());
        }
        if (f.classId() != null) {
            t.append(" AND l.academic_class_id = :c");
            sql.param("c", f.classId());
        }
        sql.text = t.toString();
        return sql;
    }

    // ── Concession requests ──────────────────────────────────────────────────────────

    public record ConcessionFilter(UUID academicYearId, UUID studentId, ConcessionRequestStatus status, UUID concessionTypeId, UUID classId,
                                   String search, UUID requestId) {
    }

    public List<ConcessionView> concessions(ConcessionFilter f, int page, int size) {
        Sql sql = concessionSql(f);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = sql.query(em, """
                SELECT cr.id, cr.request_number, s.id, %s, s.admission_number, COALESCE(c.display_name, c.name), t.id, t.code, t.name, cr.kind,
                       cr.percent, cr.amount_paise, cr.fee_head_id, h.name, cr.reason, cr.status, cr.applied_paise, cr.requested_by_name,
                       cr.created_at, cr.reviewed_by_name, cr.reviewed_at, cr.review_remarks, cr.revoked_at, cr.revoke_reason, cr.effective_from
                """.formatted(STUDENT_NAME) + sql.text + " ORDER BY cr.created_at DESC LIMIT " + size + " OFFSET " + ((long) page * size))
                .getResultList();
        return rows.stream().map(r -> new ConcessionView(uuid(r[0]), (String) r[1], uuid(r[2]), (String) r[3], (String) r[4], (String) r[5], uuid(r[6]),
                (String) r[7], (String) r[8], ConcessionKind.valueOf((String) r[9]), (BigDecimal) r[10], r[11] == null ? null : num(r[11]), uuid(r[12]),
                (String) r[13], (String) r[14], ConcessionRequestStatus.valueOf((String) r[15]), num(r[16]), (String) r[17], instant(r[18]),
                (String) r[19], instant(r[20]), (String) r[21], instant(r[22]), (String) r[23], date(r[24]))).toList();
    }

    public long countConcessions(ConcessionFilter f) {
        Sql sql = concessionSql(f);
        return num(sql.query(em, "SELECT count(*) " + sql.text).getSingleResult());
    }

    private static Sql concessionSql(ConcessionFilter f) {
        Sql sql = new Sql();
        StringBuilder t = new StringBuilder("""
                FROM fee_concession_request cr
                JOIN fee_concession_type t ON t.id = cr.concession_type_id
                LEFT JOIN fee_head h ON h.id = cr.fee_head_id
                JOIN student s ON s.id = cr.student_id
                LEFT JOIN tenant_user_profile p ON p.user_id = s.tenant_user_id
                LEFT JOIN LATERAL (SELECT e.academic_class_id FROM student_academic_enrollment e WHERE e.student_id = s.id
                                   AND e.academic_year_id = cr.academic_year_id AND e.deleted_at IS NULL ORDER BY e.enrollment_date DESC LIMIT 1) en ON true
                LEFT JOIN academic_class c ON c.id = en.academic_class_id
                WHERE 1 = 1""");
        if (f.requestId() != null) {
            t.append(" AND cr.id = :id");
            sql.param("id", f.requestId());
        }
        if (f.academicYearId() != null) {
            t.append(" AND cr.academic_year_id = :y");
            sql.param("y", f.academicYearId());
        }
        if (f.studentId() != null) {
            t.append(" AND cr.student_id = :st");
            sql.param("st", f.studentId());
        }
        if (f.status() != null) {
            t.append(" AND cr.status = :status");
            sql.param("status", f.status().name());
        }
        if (f.concessionTypeId() != null) {
            t.append(" AND cr.concession_type_id = :type");
            sql.param("type", f.concessionTypeId());
        }
        if (f.classId() != null) {
            t.append(" AND en.academic_class_id = :c");
            sql.param("c", f.classId());
        }
        if (f.search() != null && !f.search().isBlank()) {
            t.append(" AND (lower(").append(STUDENT_NAME).append(") LIKE :q OR lower(s.admission_number) LIKE :q OR lower(cr.request_number) LIKE :q)");
            sql.param("q", "%" + f.search().trim().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%");
        }
        sql.text = t.toString();
        return sql;
    }

    /** What each line received from a request: line id → paise. */
    public Map<UUID, Long> lineConcessions(UUID requestId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT ledger_line_id, amount_paise FROM fee_line_concession WHERE request_id = :r")
                .setParameter("r", requestId).getResultList();
        Map<UUID, Long> out = new HashMap<>();
        rows.forEach(r -> out.put(uuid(r[0]), num(r[1])));
        return out;
    }

    /** What each request gave one line: request id → paise. */
    public Map<UUID, Long> concessionsOnLine(UUID lineId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT request_id, amount_paise FROM fee_line_concession WHERE ledger_line_id = :l")
                .setParameter("l", lineId).getResultList();
        Map<UUID, Long> out = new HashMap<>();
        rows.forEach(r -> out.put(uuid(r[0]), num(r[1])));
        return out;
    }

    public void addLineConcession(UUID lineId, UUID requestId, long amountPaise) {
        em.createNativeQuery("INSERT INTO fee_line_concession (ledger_line_id, request_id, amount_paise) VALUES (:l, :r, :a) "
                        + "ON CONFLICT (ledger_line_id, request_id) DO UPDATE SET amount_paise = fee_line_concession.amount_paise + EXCLUDED.amount_paise")
                .setParameter("l", lineId).setParameter("r", requestId).setParameter("a", amountPaise).executeUpdate();
    }

    public void removeLineConcession(UUID lineId, UUID requestId) {
        em.createNativeQuery("DELETE FROM fee_line_concession WHERE ledger_line_id = :l AND request_id = :r")
                .setParameter("l", lineId).setParameter("r", requestId).executeUpdate();
    }

    // ── Reminders ────────────────────────────────────────────────────────────────────

    public void recordReminder(UUID ledgerId, String eventCode, String channels, int queued, UUID sentBy) {
        em.createNativeQuery("INSERT INTO fee_reminder (ledger_id, event_code, channels, queued, sent_by) VALUES (:l, :e, :c, :q, :u)")
                .setParameter("l", ledgerId).setParameter("e", eventCode).setParameter("c", channels).setParameter("q", queued)
                .setParameter("u", sentBy).executeUpdate();
    }

    // ── Billing inputs ───────────────────────────────────────────────────────────────

    /** A student on the year's roll; {@code newAdmission} = no enrolment in any earlier academic year. */
    public record RollEntry(UUID studentId, UUID classId, UUID sectionId, boolean newAdmission) {
    }

    public List<RollEntry> roll(UUID y, Collection<UUID> classIds) {
        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT ON (e.student_id) e.student_id, e.academic_class_id, e.section_id,
                       NOT EXISTS (SELECT 1 FROM student_academic_enrollment pe JOIN academic_year py ON py.id = pe.academic_year_id
                                   WHERE pe.student_id = e.student_id AND pe.deleted_at IS NULL AND py.start_date < y.start_date)
                FROM student_academic_enrollment e
                JOIN student s ON s.id = e.student_id AND s.deleted_at IS NULL
                JOIN academic_year y ON y.id = e.academic_year_id
                WHERE e.academic_year_id = :y AND e.deleted_at IS NULL AND e.enrollment_status IN ('ADMITTED', 'ACTIVE')""");
        if (classIds != null && !classIds.isEmpty()) {
            sql.append(" AND e.academic_class_id IN (:classes)");
        }
        sql.append(" ORDER BY e.student_id, e.enrollment_date DESC");
        Query q = em.createNativeQuery(sql.toString()).setParameter("y", y);
        if (classIds != null && !classIds.isEmpty()) {
            q.setParameter("classes", classIds);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        return rows.stream().map(r -> new RollEntry(uuid(r[0]), uuid(r[1]), uuid(r[2]), Boolean.TRUE.equals(r[3]))).toList();
    }

    /** The student's class and section in the year (latest live enrolment). */
    public Optional<RollEntry> enrolment(UUID studentId, UUID y) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT e.student_id, e.academic_class_id, e.section_id FROM student_academic_enrollment e "
                        + "WHERE e.student_id = :s AND e.academic_year_id = :y AND e.deleted_at IS NULL "
                        + "ORDER BY (e.enrollment_status IN ('ADMITTED', 'ACTIVE')) DESC, e.enrollment_date DESC LIMIT 1")
                .setParameter("s", studentId).setParameter("y", y).getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.of(new RollEntry(uuid(rows.get(0)[0]), uuid(rows.get(0)[1]), uuid(rows.get(0)[2]), false));
    }

    public Map<UUID, String> classNames(Collection<UUID> classIds) {
        if (classIds.isEmpty()) {
            return Map.of();
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT id, COALESCE(display_name, name) FROM academic_class WHERE id IN (:ids)")
                .setParameter("ids", classIds).getResultList();
        Map<UUID, String> out = new HashMap<>();
        rows.forEach(r -> out.put(uuid(r[0]), (String) r[1]));
        return out;
    }

    // ── Numbers and people ───────────────────────────────────────────────────────────

    /** Next running number of the kind in the year (1, 2, ...); safe under concurrency. */
    public long nextNumber(String kind, UUID y) {
        return num(em.createNativeQuery("INSERT INTO fee_number_sequence (kind, academic_year_id, next_value) VALUES (:k, :y, 2) "
                        + "ON CONFLICT (kind, academic_year_id) DO UPDATE SET next_value = fee_number_sequence.next_value + 1 RETURNING next_value - 1")
                .setParameter("k", kind).setParameter("y", y).getSingleResult());
    }

    /** Display name of a login in this school (its tenant user profile), if it has one. */
    public Optional<String> personName(UUID masterUserId) {
        if (masterUserId == null) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery("SELECT COALESCE(NULLIF(trim(p.display_name), ''), NULLIF(trim(concat_ws(' ', p.first_name, p.last_name)), ''), u.email) "
                        + "FROM tenant_user u LEFT JOIN tenant_user_profile p ON p.user_id = u.id WHERE u.master_user_id = :u AND u.deleted_at IS NULL LIMIT 1")
                .setParameter("u", masterUserId).getResultList();
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable((String) rows.get(0));
    }

    public boolean studentExists(UUID studentId) {
        return num(em.createNativeQuery("SELECT count(*) FROM student WHERE id = :s AND deleted_at IS NULL")
                .setParameter("s", studentId).getSingleResult()) > 0;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    /** SQL text plus its named parameters. */
    private static final class Sql {
        private String text;
        private final Map<String, Object> params = new HashMap<>();

        Sql param(String name, Object value) {
            params.put(name, value);
            return this;
        }

        Query query(EntityManager em, String full) {
            Query q = em.createNativeQuery(full);
            params.forEach((k, v) -> {
                if (full.contains(":" + k)) {
                    q.setParameter(k, v);
                }
            });
            return q;
        }
    }

    static long num(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    static UUID uuid(Object o) {
        return o == null ? null : o instanceof UUID u ? u : UUID.fromString(o.toString());
    }

    static LocalDate date(Object o) {
        return switch (o) {
            case null -> null;
            case LocalDate d -> d;
            case java.sql.Date d -> d.toLocalDate();
            default -> LocalDate.parse(o.toString());
        };
    }

    static Instant instant(Object o) {
        return switch (o) {
            case null -> null;
            case Instant i -> i;
            case OffsetDateTime odt -> odt.toInstant();
            case Timestamp ts -> ts.toInstant();
            default -> Instant.parse(o.toString());
        };
    }
}
