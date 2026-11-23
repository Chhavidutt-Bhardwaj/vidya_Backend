package com.vidya.tenantdata.fee.online.service;

import com.vidya.tenant.context.TenantContext;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.GatewayAccountView;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.SaveGatewayRequest;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.SettingsView;
import com.vidya.tenantdata.fee.online.dto.OnlineFeeDtos.UpdateSettingsRequest;
import com.vidya.tenantdata.fee.online.entity.FeeAutoDebitSettings;
import com.vidya.tenantdata.fee.online.entity.FeeGatewayAccount;
import com.vidya.tenantdata.fee.online.enums.GatewayProvider;
import com.vidya.tenantdata.fee.online.gateway.GatewayModels.Credentials;
import com.vidya.tenantdata.fee.online.gateway.SecretCipher;
import com.vidya.tenantdata.fee.online.repository.FeeAutoDebitSettingsRepository;
import com.vidya.tenantdata.fee.online.repository.FeeGatewayAccountRepository;
import com.vidya.tenantdata.fee.service.FeeContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** The school's gateway accounts (keys encrypted) and auto-debit settings. */
@Service
@Transactional(transactionManager = "tenantTransactionManager")
public class OnlineFeeConfigService {

    private final FeeAutoDebitSettingsRepository settingsRepo;
    private final FeeGatewayAccountRepository accounts;
    private final SecretCipher cipher;
    private final FeeContext ctx;
    private final String webhookBaseUrl;

    public OnlineFeeConfigService(FeeAutoDebitSettingsRepository settingsRepo, FeeGatewayAccountRepository accounts, SecretCipher cipher, FeeContext ctx,
                                  @Value("${vidya.fees.online.webhook-base-url:}") String webhookBaseUrl) {
        this.settingsRepo = settingsRepo;
        this.accounts = accounts;
        this.cipher = cipher;
        this.ctx = ctx;
        this.webhookBaseUrl = webhookBaseUrl == null ? "" : webhookBaseUrl.replaceAll("/+$", "");
    }

    // ── Settings ───────────────────────────────────────────────────────────────────────

