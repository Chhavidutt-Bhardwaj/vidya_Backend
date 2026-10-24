package com.vidya.master.community.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Entry representing a section/module code and an action code assignment.
 *
 * @param sectionCode Identifier of the module/section (e.g. 4 for Students, 11 for Teachers).
 * @param actionCode  Identifier of the permitted action (e.g. -9 for View, -8 for Edit).
 * @param granted     Whether this action is permitted (true) or denied (false).
 */
public record RolePermissionEntry(
        @NotNull(message = "Section code is required")
        Integer sectionCode,

        @NotNull(message = "Action code is required")
        Integer actionCode,

        boolean granted
) {}
