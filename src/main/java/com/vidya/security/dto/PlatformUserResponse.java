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
    private Set<String> permissions;
    private Instant lastLoginAt;
    private Instant createdAt;
}
