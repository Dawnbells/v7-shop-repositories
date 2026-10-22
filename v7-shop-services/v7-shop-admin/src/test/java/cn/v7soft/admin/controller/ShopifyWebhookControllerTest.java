package cn.v7soft.admin.controller;

import cn.v7soft.admin.config.ShopifyAppProperties;
import cn.v7soft.admin.service.impl.ShopifyWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ShopifyWebhookControllerTest {
    private static final String SECRET = "app-secret";
    private static final String SHOP = "demo.myshopify.com";
    /** 含非 ASCII 字符与空白，验证 HMAC 用原始字节计算 */
    private static final String BODY = "{\"id\": 450789469, \"note\": \"中文 备注\"}";

    @Mock private ShopifyWebhookService webhookService;

    private ShopifyAppProperties properties;
    private ShopifyWebhookController controller;

    @BeforeEach
    void setUp() {
        properties = new ShopifyAppProperties();
        properties.setClientId("client-id");
        properties.setClientSecret(SECRET);
        controller = new ShopifyWebhookController(properties, webhookService);
    }

    private static String hmac(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body));
    }

    private ResponseEntity<Void> call(byte[] body, String hmac, String webhookId) {
        return controller.receive(body, "orders/create", hmac, SHOP, webhookId, "2026-07");
    }

    @Test
    @DisplayName("HMAC 正确：落库并返回 200，传入原始正文与各个头")
    void shouldEnqueueWhenHmacValid() throws Exception {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        when(webhookService.enqueue(any(), any(), any(), any(), any())).thenReturn(ShopifyWebhookService.EnqueueResult.PENDING);

        ResponseEntity<Void> response = call(body, hmac(body, SECRET), "wh-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(response.getBody());
        verify(webhookService).enqueue("wh-1", "orders/create", SHOP, "2026-07", BODY);
    }

    @Test
    @DisplayName("HMAC 不符：401 空体，不落库")
    void shouldRejectInvalidHmac() throws Exception {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);

        ResponseEntity<Void> response = call(body, hmac(body, "other-secret"), "wh-1");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNull(response.getBody());
        verifyNoInteractions(webhookService);
    }

    @Test
    @DisplayName("缺 HMAC 头或正文被改动：401")
    void shouldRejectMissingHmacOrTamperedBody() throws Exception {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        String signature = hmac(body, SECRET);

        assertEquals(HttpStatus.UNAUTHORIZED, call(body, null, "wh-1").getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, call((BODY + " ").getBytes(StandardCharsets.UTF_8), signature, "wh-1").getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, call(null, signature, "wh-1").getStatusCode());
        verifyNoInteractions(webhookService);
    }

    @Test
    @DisplayName("重复 webhook_id：仍返回 200")
    void shouldReturnOkForDuplicate() throws Exception {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        when(webhookService.enqueue(any(), any(), any(), any(), any())).thenReturn(ShopifyWebhookService.EnqueueResult.DUPLICATE);

        assertEquals(HttpStatus.OK, call(body, hmac(body, SECRET), "wh-1").getStatusCode());
    }

    @Test
    @DisplayName("不支持的 topic：落 IGNORED 后返回 200")
    void shouldReturnOkForIgnoredTopic() throws Exception {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        when(webhookService.enqueue(any(), eq("products/update"), any(), any(), any()))
                .thenReturn(ShopifyWebhookService.EnqueueResult.IGNORED);

        ResponseEntity<Void> response = controller.receive(body, "products/update", hmac(body, SECRET), SHOP, "wh-2", "2026-07");

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("应用未配置：503，不校验不落库")
    void shouldReturn503WhenNotConfigured() throws Exception {
        properties.setClientSecret("");
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, call(body, hmac(body, SECRET), "wh-1").getStatusCode());
        verifyNoInteractions(webhookService);
    }

    @Test
    @DisplayName("落库异常：500，让 Shopify 重投")
    void shouldReturn500WhenEnqueueFails() throws Exception {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        when(webhookService.enqueue(any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("db down"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, call(body, hmac(body, SECRET), "wh-1").getStatusCode());
    }

    @Test
    @DisplayName("缺 X-Shopify-Webhook-Id：用 topic + 正文的 SHA-256 作为去重键（64 位）")
    void shouldFallbackWebhookId() throws Exception {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        when(webhookService.enqueue(any(), any(), any(), any(), any())).thenReturn(ShopifyWebhookService.EnqueueResult.PENDING);

        call(body, hmac(body, SECRET), null);
        call(body, hmac(body, SECRET), " ");

        String expected = ShopifyWebhookController.resolveWebhookId(null, "orders/create", BODY);
        assertEquals(64, expected.length());
        verify(webhookService, times(2)).enqueue(eq(expected), any(), any(), any(), any());
    }

    @Test
    @DisplayName("MockMvc：JSON 正文以原始字节参与 HMAC，正确 200、错误 401 且响应体为空")
    void shouldHandleRawBodyThroughMvc() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        when(webhookService.enqueue(any(), any(), any(), any(), any())).thenReturn(ShopifyWebhookService.EnqueueResult.PENDING);

        mvc.perform(post("/shopify-app/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-Shopify-Topic", "orders/create")
                        .header("X-Shopify-Hmac-Sha256", hmac(body, SECRET))
                        .header("X-Shopify-Shop-Domain", SHOP)
                        .header("X-Shopify-Webhook-Id", "wh-9")
                        .header("X-Shopify-API-Version", "2026-07"))
                .andExpect(status().isOk());
        verify(webhookService).enqueue("wh-9", "orders/create", SHOP, "2026-07", BODY);

        mvc.perform(post("/shopify-app/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-Shopify-Topic", "orders/create")
                        .header("X-Shopify-Hmac-Sha256", "bad")
                        .header("X-Shopify-Webhook-Id", "wh-10"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
        verifyNoMoreInteractions(webhookService);
    }
}
