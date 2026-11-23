package com.vidya.tenantdata.fee.online.service;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.SecurityUtils;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.fee.entity.FeeLedgerLine;
import com.vidya.tenantdata.fee.entity.FeePayment;
import com.vidya.tenantdata.fee.enums.PaymentMode;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.CheckoutChildView;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.CheckoutView;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.CreateCheckoutRequest;
import com.vidya.tenantdata.fee.online.entity.FeeAutoDebitSettings;
import com.vidya.tenantdata.fee.online.entity.FeeCheckout;
import com.vidya.tenantdata.fee.online.entity.FeeGatewayPayment;
import com.vidya.tenantdata.fee.online.enums.CheckoutKind;
import com.vidya.tenantdata.fee.online.enums.CheckoutStatus;
import com.vidya.tenantdata.fee.online.enums.GatewayProvider;
import com.vidya.tenantdata.fee.online.gateway.FeeGatewayRegistry;
import com.vidya.tenantdata.fee.online.gateway.GatewayException;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.CheckoutSession;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.CheckoutSetup;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.Credentials;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.LinkReport;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.Payer;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.PaymentReport;
import com.vidya.tenantdata.fee.online.repository.FeeCheckoutRepository;
import com.vidya.tenantdata.fee.online.repository.FeeGatewayPaymentRepository;
import com.vidya.tenantdata.fee.online.repository.OnlineFeeQueries;
import com.vidya.tenantdata.fee.online.repository.OnlineFeeQueries.CheckoutChild;
import com.vidya.tenantdata.fee.online.repository.OnlineFeeQueries.Child;
import com.vidya.tenantdata.fee.online.repository.OnlineFeeQueries.GuardianRow;
import com.vidya.tenantdata.fee.online.repository.OnlineFeeQueries.StudentBrief;
import com.vidya.tenantdata.fee.repository.FeeLedgerLineRepository;
import com.vidya.tenantdata.fee.service.FeeContext;
import com.vidya.tenantdata.fee.service.FeeMath;
import com.vidya.tenantdata.fee.service.FeeNotifier;
import com.vidya.tenantdata.fee.service.FeePaymentService;
import com.vidya.tenantdata.operations.enums.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Payment links and checkout orders for one or more children of one payer, through the school's gateway. Money that arrives is split
 * across the children <b>higher class first</b> (each child's ledger oldest dues first); a child not fully covered stays part paid,
 * and the link / order is PARTIALLY_PAID until the whole amount is in. Like infi_payment's link handlers, each gateway payment is
 * applied exactly once.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "tenantTransactionManager")
public class CheckoutService {

    /** Default smallest part payment on a link (₹100), or the whole amount when less. */
    static final long DEFAULT_MIN_PARTIAL_PAISE = 10_000;
    private static final Set<CheckoutStatus> OPEN = EnumSet.of(CheckoutStatus.CREATED, CheckoutStatus.PARTIALLY_PAID);

    private final FeeCheckoutRepository checkouts;
    private final FeeGatewayPaymentRepository gatewayPayments;
    private final OnlineFeeQueries queries;
    private final OnlineFeeConfigService config;
    private final FeeGatewayRegistry gateways;
    private final PayerAccess access;
    private final FeeContext ctx;
    private final FeePaymentService payments;
    private final FeeLedgerLineRepository lines;
    private final FeeNotifier notifier;

    /** One child's share of a payment. */
    public record Share(UUID studentId, UUID ledgerId, long duePaise) {
    }

    // ── Create ─────────────────────────────────────────────────────────────────────────

