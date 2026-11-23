package com.vidya.tenantdata.fee.online.repository;

import com.vidya.tenantdata.fee.online.enums.DebitStatus;
import com.vidya.tenantdata.fee.online.enums.GatewayProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Native SQL of online fees: children of a payer in sibling order (higher class first), mandate coverage, invite candidates,
 * checkout children, webhook log and the list views. Callers own the transaction.
 */
@Repository
public class OnlineFeeQueries {

    static final String STUDENT_NAME = "COALESCE(NULLIF(trim(p.display_name), ''), NULLIF(trim(concat_ws(' ', p.first_name, p.last_name)), ''), "
            + "s.admission_number)";
    private static final String DUE = "(ln.amount_paise - ln.concession_paise - ln.paid_paise - ln.pending_paise)";

    @PersistenceContext(unitName = "tenant")
    private EntityManager em;

    // ── Children in sibling order ──────────────────────────────────────────────────────

    /** A billed child: the ledger of the year and the class it was billed in. */
    public record Child(UUID studentId, UUID ledgerId, String studentName, String admissionNumber, String className, int classSort) {
    }

    /**
     * The given students' ledgers of the year, <b>higher class first</b> (class sort order descending), then by name. Students not
     * billed for the year are left out.
     */
    public List<Child> children(UUID academicYearId, Collection<UUID> studentIds) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        List<Child> out = new ArrayList<>();
        for (Object[] r : rows(em.createNativeQuery("""
                        SELECT s.id, l.id, %s, s.admission_number, COALESCE(c.display_name, c.name), c.sort_order
                        FROM fee_ledger l
                        JOIN student s ON s.id = l.student_id AND s.deleted_at IS NULL
                        LEFT JOIN tenant_user_profile p ON p.user_id = s.tenant_user_id
                        JOIN academic_class c ON c.id = l.academic_class_id
                        WHERE l.academic_year_id = :y AND l.student_id IN (:ids)
                        ORDER BY c.sort_order DESC, lower(%s), s.id""".formatted(STUDENT_NAME, STUDENT_NAME))
                .setParameter("y", academicYearId).setParameter("ids", studentIds))) {
            out.add(new Child(uuid(r[0]), uuid(r[1]), (String) r[2], (String) r[3], (String) r[4], ((Number) r[5]).intValue()));
        }
        return out;
    }

    /** The academic year the date falls in (the current one first if several overlap). */
    public Optional<UUID> yearOn(LocalDate date) {
        return em.createNativeQuery("SELECT id FROM academic_year WHERE deleted_at IS NULL AND :d BETWEEN start_date AND end_date "
                        + "ORDER BY is_current DESC, start_date DESC LIMIT 1")
                .setParameter("d", date).getResultList().stream().findFirst().map(OnlineFeeQueries::uuid);
    }

    // ── Guardians (payers) ─────────────────────────────────────────────────────────────

    public record GuardianRow(UUID guardianId, UUID studentId, String name, String email, String phone, UUID guardianUserId) {
    }

    private static final String GUARDIAN_SQL = "SELECT g.id, g.student_id, g.guardian_name, g.email, g.phone, g.guardian_user_id "
            + "FROM student_guardian g WHERE g.deleted_at IS NULL AND g.status = 'ACTIVE' ";

    public Optional<GuardianRow> guardian(UUID guardianId) {
        return rows(em.createNativeQuery(GUARDIAN_SQL + "AND g.id = :id").setParameter("id", guardianId)).stream().map(OnlineFeeQueries::guardianRow)
                .findFirst();
    }

    /** Guardian rows linked to the logins (a parent with three children has three rows). */
    public List<GuardianRow> guardiansOfLogins(Collection<UUID> tenantUserIds) {
        if (tenantUserIds.isEmpty()) {
            return List.of();
        }
        return rows(em.createNativeQuery(GUARDIAN_SQL + "AND g.guardian_user_id IN (:u) ORDER BY g.is_primary DESC, g.created_at")
                .setParameter("u", tenantUserIds)).stream().map(OnlineFeeQueries::guardianRow).toList();
    }

    /** Guardian rows of the students: the payer's row per child, matched by login, else by phone. */
    public List<GuardianRow> guardiansOfStudents(Collection<UUID> studentIds) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        return rows(em.createNativeQuery(GUARDIAN_SQL + "AND g.student_id IN (:s) ORDER BY g.is_primary DESC, g.created_at")
                .setParameter("s", studentIds)).stream().map(OnlineFeeQueries::guardianRow).toList();
    }

    /** Guardian rows with this phone (last ten digits): a payer without a login, across their children. */
    public List<GuardianRow> guardiansByPhone(String lastTenDigits) {
        if (lastTenDigits == null || lastTenDigits.length() < 10) {
            return List.of();
        }
        return rows(em.createNativeQuery(GUARDIAN_SQL + "AND right(regexp_replace(g.phone, '\\D', '', 'g'), 10) = :p ORDER BY g.is_primary DESC, g.created_at")
                .setParameter("p", lastTenDigits)).stream().map(OnlineFeeQueries::guardianRow).toList();
    }

    private static GuardianRow guardianRow(Object[] r) {
        return new GuardianRow(uuid(r[0]), uuid(r[1]), (String) r[2], (String) r[3], (String) r[4], uuid(r[5]));
    }

    // ── Mandate coverage ───────────────────────────────────────────────────────────────

    public void addMandateStudents(UUID mandateId, Map<UUID, UUID> guardianByStudent) {
        for (Map.Entry<UUID, UUID> e : guardianByStudent.entrySet()) {
            em.createNativeQuery("INSERT INTO fee_mandate_student (mandate_id, student_id, guardian_id, live) VALUES (:m, :s, :g, TRUE)")
                    .setParameter("m", mandateId).setParameter("s", e.getKey()).setParameter("g", e.getValue()).executeUpdate();
        }
    }

    /** Of these students, those already covered by a pending / active mandate. */
    public Set<UUID> covered(Collection<UUID> studentIds) {
        if (studentIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> out = new HashSet<>();
        for (Object o : em.createNativeQuery("SELECT student_id FROM fee_mandate_student WHERE live AND student_id IN (:s)")
                .setParameter("s", studentIds).getResultList()) {
            out.add(uuid(o));
        }
        return out;
    }

    /** Students of each mandate (all, live or not), keyed by mandate. */
    public Map<UUID, List<UUID>> mandateStudents(Collection<UUID> mandateIds) {
        Map<UUID, List<UUID>> out = new HashMap<>();
        if (mandateIds.isEmpty()) {
            return out;
        }
        for (Object[] r : rows(em.createNativeQuery("SELECT mandate_id, student_id FROM fee_mandate_student WHERE mandate_id IN (:m)")
                .setParameter("m", mandateIds))) {
            out.computeIfAbsent(uuid(r[0]), k -> new ArrayList<>()).add(uuid(r[1]));
        }
        return out;
    }

    /** The mandate covering the student now, else the latest one they were on. */
    public Optional<UUID> mandateOfStudent(UUID studentId) {
        return em.createNativeQuery("SELECT ms.mandate_id FROM fee_mandate_student ms JOIN fee_mandate m ON m.id = ms.mandate_id "
                        + "WHERE ms.student_id = :s ORDER BY ms.live DESC, m.created_at DESC LIMIT 1")
                .setParameter("s", studentId).getResultList().stream().findFirst().map(OnlineFeeQueries::uuid);
    }

    /** Guardian row the mandate uses per child (who gets its messages). */
    public Map<UUID, UUID> mandateGuardians(UUID mandateId) {
        Map<UUID, UUID> out = new HashMap<>();
        for (Object[] r : rows(em.createNativeQuery("SELECT student_id, guardian_id FROM fee_mandate_student WHERE mandate_id = :m")
                .setParameter("m", mandateId))) {
            out.put(uuid(r[0]), uuid(r[1]));
        }
        return out;
    }

    /** The mandate stops covering its children (cancelled, failed, expired): they may get a new one. */
    public void releaseMandate(UUID mandateId) {
        em.createNativeQuery("UPDATE fee_mandate_student SET live = FALSE WHERE mandate_id = :m").setParameter("m", mandateId).executeUpdate();
    }

    /**
     * Students billed in the year with something due on or before {@code by} and no pending / active mandate: whose guardians are
     * invited to set one up before the debit day.
     */
    public List<UUID> uncoveredWithDue(UUID academicYearId, LocalDate by) {
        List<UUID> out = new ArrayList<>();
        for (Object o : em.createNativeQuery("""
                        SELECT l.student_id FROM fee_ledger l JOIN fee_ledger_line ln ON ln.ledger_id = l.id AND ln.status = 'ACTIVE'
                        WHERE l.academic_year_id = :y AND ln.due_date <= :by
                          AND NOT EXISTS (SELECT 1 FROM fee_mandate_student ms WHERE ms.student_id = l.student_id AND ms.live)
                        GROUP BY l.student_id HAVING sum(%s) > 0""".formatted(DUE))
                .setParameter("y", academicYearId).setParameter("by", by).getResultList()) {
            out.add(uuid(o));
        }
        return out;
    }

    // ── Checkout children ──────────────────────────────────────────────────────────────

    public record CheckoutChild(UUID studentId, UUID ledgerId, int rank, long duePaise, long paidPaise) {
    }

    public void addCheckoutStudent(UUID checkoutId, CheckoutChild c) {
        em.createNativeQuery("INSERT INTO fee_checkout_student (checkout_id, student_id, ledger_id, sibling_rank, due_paise, paid_paise) "
                        + "VALUES (:c, :s, :l, :r, :d, 0)")
                .setParameter("c", checkoutId).setParameter("s", c.studentId()).setParameter("l", c.ledgerId()).setParameter("r", c.rank())
                .setParameter("d", c.duePaise()).executeUpdate();
    }

    /** The checkout's children, higher class (rank 0) first. */
    public List<CheckoutChild> checkoutChildren(UUID checkoutId) {
        List<CheckoutChild> out = new ArrayList<>();
        for (Object[] r : rows(em.createNativeQuery("SELECT student_id, ledger_id, sibling_rank, due_paise, paid_paise FROM fee_checkout_student "
                + "WHERE checkout_id = :c ORDER BY sibling_rank").setParameter("c", checkoutId))) {
            out.add(new CheckoutChild(uuid(r[0]), uuid(r[1]), ((Number) r[2]).intValue(), num(r[3]), num(r[4])));
        }
        return out;
    }

    public void addCheckoutPaid(UUID checkoutId, UUID studentId, long paise) {
        em.createNativeQuery("UPDATE fee_checkout_student SET paid_paise = paid_paise + :p WHERE checkout_id = :c AND student_id = :s")
                .setParameter("p", paise).setParameter("c", checkoutId).setParameter("s", studentId).executeUpdate();
    }

    /** Checkout ids that include the student, newest first. */
    public List<UUID> checkoutsOfStudent(UUID studentId) {
        List<UUID> out = new ArrayList<>();
        for (Object o : em.createNativeQuery("SELECT cs.checkout_id FROM fee_checkout_student cs JOIN fee_checkout c ON c.id = cs.checkout_id "
                + "WHERE cs.student_id = :s ORDER BY c.created_at DESC LIMIT 50").setParameter("s", studentId).getResultList()) {
            out.add(uuid(o));
        }
        return out;
    }

    // ── Webhook log ────────────────────────────────────────────────────────────────────

    public void recordEvent(GatewayProvider provider, String eventId, String type, String payload, String outcome, String detail) {
        em.createNativeQuery("INSERT INTO fee_gateway_event (provider, event_id, event_type, payload, outcome, detail) VALUES (:p, :e, :t, :b, :o, :d)")
                .setParameter("p", provider.name()).setParameter("e", eventId).setParameter("t", cut(type, 80)).setParameter("b", payload)
                .setParameter("o", outcome).setParameter("d", cut(detail, 1000)).executeUpdate();
    }

    /** An event id already applied (gateways resend webhooks). */
    public boolean eventApplied(GatewayProvider provider, String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        return !em.createNativeQuery("SELECT 1 FROM fee_gateway_event WHERE provider = :p AND event_id = :e AND outcome = 'APPLIED' LIMIT 1")
                .setParameter("p", provider.name()).setParameter("e", eventId).getResultList().isEmpty();
    }

    // ── Lists ──────────────────────────────────────────────────────────────────────────

    public record StudentBrief(UUID studentId, String studentName, String className) {
    }

    /** Name and class of the students (class of their latest ledger). */
    public Map<UUID, StudentBrief> briefs(Collection<UUID> studentIds) {
        Map<UUID, StudentBrief> out = new LinkedHashMap<>();
        if (studentIds.isEmpty()) {
            return out;
        }
        for (Object[] r : rows(em.createNativeQuery("""
                        SELECT s.id, %s, (SELECT COALESCE(c.display_name, c.name) FROM fee_ledger l JOIN academic_class c ON c.id = l.academic_class_id
                                          JOIN academic_year y ON y.id = l.academic_year_id WHERE l.student_id = s.id ORDER BY y.start_date DESC LIMIT 1)
                        FROM student s LEFT JOIN tenant_user_profile p ON p.user_id = s.tenant_user_id WHERE s.id IN (:ids)""".formatted(STUDENT_NAME))
                .setParameter("ids", studentIds))) {
            out.put(uuid(r[0]), new StudentBrief(uuid(r[0]), (String) r[1], (String) r[2]));
        }
        return out;
    }

    public record DebitRow(UUID id, String reference, UUID mandateId, String payerName, String method, String provider, UUID studentId,
                           String studentName, String className, LocalDate cycleMonth, int attemptNo, int siblingRank, LocalDate scheduledOn,
                           long amountPaise, DebitStatus status, long bounceChargePaise, long bounceGstPaise, String failureReason,
                           UUID feePaymentId, Instant submittedAt, Instant settledAt) {
    }

    public record DebitFilter(LocalDate cycleMonth, Collection<DebitStatus> statuses, UUID mandateId, UUID studentId) {
    }

    public List<DebitRow> debits(DebitFilter f, int page, int size) {
        StringBuilder sql = new StringBuilder("""
                SELECT d.id, d.reference, d.mandate_id, m.payer_name, m.method, m.provider, d.student_id, %s, COALESCE(c.display_name, c.name),
                       d.cycle_month, d.attempt_no, d.sibling_rank, d.scheduled_on, d.amount_paise, d.status, d.bounce_charge_paise,
                       d.bounce_gst_paise, d.failure_reason, d.fee_payment_id, d.submitted_at, d.settled_at
                FROM fee_debit d JOIN fee_mandate m ON m.id = d.mandate_id
                JOIN fee_ledger l ON l.id = d.ledger_id JOIN academic_class c ON c.id = l.academic_class_id
                JOIN student s ON s.id = d.student_id LEFT JOIN tenant_user_profile p ON p.user_id = s.tenant_user_id
                """.formatted(STUDENT_NAME));
        Map<String, Object> params = debitWhere(f, sql);
        sql.append(" ORDER BY d.scheduled_on DESC, d.mandate_id, d.sibling_rank, d.attempt_no LIMIT :lim OFFSET :off");
        Query q = em.createNativeQuery(sql.toString());
        params.forEach(q::setParameter);
        q.setParameter("lim", size).setParameter("off", (long) page * size);
        List<DebitRow> out = new ArrayList<>();
        for (Object[] r : rows(q)) {
            out.add(new DebitRow(uuid(r[0]), (String) r[1], uuid(r[2]), (String) r[3], (String) r[4], (String) r[5], uuid(r[6]), (String) r[7],
                    (String) r[8], date(r[9]), ((Number) r[10]).intValue(), ((Number) r[11]).intValue(), date(r[12]), num(r[13]),
                    DebitStatus.valueOf((String) r[14]), num(r[15]), num(r[16]), (String) r[17], uuid(r[18]), instant(r[19]), instant(r[20])));
        }
        return out;
    }

    public long countDebits(DebitFilter f) {
        StringBuilder sql = new StringBuilder("SELECT count(*) FROM fee_debit d ");
        Map<String, Object> params = debitWhere(f, sql);
        Query q = em.createNativeQuery(sql.toString());
        params.forEach(q::setParameter);
        return num(q.getSingleResult());
    }

    /** Count and amount per status for the month (the auto-debit card). */
    public Map<DebitStatus, long[]> debitTotals(LocalDate cycleMonth) {
        Map<DebitStatus, long[]> out = new LinkedHashMap<>();
        for (Object[] r : rows(em.createNativeQuery("SELECT status, count(*), COALESCE(sum(amount_paise), 0), COALESCE(sum(bounce_charge_paise + bounce_gst_paise), 0) "
                + "FROM fee_debit WHERE cycle_month = :m GROUP BY status").setParameter("m", cycleMonth))) {
            out.put(DebitStatus.valueOf((String) r[0]), new long[]{num(r[1]), num(r[2]), num(r[3])});
        }
        return out;
    }

    private static Map<String, Object> debitWhere(DebitFilter f, StringBuilder sql) {
        Map<String, Object> p = new HashMap<>();
        sql.append(" WHERE 1 = 1");
        if (f.cycleMonth() != null) {
            sql.append(" AND d.cycle_month = :m");
            p.put("m", f.cycleMonth());
        }
        if (f.statuses() != null && !f.statuses().isEmpty()) {
            sql.append(" AND d.status IN (:st)");
            p.put("st", f.statuses().stream().map(Enum::name).toList());
        }
        if (f.mandateId() != null) {
            sql.append(" AND d.mandate_id = :md");
            p.put("md", f.mandateId());
        }
        if (f.studentId() != null) {
            sql.append(" AND d.student_id = :sd");
            p.put("sd", f.studentId());
        }
        return p;
    }

    // ── helpers ────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static List<Object[]> rows(Query q) {
        return q.getResultList();
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
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
