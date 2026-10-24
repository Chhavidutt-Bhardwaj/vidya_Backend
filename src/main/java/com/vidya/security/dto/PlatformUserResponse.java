package com.vidya.security.dto;

import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformUserResponse {
    private UUID id;
    private String userCode;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private String phone;
    private UserStatus status;
    private PlatformRole platformRole;
    private UUID schoolId;
    private String tenantCode;
    private Set<String> permissions;
    /** Universal module/section code -> granted action codes matrix */
    private java.util.Map<Integer, Set<Integer>> permissionsMatrix;
    private Instant lastLoginAt;
    private Instant createdAt;
}
