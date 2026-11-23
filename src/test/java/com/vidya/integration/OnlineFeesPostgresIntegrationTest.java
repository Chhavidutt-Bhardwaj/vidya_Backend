package com.vidya.integration;

import com.vidya.common.exception.BusinessException;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.fee.dto.FeeDtos.CollectPaymentRequest;
import com.vidya.tenantdata.fee.enums.PaymentMode;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.CheckoutView;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.CreateCheckoutRequest;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.CreateMandateRequest;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.MandateView;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.RunView;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.SaveGatewayRequest;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.UpdateSettingsRequest;
import com.vidya.tenantdata.fee.online.entity.FeeDebit;
import com.vidya.tenantdata.fee.online.enums.CheckoutKind;
import com.vidya.tenantdata.fee.online.enums.CheckoutStatus;
import com.vidya.tenantdata.fee.online.enums.DebitStatus;
import com.vidya.tenantdata.fee.online.enums.GatewayEnvironment;
import com.vidya.tenantdata.fee.online.enums.GatewayProvider;
import com.vidya.tenantdata.fee.online.enums.MandateMethod;
import com.vidya.tenantdata.fee.online.enums.MandateStatus;
import com.vidya.tenantdata.fee.online.gateway.FeeGateway;
import com.vidya.tenantdata.fee.online.gateway.FeeGatewayRegistry;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.ChargeSubmitted;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.CheckoutSession;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.CheckoutSetup;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.LinkReport;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.MandateSession;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.MandateUpdate;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.PaymentReport;
import com.vidya.tenantdata.fee.online.gateway.SecretCipher;
import com.vidya.tenantdata.fee.online.repository.FeeAutoDebitSettingsRepository;
import com.vidya.tenantdata.fee.online.repository.FeeCheckoutRepository;
import com.vidya.tenantdata.fee.online.repository.FeeDebitRepository;
import com.vidya.tenantdata.fee.online.repository.FeeDebitRunRepository;
import com.vidya.tenantdata.fee.online.repository.FeeGatewayAccountRepository;
import com.vidya.tenantdata.fee.online.repository.FeeGatewayPaymentRepository;
import com.vidya.tenantdata.fee.online.repository.FeeMandateRepository;
import com.vidya.tenantdata.fee.online.repository.OnlineFeeQueries;
import com.vidya.tenantdata.fee.online.service.AutoDebitService;
import com.vidya.tenantdata.fee.online.service.AutoDebitSteps;
import com.vidya.tenantdata.fee.online.service.CheckoutService;
import com.vidya.tenantdata.fee.online.service.MandateService;
import com.vidya.tenantdata.fee.online.service.OnlineFeeConfigService;
import com.vidya.tenantdata.fee.online.service.PayerAccess;
import com.vidya.tenantdata.fee.repository.FeeConcessionRequestRepository;
import com.vidya.tenantdata.fee.repository.FeeLedgerLineRepository;
import com.vidya.tenantdata.fee.repository.FeeLedgerRepository;
import com.vidya.tenantdata.fee.repository.FeePaymentAllocationRepository;
import com.vidya.tenantdata.fee.repository.FeePaymentRepository;
import com.vidya.tenantdata.fee.repository.FeeQueries;
import com.vidya.tenantdata.fee.service.FeeConcessionService;
import com.vidya.tenantdata.fee.service.FeeContext;
import com.vidya.tenantdata.fee.service.FeeInvoiceService;
import com.vidya.tenantdata.fee.service.FeeNotifier;
import com.vidya.tenantdata.fee.service.FeePaymentService;
import com.vidya.tenantdata.operations.enums.NotificationChannel;
import com.vidya.tenantdata.operations.enums.NotificationEvent;
import com.vidya.tenantdata.operations.repository.FeeConcessionTypeRepository;
import com.vidya.tenantdata.operations.repository.FeeHeadRepository;
import com.vidya.tenantdata.operations.repository.FeeInstallmentRepository;
import com.vidya.tenantdata.operations.repository.FeeSettingsRepository;
import com.vidya.tenantdata.operations.repository.FeeStructureItemRepository;
import com.vidya.tenantdata.operations.repository.FeeStructureRepository;
import com.vidya.tenantdata.security.DataAccessService;
import com.vidya.tenantdata.security.OwnRecordsResolver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.aopalliance.intercept.MethodInterceptor;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Collection;
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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mandates and the monthly auto-debit end to end on the real tenant schema, with a fake Cashfree. Its own academic year in 2131.
 *
 * <p>One parent (one login) with Senior (class sort 9: ₹3,000 due 10 Jun, ₹3,000 due 10 Jul) and Junior (sort 4: ₹2,000 due 5 Jun,
 * ₹2,000 due 10 Sep); another family's child Solo (₹1,000 due 1 Jun) has no mandate. Debit day 10, notices 3 and 1 days before,
 * siblings one day apart, bounce ₹500 + 18% GST, 2 attempts, retry after 3 days. The clock moves from 7 to 11 June.
 */