    public CheckoutView create(CreateCheckoutRequest in) {
        if (!access.office() && !SecurityUtils.hasAuthority("FEE_PAY")) {
            throw new AccessDeniedException("You cannot create fee payments");
        }
        List<UUID> studentIds = List.copyOf(new LinkedHashSet<>(in.studentIds()));
        access.checkStudents(studentIds);
        GatewayProvider provider = config.paymentProvider();
        Credentials cred = config.credentials(provider);
        FeeAutoDebitSettings s = config.settings();
        AcademicYear year = ctx.year(queries.yearOn(ctx.today()).orElse(null));
        List<Child> children = queries.children(year.getId(), studentIds);
        if (children.size() != studentIds.size()) {
            throw FeeContext.error("FEE_LEDGER_NOT_FOUND", "Every student must be billed for " + year.getName() + " first");
        }
        Map<UUID, Long> due = new LinkedHashMap<>();
        children.forEach(c -> due.put(c.studentId(), due(c.ledgerId())));
        long total = due.values().stream().mapToLong(Long::longValue).sum();
        if (total <= 0) {
            throw FeeContext.error("FEE_NOTHING_DUE", "Nothing is due for " + children.stream().map(Child::studentName).collect(Collectors.joining(", ")));
        }
        long amount = in.amountPaise() != null ? in.amountPaise() : total;
        if (amount > total) {
            throw FeeContext.error("FEE_OVERPAYMENT", "The amount " + FeeMath.rupees(amount) + " is more than what is due (" + FeeMath.rupees(total) + ")");
        }
        boolean partial = in.kind() == CheckoutKind.LINK && Boolean.TRUE.equals(in.allowPartial());
        long minPartial = !partial ? amount : Math.min(amount, in.minPartialPaise() != null ? in.minPartialPaise() : DEFAULT_MIN_PARTIAL_PAISE);
        GuardianRow payer = access.payer(in.guardianId(), studentIds);
        if (payer.phone() == null || payer.phone().isBlank()) {
            throw FeeContext.error("FEE_PAYER_PHONE_REQUIRED", "The guardian has no phone number; the gateway needs one");
        }
        String names = children.stream().map(Child::studentName).collect(Collectors.joining(", "));

        FeeCheckout c = new FeeCheckout();
        c.setReference(OnlineSupport.reference(in.kind() == CheckoutKind.LINK ? "PL" : "OR"));
        c.setKind(in.kind());
        c.setProvider(provider);
        c.setAmountPaise(amount);
        c.setAllowPartial(partial);
        c.setPayerName(payer.name());
        c.setPayerEmail(payer.email() == null || payer.email().isBlank() ? null : payer.email().trim());
        c.setPayerPhone(payer.phone().trim());
        c.setPurpose(AutoDebitSteps.cut("School fees: " + names, 200));
        c.setExpiresAt(ctx.clock().instant().plus(in.kind() == CheckoutKind.LINK ? Duration.ofDays(s.getLinkExpiryDays()) : Duration.ofDays(1)));
        c.setCreatedBy(ctx.actorId());
        checkouts.save(c);
        int rank = 0;
        for (Child ch : children) {
            queries.addCheckoutStudent(c.getId(), new CheckoutChild(ch.studentId(), ch.ledgerId(), rank++, due.get(ch.studentId()), 0));
        }
        CheckoutSession session;
        try {
            session = gateways.get(provider).createCheckout(cred, new CheckoutSetup(c.getReference(), in.kind(),
                    new Payer(c.getPayerName(), c.getPayerEmail(), c.getPayerPhone()), amount, partial, minPartial, c.getExpiresAt(), c.getPurpose(),
                    in.returnUrl() != null ? in.returnUrl() : s.getReturnUrl()));
        } catch (GatewayException e) {
            throw OnlineSupport.gatewayError(e);
        }
        c.setGatewayId(session.gatewayId());
        c.setUrl(session.url());
        c.setSessionId(session.sessionId());
        checkouts.save(c);
        if (in.kind() == CheckoutKind.LINK && c.getUrl() != null && !Boolean.FALSE.equals(in.notifyPayer())) {
            Set<UUID> payerRows = access.payerRows(payer, studentIds).stream().map(GuardianRow::guardianId).collect(Collectors.toSet());
            notifier.send(NotificationEvent.FEE_PAYMENT_LINK, List.of(new FeeNotifier.Message(children.getFirst().studentId(),
                    Map.of("student_name", names, "amount", FeeMath.rupees(amount), "link", c.getUrl(),
                            "expiry_date", FeeMath.date(LocalDate.ofInstant(c.getExpiresAt(), ctx.clock().getZone()))),
                    "FEE_CHECKOUT", c.getId(), "FEE_PAYMENT_LINK:" + c.getId(), payerRows)), in.notifyChannels());
        }
        return view(c);
    }

    // ── Money in ───────────────────────────────────────────────────────────────────────

