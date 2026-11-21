package com.vidya.security.jwt;

import com.vidya.security.service.PlatformUserDetailsService;
import com.vidya.security.session.service.SessionService;
import com.vidya.security.user.PlatformUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Authenticates bearer access tokens. A token only authenticates when:
 * <ul>
 *   <li>its signature/expiry are valid and it is an ACCESS token,</li>
 *   <li>its {@code sid} points to an ACTIVE, unexpired session of the same user (so logout and
 *       revocation take effect immediately), and</li>
 *   <li>the account is still enabled and not locked.</li>
 * </ul>
 * Authorities are resolved from the DB on every request (cached briefly), never read from the token.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Current access fingerprint; compare with the access token's {@code av} claim. */
    public static final String ACCESS_VERSION_HEADER = "X-Access-Version";


    private final JwtTokenProvider tokenProvider;
    private final PlatformUserDetailsService userDetailsService;
    private final SessionService sessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = extractJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                if (tokenProvider.isRefreshToken(jwt)) {
                    log.warn("Attempt to use refresh token as bearer access token rejected");
                    filterChain.doFilter(request, response);
                    return;
                }
                UUID userId = tokenProvider.getUserIdFromToken(jwt);
                UUID sessionId = tokenProvider.getSessionIdFromToken(jwt);
                if (sessionId == null || !sessionService.validateAndTouch(sessionId, userId)) {
                    log.debug("Access token rejected: session [{}] is not active", sessionId);
                    filterChain.doFilter(request, response);
                    return;
                }

                String email = tokenProvider.getEmailFromToken(jwt);
                PlatformUserDetails userDetails = userDetailsService.loadPrincipal(email, sessionId);
                if (!userId.equals(userDetails.getUserId())
                        || !userDetails.isEnabled() || !userDetails.isAccountNonLocked()) {
                    log.warn("Access token rejected for user [{}]: account mismatch, disabled or locked", userId);
                    filterChain.doFilter(request, response);
                    return;
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                if (userDetails.getEffectiveAccess() != null) {
                    // Lets the UI notice that its token's access snapshot (claim "av") is stale and refresh it.
                    response.setHeader(ACCESS_VERSION_HEADER, userDetails.getEffectiveAccess().version());
                }
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
