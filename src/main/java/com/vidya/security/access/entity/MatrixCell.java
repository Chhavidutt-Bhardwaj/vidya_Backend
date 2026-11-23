package com.vidya.security.access.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * One cell of the frontend {@code permissionsMatrix}: holding the owning permission shows
 * {@code actionCode} in {@code sectionCode}. Codes are listed in
 * {@link com.vidya.security.service.UserPermissionMatrixServiceImpl}.
 */
@Embeddable
public record MatrixCell(
        @Column(name = "section_code", nullable = false) Integer sectionCode,
        @Column(name = "action_code", nullable = false) Integer actionCode
) {
    public static final int MIN_ACTION = -9;
    public static final int MAX_ACTION = -4;

    public boolean isValid() {
        return sectionCode != null && actionCode != null
                && sectionCode >= 1 && sectionCode <= 999
                && actionCode >= MIN_ACTION && actionCode <= MAX_ACTION;
    }
}
