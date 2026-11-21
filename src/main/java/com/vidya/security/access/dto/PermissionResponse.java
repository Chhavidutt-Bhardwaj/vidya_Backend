package com.vidya.security.access.dto;

import com.vidya.security.access.entity.MatrixCell;
import java.util.List;

import com.vidya.security.access.entity.AccessPermission;

import java.util.UUID;

public record PermissionResponse(
        UUID id,
        String code,
        String name,
        String description,
        String moduleCode,
        AccessPermission.PermissionScope scope,
        String featureCode,
        boolean active,
        List<PermissionRequest.MatrixCellDto> matrixCells
) {
    public static PermissionResponse from(AccessPermission p) {
        return new PermissionResponse(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getModuleCode(),
                p.getScope(), p.getFeatureCode(), p.isActive(),
                p.getMatrixCells().stream()
                        .sorted(java.util.Comparator.comparing(MatrixCell::sectionCode).thenComparing(MatrixCell::actionCode))
                        .map(c -> new PermissionRequest.MatrixCellDto(c.sectionCode(), c.actionCode()))
                        .toList());
    }
}
