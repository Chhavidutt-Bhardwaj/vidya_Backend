package com.vidya.tenantdata.operations.service;

import com.vidya.tenantdata.operations.dto.OperationsDtos.AttendanceSettingsRequest;
import com.vidya.tenantdata.operations.dto.OperationsDtos.AttendanceSettingsResponse;
import com.vidya.tenantdata.operations.entity.AttendanceSettings;
import com.vidya.tenantdata.operations.enums.LateCountsAs;
import com.vidya.tenantdata.operations.repository.AttendanceSettingsRepository;
import com.vidya.tenantdata.user.account.SchoolContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** The school's attendance marking rules. Attendance marking reads them through {@link #policy()}. */
@Service
@RequiredArgsConstructor
public class AttendancePolicyService {

    static final short ID = 1;

    /** What attendance marking needs: {@code backdateDays} null = no limit. */
    public record Policy(Integer backdateDays, LateCountsAs lateCountsAs, BigDecimal minimumPercent) {
    }

    private final AttendanceSettingsRepository repository;
    private final SchoolContext context;

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public Policy policy() {
        AttendanceSettings s = repository.findById(ID).orElseGet(AttendanceSettings::new);
        return new Policy(s.getBackdateDays(), s.getLateCountsAs(), s.getMinimumPercent());
    }

    @Transactional(readOnly = true, transactionManager = "tenantTransactionManager")
    public AttendanceSettingsResponse get() {
        return repository.findById(ID).map(s -> response(s, true)).orElseGet(() -> response(new AttendanceSettings(), false));
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public AttendanceSettingsResponse save(AttendanceSettingsRequest r) {
        AttendanceSettings s = repository.findById(ID).orElseGet(AttendanceSettings::new);
        AttendanceSettings defaults = new AttendanceSettings();
        s.setMarkingDeadline(r.markingDeadline());
        s.setLateAfter(r.lateAfter());
        s.setBackdateDays(r.backdateDays());
        s.setLateCountsAs(r.lateCountsAs() != null ? r.lateCountsAs() : defaults.getLateCountsAs());
        s.setMinimumPercent((r.minimumPercent() != null ? r.minimumPercent() : defaults.getMinimumPercent()).setScale(2, RoundingMode.HALF_UP));
        s.setNotifyGuardianOnAbsence(Boolean.TRUE.equals(r.notifyGuardianOnAbsence()));
        s.setUpdatedBy(context.actorId());
        return response(repository.save(s), true);
    }

    private static AttendanceSettingsResponse response(AttendanceSettings s, boolean saved) {
        return new AttendanceSettingsResponse(s.getMarkingDeadline(), s.getLateAfter(), s.getBackdateDays(), s.getLateCountsAs(),
                s.getMinimumPercent(), s.isNotifyGuardianOnAbsence(), saved);
    }
}
