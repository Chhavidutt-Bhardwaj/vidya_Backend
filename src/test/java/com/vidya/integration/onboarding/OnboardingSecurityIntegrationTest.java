package com.vidya.integration.onboarding;

import com.jayway.jsonpath.JsonPath;
import com.vidya.common.exception.TenantAccessDeniedException;
import com.vidya.master.onboarding.entity.OnboardingActivity;
import com.vidya.master.onboarding.entity.OnboardingStatus;
import com.vidya.master.onboarding.entity.PerformerType;
import com.vidya.master.onboarding.repository.OnboardingActivityRepository;
import com.vidya.master.onboarding.repository.SchoolOnboardingRepository;
import com.vidya.master.payment.entity.PaymentStatus;
import com.vidya.master.payment.repository.PaymentTransactionRepository;
import com.vidya.master.provisioning.entity.TenantProvisioning;
import com.vidya.master.provisioning.repository.TenantProvisioningRepository;
import com.vidya.master.subscription.entity.PlanStatus;
import com.vidya.master.subscription.entity.SubscriptionPlan;
import com.vidya.master.subscription.repository.SubscriptionPlanRepository;
import com.vidya.master.tenant.entity.Tenant;
import com.vidya.master.tenant.repository.TenantRepository;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.jwt.JwtTokenProvider;
import com.vidya.security.repository.PlatformUserRepository;
import com.vidya.security.session.service.SessionService;
import com.vidya.tenant.datasource.CredentialResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Onboarding I0 — security &amp; integrity, verified end-to-end against real infrastructure.
 *
 * <p>Boots the full application (real security filter chain, JWT authentication, method security,
 * master Flyway V1–V28, tenant Flyway V1–V18) against a PostgreSQL container and a Redis container.
 * Tenant provisioning creates <em>real</em> tenant databases in the same PostgreSQL server.
 *
 * <p>Each test numbers the mandatory I0 scenario(s) it covers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Testcontainers
class OnboardingSecurityIntegrationTest {

