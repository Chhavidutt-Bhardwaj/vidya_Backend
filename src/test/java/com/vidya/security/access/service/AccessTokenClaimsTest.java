package com.vidya.security.access.service;

import com.vidya.security.access.DataDomain;
import com.vidya.security.access.DataScope;
import com.vidya.security.access.SeedRoleMatrix;
import com.vidya.security.entity.PlatformRole;
import com.vidya.security.entity.PlatformUser;
import com.vidya.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Access snapshot in the JWT + data scopes + access version")
class AccessTokenClaimsTest {

    private static final String SECRET = "vidya-dev-secret-key-change-in-production-min-256-bits-length";
    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, 1800, 604800);

    private static PlatformUser user(PlatformRole role) {
        PlatformUser u = new PlatformUser("USR-" + role, role.name().toLowerCase() + "@x.io", "h", "F", "L", null, role,
                role.getScope().isSchoolBound() ? UUID.randomUUID() : null);
        u.setId(UUID.randomUUID());
        return u;
    }

    private Claims parse(String token) {
        return Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).build()
                .parseSignedClaims(token).getPayload();
    }

    @Test
    @DisplayName("teacher token carries perms, roles, scope, features, data scopes and version")
    void teacherToken() {
        PlatformUser teacher = user(PlatformRole.TEACHER);
        EffectiveAccess access = SeedRoleMatrix.accessFor(teacher);

        Claims c = parse(provider.generateAccessToken(teacher.getId(), teacher.getEmail(), teacher.getUserCode(), "TEACHER",
                teacher.getSchoolId(), "TNT-1", UUID.randomUUID(), AccessTokenClaims.of(access)));

        assertThat(c.get("perms", List.class)).contains("ATTENDANCE_CREATE", "STUDENT_READ").doesNotContain("STUDENT_DELETE");
        assertThat(c.get("roles", List.class)).containsExactly("TEACHER");
        assertThat(c.get("scope", String.class)).isEqualTo("SCHOOL");
        assertThat(c.get("ds", Map.class)).containsEntry("STUDENT", "OWN").containsEntry("ATTENDANCE", "OWN");
        assertThat(c.get("av", String.class)).isEqualTo(access.version()).hasSize(16);
        assertThat(c.get("su")).isNull();
        assertThat(c.get("type", String.class)).isEqualTo("ACCESS");
    }

    @Test
    @DisplayName("super user gets su=true instead of a 180-entry permission list (keeps the token small)")
    void superUserCompact() {
        EffectiveAccess access = SeedRoleMatrix.accessFor(user(PlatformRole.PLATFORM_SUPER_ADMIN));
        Map<String, Object> claims = AccessTokenClaims.of(access);

        assertThat(claims).containsEntry("su", true).doesNotContainKey("perms");
    }

    @Test
    @DisplayName("snapshot claims can never overwrite identity/session claims")
    void reservedClaimsProtected() {
        PlatformUser u = user(PlatformRole.TEACHER);
        UUID sid = UUID.randomUUID();
        Map<String, Object> evil = new HashMap<>();
        evil.put("sub", "someone-else");
        evil.put("type", "REFRESH");
        evil.put("sid", UUID.randomUUID().toString());
        evil.put("role", "PLATFORM_SUPER_ADMIN");

        Claims c = parse(provider.generateAccessToken(u.getId(), u.getEmail(), u.getUserCode(), "TEACHER",
                u.getSchoolId(), null, sid, evil));

        assertThat(c.getSubject()).isEqualTo(u.getId().toString());
        assertThat(c.get("type", String.class)).isEqualTo("ACCESS");
        assertThat(c.get("sid", String.class)).isEqualTo(sid.toString());
        assertThat(c.get("role", String.class)).isEqualTo("TEACHER");
    }

    @Test
    @DisplayName("data scope: ALL with <DOMAIN>_SCOPE_ALL, OWN by default for school users, ALL for platform accounts")
    void dataScopes() {
        assertThat(SeedRoleMatrix.accessFor(user(PlatformRole.SCHOOL_ADMIN)).dataScope(DataDomain.STUDENT)).isEqualTo(DataScope.ALL);
        assertThat(SeedRoleMatrix.accessFor(user(PlatformRole.SCHOOL_ADMIN)).dataScope(DataDomain.MARKS)).isEqualTo(DataScope.ALL);
        assertThat(SeedRoleMatrix.accessFor(user(PlatformRole.PRINCIPAL)).dataScope(DataDomain.ATTENDANCE)).isEqualTo(DataScope.ALL);
        assertThat(SeedRoleMatrix.accessFor(user(PlatformRole.COORDINATOR)).dataScope(DataDomain.STUDENT)).isEqualTo(DataScope.ALL);
        assertThat(SeedRoleMatrix.accessFor(user(PlatformRole.ACCOUNTS)).dataScope(DataDomain.STUDENT)).isEqualTo(DataScope.ALL);
        assertThat(SeedRoleMatrix.accessFor(user(PlatformRole.TEACHER)).dataScope(DataDomain.STUDENT)).isEqualTo(DataScope.OWN);
        assertThat(SeedRoleMatrix.accessFor(user(PlatformRole.PARENT)).dataScope(DataDomain.MARKS)).isEqualTo(DataScope.OWN);
        assertThat(SeedRoleMatrix.accessFor(user(PlatformRole.SCHOOL_STAFF)).dataScope(DataDomain.STUDENT)).isEqualTo(DataScope.OWN);
        assertThat(SeedRoleMatrix.accessFor(user(PlatformRole.PLATFORM_ADMIN)).dataScope(DataDomain.STUDENT)).isEqualTo(DataScope.ALL);
        assertThat(SeedRoleMatrix.accessFor(user(PlatformRole.FIELD_AGENT)).dataScope(DataDomain.STUDENT)).isEqualTo(DataScope.ALL);
    }

    @Test
    @DisplayName("access version is stable for the same access and changes when a permission changes")
    void versionTracksChanges() {
        EffectiveAccess a = SeedRoleMatrix.accessFor(user(PlatformRole.TEACHER));
        EffectiveAccess same = new EffectiveAccess(a.userId(), a.accountType(), a.scope(), a.schoolId(), a.roles(),
                a.globalRoles(), a.permissions(), a.enabledFeatures(), a.superUser(), Instant.now());
        Set<String> fewer = new HashSet<>(a.permissions());
        fewer.remove("MARKS_ENTRY");
        EffectiveAccess changed = new EffectiveAccess(a.userId(), a.accountType(), a.scope(), a.schoolId(), a.roles(),
                a.globalRoles(), fewer, a.enabledFeatures(), a.superUser(), Instant.now());

        assertThat(same.version()).isEqualTo(a.version());
        assertThat(changed.version()).isNotEqualTo(a.version());
    }
}
