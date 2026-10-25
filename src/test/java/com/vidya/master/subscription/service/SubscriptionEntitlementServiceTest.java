package com.vidya.master.subscription.service;

import com.vidya.master.school.entity.School;
import com.vidya.master.school.entity.SchoolStatus;
import com.vidya.master.school.repository.SchoolRepository;
import com.vidya.master.subscription.dto.CalculatePlanQuoteRequest;
import com.vidya.master.subscription.dto.CalculatePlanQuoteResponse;
import com.vidya.master.subscription.dto.ModuleFeatureGroupResponse;
import com.vidya.master.subscription.dto.SchoolEntitlementResponse;
import com.vidya.master.subscription.dto.SchoolFeatureOverrideRequest;
import com.vidya.master.subscription.dto.SchoolFeatureOverrideResponse;
import com.vidya.master.subscription.entity.BillingCycle;
import com.vidya.master.subscription.entity.FeatureCategory;
import com.vidya.master.subscription.entity.FeatureStatus;
import com.vidya.master.subscription.entity.PlanStatus;
import com.vidya.master.subscription.entity.PlanTier;
import com.vidya.master.subscription.entity.SchoolSubscription;
import com.vidya.master.subscription.entity.SchoolSubscriptionFeatureOverride;
import com.vidya.master.subscription.entity.SubscriptionFeature;
import com.vidya.master.subscription.entity.SubscriptionPlan;
import com.vidya.master.subscription.entity.SubscriptionPlanFeature;
import com.vidya.master.subscription.repository.SchoolSubscriptionFeatureOverrideRepository;
import com.vidya.master.subscription.repository.SchoolSubscriptionRepository;
import com.vidya.master.subscription.repository.SubscriptionFeatureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionEntitlementService — Modular Features, Tiers, Pricing Quotes & Overrides")
class SubscriptionEntitlementServiceTest {

    @Mock
    private SchoolRepository schoolRepository;
    @Mock
    private SchoolSubscriptionRepository schoolSubscriptionRepository;
    @Mock
    private SubscriptionFeatureRepository subscriptionFeatureRepository;
    @Mock
    private SchoolSubscriptionFeatureOverrideRepository overrideRepository;

    @InjectMocks
    private SubscriptionEntitlementServiceImpl entitlementService;

    private UUID schoolId;
    private School sampleSchool;
    private SubscriptionPlan proPlan;
    private SubscriptionFeature staffAttendanceFeature;
    private SubscriptionFeature staffPayrollFeature;
    private SubscriptionFeature studentAdmissionsFeature;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        sampleSchool = new School("SCH-001", "Springdales Global School", null, null, null, null, "spring@vidya.io", "+919876543210", null, SchoolStatus.ACTIVE);

        proPlan = new SubscriptionPlan(
                "PRO", 1, "Professional Tier", "Standard K-12 institutional plan",
                BillingCycle.ANNUAL, new BigDecimal("49990.00"), "INR", 14,
                PlanTier.PRO, false, null, PlanStatus.ACTIVE, LocalDate.now()
        );

        staffAttendanceFeature = new SubscriptionFeature(
                "STAFF_ATTENDANCE", "Teacher & Staff Attendance", "Daily clock-in and logs",
                FeatureCategory.CORE, "STAFF_MANAGEMENT", "ATTENDANCE",
                new BigDecimal("400.00"), true, 22
        );

        staffPayrollFeature = new SubscriptionFeature(
                "STAFF_PAYROLL", "Teacher Payroll", "Salary computation and payslips",
                FeatureCategory.CORE, "STAFF_MANAGEMENT", "PAYROLL",
                new BigDecimal("1200.00"), true, 23
        );

