package com.vidya.security.session.service;

import com.vidya.security.session.entity.UserSession;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Locale;

/**
 * Who is calling: IP plus a coarse user-agent breakdown for the session and login-history screens.
 * The IP is the socket address; behind a proxy, set {@code server.forward-headers-strategy=native}
 * so Spring resolves the client IP from X-Forwarded-For.
 */
public record ClientInfo(String ipAddress, String userAgent, String deviceType, String browser,
                         String operatingSystem) implements UserSession.ClientInfoView {

    public static final ClientInfo UNKNOWN = new ClientInfo(null, null, "UNKNOWN", "UNKNOWN", "UNKNOWN");

    public static ClientInfo from(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        return of(request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    public static ClientInfo of(String ip, String userAgent) {
        String ua = userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent;
        String l = ua == null ? "" : ua.toLowerCase(Locale.ROOT);
        return new ClientInfo(truncate(ip, 64), ua, device(l), browser(l), os(l));
    }

    private static String device(String ua) {
        if (ua.isEmpty()) return "UNKNOWN";
        if (ua.contains("ipad") || ua.contains("tablet")) return "TABLET";
        if (ua.contains("mobi") || ua.contains("iphone") || ua.contains("android")) return "MOBILE";
        if (ua.contains("okhttp") || ua.contains("dart") || ua.contains("postman") || ua.contains("curl")) return "API_CLIENT";
        return "DESKTOP";
    }

    private static String browser(String ua) {
        if (ua.isEmpty()) return "UNKNOWN";
        if (ua.contains("edg/")) return "Edge";
        if (ua.contains("opr/") || ua.contains("opera")) return "Opera";
        if (ua.contains("chrome/") && !ua.contains("chromium")) return "Chrome";
        if (ua.contains("firefox/")) return "Firefox";
        if (ua.contains("safari/")) return "Safari";
        if (ua.contains("postman")) return "Postman";
        if (ua.contains("okhttp") || ua.contains("dart")) return "Mobile App";
        return "Other";
    }

    private static String os(String ua) {
        if (ua.isEmpty()) return "UNKNOWN";
        if (ua.contains("windows")) return "Windows";
        if (ua.contains("android")) return "Android";
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) return "iOS";
        if (ua.contains("mac os") || ua.contains("macintosh")) return "macOS";
        if (ua.contains("linux")) return "Linux";
        return "Other";
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }
}
