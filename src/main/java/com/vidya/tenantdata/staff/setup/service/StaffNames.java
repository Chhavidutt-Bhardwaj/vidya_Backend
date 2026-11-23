package com.vidya.tenantdata.staff.setup.service;

import com.vidya.tenantdata.staff.entity.Staff;
import com.vidya.tenantdata.user.account.TenantUserAccountService;

/** Display name of a staff member. */
final class StaffNames {

    private StaffNames() {
    }

    static String name(Staff s) {
        return s.getTenantUser() != null ? TenantUserAccountService.name(s.getTenantUser()) : s.getStaffCode();
    }
}
