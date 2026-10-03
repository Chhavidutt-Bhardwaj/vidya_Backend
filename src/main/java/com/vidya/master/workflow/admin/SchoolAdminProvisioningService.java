package com.vidya.master.workflow.admin;

import com.vidya.master.workflow.admin.dto.InitialSchoolAdminRequest;
import com.vidya.master.workflow.admin.dto.InitialSchoolAdminResponse;

import java.util.UUID;

/**
 * Service responsible for provisioning the initial school administrator/owner
 * account upon tenant database readiness.
 */
public interface SchoolAdminProvisioningService {

    InitialSchoolAdminResponse provisionInitialAdmin(UUID schoolId, UUID onboardingId, InitialSchoolAdminRequest request);
}