    /** A payment-link webhook: money paid (Cashfree reports the running total, Razorpay each payment) or the link closed. */
    public String applyLink(GatewayProvider provider, LinkReport r) {
        FeeCheckout c = find(provider, r.reference(), r.linkId(), CheckoutKind.LINK);
        if (c == null) {
            return "IGNORED: no payment link " + (r.reference() != null ? r.reference() : r.linkId());
        }
        c = lock(c.getId());
        if (r.status() == CheckoutStatus.EXPIRED || r.status() == CheckoutStatus.CANCELLED) {
            if (OPEN.contains(c.getStatus()) && c.getPaidPaise() == 0) {
                c.setStatus(r.status());
                checkouts.save(c);
            }
            return "APPLIED: link " + c.getReference() + " " + r.status();
        }
        long amount = r.cumulative() ? r.amountPaise() - c.getPaidPaise() : r.amountPaise();
        if (amount <= 0) {
            return "IGNORED: nothing new paid on " + c.getReference();
        }
        return applyMoney(provider, c, r.gatewayPaymentId(), amount, r.method());
    }

    /** A captured payment of a checkout order. Returns an IGNORED note when it is not one of ours. */
    public String applyOrder(GatewayProvider provider, PaymentReport r) {
        if (!r.success()) {
            return "IGNORED: failed attempt on order " + r.gatewayOrderId();
        }
        FeeCheckout c = find(provider, r.reference(), r.gatewayOrderId(), CheckoutKind.ORDER);
        if (c == null) {
            return "IGNORED: no debit or order for " + (r.reference() != null ? r.reference() : r.gatewayOrderId());
        }
        return applyMoney(provider, lock(c.getId()), r.gatewayPaymentId(), r.amountPaise(), r.method());
    }

    private String applyMoney(GatewayProvider provider, FeeCheckout c, String gatewayPaymentId, long amount, String method) {
        if (gatewayPaymentId != null && gatewayPayments.existsByProviderAndGatewayPaymentId(provider, gatewayPaymentId)) {
            return "IGNORED: payment " + gatewayPaymentId + " already applied";
        }
        List<CheckoutChild> children = queries.checkoutChildren(c.getId());
        List<Share> shares = children.stream().map(ch -> new Share(ch.studentId(), ch.ledgerId(), due(ch.ledgerId()))).toList();
        Map<UUID, Long> split = split(amount, shares);
        long allocated = 0;
        List<FeePayment> received = new ArrayList<>();
        for (Share sh : shares) {
            long portion = split.getOrDefault(sh.studentId(), 0L);
            if (portion <= 0) {
                continue;
            }
            FeePayment p = payments.recordGatewayPayment(sh.ledgerId(), portion, PaymentMode.ONLINE, false, null, gatewayPaymentId,
                    "Online payment " + c.getReference() + (method == null ? "" : " (" + method + ")"), "Online (" + provider + ")");
            if (p != null) {
                allocated += p.getAmountPaise();
                queries.addCheckoutPaid(c.getId(), sh.studentId(), p.getAmountPaise());
                received.add(p);
            }
        }
        FeeGatewayPayment gp = new FeeGatewayPayment();
        gp.setProvider(provider);
        gp.setGatewayPaymentId(gatewayPaymentId != null ? gatewayPaymentId : c.getReference() + ":" + (c.getPaidPaise() + amount));
        gp.setCheckoutId(c.getId());
        gp.setAmountPaise(amount);
        gp.setAllocatedPaise(allocated);
        gp.setMethod(AutoDebitSteps.cut(method, 40));
        gatewayPayments.save(gp);
        c.setPaidPaise(c.getPaidPaise() + amount);
        c.setUnallocatedPaise(c.getUnallocatedPaise() + amount - allocated);
        c.setStatus(c.getPaidPaise() >= c.getAmountPaise() ? CheckoutStatus.PAID : CheckoutStatus.PARTIALLY_PAID);
        checkouts.save(c);
        received.forEach(payments::sendGatewayReceipt);
        if (allocated < amount) {
            log.warn("Checkout {}: {} paise received but only {} were due; refund the rest", c.getReference(), amount, allocated);
        }
        return "APPLIED: " + c.getReference() + " +" + amount + " (" + received.size() + " receipt(s))" + (allocated < amount ? ", unallocated "
                + (amount - allocated) : "");
    }