        studentAdmissionsFeature = new SubscriptionFeature(
                "STUDENT_ADMISSIONS", "Student Admissions Wizard", "Admissions and onboarding",
                FeatureCategory.CORE, "STUDENT_MANAGEMENT", "ADMISSIONS",
                new BigDecimal("600.00"), true, 11
        );
    }

    @Test
    @DisplayName("Should dynamically calculate quote for CUSTOM tier with modular add-ons and volume scaling")
    void shouldCalculateQuoteWithTierAndAddons() {
        when(subscriptionFeatureRepository.findByFeatureCode("STAFF_ATTENDANCE"))
                .thenReturn(Optional.of(staffAttendanceFeature));
        when(subscriptionFeatureRepository.findByFeatureCode("STAFF_PAYROLL"))
                .thenReturn(Optional.of(staffPayrollFeature));

        CalculatePlanQuoteRequest request = new CalculatePlanQuoteRequest(
                PlanTier.CUSTOM,
                BillingCycle.ANNUAL,
                List.of("STAFF_ATTENDANCE", "STAFF_PAYROLL"),
                800, // 800 students -> 3 extra blocks of 100
                50,
                new BigDecimal("10.00") // 10% discount
        );

        CalculatePlanQuoteResponse quote = entitlementService.calculatePlanQuote(request);

        assertThat(quote).isNotNull();
        assertThat(quote.tier()).isEqualTo(PlanTier.CUSTOM);
        assertThat(quote.billingCycle()).isEqualTo(BillingCycle.ANNUAL);
        // Custom has base 0. Addons = (400*10) + (1200*10) + (3*150*10) = 4000 + 12000 + 4500 = 20500
        assertThat(quote.grossAmount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(quote.discountAmount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(quote.taxAmount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(quote.netPayableAmount()).isGreaterThan(quote.taxableAmount());
        assertThat(quote.itemizedBreakdown()).isNotEmpty();
    }

    @Test
    @DisplayName("Should evaluate hierarchical entitlements combining base plan and granular overrides")
    void shouldEvaluateHierarchicalEntitlements() {
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(sampleSchool));

        SubscriptionPlanFeature pf = new SubscriptionPlanFeature(proPlan, staffAttendanceFeature, true, 100);
        proPlan.addFeature(pf);

        SchoolSubscription sub = new SchoolSubscription(
                "SUB-001", schoolId, proPlan, "PRO", 1, LocalDate.now(), LocalDate.now().plusYears(1),
                BillingCycle.ANNUAL, new BigDecimal("49990.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("49990.00"), "INR", com.vidya.master.subscription.entity.SubscriptionStatus.ACTIVE,
                true, null
        );
        when(schoolSubscriptionRepository.findActiveSubscriptionsBySchoolId(schoolId))
                .thenReturn(List.of(sub));

        // School override: SuperUser specifically enabled STAFF_PAYROLL (an addon not in pro plan)
        SchoolSubscriptionFeatureOverride override = new SchoolSubscriptionFeatureOverride(
                sampleSchool, sub, "STAFF_PAYROLL", true, null, new BigDecimal("1200.00"), "Special addon", null
        );
        when(overrideRepository.findBySchoolId(schoolId)).thenReturn(List.of(override));
        when(subscriptionFeatureRepository.findAllByOrderByDisplayOrderAsc())
                .thenReturn(List.of(staffAttendanceFeature, staffPayrollFeature));

        SchoolEntitlementResponse response = entitlementService.getSchoolEntitlements(schoolId);

        assertThat(response).isNotNull();
        assertThat(response.schoolId()).isEqualTo(schoolId);
        assertThat(response.planTier()).isEqualTo(PlanTier.PRO);
        assertThat(response.modules()).isNotEmpty();

        ModuleFeatureGroupResponse staffModule = response.modules().stream()
                .filter(m -> m.moduleCode().equals("STAFF_MANAGEMENT"))
                .findFirst()
                .orElse(null);

        assertThat(staffModule).isNotNull();
        assertThat(staffModule.isModuleEnabled()).isTrue();
        assertThat(staffModule.subFeatures()).hasSize(2);
        // Both Attendance (from plan) and Payroll (from override) are enabled
        assertThat(staffModule.subFeatures().get(0).isEnabled()).isTrue();
        assertThat(staffModule.subFeatures().get(1).isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Should apply SuperUser granular feature override for a school")
    void shouldApplyFeatureOverride() {
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(sampleSchool));
        when(subscriptionFeatureRepository.findByFeatureCode("STAFF_PAYROLL"))
                .thenReturn(Optional.of(staffPayrollFeature));
        when(schoolSubscriptionRepository.findActiveSubscriptionsBySchoolId(schoolId))
                .thenReturn(Collections.emptyList());
        when(overrideRepository.findBySchoolIdAndFeatureCode(schoolId, "STAFF_PAYROLL"))
                .thenReturn(Optional.empty());

        SchoolSubscriptionFeatureOverride savedOvr = new SchoolSubscriptionFeatureOverride(
                sampleSchool, null, "STAFF_PAYROLL", true, 50, new BigDecimal("1200.00"), "SuperUser enabled payroll", null
        );
        when(overrideRepository.save(any())).thenReturn(savedOvr);

        SchoolFeatureOverrideRequest request = new SchoolFeatureOverrideRequest(
                "STAFF_PAYROLL", true, 50, new BigDecimal("1200.00"), "SuperUser enabled payroll"
        );

        SchoolFeatureOverrideResponse response = entitlementService.overrideSchoolFeature(schoolId, request, UUID.randomUUID());

        assertThat(response).isNotNull();
        assertThat(response.featureCode()).isEqualTo("STAFF_PAYROLL");
        assertThat(response.isEnabled()).isTrue();
        assertThat(response.customUsageLimit()).isEqualTo(50);
        verify(overrideRepository).save(any());
    }

    @Test
    @DisplayName("Should remove feature override and reset to plan default")
    void shouldRemoveFeatureOverride() {
        entitlementService.removeSchoolFeatureOverride(schoolId, "STAFF_PAYROLL");
        verify(overrideRepository).deleteBySchoolIdAndFeatureCode(schoolId, "STAFF_PAYROLL");
    }

    @Test
    @DisplayName("Should return true for isModuleEnabled when any sub-feature is active")
    void shouldCheckModuleLevelEnablement() {
        when(subscriptionFeatureRepository.findByParentModuleCode("STAFF_MANAGEMENT"))
                .thenReturn(List.of(staffAttendanceFeature, staffPayrollFeature));

        // Override staffAttendance to true
        SchoolSubscriptionFeatureOverride ovr = new SchoolSubscriptionFeatureOverride(
                sampleSchool, null, "STAFF_ATTENDANCE", true, null, BigDecimal.ZERO, null, null
        );
        when(overrideRepository.findBySchoolIdAndFeatureCode(schoolId, "STAFF_ATTENDANCE"))
                .thenReturn(Optional.of(ovr));

        boolean moduleEnabled = entitlementService.isModuleEnabled(schoolId, "STAFF_MANAGEMENT");

        assertThat(moduleEnabled).isTrue();
    }
}