    private static final String PG_USER = "postgres";
    private static final String PG_PASS = "postgres";
    private static final String TENANT_APP_USER = "vidya_tenant_app";
    private static final String TENANT_APP_PASS = "tenant_app_pw";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("vidya_master")
            .withUsername(PG_USER)
            .withPassword(PG_PASS);

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    /** Secret-store stand-in; can be armed to fail N times to simulate a provisioning outage. */
    static final FlakyCredentialResolver CREDENTIALS = new FlakyCredentialResolver(Map.of(
            "TENANT_DB_USER", TENANT_APP_USER,
            "TENANT_DB_PASS", TENANT_APP_PASS));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("vidya.datasource.master.url", POSTGRES::getJdbcUrl);
        r.add("vidya.datasource.master.username", () -> PG_USER);
        r.add("vidya.datasource.master.password", () -> PG_PASS);
        r.add("vidya.provisioning.admin.url",
                () -> "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/postgres");
        r.add("vidya.provisioning.admin.username", () -> PG_USER);
        r.add("vidya.provisioning.admin.password", () -> PG_PASS);
        r.add("vidya.provisioning.tenant-db.host", POSTGRES::getHost);
        r.add("vidya.provisioning.tenant-db.port", () -> POSTGRES.getMappedPort(5432));
        r.add("vidya.redis.host", REDIS::getHost);
        r.add("vidya.redis.port", () -> REDIS.getMappedPort(6379));
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        r.add("vidya.security.jwt.secret", () -> "i0-integration-test-secret-key-that-is-at-least-256-bits-long!!");
        r.add("vidya.payment.razorpay.key-secret", () -> "rzp_it_secret");
        r.add("vidya.payment.razorpay.webhook-secret", () -> "rzp_it_webhook");
    }

    @TestConfiguration
    static class TestCredentialsConfig {
        @Bean
        @Primary
        CredentialResolver testCredentialResolver() {
            return CREDENTIALS;
        }
    }

    @Autowired private WebApplicationContext context;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private SessionService sessionService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformUserRepository userRepository;
    @Autowired private SchoolOnboardingRepository onboardingRepository;
    @Autowired private OnboardingActivityRepository activityRepository;
    @Autowired private SubscriptionPlanRepository planRepository;
    @Autowired private PaymentTransactionRepository paymentRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantProvisioningRepository provisioningRepository;

    private MockMvc mvc;
    private PlatformUser platformAdmin;
    private String adminToken;

    @BeforeEach
    void setUp() {
        // Same order as the servlet container: Spring Security chain (-100) then TenantResolutionFilter (@Order(3)).
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", jakarta.servlet.Filter.class),
                        context.getBean(com.vidya.tenant.resolver.TenantResolutionFilter.class))
                .build();
        CREDENTIALS.failuresRemaining.set(0);
        platformAdmin = createUser(PlatformRole.PLATFORM_ADMIN, null);
        adminToken = token(platformAdmin);
    }

    // =====================================================================
    // Scenario 1 — unauthenticated requests are rejected
    // =====================================================================

    @Test
    @DisplayName("S1: every onboarding endpoint rejects unauthenticated requests with 401")
    void s1_unauthenticatedRequestsRejected() throws Exception {
        UUID id = UUID.randomUUID();
        List<MockHttpServletRequestBuilder> requests = List.of(
                post("/api/v1/onboarding").contentType(MediaType.APPLICATION_JSON).content("{}"),
                get("/api/v1/onboarding"),
                get("/api/v1/onboarding/" + id),
                get("/api/v1/onboarding/code/ONB-X"),
                get("/api/v1/onboarding/" + id + "/detail"),
                put("/api/v1/onboarding/" + id + "/steps").contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/api/v1/onboarding/" + id + "/submit"),
                post("/api/v1/onboarding/" + id + "/verify").contentType(MediaType.APPLICATION_JSON).content("{\"verified\":true}"),
                post("/api/v1/onboarding/" + id + "/approve"),
                post("/api/v1/onboarding/" + id + "/reject").contentType(MediaType.APPLICATION_JSON).content("{\"rejectionReason\":\"x\"}"),
                post("/api/v1/onboarding/" + id + "/select-plan").contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/api/v1/onboarding/" + id + "/payment/initiate").contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/api/v1/onboarding/" + id + "/payment/waive").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"),
                post("/api/v1/onboarding/" + id + "/provision"),
                post("/api/v1/onboarding/" + id + "/admin"),
                post("/api/v1/onboarding/" + id + "/retry"),
                post("/api/v1/onboarding/" + id + "/activate"),
                get("/api/v1/onboarding/" + id + "/workflow-status"),
                post("/api/v1/payments/manual/confirm").contentType(MediaType.APPLICATION_JSON).content("{}"));

        for (MockHttpServletRequestBuilder request : requests) {
            MvcResult result = mvc.perform(request).andReturn();
            assertThat(result.getResponse().getStatus())
                    .as("%s %s", result.getRequest().getMethod(), result.getRequest().getRequestURI())
                    .isEqualTo(401);
        }
    }

    // =====================================================================
    // Scenario 2 — a School Admin cannot approve another school's onboarding
    // =====================================================================

    @Test
    @DisplayName("S2: school users cannot read, approve or reject another school's onboarding")
    void s2_schoolAdminCannotActOnAnotherSchool() throws Exception {
        UUID otherOnboarding = approvedOnboarding("S2 Other School");
        UUID mySchoolOnboarding = initiate("S2 My School");
        UUID mySchoolId = schoolIdOf(mySchoolOnboarding);

        for (PlatformRole role : List.of(PlatformRole.SCHOOL_ADMIN, PlatformRole.SCHOOL_OWNER)) {
            String schoolToken = token(createUser(role, mySchoolId));

            assertThat(status(post("/api/v1/onboarding/" + otherOnboarding + "/approve"), schoolToken)).isEqualTo(403);
            assertThat(status(post("/api/v1/onboarding/" + otherOnboarding + "/reject")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"rejectionReason\":\"hostile\"}"), schoolToken)).isEqualTo(403);
            assertThat(status(get("/api/v1/onboarding/" + otherOnboarding), schoolToken)).isEqualTo(403);
            assertThat(status(get("/api/v1/onboarding"), schoolToken)).isEqualTo(403);
            // Not even its own school's onboarding may be approved by a school user (platform-only decision)
            assertThat(status(post("/api/v1/onboarding/" + mySchoolOnboarding + "/approve"), schoolToken)).isEqualTo(403);
        }
        // A community manager with no membership in the onboarding's community is also denied
        String communityToken = token(createUser(PlatformRole.COMMUNITY_MANAGER, null));
        assertThat(status(get("/api/v1/onboarding/" + otherOnboarding), communityToken)).isEqualTo(403);
        assertThat(statusOf(otherOnboarding)).isEqualTo(OnboardingStatus.APPROVED);
    }

    // =====================================================================
    // Scenarios 3 & 4 — role whitelist, no silent reassignment
    // =====================================================================

    @Test
    @DisplayName("S3: PLATFORM_SUPER_ADMIN (or any privileged role) cannot be assigned through initial admin creation")
    void s3_cannotAssignPrivilegedRole() throws Exception {
        UUID onboardingId = provisionedOnboarding("S3 School");

        for (String role : List.of("PLATFORM_SUPER_ADMIN", "PLATFORM_ADMIN", "COMMUNITY_ADMIN")) {
            String email = "s3-" + role.toLowerCase() + "-" + shortId() + "@example.org";
            int st = status(post("/api/v1/onboarding/" + onboardingId + "/admin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"firstName\":\"Eve\",\"lastName\":\"X\",\"role\":\"" + role + "\"}"),
                    adminToken);
            assertThat(st).as("role %s", role).isEqualTo(400);
            assertThat(userRepository.findByEmailIgnoreCase(email)).isEmpty();
        }

        // A school user cannot even reach the endpoint
        String schoolToken = token(createUser(PlatformRole.SCHOOL_OWNER, schoolIdOf(onboardingId)));
        assertThat(status(post("/api/v1/onboarding/" + onboardingId + "/admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"s3-self-" + shortId() + "@example.org\",\"firstName\":\"E\",\"lastName\":\"X\",\"role\":\"SCHOOL_ADMIN\"}"),
                schoolToken)).isEqualTo(403);

        // The allowed roles work
        String okEmail = "s3-ok-" + shortId() + "@example.org";
        assertThat(status(post("/api/v1/onboarding/" + onboardingId + "/admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + okEmail + "\",\"firstName\":\"Ok\",\"lastName\":\"Admin\",\"role\":\"SCHOOL_ADMIN\"}"),
                adminToken)).isEqualTo(200);
        assertThat(userRepository.findByEmailIgnoreCase(okEmail)).get()
                .extracting(PlatformUser::getPlatformRole).isEqualTo(PlatformRole.SCHOOL_ADMIN);
    }

    @Test
    @DisplayName("S4: an existing user is never silently re-linked, re-roled or reactivated")
    void s4_existingUsersNotReassigned() throws Exception {
        UUID onboardingId = provisionedOnboarding("S4 School");

        PlatformUser victim = createUser(PlatformRole.COMMUNITY_MANAGER, null);
        PlatformUser otherSchoolOwner = createUser(PlatformRole.SCHOOL_OWNER, schoolIdOf(initiate("S4 Other School")));

        for (PlatformUser existing : List.of(victim, otherSchoolOwner, platformAdmin)) {
            int st = status(post("/api/v1/onboarding/" + onboardingId + "/admin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + existing.getEmail() + "\",\"firstName\":\"A\",\"lastName\":\"B\",\"role\":\"SCHOOL_OWNER\"}"),
                    adminToken);
            assertThat(st).as("existing %s", existing.getPlatformRole()).isEqualTo(409);

            PlatformUser reloaded = userRepository.findById(existing.getId()).orElseThrow();
            assertThat(reloaded.getSchoolId()).isEqualTo(existing.getSchoolId());
            assertThat(reloaded.getPlatformRole()).isEqualTo(existing.getPlatformRole());
            assertThat(reloaded.getStatus()).isEqualTo(existing.getStatus());
        }
    }

    // =====================================================================
    // Scenario 5 — a DRAFT onboarding cannot be activated
    // =====================================================================

    @Test
    @DisplayName("S5: DRAFT / unapproved onboardings cannot be activated via /activate, /retry or /provision")
    void s5_draftCannotBeActivated() throws Exception {
        UUID draft = initiate("S5 Draft School");
        assertThat(statusOf(draft)).isEqualTo(OnboardingStatus.DRAFT);

        assertThat(status(post("/api/v1/onboarding/" + draft + "/activate"), adminToken)).isEqualTo(422);
        // Legacy body with plan/payment fields must not create a subscription or payment either
        assertThat(status(post("/api/v1/onboarding/" + draft + "/activate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"planId\":\"" + activePlan().getId() + "\",\"billingCycle\":\"ANNUAL\",\"paymentProvider\":\"MANUAL\"}"),
                adminToken)).isEqualTo(422);
        assertThat(status(post("/api/v1/onboarding/" + draft + "/retry"), adminToken)).isEqualTo(422);
        assertThat(status(post("/api/v1/onboarding/" + draft + "/provision"), adminToken)).isEqualTo(422);

        assertThat(statusOf(draft)).isEqualTo(OnboardingStatus.DRAFT);
        assertThat(tenantRepository.findBySchoolId(schoolIdOf(draft))).isEmpty();
        assertThat(paymentRepository.findBySchoolId(schoolIdOf(draft), org.springframework.data.domain.Pageable.unpaged()))
                .isEmpty();

        // Verification and plan selection can no longer be skipped either
        assertThat(status(post("/api/v1/onboarding/" + draft + "/select-plan").contentType(MediaType.APPLICATION_JSON)
                .content("{\"planId\":\"" + activePlan().getId() + "\"}"), adminToken)).isEqualTo(422);
    }

    // =====================================================================
    // Scenario 6 — unsettled online payments are not treated as successful
    // =====================================================================

    @Test
    @DisplayName("S6: an unsigned/forged gateway verification never settles the payment or allows approval")
    void s6_unsettledOnlinePaymentNotSuccessful() throws Exception {
        UUID id = verifiedWithPlan("S6 School");
        MvcResult init = perform(post("/api/v1/onboarding/" + id + "/payment/initiate")
                .contentType(MediaType.APPLICATION_JSON).content("{\"paymentProvider\":\"RAZORPAY\"}"), adminToken);
        assertThat(init.getResponse().getStatus()).isEqualTo(200);
        String txn = JsonPath.read(init.getResponse().getContentAsString(), "$.data.transactionCode");
        String orderId = JsonPath.read(init.getResponse().getContentAsString(), "$.data.providerOrderId");
        assertThat(statusOf(id)).isEqualTo(OnboardingStatus.PAYMENT_PENDING);

        // 1. No signature (previously treated as success)
        perform(post("/api/v1/payments/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"transactionCode\":\"" + txn + "\",\"providerOrderId\":\"" + orderId + "\",\"providerPaymentId\":\"pay_fake\"}"),
                adminToken);
        assertThat(paymentRepository.findByTransactionCode(txn).orElseThrow().getStatus()).isNotEqualTo(PaymentStatus.SUCCESS);

        // 2. Unsigned webhook claiming capture
        String webhook = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_fake\",\"order_id\":\""
                + orderId + "\",\"method\":\"upi\",\"status\":\"captured\"}}}}";
        mvc.perform(post("/api/v1/webhooks/razorpay").contentType(MediaType.APPLICATION_JSON).content(webhook)).andReturn();
        assertThat(paymentRepository.findByTransactionCode(txn).orElseThrow().getStatus()).isNotEqualTo(PaymentStatus.SUCCESS);

        // 3. Onboarding stays unpaid and cannot be approved
        assertThat(statusOf(id)).isEqualTo(OnboardingStatus.PAYMENT_PENDING);
        assertThat(status(post("/api/v1/onboarding/" + id + "/approve"), adminToken)).isEqualTo(422);
        assertThat(statusOf(id)).isEqualTo(OnboardingStatus.PAYMENT_PENDING);
    }

    // =====================================================================
    // Scenario 7 — manual payment waivers / confirmations require authorization
    // =====================================================================

    @Test
    @DisplayName("S7: waivers and manual confirmations require dedicated permissions; the approver is audited")
    void s7_manualWaiverRequiresAuthorizedApproval() throws Exception {
        UUID id = verifiedWithPlan("S7 School");
        MvcResult init = perform(post("/api/v1/onboarding/" + id + "/payment/initiate")
                .contentType(MediaType.APPLICATION_JSON).content("{\"paymentProvider\":\"MANUAL\",\"paymentMethod\":\"NEFT\"}"),
                adminToken);
        String txn = JsonPath.read(init.getResponse().getContentAsString(), "$.data.transactionCode");

        String schoolOwner = token(createUser(PlatformRole.SCHOOL_OWNER, schoolIdOf(id)));
        String communityManager = token(createUser(PlatformRole.COMMUNITY_MANAGER, null));
        String fieldAgent = token(createUser(PlatformRole.FIELD_AGENT, null));

        // Self-settlement paths are closed
        String confirm = "{\"transactionCode\":\"" + txn + "\",\"paymentMethod\":\"NEFT\",\"referenceNumber\":\"UTR123\",\"confirmedBy\":\"ceo\"}";
        for (String t : List.of(schoolOwner, communityManager, fieldAgent)) {
            assertThat(status(post("/api/v1/payments/manual/confirm").contentType(MediaType.APPLICATION_JSON).content(confirm), t))
                    .isEqualTo(403);
            assertThat(status(post("/api/v1/onboarding/" + id + "/payment/waive").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"please\"}"), t)).isEqualTo(403);
        }
        assertThat(status(post("/api/v1/payments/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"transactionCode\":\"" + txn + "\"}"), schoolOwner)).isEqualTo(422);
        assertThat(paymentRepository.findByTransactionCode(txn).orElseThrow().getStatus()).isNotEqualTo(PaymentStatus.SUCCESS);
        assertThat(statusOf(id)).isEqualTo(OnboardingStatus.PAYMENT_PENDING);

        // A waiver without a reason is rejected even for an authorized user
        assertThat(status(post("/api/v1/onboarding/" + id + "/payment/waive").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"  \"}"), adminToken)).isEqualTo(400);

        // Authorized waiver succeeds and is attributed to the authenticated platform admin
        assertThat(status(post("/api/v1/onboarding/" + id + "/payment/waive").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Pilot school, 6-month trial\",\"reference\":\"CT-1\"}"), adminToken)).isEqualTo(200);
        assertThat(statusOf(id)).isEqualTo(OnboardingStatus.PAYMENT_WAIVED);
        OnboardingActivity waiver = activity(id, "PAYMENT_WAIVED");
        assertThat(waiver.getPerformedBy()).isEqualTo(platformAdmin.getId());
        assertThat(waiver.getPerformedByType()).isEqualTo(PerformerType.PLATFORM_USER);

        // Authorized manual confirmation records the authenticated user, not the body's confirmedBy
        UUID id2 = verifiedWithPlan("S7 Second School");
        MvcResult init2 = perform(post("/api/v1/onboarding/" + id2 + "/payment/initiate")
                .contentType(MediaType.APPLICATION_JSON).content("{\"paymentProvider\":\"MANUAL\",\"paymentMethod\":\"NEFT\"}"),
                adminToken);
        String txn2 = JsonPath.read(init2.getResponse().getContentAsString(), "$.data.transactionCode");
        assertThat(status(post("/api/v1/payments/manual/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(confirm.replace(txn, txn2)), adminToken)).isEqualTo(200);
        assertThat(statusOf(id2)).isEqualTo(OnboardingStatus.PAYMENT_COMPLETED);
        assertThat(String.valueOf(paymentRepository.findByTransactionCode(txn2).orElseThrow().getRawCallback().get("confirmedBy")))
                .isEqualTo(platformAdmin.getId().toString());
    }

    // =====================================================================
    // Scenario 8 — provisioning host/port cannot be supplied by clients
    // =====================================================================

    @Test
    @DisplayName("S8: client-supplied database host/port are rejected; the tenant uses server configuration")
    void s8_clientCannotSupplyDbHost() throws Exception {
        UUID id = approvedOnboarding("S8 School");

        assertThat(status(post("/api/v1/onboarding/" + id + "/provision").contentType(MediaType.APPLICATION_JSON)
                .content("{\"databaseHost\":\"attacker.example.com\",\"databasePort\":6543}"), adminToken)).isEqualTo(400);
        assertThat(status(post("/api/v1/onboarding/" + id + "/provision").contentType(MediaType.APPLICATION_JSON)
                .content("{\"databaseHost\":\"attacker.example.com\"}"), adminToken)).isEqualTo(400);
        assertThat(statusOf(id)).isEqualTo(OnboardingStatus.APPROVED);
        assertThat(tenantRepository.findBySchoolId(schoolIdOf(id))).isEmpty();

        assertThat(status(post("/api/v1/onboarding/" + id + "/provision"), adminToken)).isEqualTo(200);
        Tenant tenant = tenantRepository.findBySchoolId(schoolIdOf(id)).orElseThrow();
        assertThat(tenant.getDatabaseHost()).isEqualTo(POSTGRES.getHost());
        assertThat(tenant.getDatabasePort()).isEqualTo(POSTGRES.getMappedPort(5432));
    }

    // =====================================================================
    // Scenario 9 — failed tenant provisioning can be retried safely
    // =====================================================================

    @Test
    @DisplayName("S9: a failed provisioning is recorded and a retry completes it without duplicates or re-payment")
    void s9_failedProvisioningCanBeRetried() throws Exception {
        UUID id = approvedOnboarding("S9 School");
        UUID schoolId = schoolIdOf(id);
        long paymentsBefore = paymentRepository.findBySchoolId(schoolId, org.springframework.data.domain.Pageable.unpaged())
                .getTotalElements();

        CREDENTIALS.failuresRemaining.set(1);   // secret store outage during DATABASE_CREATING
        int failed = status(post("/api/v1/onboarding/" + id + "/provision"), adminToken);
        assertThat(failed).isGreaterThanOrEqualTo(500);
        assertThat(statusOf(id)).isEqualTo(OnboardingStatus.PROVISIONING_FAILED);
        Tenant tenant = tenantRepository.findBySchoolId(schoolId).orElseThrow();
        TenantProvisioning record = provisioningRepository.findByTenantId(tenant.getId()).orElseThrow();
        assertThat(record.isFailed()).isTrue();
        assertThat(activity(id, "TENANT_PROVISIONING_FAILED")).isNotNull();

        // While provisioning keeps failing, /activate (which resumes provisioning) cannot activate the school
        CREDENTIALS.failuresRemaining.set(1);
        assertThat(status(post("/api/v1/onboarding/" + id + "/activate").contentType(MediaType.APPLICATION_JSON)
                .content("{}"), adminToken)).isNotEqualTo(200);
        assertThat(statusOf(id)).isEqualTo(OnboardingStatus.PROVISIONING_FAILED);
        assertThat(provisioningRepository.findByTenantId(tenant.getId()).orElseThrow().isFailed()).isTrue();
        CREDENTIALS.failuresRemaining.set(0);

        // Retry: provisioning resumes and the school is activated
        assertThat(status(post("/api/v1/onboarding/" + id + "/retry"), adminToken)).isEqualTo(200);
        assertThat(statusOf(id)).isEqualTo(OnboardingStatus.ACTIVE);

        assertThat(tenantRepository.findAll().stream().filter(t -> schoolId.equals(t.getSchoolId()))).hasSize(1);
        TenantProvisioning completed = provisioningRepository.findByTenantId(tenant.getId()).orElseThrow();
        assertThat(completed.isCompleted()).isTrue();
        assertThat(completed.getAttemptCount()).isGreaterThanOrEqualTo(2);
        assertThat(paymentRepository.findBySchoolId(schoolId, org.springframework.data.domain.Pageable.unpaged())
                .getTotalElements()).isEqualTo(paymentsBefore);
        assertThat(tenantTableExists(tenant.getDatabaseName(), "student_report_card"))
                .as("tenant V18 applied (report cards FK to section)").isTrue();

        // A second retry on an ACTIVE onboarding is a no-op
        assertThat(status(post("/api/v1/onboarding/" + id + "/retry"), adminToken)).isEqualTo(200);
        assertThat(tenantRepository.findAll().stream().filter(t -> schoolId.equals(t.getSchoolId()))).hasSize(1);
    }

    // =====================================================================
    // Scenario 10 — two schools cannot access each other's tenant databases
    // =====================================================================

    @Test
    @DisplayName("S10: a school admin can use its own tenant but is denied the other school's tenant")
    void s10_tenantIsolationBetweenSchools() throws Exception {
        UUID a = activeOnboarding("S10 School A");
        UUID b = activeOnboarding("S10 School B");
        Tenant tenantA = tenantRepository.findBySchoolId(schoolIdOf(a)).orElseThrow();
        Tenant tenantB = tenantRepository.findBySchoolId(schoolIdOf(b)).orElseThrow();
        assertThat(tenantA.getDatabaseName()).isNotEqualTo(tenantB.getDatabaseName());

        String adminA = token(createUser(PlatformRole.SCHOOL_ADMIN, schoolIdOf(a)));

        MvcResult own = perform(get("/api/v1/academic-years").header("X-Tenant-ID", tenantA.getTenantCode()), adminA);
        assertThat(own.getResponse().getStatus()).isEqualTo(200);

        // The filter throws TenantAccessDeniedException (@ResponseStatus(FORBIDDEN) -> 403 in a servlet container);
        // MockMvc surfaces the exception itself because there is no container error dispatch.
        assertThatThrownBy(() -> perform(get("/api/v1/academic-years").header("X-Tenant-ID", tenantB.getTenantCode()), adminA))
                .satisfies(t -> assertThat(t instanceof TenantAccessDeniedException
                        || org.assertj.core.util.Throwables.getRootCause(t) instanceof TenantAccessDeniedException)
                        .as("denied with TenantAccessDeniedException, got %s", t).isTrue());
        assertThat(org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(
                TenantAccessDeniedException.class, org.springframework.web.bind.annotation.ResponseStatus.class).value())
                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);

        // Master-side records of the other school are also denied
        assertThat(status(get("/api/v1/onboarding/" + b + "/workflow-status"), adminA)).isEqualTo(403);
        assertThat(status(get("/api/v1/schools/" + schoolIdOf(b)), adminA)).isEqualTo(403);
        assertThat(status(get("/api/v1/schools/" + schoolIdOf(a)), adminA)).isEqualTo(200);
    }

    // =====================================================================
    // Scenario 11 — audit records contain the actual authenticated actor
    // =====================================================================

    @Test
    @DisplayName("S11: every user-initiated onboarding transition records the authenticated actor")
    void s11_auditRecordsAuthenticatedActor() throws Exception {
        UUID id = approvedOnboarding("S11 School");

        // Spoofing attempt: the legacy approvedBy / confirmedBy fields can no longer influence the audit trail
        for (String action : List.of("ONBOARDING_INITIATED", "ONBOARDING_SUBMITTED", "ONBOARDING_VERIFIED",
                "PLAN_SELECTED", "PAYMENT_WAIVED", "ONBOARDING_APPROVED")) {
            OnboardingActivity a = activity(id, action);
            assertThat(a.getPerformedBy()).as(action).isEqualTo(platformAdmin.getId());
            assertThat(a.getPerformedByType()).as(action).isEqualTo(PerformerType.PLATFORM_USER);
        }

        PlatformUser secondAdmin = createUser(PlatformRole.PLATFORM_SUPER_ADMIN, null);
        assertThat(status(post("/api/v1/onboarding/" + id + "/reject").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rejectionReason\":\"Documents forged\"}"), token(secondAdmin))).isEqualTo(200);
        assertThat(activity(id, "ONBOARDING_REJECTED").getPerformedBy()).isEqualTo(secondAdmin.getId());
    }

    // =====================================================================
    // Scenario 12 — an ACTIVE onboarding cannot be rejected
    // =====================================================================

    @Test
    @DisplayName("S12: an ACTIVE onboarding cannot be rejected through the normal rejection workflow")
    void s12_activeOnboardingCannotBeRejected() throws Exception {
        UUID id = activeOnboarding("S12 School");

        assertThat(status(post("/api/v1/onboarding/" + id + "/reject").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rejectionReason\":\"changed our minds\"}"), adminToken)).isEqualTo(422);
        assertThat(statusOf(id)).isEqualTo(OnboardingStatus.ACTIVE);
        assertThat(onboardingRepository.findById(id).orElseThrow().getRejectionReason()).isNull();
    }

    // =====================================================================
    // Helpers — drive onboardings through the real API as a platform admin
    // =====================================================================

    private UUID initiate(String schoolName) throws Exception {
        String body = "{\"source\":\"DIRECT\",\"schoolName\":\"" + schoolName + " " + shortId() + "\","
                + "\"email\":\"school-" + shortId() + "@example.org\",\"phone\":\"9876543210\","
                + "\"city\":\"Pune\",\"state\":\"MH\",\"primaryBoard\":\"STATE\",\"contactPersonName\":\"Principal Rao\"}";
        MvcResult r = perform(post("/api/v1/onboarding").contentType(MediaType.APPLICATION_JSON).content(body), adminToken);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return UUID.fromString(JsonPath.read(r.getResponse().getContentAsString(), "$.data.id"));
    }

    private UUID verifiedWithPlan(String schoolName) throws Exception {
        UUID id = initiate(schoolName);
        expectOk(post("/api/v1/onboarding/" + id + "/submit"));
        expectOk(post("/api/v1/onboarding/" + id + "/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"verified\":true,\"notes\":\"docs ok\"}"));
        expectOk(post("/api/v1/onboarding/" + id + "/select-plan").contentType(MediaType.APPLICATION_JSON)
                .content("{\"planId\":\"" + activePlan().getId() + "\"}"));
        return id;
    }

    private UUID approvedOnboarding(String schoolName) throws Exception {
        UUID id = verifiedWithPlan(schoolName);
        expectOk(post("/api/v1/onboarding/" + id + "/payment/waive").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"integration test waiver\"}"));
        expectOk(post("/api/v1/onboarding/" + id + "/approve").param("notes", "approved"));
        assertThat(statusOf(id)).isEqualTo(OnboardingStatus.APPROVED);
        return id;
    }

    private UUID provisionedOnboarding(String schoolName) throws Exception {
        UUID id = approvedOnboarding(schoolName);
        expectOk(post("/api/v1/onboarding/" + id + "/provision"));
        assertThat(statusOf(id)).isEqualTo(OnboardingStatus.PROVISIONED);
        return id;
    }

    private UUID activeOnboarding(String schoolName) throws Exception {
        UUID id = approvedOnboarding(schoolName);
        expectOk(post("/api/v1/onboarding/" + id + "/activate"));
        assertThat(statusOf(id)).isEqualTo(OnboardingStatus.ACTIVE);
        return id;
    }

    private void expectOk(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult r = perform(request, adminToken);
        assertThat(r.getResponse().getStatus())
                .as("%s %s -> %s", r.getRequest().getMethod(), r.getRequest().getRequestURI(), r.getResponse().getContentAsString())
                .isEqualTo(200);
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, String bearer) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + bearer)).andReturn();
    }

    private int status(MockHttpServletRequestBuilder request, String bearer) throws Exception {
        return perform(request, bearer).getResponse().getStatus();
    }

    private OnboardingStatus statusOf(UUID onboardingId) {
        return onboardingRepository.findById(onboardingId).orElseThrow().getStatus();
    }

    private UUID schoolIdOf(UUID onboardingId) throws Exception {
        MvcResult r = perform(get("/api/v1/onboarding/" + onboardingId), adminToken);
        return UUID.fromString(JsonPath.read(r.getResponse().getContentAsString(), "$.data.schoolId"));
    }

    private OnboardingActivity activity(UUID onboardingId, String action) {
        return activityRepository.findByOnboardingIdOrderByCreatedAtDesc(onboardingId).stream()
                .filter(a -> action.equals(a.getAction()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No activity " + action + " for onboarding " + onboardingId));
    }

    private SubscriptionPlan activePlan() {
        return planRepository.findAll().stream()
                .filter(p -> p.getStatus() == PlanStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No ACTIVE subscription plan seeded"));
    }

    private PlatformUser createUser(PlatformRole role, UUID schoolId) {
        String id = shortId();
        PlatformUser user = new PlatformUser("USR-" + id.toUpperCase(), role.name().toLowerCase() + "-" + id + "@example.org",
                passwordEncoder.encode("Password@123"), "Test", role.name(), "+919800000000", role, schoolId);
        return userRepository.save(user);
    }

    private String token(PlatformUser user) {
        // The JWT filter only accepts access tokens bound (via "sid") to an ACTIVE server-side session.
        UUID sessionId = sessionService.open(user, null, Instant.now().plus(Duration.ofHours(1))).getId();
        return jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getUserCode(),
                user.getPlatformRole().name(), user.getSchoolId(), null, sessionId);
    }

    private static boolean tenantTableExists(String databaseName, String table) throws Exception {
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + databaseName;
        try (Connection c = DriverManager.getConnection(url, PG_USER, PG_PASS);
             ResultSet rs = c.getMetaData().getTables(null, "public", table, null)) {
            return rs.next();
        }
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** CredentialResolver backed by a map that can be armed to fail a number of times. */
    static final class FlakyCredentialResolver implements CredentialResolver {
        private final Map<String, String> values;
        final AtomicInteger failuresRemaining = new AtomicInteger();

        FlakyCredentialResolver(Map<String, String> values) {
            this.values = values;
        }

        @Override
        public String resolve(String referenceKey) {
            if (failuresRemaining.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                throw new IllegalStateException("Simulated secret-store outage resolving " + referenceKey);
            }
            String value = values.get(referenceKey);
            if (value == null) {
                throw new IllegalStateException("Unknown credential reference " + referenceKey);
            }
            return value;
        }
    }
}
