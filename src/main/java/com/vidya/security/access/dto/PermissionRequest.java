package com.vidya.security.access.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

import com.vidya.security.access.entity.AccessPermission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create (code required) or update (code ignored; it is immutable) a permission. */
public record PermissionRequest(
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "code must be UPPER_SNAKE_CASE")
        @Size(max = 100) String code,
        @NotBlank @Size(max = 200) String name,
        String description,
        @Size(max = 50) String moduleCode,
        AccessPermission.PermissionScope scope,
        /** Subscription feature/module that must be enabled for school users; null = always available. */
        @Size(max = 50) String featureCode,
        Boolean active,
        /** Frontend permissionsMatrix cells; null = leave unchanged on update, [] = none. */
        List<@Valid MatrixCellDto> matrixCells
) {
    /** sectionCode 1..999; actionCode -9 VIEW, -8 EDIT, -7 DELETE, -6 APPROVE, -5 SCOPED, -4 FULL. */
    public record MatrixCellDto(
            @NotNull @Min(1) @Max(999) Integer sectionCode,
            @NotNull @Min(-9) @Max(-4) Integer actionCode
    ) {}
}