    /** The saved settings, or the defaults (auto-debit off). */
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public FeeAutoDebitSettings settings() {
        return settingsRepo.findById(FeeAutoDebitSettings.SINGLETON).orElseGet(FeeAutoDebitSettings::new);
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public SettingsView view() {
        return view(settings());
    }

    public SettingsView update(UpdateSettingsRequest in) {
        FeeAutoDebitSettings s = settings();
        if (in.mandateProvider() != null) {
            s.setMandateProvider(in.mandateProvider());
        }
        if (in.paymentProvider() != null) {
            s.setPaymentProvider(in.paymentProvider());
        }
        if (in.debitDay() != null) {
            s.setDebitDay(in.debitDay().shortValue());
        }
        if (in.noticeDays() != null) {
            s.setNoticeDays(in.noticeDays().stream().distinct().sorted((a, b) -> b - a).map(String::valueOf).collect(Collectors.joining(",")));
        }
        if (in.inviteDaysBefore() != null) {
            s.setInviteDaysBefore(in.inviteDaysBefore().shortValue());
        }
        if (in.mandateSetupUrl() != null) {
            s.setMandateSetupUrl(blankToNull(in.mandateSetupUrl()));
        }
        if (in.siblingGapDays() != null) {
            s.setSiblingGapDays(in.siblingGapDays().shortValue());
        }
        if (in.maxAttempts() != null) {
            s.setMaxAttempts(in.maxAttempts().shortValue());
        }
        if (in.retryAfterDays() != null) {
            s.setRetryAfterDays(in.retryAfterDays().shortValue());
        }
        if (in.bounceChargePaise() != null) {
            s.setBounceChargePaise(in.bounceChargePaise());
        }
        if (in.bounceGstPercent() != null) {
            s.setBounceGstPercent(in.bounceGstPercent().setScale(2, RoundingMode.HALF_UP));
        }
        if (in.mandateMaxAmountPaise() != null) {
            s.setMandateMaxAmountPaise(in.mandateMaxAmountPaise());
        }
        if (in.mandateValidityYears() != null) {
            s.setMandateValidityYears(in.mandateValidityYears().shortValue());
        }
        if (in.linkExpiryDays() != null) {
            s.setLinkExpiryDays(in.linkExpiryDays().shortValue());
        }
        if (in.returnUrl() != null) {
            s.setReturnUrl(blankToNull(in.returnUrl()));
        }
        if (in.enabled() != null) {
            s.setEnabled(in.enabled());
        }
        if (s.isEnabled()) {
            if (s.getMandateProvider() == null) {
                throw FeeContext.error("FEE_AUTODEBIT_PROVIDER_REQUIRED", "Choose the gateway for auto-debit mandates before turning auto-debit on");
            }
            requireAccount(s.getMandateProvider());
        }
        if (s.getPaymentProvider() != null) {
            requireAccount(s.getPaymentProvider());
        }
        s.setUpdatedBy(ctx.actorId());
        return view(settingsRepo.save(s));
    }

    SettingsView view(FeeAutoDebitSettings s) {
        return new SettingsView(s.isEnabled(), s.getMandateProvider(), s.getPaymentProvider(), s.getDebitDay(), s.noticeDayList(), s.getInviteDaysBefore(),
                s.getMandateSetupUrl(), s.getSiblingGapDays(), s.getMaxAttempts(), s.getRetryAfterDays(), s.getBounceChargePaise(),
                s.getBounceGstPercent(), s.getBounceChargePaise() + gstOn(s.getBounceChargePaise(), s.getBounceGstPercent()), s.getMandateMaxAmountPaise(),
                s.getMandateValidityYears(), s.getLinkExpiryDays(), s.getReturnUrl(), nextDebitDate(s.getDebitDay(), ctx.today()), cipher.configured(),
                s.getUpdatedAt());
    }

    /** GST on an amount at the percent, rounded to the paisa. */
    public static long gstOn(long paise, BigDecimal percent) {
        return BigDecimal.valueOf(paise).multiply(percent).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact();
    }

    /** The debit day of this month if today has not passed it, else of next month. */
    public static LocalDate nextDebitDate(int debitDay, LocalDate today) {
        LocalDate thisMonth = today.withDayOfMonth(debitDay);
        return today.isAfter(thisMonth) ? thisMonth.plusMonths(1) : thisMonth;
    }

    // ── Gateway accounts ───────────────────────────────────────────────────────────────

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<GatewayAccountView> gateways() {
        return Arrays.stream(GatewayProvider.values()).map(p -> accounts.findByProvider(p).map(this::view)
                .orElse(new GatewayAccountView(p, null, null, false, false, false, webhookUrl(p), null))).toList();
    }

    public GatewayAccountView saveGateway(GatewayProvider provider, SaveGatewayRequest in) {
        FeeGatewayAccount a = accounts.findByProvider(provider).orElseGet(() -> {
            FeeGatewayAccount n = new FeeGatewayAccount();
            n.setProvider(provider);
            return n;
        });
        boolean keyChanged = a.getKeyId() != null && !Objects.equals(a.getKeyId(), in.keyId().trim());
        if ((a.getSecretEnc() == null || keyChanged) && (in.secret() == null || in.secret().isBlank())) {
            throw FeeContext.error("FEE_GATEWAY_SECRET_REQUIRED", "Enter the " + (provider == GatewayProvider.CASHFREE ? "client secret" : "key secret")
                    + " for this key");
        }
        a.setEnvironment(in.environment());
        a.setKeyId(in.keyId().trim());
        if (in.secret() != null && !in.secret().isBlank()) {
            a.setSecretEnc(cipher.encrypt(in.secret().trim()));
        }
        if (in.webhookSecret() != null && !in.webhookSecret().isBlank()) {
            a.setWebhookSecretEnc(cipher.encrypt(in.webhookSecret().trim()));
        }
        if (in.active() != null) {
            a.setActive(in.active());
        }
        a.setUpdatedBy(ctx.actorId());
        return view(accounts.save(a));
    }

    public void deleteGateway(GatewayProvider provider) {
        FeeAutoDebitSettings s = settings();
        if (s.getMandateProvider() == provider || s.getPaymentProvider() == provider) {
            throw FeeContext.error("FEE_GATEWAY_IN_USE", provider + " is selected in the online fee settings; choose another gateway first");
        }
        accounts.findByProvider(provider).ifPresent(accounts::delete);
    }

    /** Decrypted keys of an active account; throws when the school has not connected that gateway. */
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Credentials credentials(GatewayProvider provider) {
        FeeGatewayAccount a = requireAccount(provider);
        return new Credentials(provider, a.getEnvironment(), a.getKeyId(), cipher.decrypt(a.getSecretEnc()), cipher.decrypt(a.getWebhookSecretEnc()));
    }

    /** Keys for checking a webhook: also of an inactive account (payments made before it was switched off still arrive). */
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Credentials webhookCredentials(GatewayProvider provider) {
        return accounts.findByProvider(provider)
                .map(a -> new Credentials(provider, a.getEnvironment(), a.getKeyId(), cipher.decrypt(a.getSecretEnc()), cipher.decrypt(a.getWebhookSecretEnc())))
                .orElse(null);
    }

    /** The key id (not secret) a browser / app checkout needs. */
    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public String keyId(GatewayProvider provider) {
        return accounts.findByProvider(provider).map(FeeGatewayAccount::getKeyId).orElse(null);
    }

    public GatewayProvider paymentProvider() {
        FeeAutoDebitSettings s = settings();
        GatewayProvider p = s.getPaymentProvider() != null ? s.getPaymentProvider() : s.getMandateProvider();
        if (p == null) {
            throw FeeContext.error("FEE_ONLINE_NOT_SET_UP", "Online fee payment is not set up for this school");
        }
        return p;
    }

    private FeeGatewayAccount requireAccount(GatewayProvider provider) {
        FeeGatewayAccount a = accounts.findByProvider(provider).orElseThrow(() -> FeeContext.error("FEE_GATEWAY_NOT_CONNECTED",
                provider + " is not connected: add the school's " + provider + " keys first"));
        if (!a.isActive()) {
            throw FeeContext.error("FEE_GATEWAY_INACTIVE", provider + " is switched off for this school");
        }
        return a;
    }

    private GatewayAccountView view(FeeGatewayAccount a) {
        return new GatewayAccountView(a.getProvider(), a.getEnvironment(), a.getKeyId(), a.getSecretEnc() != null, a.getWebhookSecretEnc() != null,
                a.isActive(), webhookUrl(a.getProvider()), a.getUpdatedAt());
    }

    String webhookUrl(GatewayProvider p) {
        String code = TenantContext.hasTenant() ? TenantContext.getTenantCode() : "{tenantCode}";
        return webhookBaseUrl + "/api/v1/webhooks/fees/" + code + "/" + p.name().toLowerCase();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