    /**
     * Higher class first: each child (in the given order) gets as much of the money as it owes before the next child gets any.
     * What is left after every child is paid off is not in the map.
     */
    public static Map<UUID, Long> split(long amount, List<Share> higherClassFirst) {
        Map<UUID, Long> out = new LinkedHashMap<>();
        long left = amount;
        for (Share s : higherClassFirst) {
            long take = Math.max(0, Math.min(left, s.duePaise()));
            out.put(s.studentId(), take);
            left -= take;
        }
        return out;
    }

    // ── Read / cancel ──────────────────────────────────────────────────────────────────

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public CheckoutView get(UUID id) {
        FeeCheckout c = checkouts.findById(id).orElseThrow(() -> new ResourceNotFoundException("FeeCheckout", id));
        access.checkStudents(queries.checkoutChildren(c.getId()).stream().map(CheckoutChild::studentId).toList());
        return view(c);
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<CheckoutView> forStudent(UUID studentId) {
        return queries.checkoutsOfStudent(studentId).stream().map(checkouts::findById).flatMap(java.util.Optional::stream).map(this::view).toList();
    }

    public CheckoutView cancel(UUID id) {
        FeeCheckout c = lock(id);
        access.checkStudents(queries.checkoutChildren(c.getId()).stream().map(CheckoutChild::studentId).toList());
        if (!access.office() && (c.getCreatedBy() == null || !c.getCreatedBy().equals(ctx.actorId()))) {
            throw new AccessDeniedException("Only the school office or whoever created it can cancel this payment");
        }
        if (!OPEN.contains(c.getStatus())) {
            throw FeeContext.error("FEE_CHECKOUT_STATE", "This payment is already " + c.getStatus());
        }
        try {
            gateways.get(c.getProvider()).cancelCheckout(config.credentials(c.getProvider()), c.getKind(), c.getReference(), c.getGatewayId());
        } catch (GatewayException e) {
            throw OnlineSupport.gatewayError(e);
        }
        c.setStatus(c.getPaidPaise() > 0 ? CheckoutStatus.PARTIALLY_PAID : CheckoutStatus.CANCELLED);
        c.setExpiresAt(ctx.clock().instant());
        return view(checkouts.save(c));
    }

    // ── helpers ────────────────────────────────────────────────────────────────────────

    CheckoutView view(FeeCheckout c) {
        List<CheckoutChild> children = queries.checkoutChildren(c.getId());
        Map<UUID, StudentBrief> briefs = queries.briefs(children.stream().map(CheckoutChild::studentId).toList());
        List<CheckoutChildView> views = children.stream().map(ch -> {
            StudentBrief b = briefs.get(ch.studentId());
            return new CheckoutChildView(ch.studentId(), b == null ? null : b.studentName(), b == null ? null : b.className(), ch.rank(), ch.duePaise(),
                    ch.paidPaise());
        }).toList();
        return new CheckoutView(c.getId(), c.getReference(), c.getKind(), c.getProvider(), c.getStatus(), c.getAmountPaise(), c.getPaidPaise(),
                c.getUnallocatedPaise(), c.isAllowPartial(), c.getPayerName(), c.getPurpose(), c.getUrl(), c.getSessionId(),
                c.getProvider() == GatewayProvider.RAZORPAY ? config.keyId(c.getProvider()) : null, c.getExpiresAt(), views, c.getCreatedAt());
    }

    private FeeCheckout find(GatewayProvider provider, String reference, String gatewayId, CheckoutKind kind) {
        FeeCheckout c = reference != null ? checkouts.findByReference(reference).orElse(null) : null;
        if (c == null && gatewayId != null) {
            c = checkouts.findFirstByProviderAndGatewayId(provider, gatewayId).orElse(null);
        }
        return c != null && c.getProvider() == provider && c.getKind() == kind ? c : null;
    }

    private long due(UUID ledgerId) {
        return lines.findByLedgerIdOrderByDueDateAscInstallmentNoAscCreatedAtAsc(ledgerId).stream().mapToLong(FeeLedgerLine::duePaise).sum();
    }

    private FeeCheckout lock(UUID id) {
        return checkouts.lock(id).orElseThrow(() -> new ResourceNotFoundException("FeeCheckout", id));
    }
}
