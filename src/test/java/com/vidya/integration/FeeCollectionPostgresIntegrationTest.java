package com.vidya.integration;

import com.vidya.common.exception.BusinessException;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.dashboard.dto.DashboardDtos.ActionItemType;
import com.vidya.tenantdata.dashboard.repository.DashboardQueries;
import com.vidya.tenantdata.fee.dto.FeeDtos.AddChargeRequest;
import com.vidya.tenantdata.fee.dto.FeeDtos.ApproveRequest;
import com.vidya.tenantdata.fee.dto.FeeDtos.ChequeOutcome;
import com.vidya.tenantdata.fee.dto.FeeDtos.ChequeStatusRequest;
import com.vidya.tenantdata.fee.dto.FeeDtos.CollectPaymentRequest;
import com.vidya.tenantdata.fee.dto.FeeDtos.CollectionsReport;
import com.vidya.tenantdata.fee.dto.FeeDtos.ConcessionView;
import com.vidya.tenantdata.fee.dto.FeeDtos.CreateConcessionRequest;
import com.vidya.tenantdata.fee.dto.FeeDtos.DashboardFees;
import com.vidya.tenantdata.fee.dto.FeeDtos.DayBook;
import com.vidya.tenantdata.fee.dto.FeeDtos.Defaulters;
import com.vidya.tenantdata.fee.dto.FeeDtos.FeeBreakdown;
import com.vidya.tenantdata.fee.dto.FeeDtos.FeeSummary;
import com.vidya.tenantdata.fee.dto.FeeDtos.GenerateInvoicesRequest;
import com.vidya.tenantdata.fee.dto.FeeDtos.GenerateInvoicesResult;
import com.vidya.tenantdata.fee.dto.FeeDtos.LedgerDetail;
import com.vidya.tenantdata.fee.dto.FeeDtos.LedgerRow;
import com.vidya.tenantdata.fee.dto.FeeDtos.LineView;
import com.vidya.tenantdata.fee.dto.FeeDtos.PaymentResult;
import com.vidya.tenantdata.fee.dto.FeeDtos.Receipt;
import com.vidya.tenantdata.fee.dto.FeeDtos.ReminderFilter;
import com.vidya.tenantdata.fee.dto.FeeDtos.ReminderRequest;
import com.vidya.tenantdata.fee.dto.FeeDtos.ReminderResult;
import com.vidya.tenantdata.fee.dto.FeeDtos.RevokeRequest;
import com.vidya.tenantdata.fee.enums.AgeingBucket;
import com.vidya.tenantdata.fee.enums.ConcessionRequestStatus;
import com.vidya.tenantdata.fee.enums.DueStatus;
import com.vidya.tenantdata.fee.enums.LineStatus;
import com.vidya.tenantdata.fee.enums.PaymentMode;
import com.vidya.tenantdata.fee.enums.PaymentStatus;
import com.vidya.tenantdata.fee.repository.FeeConcessionRequestRepository;
import com.vidya.tenantdata.fee.repository.FeeLedgerLineRepository;
import com.vidya.tenantdata.fee.repository.FeeLedgerRepository;
import com.vidya.tenantdata.fee.repository.FeePaymentAllocationRepository;
import com.vidya.tenantdata.fee.repository.FeePaymentRepository;
import com.vidya.tenantdata.fee.repository.FeeQueries;
import com.vidya.tenantdata.fee.repository.FeeQueries.CollectionGroup;
import com.vidya.tenantdata.fee.service.FeeConcessionService;
import com.vidya.tenantdata.fee.service.FeeContext;
import com.vidya.tenantdata.fee.service.FeeInvoiceService;
import com.vidya.tenantdata.fee.service.FeeLedgerService;
import com.vidya.tenantdata.fee.service.FeeNotifier;
import com.vidya.tenantdata.fee.service.FeePaymentService;
import com.vidya.tenantdata.fee.service.FeeReminderService;
import com.vidya.tenantdata.fee.service.FeeReportService;
import com.vidya.tenantdata.operations.enums.NotificationChannel;
import com.vidya.tenantdata.operations.enums.NotificationEvent;
import com.vidya.tenantdata.operations.repository.FeeConcessionTypeRepository;
import com.vidya.tenantdata.operations.repository.FeeHeadRepository;
import com.vidya.tenantdata.operations.repository.FeeInstallmentRepository;
import com.vidya.tenantdata.operations.repository.FeeSettingsRepository;
import com.vidya.tenantdata.operations.repository.FeeStructureItemRepository;
import com.vidya.tenantdata.operations.repository.FeeStructureRepository;
import com.vidya.tenantdata.security.DataAccessService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentCaptor;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fee collection end to end on the real tenant schema: the services run over JPA repositories and native SQL, one transaction per call.
 * Data lives in its own academic year in 2093 (the container is shared with other integration tests).
 *
 * <p>Set-up: 4 quarterly instalments of 25%; Tuition ₹3,000 a quarter (₹12,000 a year), Admission ₹5,000 once, Transport (optional).
 * Class 1 has an ALL structure (Tuition, Transport) and a NEW_ADMISSION one (Tuition, Admission); class 2 only a draft.
 * Student A was enrolled last year (existing), B is new, C is in class 2. Today is 20 Aug 2093.
 */
