package cn.v7soft.admin.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Shopify HMAC 校验：
 * <ul>
 *     <li>OAuth / App URL 查询串：去掉 hmac，键按字典序排序，k=v 用 &amp; 连接，HMAC-SHA256 后 hex</li>
 *     <li>webhook 正文：对原始字节做 HMAC-SHA256 后 base64，与 X-Shopify-Hmac-Sha256 比较</li>
 * </ul>
 * 比较均为常量时间。
 */
public final class ShopifyHmac {
    private static final String ALGORITHM = "HmacSHA256";

    private ShopifyHmac() {
    }

    /**
     * 校验查询串签名
     * @param params 请求参数（如 {@code HttpServletRequest#getParameterMap()}），必须包含 hmac
     * @param secret 公开应用 Client Secret
     */
    public static boolean verifyQuery(Map<String, String[]> params, String secret) {
        if (params == null || isBlank(secret)) {
            return false;
        }
        String[] hmacValues = params.get("hmac");
        if (hmacValues == null || hmacValues.length != 1 || isBlank(hmacValues[0])) {
            return false;
        }
        Map<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            String key = entry.getKey();
            if (key == null || "hmac".equals(key)) {
                continue;
            }
            String[] values = entry.getValue();
            // 同名多值时 Shopify 以 ["a", "b"] 形式参与签名；正常回调不会出现
            String value = values == null || values.length == 0 ? ""
                    : values.length == 1 ? values[0]
                    : Arrays.stream(values).map(v -> "\"" + v + "\"")
                    .collect(Collectors.joining(", ", "[", "]"));
            sorted.put(key, value);
        }
        String message = sorted.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
        byte[] expected = hmac(message.getBytes(StandardCharsets.UTF_8), secret);
        String expectedHex = HexFormat.of().formatHex(expected);
        return constantTimeEquals(expectedHex, hmacValues[0].toLowerCase(Locale.ROOT));
    }

    /**
     * 校验 webhook 正文签名
     * @param body 原始请求字节
     * @param headerBase64 X-Shopify-Hmac-Sha256 头
     * @param secret 公开应用 Client Secret
     */
    public static boolean verifyBody(byte[] body, String headerBase64, String secret) {
        if (body == null || isBlank(headerBase64) || isBlank(secret)) {
            return false;
        }
        String expected = Base64.getEncoder().encodeToString(hmac(body, secret));
        return constantTimeEquals(expected, headerBase64.trim());
    }

    private static byte[] hmac(byte[] data, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 不可用", e);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
