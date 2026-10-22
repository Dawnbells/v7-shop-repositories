package cn.v7soft.admin.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopifyHmacTest {
    private static final String SECRET = "hush";
    private static final String DOC_HMAC = "700e2dadb827fcc8609e9d5ce208b2e9cdaab9df07390d2cbca10d7c328fc4bf";

    /**
     * Shopify 文档中的 OAuth 回调示例
     */
    private static Map<String, String[]> docParams() {
        Map<String, String[]> params = new LinkedHashMap<>();
        // 故意打乱顺序，验证按键排序
        params.put("timestamp", new String[]{"1337178173"});
        params.put("hmac", new String[]{DOC_HMAC});
        params.put("shop", new String[]{"some-shop.myshopify.com"});
        params.put("code", new String[]{"0907a61c0c8d55e99db179b68161bc00"});
        params.put("state", new String[]{"0.6784241404160823"});
        return params;
    }

    @Test
    @DisplayName("verifyQuery：Shopify 文档示例校验通过")
    void shouldVerifyShopifyDocExample() {
        assertTrue(ShopifyHmac.verifyQuery(docParams(), SECRET));
    }

    @Test
    @DisplayName("verifyQuery：hmac 大写 hex 也能通过")
    void shouldAcceptUppercaseHex() {
        Map<String, String[]> params = docParams();
        params.put("hmac", new String[]{DOC_HMAC.toUpperCase()});
        assertTrue(ShopifyHmac.verifyQuery(params, SECRET));
    }

    @Test
    @DisplayName("verifyQuery：任一参数值改动一个字符都校验失败")
    void shouldRejectWhenAnyParamByteChanged() {
        for (String key : docParams().keySet()) {
            String original = docParams().get(key)[0];
            for (int i = 0; i < original.length(); i++) {
                Map<String, String[]> params = docParams();
                char c = original.charAt(i);
                char replaced = c == '0' ? '1' : '0';
                params.put(key, new String[]{original.substring(0, i) + replaced + original.substring(i + 1)});
                assertFalse(ShopifyHmac.verifyQuery(params, SECRET), "改动 " + key + " 第 " + i + " 个字符后应失败");
            }
        }
    }

    @Test
    @DisplayName("verifyQuery：多出参数、缺少 hmac、secret 错误或为空时失败")
    void shouldRejectInvalidInputs() {
        Map<String, String[]> extra = docParams();
        extra.put("host", new String[]{"abc"});
        assertFalse(ShopifyHmac.verifyQuery(extra, SECRET));

        Map<String, String[]> noHmac = docParams();
        noHmac.remove("hmac");
        assertFalse(ShopifyHmac.verifyQuery(noHmac, SECRET));

        assertFalse(ShopifyHmac.verifyQuery(docParams(), "hush2"));
        assertFalse(ShopifyHmac.verifyQuery(docParams(), ""));
        assertFalse(ShopifyHmac.verifyQuery(docParams(), null));
        assertFalse(ShopifyHmac.verifyQuery(null, SECRET));
    }

    @Test
    @DisplayName("verifyBody：原始字节的 base64 HMAC 校验通过，改动正文或签名失败")
    void shouldVerifyBodyHmac() throws Exception {
        byte[] body = "{\"id\":820982911946154508,\"email\":\"jon@example.com\"}".getBytes(StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String header = Base64.getEncoder().encodeToString(mac.doFinal(body));

        assertTrue(ShopifyHmac.verifyBody(body, header, SECRET));

        byte[] tampered = body.clone();
        tampered[5] = (byte) (tampered[5] + 1);
        assertFalse(ShopifyHmac.verifyBody(tampered, header, SECRET));

        char first = header.charAt(0);
        String tamperedHeader = (first == 'A' ? 'B' : 'A') + header.substring(1);
        assertFalse(ShopifyHmac.verifyBody(body, tamperedHeader, SECRET));

        assertFalse(ShopifyHmac.verifyBody(body, header, "other"));
        assertFalse(ShopifyHmac.verifyBody(body, null, SECRET));
        assertFalse(ShopifyHmac.verifyBody(body, header, null));
        assertFalse(ShopifyHmac.verifyBody(null, header, SECRET));
    }
}
