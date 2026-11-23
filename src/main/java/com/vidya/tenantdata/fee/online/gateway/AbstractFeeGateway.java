package com.vidya.tenantdata.fee.online.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.util.Map;

/** JSON calls, error mapping, HMAC and rupee conversion shared by the gateways. */
@Slf4j
abstract class AbstractFeeGateway implements FeeGateway {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    protected final GatewayHttp http;
    protected final ObjectMapper json = new ObjectMapper();

    protected AbstractFeeGateway(GatewayHttp http) {
        this.http = http;
    }

    protected ObjectNode obj() {
        return json.createObjectNode();
    }

    protected JsonNode call(String method, String url, Map<String, String> headers, JsonNode body) {
        GatewayHttp.Response r;
        try {
            r = http.send(method, url, headers, body == null ? null : json.writeValueAsString(body));
        } catch (IOException e) {
            throw new GatewayException(provider(), "could not reach the gateway: " + e.getMessage(), e);
        }
        JsonNode node = parse(r.body());
        if (!r.ok()) {
            String msg = errorMessage(node);
            log.warn("{} {} {} -> {} {}", provider(), method, url, r.status(), msg);
            throw new GatewayException(provider(), r.status(), msg == null || msg.isBlank() ? "HTTP " + r.status() : msg);
        }
        return node;
    }

    /** The gateway's own error text from an error body. */
    protected abstract String errorMessage(JsonNode body);

    protected JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return obj();
        }
        try {
            return json.readTree(body);
        } catch (IOException e) {
            throw new GatewayException(provider(), 0, "unreadable response: " + body.substring(0, Math.min(200, body.length())));
        }
    }

    protected static byte[] hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Constant-time comparison of two signatures. */
    protected static boolean sameSignature(String expected, String received) {
        return received != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), received.trim().getBytes(StandardCharsets.UTF_8));
    }

    protected static BigDecimal rupees(long paise) {
        return BigDecimal.valueOf(paise, 2);
    }

    /** "1234.5" (rupees) → 123450 paise. */
    protected static long paise(JsonNode rupees) {
        if (rupees == null || rupees.isNull() || rupees.isMissingNode()) {
            return 0;
        }
        return new BigDecimal(rupees.asText("0")).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    protected static String text(JsonNode n, String... path) {
        JsonNode cur = n;
        for (String p : path) {
            if (cur == null) {
                return null;
            }
            cur = cur.get(p);
        }
        return cur == null || cur.isNull() || cur.isMissingNode() ? null : cur.asText();
    }

    protected static String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