@DisplayName("Online fees and auto-debit on PostgreSQL")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OnlineFeesPostgresIntegrationTest extends AbstractTenantPostgresIntegrationTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final String S = UUID.randomUUID().toString().substring(0, 6);
    private static final UUID OFFICE = UUID.randomUUID();

    /** A clock the test moves forward. */
    static final class MovingClock extends Clock {
        Instant now;

        MovingClock(LocalDate day) {
            set(day);
        }

        void set(LocalDate day) {
            now = day.atTime(9, 0).atZone(IST).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return IST;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final MovingClock clock = new MovingClock(day(7));
    private static final FeeNotifier notifier = mock(FeeNotifier.class);
    private static final FeeGateway cashfree = mock(FeeGateway.class);

    private static EntityManagerFactory emf;
    private static EntityManager em;
    private static OnlineFeeConfigService config;
    private static MandateService mandates;
    private static AutoDebitSteps steps;
    private static AutoDebitService autoDebit;
    private static CheckoutService checkouts;
    private static FeePaymentService payments;
    private static OnlineFeeQueries onlineQueries;
    private static FeeDebitRepository debitRepo;

    private static UUID year;
    private static UUID senior;
    private static UUID junior;
    private static UUID solo;
    private static UUID seniorGuardian;
    private static UUID seniorLedger;
    private static UUID juniorLedger;
    private static UUID mandateId;
    private static String firstDebitRef;

    static LocalDate day(int d) {
        return LocalDate.of(2131, 6, d);
    }

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
        onlineQueries = inject(new OnlineFeeQueries(), OnlineFeeQueries.class);
        DataAccessService dataAccess = mock(DataAccessService.class);
        when(dataAccess.canAccessStudent(any(), any(), any())).thenReturn(true);
        when(dataAccess.hasFullScope(any(), any())).thenReturn(true);

        FeeLedgerRepository ledgerRepo = repos.getRepository(FeeLedgerRepository.class);
        FeeLedgerLineRepository lineRepo = repos.getRepository(FeeLedgerLineRepository.class);
        FeeHeadRepository headRepo = repos.getRepository(FeeHeadRepository.class);
        FeeContext ctx = new FeeContext(repos.getRepository(AcademicYearRepository.class), repos.getRepository(FeeSettingsRepository.class),
                ledgerRepo, queries, dataAccess, clock);
        FeeConcessionService concessions = new FeeConcessionService(ctx, queries, repos.getRepository(FeeConcessionRequestRepository.class),
                repos.getRepository(FeeConcessionTypeRepository.class), ledgerRepo, lineRepo);
        FeeInvoiceService invoices = new FeeInvoiceService(ctx, queries, ledgerRepo, lineRepo, repos.getRepository(FeeStructureRepository.class),
                repos.getRepository(FeeStructureItemRepository.class), headRepo, repos.getRepository(FeeInstallmentRepository.class), concessions);
        payments = new FeePaymentService(ctx, queries, ledgerRepo, lineRepo, repos.getRepository(FeePaymentRepository.class),
                repos.getRepository(FeePaymentAllocationRepository.class), headRepo, notifier);

        SecretCipher cipher = new SecretCipher(Base64.getEncoder().encodeToString(new byte[32]));
        config = new OnlineFeeConfigService(repos.getRepository(FeeAutoDebitSettingsRepository.class), repos.getRepository(FeeGatewayAccountRepository.class),
                cipher, ctx, "https://api.test");
        when(cashfree.provider()).thenReturn(GatewayProvider.CASHFREE);
        when(cashfree.presentationLeadDays()).thenReturn(1);
        FeeGatewayRegistry registry = new FeeGatewayRegistry(List.of(cashfree));
        PayerAccess access = new PayerAccess(ctx, mock(OwnRecordsResolver.class), onlineQueries);
        FeeMandateRepository mandateRepo = repos.getRepository(FeeMandateRepository.class);
        debitRepo = repos.getRepository(FeeDebitRepository.class);
        FeeDebitRunRepository runRepo = repos.getRepository(FeeDebitRunRepository.class);
        FeeGatewayPaymentRepository gpRepo = repos.getRepository(FeeGatewayPaymentRepository.class);
        mandates = new MandateService(mandateRepo, debitRepo, onlineQueries, config, registry, access, ctx, notifier);
        // Like @Transactional in the app: each step call is its own transaction (joins one already open)
        ProxyFactory pf = new ProxyFactory(new AutoDebitSteps(debitRepo, mandateRepo, runRepo, gpRepo, onlineQueries, config, payments, invoices, lineRepo,
                headRepo, ctx, notifier, registry));
        pf.setProxyTargetClass(true);
        pf.addAdvice((MethodInterceptor) inv -> tx(() -> {
            try {
                return inv.proceed();
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        }));
        steps = (AutoDebitSteps) pf.getProxy();
        autoDebit = new AutoDebitService(steps, config, registry, debitRepo, runRepo, mandateRepo, onlineQueries, ctx);
        checkouts = new CheckoutService(repos.getRepository(FeeCheckoutRepository.class), gpRepo, onlineQueries, config, registry, access, ctx, payments,
                lineRepo, notifier);

        seed();
        office();
        tx(() -> config.saveGateway(GatewayProvider.CASHFREE, new SaveGatewayRequest(GatewayEnvironment.SANDBOX, "cf_app", "cf_secret", null, true)));
        tx(() -> config.update(new UpdateSettingsRequest(true, GatewayProvider.CASHFREE, GatewayProvider.CASHFREE, 10, List.of(3, 1), 7,
                "https://app.test/autopay", 1, 2, 3, 50_000L, new BigDecimal("18"), null, null, null, null)));
    }

    @BeforeEach
    void quiet() {
        clearInvocations(notifier);
        when(notifier.send(any(), anyCollection(), any())).thenAnswer(inv -> {
            Collection<FeeNotifier.Message> msgs = inv.getArgument(1);
            return new FeeNotifier.Outcome(msgs.size(), Set.of(), Map.of(), Set.of(NotificationChannel.SMS), true);
        });
        office();
    }

    @AfterAll
    static void close() {
        SecurityContextHolder.clearContext();
        if (em != null && em.isOpen()) em.close();
        if (emf != null && emf.isOpen()) emf.close();
    }

    @Test
    @Order(1)
    @DisplayName("a mandate for one parent covers all their children; a second one for them is refused")
    void mandate() {
        when(cashfree.createMandate(any(), any())).thenAnswer(inv -> new MandateSession("cf_sub_1", null, "sess_1", null, MandateStatus.PENDING));
        MandateView m = tx(() -> mandates.create(new CreateMandateRequest(seniorGuardian, null, MandateMethod.ENACH, null, null, null)));
        mandateId = m.id();
        assertThat(m.status()).isEqualTo(MandateStatus.PENDING);
        assertThat(m.sessionId()).isEqualTo("sess_1");
        assertThat(m.maxAmountPaise()).isEqualTo(10_000_000);
        assertThat(m.students()).extracting(b -> b.studentId()).containsExactlyInAnyOrder(senior, junior);
        assertThat(m.expiresOn()).isEqualTo(day(7).plusYears(10));

        assertThatThrownBy(() -> tx(() -> mandates.create(new CreateMandateRequest(seniorGuardian, null, MandateMethod.UPI, null, null, null))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("already has a pending or active mandate");

        String applied = tx(() -> mandates.apply(GatewayProvider.CASHFREE, new MandateUpdate(m.reference(), "cf_sub_1", null, null, MandateStatus.ACTIVE, "ACTIVE")));
        assertThat(applied).startsWith("APPLIED").endsWith("PENDING -> ACTIVE");
        assertThat(tx(() -> mandates.get(mandateId)).activatedAt()).isEqualTo(clock.instant());
    }

    @Test
    @Order(2)
    @DisplayName("the 10th: higher class debited first, the lower class a day later; planning twice changes nothing")
    void plan() {
        assertThat(tx(() -> steps.plan(day(1), false))).isEqualTo(2);
        assertThat(tx(() -> steps.plan(day(1), false))).isZero();
        List<FeeDebit> planned = tx(() -> debitRepo.findByMandateIdAndCycleMonth(mandateId, day(1)));
        FeeDebit s = planned.stream().filter(d -> d.getStudentId().equals(senior)).findFirst().orElseThrow();
        FeeDebit j = planned.stream().filter(d -> d.getStudentId().equals(junior)).findFirst().orElseThrow();
        assertThat(s.getSiblingRank()).isZero();
        assertThat(s.getScheduledOn()).isEqualTo(day(10));
        assertThat(s.getAmountPaise()).isEqualTo(300_000);
        assertThat(j.getSiblingRank()).isEqualTo((short) 1);
        assertThat(j.getScheduledOn()).isEqualTo(day(11));
        assertThat(j.getAmountPaise()).isEqualTo(200_000);
        firstDebitRef = s.getReference();
    }

    @Test
    @Order(3)
    @DisplayName("before the debit day: the payer gets the pre-debit notice; families without a mandate get one invitation")
    void noticesAndInvites() {
        assertThat(tx(() -> steps.notices())).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<FeeNotifier.Message>> sent = ArgumentCaptor.forClass(Collection.class);
        verify(notifier).send(eq(NotificationEvent.FEE_AUTO_DEBIT_NOTICE), sent.capture(), any());
        FeeNotifier.Message notice = sent.getValue().iterator().next();
        assertThat(notice.studentId()).isEqualTo(senior);
        assertThat(notice.values()).containsEntry("amount", "₹3,000.00").containsEntry("debit_date", "10 Jun 2131");
        assertThat(notice.onlyGuardianIds()).containsExactly(seniorGuardian);

        assertThat(tx(() -> steps.invite(day(10)))).isEqualTo(1);
        verify(notifier).send(eq(NotificationEvent.FEE_MANDATE_INVITE), sent.capture(), any());
        FeeNotifier.Message invite = sent.getValue().iterator().next();
        assertThat(invite.studentId()).isEqualTo(solo);
        assertThat(invite.values()).containsEntry("setup_link", "https://app.test/autopay").containsEntry("amount", "₹1,000.00");
    }

    @Test
    @Order(4)
    @DisplayName("the day before (Cashfree lead): the higher class's debit is sent and its due held as pending")
    void submit() {
        clock.set(day(9));
        when(cashfree.charge(any(), any())).thenReturn(new ChargeSubmitted("cfp_1", null));
        RunView r = autoDebit.run("MANUAL", null);
        assertThat(r.status()).isEqualTo("COMPLETED");
        assertThat(r.submitted()).isEqualTo(1);
        FeeDebit d = tx(() -> debitRepo.findByReference(firstDebitRef)).orElseThrow();
        assertThat(d.getStatus()).isEqualTo(DebitStatus.SUBMITTED);
        assertThat(d.getGatewayPaymentId()).isEqualTo("cfp_1");
        assertThat(pending(seniorLedger, day(10))).isEqualTo(300_000);
        assertThat(paymentStatus(d.getFeePaymentId())).isEqualTo("PENDING_CLEARANCE");
    }

    @Test
    @Order(5)
    @DisplayName("a bounce releases the hold, adds the NACH bounce charge and its 18% GST, and schedules a retry that includes them")
    void bounce() {
        String out = tx(() -> steps.outcome(GatewayProvider.CASHFREE, new PaymentReport(firstDebitRef, null, "cfp_1", 300_000, "NACH", false, "Insufficient funds")));
        assertThat(out).startsWith("APPLIED").contains("retry on 2131-06-12");
        FeeDebit d = tx(() -> debitRepo.findByReference(firstDebitRef)).orElseThrow();
        assertThat(d.getStatus()).isEqualTo(DebitStatus.FAILED);
        assertThat(d.getBounceChargePaise()).isEqualTo(50_000);
        assertThat(d.getBounceGstPaise()).isEqualTo(9_000);
        assertThat(pending(seniorLedger, day(10))).isZero();
        assertThat(paymentStatus(d.getFeePaymentId())).isEqualTo("BOUNCED");
        assertThat(jdbcTemplate.queryForList("SELECT amount_paise FROM fee_ledger_line WHERE ledger_id = ? AND source = 'CHARGE' ORDER BY amount_paise DESC",
                Long.class, seniorLedger)).containsExactly(300_000L, 300_000L, 50_000L, 9_000L);

        FeeDebit retry = tx(() -> debitRepo.findByMandateIdAndCycleMonth(mandateId, day(1))).stream().filter(x -> x.getAttemptNo() == 2).findFirst().orElseThrow();
        assertThat(retry.getScheduledOn()).isEqualTo(day(12));
        assertThat(retry.getAmountPaise()).isEqualTo(359_000);
        verify(notifier).send(eq(NotificationEvent.FEE_AUTO_DEBIT_FAILED), anyCollection(), any());

        String again = tx(() -> steps.outcome(GatewayProvider.CASHFREE, new PaymentReport(firstDebitRef, null, "cfp_1", 300_000, "NACH", false, "x")));
        assertThat(again).startsWith("IGNORED");
    }

    @Test
    @Order(6)
    @DisplayName("a parent who pays before the day is not debited")
    void paidBefore() {
        clock.set(day(10));
        tx(() -> payments.collect(new CollectPaymentRequest(junior, year, 200_000L, PaymentMode.CASH, null, null, null, null, null, null, null)));
        RunView r = autoDebit.run("MANUAL", null);
        assertThat(r.skipped()).isEqualTo(1);
        assertThat(r.submitted()).isZero();
        FeeDebit j = tx(() -> debitRepo.findByMandateIdAndCycleMonth(mandateId, day(1))).stream().filter(x -> x.getStudentId().equals(junior)).findFirst()
                .orElseThrow();
        assertThat(j.getStatus()).isEqualTo(DebitStatus.SKIPPED);
    }

    @Test
    @Order(7)
    @DisplayName("the retry collects fee and bounce charges together and sends the receipt")
    void retrySucceeds() {
        clock.set(day(11));
        when(cashfree.charge(any(), any())).thenReturn(new ChargeSubmitted("cfp_2", null));
        assertThat(autoDebit.run("MANUAL", null).submitted()).isEqualTo(1);
        FeeDebit retry = tx(() -> debitRepo.findFirstByGatewayPaymentId("cfp_2")).orElseThrow();
        assertThat(retry.getAmountPaise()).isEqualTo(359_000);
        String out = tx(() -> steps.outcome(GatewayProvider.CASHFREE, new PaymentReport(retry.getReference(), null, "cfp_2", 359_000, "NACH", true, null)));
        assertThat(out).startsWith("APPLIED").endsWith("succeeded");
        assertThat(paymentStatus(retry.getFeePaymentId())).isEqualTo("SUCCESS");
        assertThat(jdbcTemplate.queryForObject("SELECT mode FROM fee_payment WHERE id = ?", String.class, retry.getFeePaymentId())).isEqualTo("NACH");
        assertThat(jdbcTemplate.queryForObject("SELECT sum(paid_paise) FROM fee_ledger_line WHERE ledger_id = ? AND due_date <= ?", Long.class,
                seniorLedger, day(11))).isEqualTo(359_000);
        verify(notifier, atLeastOnce()).send(eq(NotificationEvent.FEE_PAYMENT_RECEIVED), anyCollection(), any());
    }

    @Test
    @Order(8)
    @DisplayName("a part payment on a link for both children goes to the higher class first; the rest completes it")
    void siblingsLink() {
        when(cashfree.createCheckout(any(), any())).thenAnswer(inv -> {
            CheckoutSetup s = inv.getArgument(1);
            return new CheckoutSession(s.reference(), "https://pay.test/" + s.reference(), "cf_link_1");
        });
        CheckoutView c = tx(() -> checkouts.create(new CreateCheckoutRequest(List.of(junior, senior), CheckoutKind.LINK, null, true, null, seniorGuardian,
                false, null, null)));
        assertThat(c.amountPaise()).isEqualTo(500_000);
        assertThat(c.children()).extracting(ch -> ch.studentId()).containsExactly(senior, junior);

        String first = tx(() -> checkouts.applyLink(GatewayProvider.CASHFREE, new LinkReport(c.reference(), c.reference(), c.reference() + ":350000", 350_000,
                true, CheckoutStatus.PARTIALLY_PAID, "LINK")));
        assertThat(first).startsWith("APPLIED");
        CheckoutView part = tx(() -> checkouts.get(c.id()));
        assertThat(part.status()).isEqualTo(CheckoutStatus.PARTIALLY_PAID);
        assertThat(part.children()).extracting(ch -> ch.paidPaise()).containsExactly(300_000L, 50_000L);
        assertThat(dueAll(seniorLedger)).isZero();
        assertThat(dueAll(juniorLedger)).isEqualTo(150_000);

        tx(() -> checkouts.applyLink(GatewayProvider.CASHFREE, new LinkReport(c.reference(), c.reference(), c.reference() + ":500000", 500_000, true,
                CheckoutStatus.PAID, "LINK")));
        CheckoutView paid = tx(() -> checkouts.get(c.id()));
        assertThat(paid.status()).isEqualTo(CheckoutStatus.PAID);
        assertThat(paid.unallocatedPaise()).isZero();
        assertThat(dueAll(juniorLedger)).isZero();
        assertThat(tx(() -> checkouts.applyLink(GatewayProvider.CASHFREE, new LinkReport(c.reference(), c.reference(), c.reference() + ":500000", 500_000,
                true, CheckoutStatus.PAID, "LINK")))).startsWith("IGNORED");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM fee_payment WHERE mode = 'ONLINE' AND ledger_id IN (?, ?)", Long.class,
                seniorLedger, juniorLedger)).isEqualTo(3);
    }

    @Test
    @Order(9)
    @DisplayName("cancelling the mandate frees the children for a new one")
    void cancel() {
        MandateView m = tx(() -> mandates.cancel(mandateId, "Parent asked"));
        assertThat(m.status()).isEqualTo(MandateStatus.CANCELLED);
        verify(cashfree).cancelMandate(any(), any());
        assertThat(tx(() -> onlineQueries.covered(List.of(senior, junior)))).isEmpty();
        assertThatThrownBy(() -> tx(() -> mandates.cancel(mandateId, "again"))).isInstanceOf(BusinessException.class);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────

    private static long pending(UUID ledger, LocalDate by) {
        return jdbcTemplate.queryForObject("SELECT COALESCE(sum(pending_paise), 0) FROM fee_ledger_line WHERE ledger_id = ? AND due_date <= ?", Long.class,
                ledger, by);
    }

    private static long dueAll(UUID ledger) {
        return jdbcTemplate.queryForObject("SELECT COALESCE(sum(amount_paise - concession_paise - paid_paise - pending_paise), 0) FROM fee_ledger_line "
                + "WHERE ledger_id = ? AND status = 'ACTIVE'", Long.class, ledger);
    }

    private static String paymentStatus(UUID paymentId) {
        return jdbcTemplate.queryForObject("SELECT status FROM fee_payment WHERE id = ?", String.class, paymentId);
    }

    private static <T> T tx(Supplier<T> work) {
        if (em.getTransaction().isActive()) {
            return work.get();
        }
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

    private static void office() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(OFFICE, null,
                List.of(new SimpleGrantedAuthority("FEE_MANAGE"), new SimpleGrantedAuthority("FEE_READ"))));
    }

    private static <T> T inject(T target, Class<T> type) throws Exception {
        Field f = type.getDeclaredField("em");
        f.setAccessible(true);
        f.set(target, em);
        return target;
    }

    private static void seed() {
        year = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO academic_year (id, code, name, start_date, end_date, is_current, status) VALUES (?, ?, ?, ?, ?, false, 'ACTIVE')",
                year, "AY-O-" + S, "Online " + S, LocalDate.of(2131, 4, 1), LocalDate.of(2132, 3, 31));
        UUID nine = UUID.randomUUID();
        UUID four = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO academic_class (id, code, name, sort_order) VALUES (?, ?, ?, 9), (?, ?, ?, 4)",
                nine, "OC9-" + S, "Online 9 " + S, four, "OC4-" + S, "Online 4 " + S);
        UUID head = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO fee_head (id, code, name, frequency, is_optional) VALUES (?, ?, ?, 'MONTHLY', false)", head, "OT-" + S, "Online tuition " + S);

        UUID parent = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tenant_user (id, user_code, email, user_type) VALUES (?, ?, ?, 'STUDENT')", parent, "TU-op" + S, "op" + S + "@x.test");
        senior = student("os", nine);
        junior = student("oj", four);
        solo = student("ox", four);
        seniorGuardian = guardian(senior, parent, "9811111111", "Parent O");
        guardian(junior, parent, "9811111111", "Parent O");
        guardian(solo, null, "9822222222", "Parent X");

        seniorLedger = ledger(senior, nine, head, Map.of(day(10), 300_000L, day(10).plusMonths(1), 300_000L));
        juniorLedger = ledger(junior, four, head, Map.of(day(5), 200_000L, day(10).plusMonths(3), 200_000L));
        ledger(solo, four, head, Map.of(day(1), 100_000L));
    }

    private static UUID student(String tag, UUID cls) {
        UUID tu = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tenant_user (id, user_code, email, user_type) VALUES (?, ?, ?, 'STUDENT')", tu, "TU-" + tag + S, tag + S + "@x.test");
        jdbcTemplate.update("INSERT INTO tenant_user_profile (user_id, first_name) VALUES (?, ?)", tu, "Kid " + tag);
        UUID st = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO student (id, student_code, tenant_user_id, admission_number, admission_date) VALUES (?, ?, ?, ?, ?)",
                st, "STU-" + tag + S, tu, "ADM-" + tag + S, LocalDate.of(2131, 4, 1));
        jdbcTemplate.update("INSERT INTO student_academic_enrollment (enrollment_code, student_id, academic_year_id, academic_class_id, enrollment_status) "
                + "VALUES (?, ?, ?, ?, 'ACTIVE')", "E-" + UUID.randomUUID(), st, year, cls);
        return st;
    }

    private static UUID guardian(UUID student, UUID login, String phone, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO student_guardian (id, student_id, guardian_code, relationship_type, guardian_name, phone, status, guardian_user_id, "
                + "is_primary) VALUES (?, ?, ?, 'MOTHER', ?, ?, 'ACTIVE', ?, true)", id, student, "G-" + UUID.randomUUID().toString().substring(0, 8), name,
                phone, login);
        return id;
    }

    private static UUID ledger(UUID student, UUID cls, UUID head, Map<LocalDate, Long> lines) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO fee_ledger (id, invoice_number, student_id, academic_year_id, academic_class_id) VALUES (?, ?, ?, ?, ?)",
                id, "INV-" + UUID.randomUUID().toString().substring(0, 8), student, year, cls);
        lines.forEach((due, amount) -> jdbcTemplate.update("INSERT INTO fee_ledger_line (ledger_id, fee_head_id, label, source, amount_paise, due_date, reason) "
                + "VALUES (?, ?, ?, 'CHARGE', ?, ?, 'seed')", id, head, "Tuition " + due, amount, due));
        return id;
    }
}
