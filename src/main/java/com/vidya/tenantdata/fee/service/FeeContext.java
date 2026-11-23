package com.vidya.tenantdata.fee.service;

import com.vidya.common.exception.BusinessException;
import com.vidya.common.exception.ResourceNotFoundException;
import com.vidya.common.util.SecurityUtils;
import com.vidya.security.user.PlatformUserDetails;
import com.vidya.tenantdata.academic.entity.AcademicYear;
import com.vidya.tenantdata.academic.repository.AcademicYearRepository;
import com.vidya.tenantdata.fee.entity.FeeLedger;
import com.vidya.tenantdata.fee.repository.FeeLedgerRepository;
import com.vidya.tenantdata.fee.repository.FeeQueries;
import com.vidya.tenantdata.operations.entity.FeeSettings;
import com.vidya.tenantdata.operations.repository.FeeSettingsRepository;
import com.vidya.tenantdata.security.DataAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/** What every fee service needs: the year, today, who is acting, the year's fee settings and student access checks. */
@Component
@RequiredArgsConstructor
public class FeeContext {

    public static final String DOMAIN = "FEE";

    private final AcademicYearRepository years;
    private final FeeSettingsRepository settings;
    private final FeeLedgerRepository ledgers;
    private final FeeQueries queries;
    private final DataAccessService dataAccess;
    private final Clock clock;

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public Clock clock() {
        return clock;
    }

    /** The given year, else the current one. */
    public AcademicYear year(UUID academicYearId) {
        if (academicYearId != null) {
            return years.findByIdAndNotDeleted(academicYearId).orElseThrow(() -> new ResourceNotFoundException("AcademicYear", academicYearId));
        }
        return years.findCurrentYear().orElseThrow(() -> new BusinessException("FEE_NO_ACADEMIC_YEAR",
                "No current academic year is set; pass academicYearId"));
    }

    /** The year's fee settings, or the defaults (no late fine, partial payments allowed, prefix RCP). */
    public FeeSettings settings(UUID academicYearId) {
        return settings.findById(academicYearId).orElseGet(() -> new FeeSettings(academicYearId));
    }

    public UUID actorId() {
        return SecurityUtils.getCurrentUserId().orElse(null);
    }

    /** Name for receipts and audit columns: the school profile of the login, else its e-mail or user code. */
    public String actorName() {
        UUID id = actorId();
        return queries.personName(id).orElseGet(() -> {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            if (a != null && a.getPrincipal() instanceof PlatformUserDetails u) {
                return u.getEmail() != null ? u.getEmail() : u.getUserCode();
            }
            return id == null ? "system" : id.toString();
        });
    }

    /** Throws 403 unless the caller may see this student's fees (school-wide scope, or their own child / self). */
    public void checkStudent(UUID studentId) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !dataAccess.canAccessStudent(a, studentId, DOMAIN)) {
            throw new AccessDeniedException("You cannot see the fees of this student");
        }
    }

    public boolean fullScope() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && dataAccess.hasFullScope(a, DOMAIN);
    }

    /** Locks the ledger row until commit: every change to its lines goes through here. */
    public FeeLedger lockLedger(UUID ledgerId) {
        return ledgers.lock(ledgerId).orElseThrow(() -> new ResourceNotFoundException("FeeLedger", ledgerId));
    }

    public static BusinessException error(String code, String message) {
        return new BusinessException(code, message);
    }
}
