package com.vidya.tenantdata.fee.online.service;

import com.vidya.common.util.SecurityUtils;
import com.vidya.tenantdata.fee.online.repository.OnlineFeeQueries;
import com.vidya.tenantdata.fee.online.repository.OnlineFeeQueries.GuardianRow;
import com.vidya.tenantdata.fee.service.FeeContext;
import com.vidya.tenantdata.security.OwnRecordsResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Who is paying: office staff (FEE_MANAGE + school-wide fee scope) act for any payer; a parent acts for themselves and their own
 * children. A parent with several children has one student_guardian row per child, tied together by their login (else phone).
 */
@Component
@RequiredArgsConstructor
public class PayerAccess {

    private final FeeContext ctx;
    private final OwnRecordsResolver own;
    private final OnlineFeeQueries queries;

    public boolean office() {
        return ctx.fullScope() && SecurityUtils.hasAuthority("FEE_MANAGE");
    }

    /** The caller's tenant_user ids (empty for system jobs). */
    public Set<UUID> myTenantUsers() {
        return SecurityUtils.getCurrentUserId().map(id -> own.resolve(id).tenantUserIds()).orElse(Set.of());
    }

    /** The caller's own guardian rows (a parent's link to each child). */
    public List<GuardianRow> myGuardianRows() {
        return queries.guardiansOfLogins(myTenantUsers());
    }

    /** Throws 403 unless the caller may see every one of the students' fees. */
    public void checkStudents(Collection<UUID> studentIds) {
        studentIds.forEach(ctx::checkStudent);
    }

    /** Office, or the payer themselves (their login is on the mandate / link). */
    public void checkPayer(UUID guardianUserId) {
        if (office()) {
            return;
        }
        if (guardianUserId == null || !myTenantUsers().contains(guardianUserId)) {
            throw new AccessDeniedException("Only the payer or the school office can do this");
        }
    }

    /**
     * The payer's guardian row: the given one (office, or a parent naming their own row), else the caller's row for the first
     * of the students, else the first student's primary guardian (office).
     */
    public GuardianRow payer(UUID guardianId, List<UUID> studentIds) {
        if (guardianId != null) {
            GuardianRow g = queries.guardian(guardianId).orElseThrow(() -> FeeContext.error("FEE_GUARDIAN_NOT_FOUND", "Guardian " + guardianId + " not found"));
            if (!office() && (g.guardianUserId() == null || !myTenantUsers().contains(g.guardianUserId()))) {
                throw new AccessDeniedException("You can only pay or set up a mandate as yourself");
            }
            return g;
        }
        List<GuardianRow> mine = myGuardianRows();
        if (!mine.isEmpty()) {
            Optional<GuardianRow> forStudent = studentIds == null || studentIds.isEmpty() ? Optional.of(mine.getFirst())
                    : mine.stream().filter(g -> studentIds.contains(g.studentId())).findFirst();
            if (forStudent.isPresent()) {
                return forStudent.get();
            }
        }
        if (office() && studentIds != null && !studentIds.isEmpty()) {
            return queries.guardiansOfStudents(List.of(studentIds.getFirst())).stream().findFirst()
                    .orElseThrow(() -> FeeContext.error("FEE_GUARDIAN_NOT_FOUND", "The student has no active guardian with contact details"));
        }
        throw FeeContext.error("FEE_GUARDIAN_REQUIRED", "Choose the guardian who pays (guardianId)");
    }

    /**
     * The payer's rows for each of their children: same login, else same phone. Keyed by student; only the given students when set.
     */
    public List<GuardianRow> payerRows(GuardianRow payer, Collection<UUID> limitTo) {
        List<GuardianRow> rows = new ArrayList<>(List.of(payer));
        rows.addAll(payer.guardianUserId() != null ? queries.guardiansOfLogins(List.of(payer.guardianUserId()))
                : queries.guardiansByPhone(lastTen(payer.phone())));
        Set<UUID> seen = new HashSet<>();
        return rows.stream().filter(g -> limitTo == null || limitTo.contains(g.studentId())).filter(g -> seen.add(g.studentId())).toList();
    }

    static String lastTen(String phone) {
        String d = phone == null ? "" : phone.replaceAll("\\D", "");
        return d.length() <= 10 ? d : d.substring(d.length() - 10);
    }
}