@DisplayName("Fee collection on PostgreSQL")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FeeCollectionPostgresIntegrationTest extends AbstractTenantPostgresIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2093, 8, 20);
    private static final UUID MAKER = UUID.randomUUID();
    private static final UUID CHECKER = UUID.randomUUID();
    private static final String S = UUID.randomUUID().toString().substring(0, 6);

    private static EntityManagerFactory emf;
    private static EntityManager em;
    private static FeeInvoiceService invoices;
    private static FeeLedgerService ledgers;
    private static FeePaymentService payments;
    private static FeeConcessionService concessions;
    private static FeeReminderService reminders;
    private static FeeReportService reports;
    private static DashboardQueries dashboardQueries;
    private static final FeeNotifier notifier = mock(FeeNotifier.class);

    private static UUID year;
    private static UUID class1;
    private static UUID tuition;
    private static UUID admission;
    private static UUID sibling;
    private static UUID studentA;
    private static UUID studentB;
    private static UUID concessionA;
    private static UUID chequeB;

    @BeforeAll
    static void init() throws Exception {
        LocalContainerEntityManagerFactoryBean emfb = new LocalContainerEntityManagerFactoryBean();
        emfb.setDataSource(dataSource);
        emfb.setPackagesToScan("com.vidya.tenantdata", "com.vidya.master");
        emfb.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Properties props = new Properties();
        props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        emfb.setJpaProperties(props);
        emfb.afterPropertiesSet();
        emf = emfb.getObject();
        em = emf.createEntityManager();

        JpaRepositoryFactory repos = new JpaRepositoryFactory(em);
        FeeQueries queries = inject(new FeeQueries(), FeeQueries.class);
        dashboardQueries = inject(new DashboardQueries(), DashboardQueries.class);
        DataAccessService dataAccess = mock(DataAccessService.class);
        when(dataAccess.canAccessStudent(any(), any(), any())).thenReturn(true);
        when(dataAccess.hasFullScope(any(), any())).thenReturn(true);
        Clock clock = Clock.fixed(TODAY.atTime(11, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant(), ZoneId.of("Asia/Kolkata"));

        FeeLedgerRepository ledgerRepo = repos.getRepository(FeeLedgerRepository.class);
        FeeLedgerLineRepository lineRepo = repos.getRepository(FeeLedgerLineRepository.class);
        FeePaymentRepository paymentRepo = repos.getRepository(FeePaymentRepository.class);
        FeeHeadRepository headRepo = repos.getRepository(FeeHeadRepository.class);
        FeeContext ctx = new FeeContext(repos.getRepository(AcademicYearRepository.class), repos.getRepository(FeeSettingsRepository.class),
                ledgerRepo, queries, dataAccess, clock);
        concessions = new FeeConcessionService(ctx, queries, repos.getRepository(FeeConcessionRequestRepository.class),
                repos.getRepository(FeeConcessionTypeRepository.class), ledgerRepo, lineRepo);
        invoices = new FeeInvoiceService(ctx, queries, ledgerRepo, lineRepo, repos.getRepository(FeeStructureRepository.class),
                repos.getRepository(FeeStructureItemRepository.class), headRepo, repos.getRepository(FeeInstallmentRepository.class), concessions);
        ledgers = new FeeLedgerService(ctx, queries, ledgerRepo, lineRepo, paymentRepo, headRepo, concessions);
        payments = new FeePaymentService(ctx, queries, ledgerRepo, lineRepo, paymentRepo, repos.getRepository(FeePaymentAllocationRepository.class),
                headRepo, notifier);
        reminders = new FeeReminderService(ctx, queries, lineRepo, notifier);
        reports = new FeeReportService(ctx, queries);

        when(notifier.send(any(), anyCollection(), any())).thenAnswer(inv -> {
            Collection<FeeNotifier.Message> msgs = inv.getArgument(1);
            Map<UUID, Integer> per = new HashMap<>();
            msgs.forEach(m -> per.put(m.studentId(), 1));
            return new FeeNotifier.Outcome(msgs.size(), Set.of(), per, Set.of(NotificationChannel.WHATSAPP), true);
        });
        seed();
    }

    @AfterAll
    static void close() {
        SecurityContextHolder.clearContext();
        if (em != null && em.isOpen()) em.close();
        if (emf != null && emf.isOpen()) emf.close();
    }

    // ── 1. Invoices ──────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("a dry run shows what would be billed and saves nothing")
    void dryRun() {
        as(MAKER);
        GenerateInvoicesResult r = tx(() -> invoices.generate(new GenerateInvoicesRequest(year, null, null, true)));
        assertThat(r.studentsConsidered()).isEqualTo(3);
        assertThat(r.studentsBilled()).isEqualTo(2);
        assertThat(r.ledgersCreated()).isEqualTo(2);
        assertThat(r.linesCreated()).isEqualTo(9);
        assertThat(r.totalPaise()).isEqualTo(2_900_000);
        assertThat(r.classesNotBilled()).singleElement().satisfies(c -> {
            assertThat(c.students()).isEqualTo(1);
            assertThat(c.reason()).isEqualTo("No active fee structure for this class");
        });
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM fee_ledger WHERE academic_year_id = ?", Long.class, year)).isZero();
    }

    @Test
    @Order(2)
    @DisplayName("generation bills each student from the structure that applies to them, and running it again adds nothing")
    void generate() {
        as(MAKER);
        GenerateInvoicesResult r = tx(() -> invoices.generate(new GenerateInvoicesRequest(year, null, null, false)));
        assertThat(r.ledgersCreated()).isEqualTo(2);
        assertThat(r.linesCreated()).isEqualTo(9);
        assertThat(r.totalPaise()).isEqualTo(2_900_000);

        LedgerDetail a = tx(() -> ledgers.forStudent(studentA, year));
        assertThat(a.ledger().invoiceNumber()).startsWith("INV/93/");
        assertThat(a.lines()).hasSize(4).allSatisfy(l -> assertThat(l.amountPaise()).isEqualTo(300_000));
        LedgerDetail b = tx(() -> ledgers.forStudent(studentB, year));
        assertThat(b.ledger().totalBilledPaise()).isEqualTo(1_700_000);
        assertThat(b.lines()).filteredOn(l -> l.feeHeadId().equals(admission)).singleElement()
                .satisfies(l -> assertThat(l.dueDate()).isEqualTo(LocalDate.of(2093, 4, 10)));

        GenerateInvoicesResult again = tx(() -> invoices.generate(new GenerateInvoicesRequest(year, null, null, false)));
        assertThat(again.linesCreated()).isZero();
        assertThat(again.linesAlreadyBilled()).isEqualTo(9);
        assertThat(again.ledgersCreated()).isZero();
    }

    // ── 2. Concessions (maker-checker) ───────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("a concession needs a second person; approving it takes it off the ledger and the action item disappears")
    void concession() {
        as(MAKER);
        ConcessionView v = tx(() -> concessions.create(new CreateConcessionRequest(studentA, year, sibling, null, null, "Elder sister in class 9")));
        concessionA = v.id();
        assertThat(v.status()).isEqualTo(ConcessionRequestStatus.PENDING);
        assertThat(v.percent()).isEqualByComparingTo("10");
        assertThat(v.requestNumber()).startsWith("CON/93/");

        assertThatThrownBy(() -> tx(() -> concessions.create(new CreateConcessionRequest(studentA, year, sibling, null, null, "again"))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("already has");
        assertThatThrownBy(() -> tx(() -> concessions.approve(concessionA, new ApproveRequest(null))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("someone other");

        assertThat(tx(() -> dashboardQueries.pending(ActionItemType.FEE_CONCESSION, year, CHECKER))).singleElement()
                .satisfies(p -> assertThat(p.description()).contains("10%"));
        assertThat(tx(() -> dashboardQueries.pending(ActionItemType.FEE_CONCESSION, year, MAKER))).isEmpty();

        as(CHECKER);
        ConcessionView approved = tx(() -> concessions.approve(concessionA, new ApproveRequest("Verified")));
        assertThat(approved.status()).isEqualTo(ConcessionRequestStatus.APPROVED);
        assertThat(approved.appliedPaise()).isEqualTo(120_000);
        assertThat(tx(() -> ledgers.forStudent(studentA, year)).ledger().netPayablePaise()).isEqualTo(1_080_000);
        assertThat(tx(() -> dashboardQueries.pending(ActionItemType.FEE_CONCESSION, year, CHECKER))).isEmpty();
    }

    // ── 3. Payments ──────────────────────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("cash goes to the oldest dues first; overpaying is refused; the receipt has the amount in words")
    void cash() {
        as(MAKER);
        PaymentResult p = tx(() -> payments.collect(new CollectPaymentRequest(studentA, year, 400_000L, PaymentMode.CASH, null, null, null, null,
                null, "Counter 1", null)));
        assertThat(p.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(p.allocations()).extracting(a -> a.amountPaise()).containsExactly(270_000L, 130_000L);
        assertThat(p.newDuePaise()).isEqualTo(680_000);

        assertThatThrownBy(() -> tx(() -> payments.collect(new CollectPaymentRequest(studentA, year, 680_001L, PaymentMode.UPI, null, null, null,
                null, null, null, null)))).isInstanceOf(BusinessException.class).hasMessageContaining("more than what is due");

        Receipt r = tx(() -> payments.receipt(p.paymentId()));
        assertThat(r.amountInWords()).isEqualTo("Rupees Four Thousand Only");
        assertThat(r.lines()).hasSize(2);
        assertThat(r.balanceDuePaise()).isEqualTo(680_000);

        LedgerRow a = tx(() -> ledgers.forStudent(studentA, year)).ledger();
        assertThat(a.status()).isEqualTo(DueStatus.OVERDUE);
        assertThat(a.overduePaise()).isEqualTo(140_000);
        assertThat(a.oldestOverdueDate()).isEqualTo(LocalDate.of(2093, 7, 10));
        assertThat(a.bucket()).isEqualTo(AgeingBucket.D31_60);
    }

    @Test
    @Order(5)
    @DisplayName("a cheque is held as pending until it clears; a reversal takes the money back off the lines")
    void cheque() {
        as(MAKER);
        assertThatThrownBy(() -> tx(() -> payments.collect(new CollectPaymentRequest(studentB, year, 500_000L, PaymentMode.CHEQUE, null, null,
                null, null, null, null, null)))).hasMessageContaining("cheque number");
        PaymentResult p = tx(() -> payments.collect(new CollectPaymentRequest(studentB, year, 500_000L, PaymentMode.CHEQUE, "004512", null,
                "SBI", null, null, null, null)));
        chequeB = p.paymentId();
        assertThat(p.status()).isEqualTo(PaymentStatus.PENDING_CLEARANCE);
        LedgerRow pending = tx(() -> ledgers.forStudent(studentB, year)).ledger();
        assertThat(pending.pendingClearancePaise()).isEqualTo(500_000);
        assertThat(pending.paidPaise()).isZero();
        assertThat(pending.duePaise()).isEqualTo(1_200_000);

        DayBook book = tx(() -> payments.dayBook(TODAY, TODAY, null, null, null, null, 0, 20));
        assertThat(book.totalPaise()).isEqualTo(900_000);
        assertThat(book.payments().getTotalElements()).isEqualTo(2);

        tx(() -> payments.chequeStatus(chequeB, new ChequeStatusRequest(ChequeOutcome.CLEARED, TODAY, null)));
        LedgerRow cleared = tx(() -> ledgers.forStudent(studentB, year)).ledger();
        assertThat(cleared.paidPaise()).isEqualTo(500_000);
        assertThat(cleared.pendingClearancePaise()).isZero();

        as(CHECKER);
        assertThat(tx(() -> payments.reverse(chequeB, "Wrong student")).status()).isEqualTo(PaymentStatus.REVERSED);
        assertThat(tx(() -> ledgers.forStudent(studentB, year)).ledger().paidPaise()).isZero();
        assertThatThrownBy(() -> tx(() -> payments.reverse(chequeB, "twice"))).hasMessageContaining("already REVERSED");
        assertThat(tx(() -> payments.dayBook(TODAY, TODAY, null, null, null, null, 0, 20)).totalPaise()).isEqualTo(400_000);
    }

    // ── 4. Totals ────────────────────────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("summary, dashboard figures, collections report and breakdown agree")
    void totals() {
        FeeSummary s = tx(() -> reports.summary(year, null, null, null, null));
        assertThat(s.ledgers()).isEqualTo(2);
        assertThat(s.totalInvoicedPaise()).isEqualTo(2_900_000);
        assertThat(s.concessionPaise()).isEqualTo(120_000);
        assertThat(s.paidPaise()).isEqualTo(400_000);
        assertThat(s.collectedInWindowPaise()).isEqualTo(400_000);
        assertThat(s.overdueLedgers()).isEqualTo(2);
        // Due by today: A 2 × ₹2,700; B 2 × ₹3,000 + ₹5,000 admission = ₹16,400; ₹4,000 paid
        assertThat(s.dueByToPaise()).isEqualTo(1_640_000);
        assertThat(s.collectionRate()).isEqualByComparingTo("24.4");

        DashboardFees d = tx(() -> reports.dashboard(year, TODAY.withDayOfMonth(1), TODAY));
        assertThat(d.collectedPaise()).isEqualTo(400_000);
        assertThat(d.targetPaise()).isZero();
        assertThat(d.collectedTodayPaise()).isEqualTo(400_000);
        assertThat(d.chequesPending()).isZero();

        CollectionsReport byHead = tx(() -> reports.collections(year, TODAY, TODAY, CollectionGroup.FEE_HEAD));
        assertThat(byHead.rows()).singleElement().satisfies(r -> {
            assertThat(r.label()).startsWith("Tuition");
            assertThat(r.amountPaise()).isEqualTo(400_000);
        });

        FeeBreakdown b = tx(() -> reports.breakdown(year));
        assertThat(b.byHead()).extracting(g -> g.billedPaise()).containsExactlyInAnyOrder(2_400_000L, 500_000L);
        assertThat(b.byClass()).singleElement().satisfies(g -> assertThat(g.paidPaise()).isEqualTo(400_000));
    }

    // ── 5. Changes to ledgers ────────────────────────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("revoking gives back the concession on later dues only; lines can be cancelled and charges added")
    void changes() {
        as(CHECKER);
        ConcessionView revoked = tx(() -> concessions.revoke(concessionA, new RevokeRequest("Sister left", LocalDate.of(2093, 10, 1))));
        assertThat(revoked.status()).isEqualTo(ConcessionRequestStatus.REVOKED);
        assertThat(revoked.appliedPaise()).isEqualTo(60_000);

        as(MAKER);
        LedgerDetail b = tx(() -> ledgers.forStudent(studentB, year));
        LineView last = b.lines().stream().filter(l -> Integer.valueOf(4).equals(l.installmentNo())).findFirst().orElseThrow();
        LineView cancelled = tx(() -> invoices.cancelLine(b.ledger().id(), last.id(), "Leaving in December"));
        assertThat(cancelled.lineStatus()).isEqualTo(LineStatus.CANCELLED);
        assertThat(cancelled.duePaise()).isZero();

        LedgerDetail a = tx(() -> ledgers.forStudent(studentA, year));
        LineView paidLine = a.lines().get(0);
        assertThatThrownBy(() -> tx(() -> invoices.cancelLine(a.ledger().id(), paidLine.id(), "no"))).hasMessageContaining("reverse the payment");
        LineView charge = tx(() -> invoices.addCharge(a.ledger().id(), new AddChargeRequest(admission, 25_000L, null, "Lost ID card", "Replacement")));
        assertThat(charge.dueDate()).isEqualTo(TODAY);
        assertThat(tx(() -> ledgers.forStudent(studentA, year)).ledger().duePaise()).isEqualTo(680_000 + 60_000 + 25_000);
        assertThat(tx(() -> ledgers.forStudent(studentB, year)).ledger().netPayablePaise()).isEqualTo(1_400_000);
    }

    // ── 6. Defaulters and reminders ──────────────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("defaulters are bucketed by the age of their oldest unpaid due; reminders go out once and are recorded")
    void defaultersAndReminders() {
        Defaulters d = tx(() -> ledgers.defaulters(year, null, null, null, null, null, 0, 20, null));
        assertThat(d.content().getContent()).extracting(LedgerRow::studentId).containsExactly(studentB, studentA);
        assertThat(d.buckets()).filteredOn(b -> b.count() > 0).extracting(b -> b.bucket()).containsExactly(AgeingBucket.D31_60, AgeingBucket.D90_PLUS);
        assertThat(tx(() -> ledgers.defaulters(year, null, null, AgeingBucket.D90_PLUS, null, null, 0, 20, null)).content().getContent())
                .extracting(LedgerRow::studentId).containsExactly(studentB);

        as(MAKER);
        ReminderResult r = tx(() -> reminders.send(new ReminderRequest(null, new ReminderFilter(year, null, null, null, null), null)));
        assertThat(r.ledgers()).isEqualTo(2);
        assertThat(r.messagesQueued()).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<FeeNotifier.Message>> sent = ArgumentCaptor.forClass(Collection.class);
        verify(notifier).send(eq(NotificationEvent.FEE_OVERDUE), sent.capture(), any());
        assertThat(sent.getValue()).extracting(m -> m.values().get("amount")).containsExactlyInAnyOrder("₹1,400.00", "₹11,000.00");
        assertThat(tx(() -> ledgers.forStudent(studentA, year)).ledger().remindersSent()).isEqualTo(1);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────

    private static <T> T tx(Supplier<T> work) {
        em.getTransaction().begin();
        try {
            T out = work.get();
            em.getTransaction().commit();
            return out;
        } catch (RuntimeException e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw e;
        } finally {
            em.clear();
        }
    }

    private static void as(UUID user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private static <T> T inject(T target, Class<T> type) throws Exception {
        Field f = type.getDeclaredField("em");
        f.setAccessible(true);
        f.set(target, em);
        return target;
    }

    private static void seed() {
        UUID previous = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO academic_year (id, code, name, start_date, end_date, is_current, status) VALUES (?, ?, ?, ?, ?, false, 'CLOSED')",
                previous, "AY-FP-" + S, "Fee prev " + S, LocalDate.of(2092, 4, 1), LocalDate.of(2093, 3, 31));
        year = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO academic_year (id, code, name, start_date, end_date, is_current, status) VALUES (?, ?, ?, ?, ?, false, 'ACTIVE')",
                year, "AY-F-" + S, "Fee " + S, LocalDate.of(2093, 4, 1), LocalDate.of(2094, 3, 31));
        class1 = UUID.randomUUID();
        UUID class2 = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO academic_class (id, code, name, sort_order) VALUES (?, ?, ?, 1), (?, ?, ?, 2)",
                class1, "FC1-" + S, "Fee class 1 " + S, class2, "FC2-" + S, "Fee class 2 " + S);

        String[] due = {"2093-04-10", "2093-07-10", "2093-10-10", "2094-01-10"};
        for (int i = 0; i < 4; i++) {
            jdbcTemplate.update("INSERT INTO fee_installment (academic_year_id, installment_no, name, due_date, percent) VALUES (?, ?, ?, ?, 25)",
                    year, i + 1, "Quarter " + (i + 1), LocalDate.parse(due[i]));
        }
        tuition = head("TUI", "Tuition " + S, "QUARTERLY", false);
        admission = head("ADM", "Admission " + S, "ONE_TIME", false);
        UUID transport = head("BUS", "Transport " + S, "MONTHLY", true);

        structure(class1, "ALL", "ACTIVE", Map.of(tuition, "3000", transport, "1500"));
        structure(class1, "NEW_ADMISSION", "ACTIVE", Map.of(tuition, "3000", admission, "5000"));
        structure(class2, "ALL", "DRAFT", Map.of(tuition, "4000"));

        sibling = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO fee_concession_type (id, code, name, kind, value, fee_head_id, requires_approval) VALUES (?, ?, ?, 'PERCENT', 10, ?, true)",
                sibling, "SIB-" + S, "Sibling " + S, tuition);

        studentA = student("fa");
        studentB = student("fb");
        UUID studentC = student("fc");
        enroll(studentA, previous, class1, "PROMOTED");
        enroll(studentA, year, class1, "ACTIVE");
        enroll(studentB, year, class1, "ADMITTED");
        enroll(studentC, year, class2, "ACTIVE");
    }

    private static UUID head(String code, String name, String frequency, boolean optional) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO fee_head (id, code, name, frequency, is_optional) VALUES (?, ?, ?, ?, ?)", id, code + "-" + S, name, frequency, optional);
        return id;
    }

    private static void structure(UUID cls, String appliesTo, String status, Map<UUID, String> items) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO fee_structure (id, structure_code, academic_year_id, academic_class_id, applies_to, name, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, "FS-" + UUID.randomUUID().toString().substring(0, 8), year, cls, appliesTo, appliesTo + " " + S, status);
        items.forEach((head, amount) -> jdbcTemplate.update("INSERT INTO fee_structure_item (fee_structure_id, fee_head_id, amount) VALUES (?, ?, ?)",
                id, head, new java.math.BigDecimal(amount)));
    }

    private static UUID student(String tag) {
        UUID tu = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tenant_user (id, user_code, email, user_type) VALUES (?, ?, ?, 'STUDENT')", tu, "TU-" + tag + S, tag + S + "@x.test");
        jdbcTemplate.update("INSERT INTO tenant_user_profile (user_id, first_name) VALUES (?, ?)", tu, "Kid " + tag);
        UUID st = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO student (id, student_code, tenant_user_id, admission_number, admission_date) VALUES (?, ?, ?, ?, ?)",
                st, "STU-" + tag + S, tu, "ADM-" + tag + S, LocalDate.of(2093, 4, 1));
        jdbcTemplate.update("INSERT INTO student_guardian (student_id, guardian_code, relationship_type, guardian_name, phone, status) "
                + "VALUES (?, ?, 'MOTHER', ?, '9800000000', 'ACTIVE')", st, "G-" + tag + S, "Parent " + tag);
        return st;
    }

    private static void enroll(UUID student, UUID y, UUID cls, String status) {
        jdbcTemplate.update("INSERT INTO student_academic_enrollment (enrollment_code, student_id, academic_year_id, academic_class_id, enrollment_status) "
                + "VALUES (?, ?, ?, ?, ?)", "E-" + UUID.randomUUID(), student, y, cls, status);
    }
}
