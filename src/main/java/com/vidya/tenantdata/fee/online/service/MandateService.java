package com.vidya.tenantdata.fee.online.service;

import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.SecurityUtils;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.CreateMandateRequest;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.MandateView;
import com.vidya.tenantdata.fee.online.entity.FeeAutoDebitSettings;
import com.vidya.tenantdata.fee.online.entity.FeeDebit;
import com.vidya.tenantdata.fee.online.entity.FeeMandate;
import com.vidya.tenantdata.fee.online.enums.DebitStatus;
import com.vidya.tenantdata.fee.online.enums.GatewayProvider;
import com.vidya.tenantdata.fee.online.enums.MandateMethod;
import com.vidya.tenantdata.fee.online.enums.MandateStatus;
import com.vidya.tenantdata.fee.online.gateway.FeeGateway;
import com.vidya.tenantdata.fee.online.gateway.FeeGatewayRegistry;
import com.vidya.tenantdata.fee.online.gateway.GatewayException;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.Credentials;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.MandateRef;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.MandateSession;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.MandateSetup;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.MandateState;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.MandateUpdate;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.Payer;
import com.vidya.tenantdata.fee.online.repository.FeeDebitRepository;
import com.vidya.tenantdata.fee.online.repository.FeeMandateRepository;
import com.vidya.tenantdata.fee.online.repository.OnlineFeeQueries;
import com.vidya.tenantdata.fee.online.repository.OnlineFeeQueries.GuardianRow;
import com.vidya.tenantdata.fee.online.repository.OnlineFeeQueries.StudentBrief;
import com.vidya.tenantdata.fee.service.FeeContext;
import com.vidya.tenantdata.fee.service.FeeMath;
import com.vidya.tenantdata.fee.service.FeeNotifier;
import com.vidya.tenantdata.operations.enums.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Auto-debit mandates (as infi_payment's CashfreeMandateBusiness / RazorpayMandateBusiness): one payer, one mandate, all the children
 * it covers. A child is on at most one pending / active mandate. Status comes from webhooks, with an hourly poll for pending ones.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(transactionManager = "tenantTransactionManager")
public class MandateService {

    private static final Set<DebitStatus> NOT_SENT = EnumSet.of(DebitStatus.SCHEDULED);

    private final FeeMandateRepository mandates;
    private final FeeDebitRepository debits;
    private final OnlineFeeQueries queries;
    private final OnlineFeeConfigService config;
    private final FeeGatewayRegistry gateways;
    private final PayerAccess access;
    private final FeeContext ctx;
    private final FeeNotifier notifier;

    // ── Create ─────────────────────────────────────────────────────────────────────────

    public MandateView create(CreateMandateRequest in) {
        if (!access.office() && !SecurityUtils.hasAuthority("FEE_PAY")) {
            throw new AccessDeniedException("You cannot set up auto-debit mandates");
        }
        FeeAutoDebitSettings s = config.settings();
        GatewayProvider provider = in.provider() != null ? in.provider() : s.getMandateProvider();
        if (provider == null) {
            throw FeeContext.error("FEE_ONLINE_NOT_SET_UP", "Auto-debit is not set up for this school");
        }
        Credentials cred = config.credentials(provider);
        GuardianRow payer = access.payer(in.guardianId(), in.studentIds());
        if (payer.phone() == null || payer.phone().isBlank()) {
            throw FeeContext.error("FEE_PAYER_PHONE_REQUIRED", "The guardian has no phone number; the gateway needs one");
        }
        List<GuardianRow> rows = access.payerRows(payer, in.studentIds());
        Set<UUID> children = rows.stream().map(GuardianRow::studentId).collect(Collectors.toSet());
        if (in.studentIds() != null && !in.studentIds().isEmpty()) {
            List<UUID> notTheirs = in.studentIds().stream().filter(id -> !children.contains(id)).toList();
            if (!notTheirs.isEmpty()) {
                throw FeeContext.error("FEE_NOT_PAYERS_CHILD", "Student " + notTheirs.getFirst() + " is not a child of " + payer.name());
            }
        }
        access.checkStudents(children);
        Set<UUID> covered = queries.covered(children);
        if (in.studentIds() != null && !in.studentIds().isEmpty() && !covered.isEmpty()) {
            throw FeeContext.error("FEE_MANDATE_EXISTS", "A pending or active mandate already covers " + names(covered)
                    + "; cancel it first");
        }
        Map<UUID, UUID> guardianByStudent = new LinkedHashMap<>();
        rows.stream().filter(g -> !covered.contains(g.studentId())).forEach(g -> guardianByStudent.put(g.studentId(), g.guardianId()));
        if (guardianByStudent.isEmpty()) {
            throw FeeContext.error("FEE_MANDATE_EXISTS", "Every child of " + payer.name() + " already has a pending or active mandate");
        }
        long max = in.maxAmountPaise() != null ? in.maxAmountPaise() : s.getMandateMaxAmountPaise();

        FeeMandate m = new FeeMandate();
        m.setReference(OnlineSupport.reference("MD"));
        m.setProvider(provider);
        m.setMethod(in.method());
        m.setPayerName(payer.name());
        m.setPayerEmail(blankToNull(payer.email()));
        m.setPayerPhone(payer.phone().trim());
        m.setGuardianUserId(payer.guardianUserId());
        m.setMaxAmountPaise(max);
        m.setExpiresOn(ctx.today().plusYears(s.getMandateValidityYears()));
        m.setCreatedBy(ctx.actorId());
        mandates.save(m);
        queries.addMandateStudents(m.getId(), guardianByStudent);

        String studentNames = names(guardianByStudent.keySet());
        MandateSession session;
        try {
            session = gateways.get(provider).createMandate(cred, new MandateSetup(m.getReference(), in.method(), payer(m), max, m.getExpiresOn(),
                    s.getReturnUrl(), "School fees: " + studentNames));
        } catch (GatewayException e) {
            throw OnlineSupport.gatewayError(e);
        }
        m.setGatewayMandateId(session.gatewayMandateId());
        m.setGatewayCustomerId(session.gatewayCustomerId());
        m.setGatewayAuthId(session.gatewayAuthId());
        m.setAuthUrl(session.authUrl());
        m.setStatus(session.status() == null ? MandateStatus.PENDING : session.status());
        mandates.save(m);
        if (!Boolean.FALSE.equals(in.notifyPayer()) && m.getAuthUrl() != null) {
            UUID first = guardianByStudent.keySet().iterator().next();
            notifier.send(NotificationEvent.FEE_MANDATE_REQUEST, List.of(new FeeNotifier.Message(first,
                    Map.of("payer_name", m.getPayerName(), "student_name", studentNames, "auth_link", m.getAuthUrl(), "max_amount", FeeMath.rupees(max)),
                    "FEE_MANDATE", m.getId(), "FEE_MANDATE_REQUEST:" + m.getId(), Set.copyOf(guardianByStudent.values()))), null);
        }
        log.info("Mandate {} created on {} for {} children", m.getReference(), provider, guardianByStudent.size());
        return view(m);
    }

    // ── Read ───────────────────────────────────────────────────────────────────────────

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public MandateView get(UUID id) {
        FeeMandate m = mandate(id);
        checkCanSee(m);
        return view(m);
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Page<MandateView> list(Set<MandateStatus> statuses, int page, int size) {
        PageRequest p = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200));
        Page<FeeMandate> rows = statuses == null || statuses.isEmpty() ? mandates.findAllByOrderByCreatedAtDesc(p)
                : mandates.findByStatusInOrderByCreatedAtDesc(statuses, p);
        return rows.map(this::view);
    }

    /** The caller's own mandates (parent app). */
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<MandateView> mine() {
        Set<UUID> me = access.myTenantUsers();
        return me.isEmpty() ? List.of() : mandates.findByGuardianUserIdInOrderByCreatedAtDesc(me).stream().map(this::view).toList();
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<MandateView> forStudent(UUID studentId) {
        return queries.mandateOfStudent(studentId).flatMap(mandates::findById).map(this::view);
    }

    // ── Change ─────────────────────────────────────────────────────────────────────────

    /** Asks the gateway for the mandate's state now (when a webhook was missed). */
    public MandateView refresh(UUID id) {
        FeeMandate m = mandate(id);
        checkCanSee(m);
        sync(m);
        return view(m);
    }

    public MandateView cancel(UUID id, String reason) {
        FeeMandate m = lock(id);
        access.checkPayer(m.getGuardianUserId());
        if (!m.getStatus().live()) {
            throw FeeContext.error("FEE_MANDATE_STATE", "This mandate is already " + m.getStatus());
        }
        try {
            gateways.get(m.getProvider()).cancelMandate(config.credentials(m.getProvider()), ref(m));
        } catch (GatewayException e) {
            throw OnlineSupport.gatewayError(e);
        }
        end(m, MandateStatus.CANCELLED, reason, ctx.actorId());
        return view(m);
    }

    /** A webhook said the mandate changed. Returns what was done (for the webhook log). */
    public String apply(GatewayProvider provider, MandateUpdate u) {
        Optional<FeeMandate> found = Optional.empty();
        if (u.reference() != null) {
            found = mandates.findByReference(u.reference()).filter(m -> m.getProvider() == provider);
        }
        if (found.isEmpty() && u.gatewayMandateId() != null) {
            found = mandates.findFirstByProviderAndGatewayMandateId(provider, u.gatewayMandateId());
        }
        if (found.isEmpty() && u.gatewayAuthId() != null) {
            found = mandates.findFirstByProviderAndGatewayAuthId(provider, u.gatewayAuthId());
        }
        if (found.isEmpty() && u.gatewayCustomerId() != null) {
            found = mandates.findByProviderAndGatewayCustomerIdAndStatusOrderByCreatedAtDesc(provider, u.gatewayCustomerId(), MandateStatus.PENDING)
                    .stream().findFirst();
        }
        if (found.isEmpty()) {
            return "IGNORED: no mandate for " + firstNonNull(u.reference(), u.gatewayMandateId(), u.gatewayAuthId());
        }
        FeeMandate m = lock(found.get().getId());
        if (m.getGatewayMandateId() == null && u.gatewayMandateId() != null) {
            m.setGatewayMandateId(u.gatewayMandateId());
        }
        if (m.getGatewayCustomerId() == null && u.gatewayCustomerId() != null) {
            m.setGatewayCustomerId(u.gatewayCustomerId());
        }
        MandateStatus before = m.getStatus();
        transition(m, u.status(), u.reason());
        mandates.save(m);
        return "APPLIED: " + m.getReference() + " " + before + " -> " + m.getStatus();
    }

    /**
     * Polls pending mandates not heard of for an hour (bank approval of eNACH can take days) and expires mandates past their end
     * date. Each mandate on its own; a gateway error leaves it for the next round.
     */
    public int syncPending() {
        Instant now = ctx.clock().instant();
        int n = 0;
        for (FeeMandate m : mandates.findByStatusIn(EnumSet.of(MandateStatus.PENDING, MandateStatus.ACTIVE, MandateStatus.PAUSED))) {
            if (m.getStatus().live() && m.getExpiresOn().isBefore(ctx.today())) {
                end(lock(m.getId()), MandateStatus.EXPIRED, "Mandate validity ended", null);
                n++;
                continue;
            }
            Instant last = m.getLastSyncedAt() != null ? m.getLastSyncedAt() : m.getCreatedAt();
            if (m.getStatus() == MandateStatus.PENDING && last != null && Duration.between(last, now).toMinutes() >= 60) {
                try {
                    sync(m);
                    n++;
                } catch (RuntimeException e) {
                    log.warn("Mandate {} sync failed: {}", m.getReference(), e.getMessage());
                }
            }
        }
        return n;
    }

    private void sync(FeeMandate m) {
        FeeGateway g = gateways.get(m.getProvider());
        MandateState st;
        try {
            st = g.fetchMandate(config.credentials(m.getProvider()), ref(m));
        } catch (GatewayException e) {
            throw OnlineSupport.gatewayError(e);
        }
        FeeMandate locked = lock(m.getId());
        if (locked.getGatewayMandateId() == null && st.gatewayMandateId() != null) {
            locked.setGatewayMandateId(st.gatewayMandateId());
        }
        transition(locked, st.status(), st.reason());
        locked.setLastSyncedAt(ctx.clock().instant());
        mandates.save(locked);
        copy(locked, m);
    }

    /** Ended mandates stay ended (a late webhook cannot revive them). */
    void transition(FeeMandate m, MandateStatus to, String reason) {
        if (to == null || !m.getStatus().live() || to == m.getStatus()) {
            return;
        }
        if (to.live()) {
            if (to == MandateStatus.ACTIVE && m.getActivatedAt() == null) {
                m.setActivatedAt(ctx.clock().instant());
            }
            m.setStatus(to);
            return;
        }
        end(m, to, reason, null);
    }

    /** Ends the mandate: its children are free for a new one; debits not yet sent are cancelled (sent ones still get the bank's answer). */
    void end(FeeMandate m, MandateStatus status, String reason, UUID by) {
        m.setStatus(status);
        m.setEndedAt(ctx.clock().instant());
        m.setEndedBy(by);
        m.setEndReason(cut(reason));
        mandates.save(m);
        queries.releaseMandate(m.getId());
        for (FeeDebit d : debits.findByMandateIdAndStatusIn(m.getId(), NOT_SENT)) {
            d.setStatus(DebitStatus.CANCELLED);
            d.setFailureReason("Mandate " + status.name().toLowerCase());
            debits.save(d);
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────────────

    MandateView view(FeeMandate m) {
        List<UUID> ids = queries.mandateStudents(List.of(m.getId())).getOrDefault(m.getId(), List.of());
        List<StudentBrief> students = new ArrayList<>(queries.briefs(ids).values());
        return new MandateView(m.getId(), m.getReference(), m.getProvider(), m.getMethod(), m.getStatus(), m.getPayerName(), m.getPayerEmail(),
                m.getPayerPhone(), m.getMaxAmountPaise(), m.getExpiresOn(), m.getAuthUrl(),
                m.getProvider() == GatewayProvider.CASHFREE ? m.getGatewayAuthId() : null, students, m.getActivatedAt(), m.getEndedAt(),
                m.getEndReason(), m.getCreatedAt(), m.getLastSyncedAt());
    }

    static MandateRef ref(FeeMandate m) {
        return new MandateRef(m.getReference(), m.getGatewayMandateId(), m.getGatewayCustomerId(), m.getGatewayAuthId());
    }

    static Payer payer(FeeMandate m) {
        return new Payer(m.getPayerName(), m.getPayerEmail(), m.getPayerPhone());
    }

    static String method(MandateMethod method) {
        return method == MandateMethod.UPI ? "UPI Autopay" : "eNACH (bank account)";
    }

    private void checkCanSee(FeeMandate m) {
        if (!ctx.fullScope()) {
            access.checkPayer(m.getGuardianUserId());
        }
    }

    private FeeMandate mandate(UUID id) {
        return mandates.findById(id).orElseThrow(() -> new ResourceNotFoundException("FeeMandate", id));
    }

    private FeeMandate lock(UUID id) {
        return mandates.lock(id).orElseThrow(() -> new ResourceNotFoundException("FeeMandate", id));
    }

    private String names(Set<UUID> studentIds) {
        return queries.briefs(studentIds).values().stream().map(StudentBrief::studentName).collect(Collectors.joining(", "));
    }

    private static void copy(FeeMandate from, FeeMandate to) {
        if (from != to) {
            to.setStatus(from.getStatus());
            to.setGatewayMandateId(from.getGatewayMandateId());
            to.setActivatedAt(from.getActivatedAt());
            to.setEndedAt(from.getEndedAt());
            to.setEndReason(from.getEndReason());
            to.setLastSyncedAt(from.getLastSyncedAt());
        }
    }

    private static String firstNonNull(String... s) {
        for (String x : s) {
            if (x != null) {
                return x;
            }
        }
        return "?";
    }

    private static String cut(String s) {
        return s == null || s.length() <= 500 ? s : s.substring(0, 500);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
