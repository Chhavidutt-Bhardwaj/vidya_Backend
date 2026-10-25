package com.vidya.master.subscription.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Functional module grouping containing its modular base price and granular sub-features.
 * e.g., STAFF_MANAGEMENT module containing Staff Directory, Attendance, Payroll, Leave, etc.
 */
public record ModuleFeatureGroupResponse(
        String moduleCode,
        String moduleName,
        String description,
        BigDecimal moduleBasePrice,
        boolean isModuleEnabled,
        List<ModuleFeatureItemResponse> subFeatures
) {}
