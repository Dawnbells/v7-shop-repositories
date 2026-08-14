package cn.v7soft.admin.service.impl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.BatchJob;
import com.google.genai.types.BatchJobSource;
import com.google.genai.types.Content;
import com.google.genai.types.CreateBatchJobConfig;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponsePromptFeedback;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.Part;
import com.google.genai.types.UploadFileConfig;

import cn.v7soft.admin.exception.DailyQuotaExhaustedException;
import cn.v7soft.admin.exception.GeminiContentBlockedException;
import cn.v7soft.dao.entities.primary.AiAccount;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GeminiTranslateService {

    private static final String MODEL = "gemini-3.1-flash-image-preview";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final int TIMEOUT_TEXT_MS = 120_000;
    private static final int TIMEOUT_HTML_MS = 180_000;
    private static final int TIMEOUT_IMAGE_MS = 300_000;
    private static final int TIMEOUT_DEFAULT_MS = 180_000;

    @lombok.Data
    @lombok.Builder
    public static class TokenUsage {

        private Integer promptTokens;
        private Integer completionTokens;
        private Integer thinkingTokens;
        private Integer totalTokens;
        private Long elapsedMs;
    }

    private final Map<String, Client> clientMap;
    private final Client primaryClient;
    private final GeminiQuotaTracker tracker;
    private final Retry geminiInternalRetry;

    public GeminiTranslateService(
            @Value("${gemini.api-keys}") String apiKeysConfig,
            @Value("${gemini.proxy-host:}") String proxyHost,
            @Value("${gemini.proxy-port:0}") int proxyPort,
            GeminiQuotaTracker tracker,
            Retry geminiInternalRetry) {
        this.tracker = tracker;
        this.geminiInternalRetry = geminiInternalRetry;
        if (proxyHost != null && !proxyHost.isBlank()) {
            System.setProperty("http.proxyHost", proxyHost);
            System.setProperty("http.proxyPort", String.valueOf(proxyPort));
            System.setProperty("https.proxyHost", proxyHost);
            System.setProperty("https.proxyPort", String.valueOf(proxyPort));
            log.info("Gemini API 代理已配置: {}:{}", proxyHost, proxyPort);
        }
        String[] keys = apiKeysConfig.split(",");
        this.clientMap = new LinkedHashMap<>();
        for (String key : keys) {
            clientMap.put(key, buildClient(key));
        }
        this.primaryClient = clientMap.values().iterator().next();
        log.info("[GeminiTranslateService] 初始化完成: {} 个 API Key", keys.length);
    }

    private Client buildClient(String apiKey) {
        return Client.builder()
                .apiKey(apiKey)
                .httpOptions(HttpOptions.builder()
                                     .timeout(TIMEOUT_DEFAULT_MS)
                                     .retryOptions(HttpRetryOptions.builder()
                                                           .attempts(2)
                                                           .httpStatusCodes(408, 500, 502, 503, 504)
                                                           .initialDelay(2.0)
                                                           .maxDelay(10.0)
                                                           .expBase(2.0)
                                                           .build())
                                     .build())
                .build();
    }

    // ======================== callGemini: 统一配额管理入口 ========================

    private <T> T callGemini(Function<Client, T> action) {
        String apiKey = tracker.tryAcquire();
        if (apiKey == null) {
            throw new DailyQuotaExhaustedException("所有 API Key 今日配额已耗尽");
        }
        try {
            return action.apply(clientMap.get(apiKey));
        } catch (ApiException e) {
            if (isDailyQuota429(e)) {
                tracker.markExhausted(apiKey);
                String nextKey = tracker.tryAcquire();
                if (nextKey == null) {
                    throw new DailyQuotaExhaustedException("所有 API Key 今日配额已耗尽", e);
                }
                try {
                    return action.apply(clientMap.get(nextKey));
                } catch (ApiException e2) {
                    if (isDailyQuota429(e2)) {
                        tracker.markExhausted(nextKey);
                        throw new DailyQuotaExhaustedException("所有 API Key 今日配额已耗尽", e2);
                    }
                    if (shouldDecrement(e2))
                        tracker.decrement(nextKey);
                    throw e2;
                }
            }
            if (shouldDecrement(e))
                tracker.decrement(apiKey);
            throw e;
        }
    }

    private static boolean isDailyQuota429(ApiException e) {
        return e.code() == 429 && e.getMessage() != null && e.getMessage().contains("per_day");
    }

    private static boolean shouldDecrement(ApiException e) {
        int code = e.code();
        return code == 400 || code == 401 || code == 403 || code == 429;
    }

    // ======================== 结果可用性校验 + 错误分档 ========================

    /**
     * 视为"正常收尾"的终止原因，其余一律按不可用结果处理。
     * <p>
     * NO_IMAGE 必须留在白名单里：translateImageRaw 的 prompt 明确要求"图中无可译文案时不要生成图片"，
     * 模型据此只回文本是**正常业务结果**（调用方拿到 null 后保留原图），不是政策阻断。
     */
    private static final Set<String> ACCEPTABLE_FINISH_REASONS =
            Set.of("STOP", "FINISH_REASON_UNSPECIFIED", "NO_IMAGE");

    /**
     * 官方文档标记 Retryable=Yes 的 HTTP 状态。
     * 文档明确 Retryable=No 的（400 / 401 / 403 / 404 / 416 / 499 / 501）重试没有意义，直接判永久失败。
     * 409 在文档里既有 aborted(Yes) 又有 already_exists(No)，翻译调用不会产生 already_exists，按可重试处理。
     * 参见 https://ai.google.dev/gemini-api/docs/api-errors 与 troubleshooting 的"do retry 429/408/5xx"。
     */
    private static final Set<Integer> RETRYABLE_API_STATUS = Set.of(408, 409, 429, 500, 502, 503, 504);

    /**
     * 判断 Gemini 调用异常是否值得重试。
     * 只有 Gemini 明确标记可重试的 HTTP 状态，以及 IO / timeout 这类瞬时网络异常才重试。
     * 未知的本地运行时异常默认不可重试，避免配置或代码错误被上层变成无限重试。
     */
    public static boolean isRetryableApiError(Throwable e) {
        for (Throwable current = e; current != null; current = current.getCause()) {
            // 所有 key 当日配额耗尽 —— 429 语义，等配额自然恢复
            if (current instanceof DailyQuotaExhaustedException) {
                return true;
            }
            if (current instanceof ApiException ae) {
                return RETRYABLE_API_STATUS.contains(ae.code());
            }
            if (current instanceof IOException || current instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验生成结果可用：prompt 未被内容策略拦截，且候选以 STOP 正常收尾（文本场景还要求真的带回内容）。
     * <p>
     * 不可用时抛 {@link GeminiContentBlockedException}，reason 带上 Gemini 的原始枚举名。
     * 这样政策阻断（SAFETY / PROHIBITED_CONTENT / …）和输出截断（MAX_TOKENS）都不会再被
     * {@code response.text()} 返回 null 悄悄吞掉 —— 那会让半截译文或空译文直接写进产品。
     *
     * @param expectText 文本/HTML 场景传 true；图片场景传 false（图片正常可以只回文本）
     */
    private static void ensureUsableResponse(GenerateContentResponse response, String label, boolean expectText) {
        if (response == null) {
            throw new GeminiContentBlockedException("EMPTY_RESPONSE", label + ": Gemini 未返回响应");
        }
        String blockReason = response.promptFeedback()
                .flatMap(GenerateContentResponsePromptFeedback::blockReason)
                .map(Object::toString)
                .filter(reason -> !reason.isBlank() && !"BLOCKED_REASON_UNSPECIFIED".equals(reason))
                .orElse(null);
        if (blockReason != null) {
            throw new GeminiContentBlockedException(blockReason,
                    label + ": prompt 被 Gemini 内容策略拦截 (" + blockReason + ")");
        }
        // 用 candidates() 而不是 response.finishReason()，后者在无候选时的行为没有保证
        String finishReason = response.candidates()
                .filter(candidates -> !candidates.isEmpty())
                .flatMap(candidates -> candidates.get(0).finishReason())
                .map(Object::toString)
                .filter(reason -> !reason.isBlank())
                .orElse(null);
        if (finishReason != null && !ACCEPTABLE_FINISH_REASONS.contains(finishReason)) {
            throw new GeminiContentBlockedException(finishReason,
                    label + ": Gemini 生成未正常收尾 (" + finishReason + ")");
        }
        if (expectText) {
            String text = response.text();
            if (text == null || text.isBlank()) {
                throw new GeminiContentBlockedException("EMPTY_RESPONSE",
                        label + ": Gemini 返回空内容 (finishReason=" + finishReason + ")");
            }
        }
    }

    // ======================== 带 Resilience4j 重试的翻译方法 ========================

    public String translateText(String text, String targetLanguageName) {
        return Retry.decorateSupplier(geminiInternalRetry,
                                      () -> translateTextRaw(text, targetLanguageName)).get();
    }

    public String translateHtml(String html, String targetLanguageName) {
        return Retry.decorateSupplier(geminiInternalRetry,
                                      () -> translateHtmlRaw(html, targetLanguageName)).get();
    }

    public byte[] translateImage(byte[] imageBytes, String mimeType, String targetLanguageName) {
        return Retry.decorateSupplier(geminiInternalRetry,
                                      () -> translateImageRaw(imageBytes, mimeType, targetLanguageName)).get();
    }

    // ======================== Raw 方法 ========================

    public String translateTextRaw(String text, String targetLanguageName) {
        return translateTextRaw(text, targetLanguageName, null);
    }

    public String translateTextRaw(String text, String targetLanguageName, Consumer<TokenUsage> usageCallback) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String prompt = """
                You are a professional e-commerce product translator.
                Translate the following text to %s.
                Rules:
                - Keep brand names, model numbers, and units unchanged
                - If the text is already in the target language, keep it as is
                - Output ONLY the translated text, nothing else
                
                Text:
                %s
                """.formatted(targetLanguageName, text);

        GenerateContentConfig config = GenerateContentConfig.builder()
                .temperature(0.1f)
                .httpOptions(HttpOptions.builder().timeout(TIMEOUT_TEXT_MS).build())
                .build();

        long start = System.currentTimeMillis();
        GenerateContentResponse response = callGemini(client -> {
            log.info("[translateText] 请求 Gemini: model={}, targetLang={}, textLength={}, timeout={}ms",
                     MODEL, targetLanguageName, text.length(), TIMEOUT_TEXT_MS);
            return client.models.generateContent(MODEL, prompt, config);
        });
        long elapsed = System.currentTimeMillis() - start;

        // 先记账再校验：prompt token 已经消耗，即便结果被阻断也要回传用量
        logTokenUsage("translateText", elapsed, response);
        emitTokenUsage(response, elapsed, usageCallback);
        ensureUsableResponse(response, "translateText", true);
        return response.text();
    }

    public String translateHtmlRaw(String html, String targetLanguageName) {
        return translateHtmlRaw(html, targetLanguageName, null);
    }

    public String translateHtmlRaw(String html, String targetLanguageName, Consumer<TokenUsage> usageCallback) {
        if (html == null || html.isBlank()) {
            return html;
        }

        Content systemInstruction = Content.fromParts(Part.fromText("""
                                                                            You are a professional HTML content translator for e-commerce product pages.
                                                                            You MUST follow these rules strictly:
                                                                            - Translate ONLY the visible text content to %s
                                                                            - Preserve ALL HTML tags, attributes, and structure exactly as they are
                                                                            - Do NOT modify any tag names, class names, style attributes, src URLs, or href URLs
                                                                            - Do NOT modify <img> tags or their src/alt attributes in any way
                                                                            - Do NOT add or remove any HTML tags
                                                                            - Keep brand names, product model numbers unchanged
                                                                            - Output the complete translated HTML, nothing else
                                                                            """.formatted(targetLanguageName)));

        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(systemInstruction)
                .temperature(0.1f)
                .httpOptions(HttpOptions.builder().timeout(TIMEOUT_HTML_MS).build())
                .build();

        String prompt = "Translate the text content in this HTML to "
                        + targetLanguageName + ":\n\n" + html;

        long start = System.currentTimeMillis();
        GenerateContentResponse response = callGemini(c -> {
            log.info("[translateHtml] 请求 Gemini: model={}, targetLang={}, htmlLength={}, timeout={}ms",
                     MODEL, targetLanguageName, html.length(), TIMEOUT_HTML_MS);
            return c.models.generateContent(MODEL, prompt, config);
        });
        long elapsed = System.currentTimeMillis() - start;

        logTokenUsage("translateHtml", elapsed, response);
        emitTokenUsage(response, elapsed, usageCallback);
        ensureUsableResponse(response, "translateHtml", true);
        return response.text();
    }

    public byte[] translateImageRaw(byte[] imageBytes, String mimeType, String targetLanguageName) {
        return translateImageRaw(imageBytes, mimeType, targetLanguageName, null);
    }

    public byte[] translateImageRaw(byte[] imageBytes, String mimeType, String targetLanguageName,
                                    Consumer<TokenUsage> usageCallback) {
        String prompt = """
                First, analyze whether the image contains any readable text.
                
                Then classify detected text into two categories:
                
                1. Translatable overlay text:
                   text that is clearly added as part of the design or layout, such as titles, \
                descriptions, feature callouts, promotional text, labels, or other explanatory \
                text placed on top of the image.
                
                2. Non-translatable embedded text:
                   text that is physically part of the photographed product itself or its packaging, \
                such as printed text on the product, bottle, box, bag, label, tag, sticker, manual \
                shown in the photo, engraved text, embossed text, or any text naturally appearing \
                inside the original photographed object.
                
                Rules:
                
                - If the image contains NO text:
                  Return exactly this text only:
                  No text detected, no translation needed.
                  Do NOT generate any image.
                
                - If the image contains ONLY non-translatable embedded text:
                  Return exactly this text only:
                  Only product/package text detected, no translation needed.
                  Do NOT generate any image.
                
                - If the image contains translatable overlay text:
                  Translate ONLY the translatable overlay text into %s.
                
                  This is a strict text-only edit on the image.
                
                  Requirements:
                  - Keep background, product, colors, and layout exactly unchanged.
                  - Do NOT translate or modify any text that is part of the actual product or \
                packaging shown in the photo.
                  - Only replace translatable overlay text; leave embedded product/package text \
                untouched.
                  - Do not redraw or recreate the image.
                  - Preserve original font style, size, alignment, and spacing as much as possible.
                  - Ensure translated text fits naturally within original text areas.
                  - Use concise, natural %s suitable for e-commerce.
                
                  Output rules (VERY IMPORTANT):
                  - Output ONLY the final translated image.
                  - Do NOT output any text, explanation, markdown, or JSON.
                  - Do NOT describe the translation.
                  - Do NOT list extracted text.
                  - The response must contain only the image.
                
                - If uncertain whether some text is overlay text or embedded product/package text, \
                leave it unchanged.
                """.formatted(targetLanguageName, targetLanguageName);
        Content content = Content.fromParts(
                Part.fromBytes(imageBytes, mimeType),
                Part.fromText(prompt)
        );

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseModalities(List.of("TEXT", "IMAGE"))
                .temperature(0.2f)
                .httpOptions(HttpOptions.builder().timeout(TIMEOUT_IMAGE_MS).build())
                .build();



        long start = System.currentTimeMillis();
        GenerateContentResponse response = callGemini(c -> {
            log.info("[translateImage] 请求 Gemini: model={}, targetLang={}, mimeType={}, imageSize={}bytes, timeout={}ms",
                     MODEL, targetLanguageName, mimeType, imageBytes.length, TIMEOUT_IMAGE_MS);
            return c.models.generateContent(MODEL, content, config);
        });
        long elapsed = System.currentTimeMillis() - start;

        logTokenUsage("translateImage", elapsed, response);
        emitTokenUsage(response, elapsed, usageCallback);
        // 图片场景允许只回文本（"无需翻译"），所以 expectText=false；
        // 但 IMAGE_SAFETY / PROHIBITED_CONTENT 这类阻断以前和"无需翻译"长得一模一样，现在会被区分出来
        return extractUsableImageResult(response, "translateImage");
    }

    /** 所有同步图片生成入口统一走同一套政策/终止原因校验。包可见以便做纯响应回归测试。 */
    static byte[] extractUsableImageResult(GenerateContentResponse response, String label) {
        ensureUsableResponse(response, label, false);
        return extractImageResult(response);
    }

    private static byte[] extractImageResult(GenerateContentResponse response) {
        List<Part> parts = response.parts();
        if (parts == null || parts.isEmpty()) {
            log.warn("[translateImage] Gemini 响应中没有 parts");
            return null;
        }
        byte[] imageResult = null;
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            if (part.inlineData().isPresent()) {
                var blob = part.inlineData().get();
                int dataSize = blob.data().isPresent() ? blob.data().get().length : 0;
                log.info("[translateImage] part[{}] 类型=IMAGE, mimeType={}, dataSize={}bytes",
                         i, blob.mimeType().orElse("unknown"), dataSize);
                if (blob.data().isPresent()) {
                    imageResult = blob.data().get();
                }
            } else if (part.text().isPresent()) {
                boolean isThought = part.thought().orElse(false);
                log.info("[translateImage] part[{}] 类型=TEXT, thought={}, text={}",
                         i, isThought, part.text().get());
            } else {
                log.info("[translateImage] part[{}] 类型=OTHER, content={}", i, part.toJson());
            }
        }
        if (imageResult != null) {
            log.info("[translateImage] 翻译后图片大小={}bytes", imageResult.length);
            return imageResult;
        }
        log.info("[translateImage] 图片无需翻译, 模型未返回图片数据");
        return null;
    }

    // ======================== 流式翻译方法（支持自定义 prompt + 按 AiAccount 构建 Client） ========================

    private Client buildClientForAccount(AiAccount account) {
        String apiKey = account.getApiKey();
        var httpBuilder = HttpOptions.builder()
                .timeout(TIMEOUT_DEFAULT_MS)
                .retryOptions(HttpRetryOptions.builder()
                                      .attempts(2)
                                      .httpStatusCodes(408, 500, 502, 503, 504)
                                      .initialDelay(2.0)
                                      .maxDelay(10.0)
                                      .expBase(2.0)
                                      .build());
        var builder = Client.builder().apiKey(apiKey).httpOptions(httpBuilder.build());
        return builder.build();
    }

    private String resolveModel(AiAccount account) {
        return (account.getModel() != null && !account.getModel().isBlank()) ? account.getModel() : MODEL;
    }

    private String buildTextPrompt(String userPrompt, String targetLanguageName, String text) {
        if (userPrompt == null || userPrompt.isBlank()) {
            return """
                    You are a professional e-commerce product translator.
                    Translate the following text to %s.
                    Rules:
                    - Keep brand names, model numbers, and units unchanged
                    - If the text is already in the target language, keep it as is
                    - Output ONLY the translated text, nothing else
                    
                    Text:
                    %s
                    """.formatted(targetLanguageName, text);
        }
        return """
                Target language: %s
                
                User instruction:
                %s
                
                Text:
                %s
                """.formatted(targetLanguageName, userPrompt, text);
    }

    private String buildHtmlSystemInstruction(String userPrompt, String targetLanguageName) {
        if (userPrompt == null || userPrompt.isBlank()) {
            return """
                    You are a professional HTML content translator for e-commerce product pages.
                    You MUST follow these rules strictly:
                    - Translate ONLY the visible text content to %s
                    - Preserve ALL HTML tags, attributes, and structure exactly as they are
                    - Do NOT modify any tag names, class names, style attributes, src URLs, or href URLs
                    - Do NOT modify <img> tags or their src/alt attributes in any way
                    - Do NOT add or remove any HTML tags
                    - Keep brand names, product model numbers unchanged
                    - Output the complete translated HTML, nothing else
                    """.formatted(targetLanguageName);
        }
        return """
                You are a professional HTML content translator.
                Target language: %s
                
                User instruction:
                %s
                
                Rules:
                - Preserve ALL HTML tags, attributes, and structure exactly as they are
                - Do NOT modify any tag names, class names, style attributes, src URLs, or href URLs
                - Do NOT modify <img> tags or their src/alt attributes in any way
                - Do NOT add or remove any HTML tags
                - Output the complete translated HTML, nothing else
                """.formatted(targetLanguageName, userPrompt);
    }

    private String buildImagePrompt(String userPrompt, String targetLanguageName) {
        if (userPrompt == null || userPrompt.isBlank()) {
            return """
                    First, analyze whether the image contains any readable text.
                    
                    Then classify detected text into two categories:
                    
                    1. Translatable overlay text:
                       text that is clearly added as part of the design or layout, such as titles, \
                    descriptions, feature callouts, promotional text, labels, or other explanatory \
                    text placed on top of the image.
                    
                    2. Non-translatable embedded text:
                       text that is physically part of the photographed product itself or its packaging, \
                    such as printed text on the product, bottle, box, bag, label, tag, sticker, manual \
                    shown in the photo, engraved text, embossed text, or any text naturally appearing \
                    inside the original photographed object.
                    
                    Rules:
                    
                    - If the image contains NO text:
                      Return exactly this text only:
                      No text detected, no translation needed.
                      Do NOT generate any image.
                    
                    - If the image contains ONLY non-translatable embedded text:
                      Return exactly this text only:
                      Only product/package text detected, no translation needed.
                      Do NOT generate any image.
                    
                    - If the image contains translatable overlay text:
                      Translate ONLY the translatable overlay text into %s.
                    
                      This is a strict text-only edit on the image.
                    
                      Requirements:
                      - Keep background, product, colors, and layout exactly unchanged.
                      - Do NOT translate or modify any text that is part of the actual product or \
                    packaging shown in the photo.
                      - Only replace translatable overlay text; leave embedded product/package text \
                    untouched.
                      - Do not redraw or recreate the image.
                      - Preserve original font style, size, alignment, and spacing as much as possible.
                      - Ensure translated text fits naturally within original text areas.
                      - Use concise, natural %s suitable for e-commerce.
                    
                      Output rules (VERY IMPORTANT):
                      - Output ONLY the final translated image.
                      - Do NOT output any text, explanation, markdown, or JSON.
                      - Do NOT describe the translation.
                      - Do NOT list extracted text.
                      - The response must contain only the image.
                    
                    - If uncertain whether some text is overlay text or embedded product/package text, \
                    leave it unchanged.
                    """.formatted(targetLanguageName, targetLanguageName);
        }
        return """
                Target language: %s
                
                User instruction:
                %s
                
                Output rules:
                - Output ONLY the final translated image.
                - Do NOT output any text, explanation, markdown, or JSON.
                """.formatted(targetLanguageName, userPrompt);
    }

    public void streamTranslateText(AiAccount account, String userPrompt, String text, String targetLanguageName,
                                    Consumer<String> onChunk, Consumer<TokenUsage> onUsage,
                                    Runnable onComplete, Consumer<Throwable> onError) {
        if (text == null || text.isBlank()) {
            onChunk.accept(text);
            onComplete.run();
            return;
        }
        Client client = buildClientForAccount(account);
        String model = resolveModel(account);
        String prompt = buildTextPrompt(userPrompt, targetLanguageName, text);

        GenerateContentConfig config = GenerateContentConfig.builder()
                .temperature(0.1f)
                .httpOptions(HttpOptions.builder().timeout(TIMEOUT_TEXT_MS).build())
                .build();

        long start = System.currentTimeMillis();
        try {
            log.info("[streamTranslateText] model={}, targetLang={}, textLen={}", model, targetLanguageName, text.length());
            var stream = client.models.generateContentStream(model, prompt, config);
            for (var chunk : stream) {
                String chunkText = chunk.text();
                if (chunkText != null && !chunkText.isEmpty()) {
                    onChunk.accept(chunkText);
                }
                chunk.usageMetadata().ifPresent(usage -> {
                    long elapsed = System.currentTimeMillis() - start;
                    TokenUsage tu = TokenUsage.builder()
                            .promptTokens(usage.promptTokenCount().orElse(0))
                            .completionTokens(usage.candidatesTokenCount().orElse(0))
                            .thinkingTokens(usage.thoughtsTokenCount().orElse(0))
                            .totalTokens(usage.totalTokenCount().orElse(0))
                            .elapsedMs(elapsed)
                            .build();
                    if (onUsage != null) onUsage.accept(tu);
                });
            }
            onComplete.run();
        } catch (Throwable e) {
            log.error("[streamTranslateText] error", e);
            onError.accept(e);
        }
    }

    public void streamTranslateHtml(AiAccount account, String userPrompt, String html, String targetLanguageName,
                                    Consumer<String> onChunk, Consumer<TokenUsage> onUsage,
                                    Runnable onComplete, Consumer<Throwable> onError) {
        if (html == null || html.isBlank()) {
            onChunk.accept(html);
            onComplete.run();
            return;
        }
        Client client = buildClientForAccount(account);
        String model = resolveModel(account);
        String systemText = buildHtmlSystemInstruction(userPrompt, targetLanguageName);

        Content systemInstruction = Content.fromParts(Part.fromText(systemText));
        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(systemInstruction)
                .temperature(0.1f)
                .httpOptions(HttpOptions.builder().timeout(TIMEOUT_HTML_MS).build())
                .build();
        String prompt = "Translate the text content in this HTML to " + targetLanguageName + ":\n\n" + html;

        long start = System.currentTimeMillis();
        try {
            log.info("[streamTranslateHtml] model={}, targetLang={}, htmlLen={}", model, targetLanguageName, html.length());
            var stream = client.models.generateContentStream(model, prompt, config);
            for (var chunk : stream) {
                String chunkText = chunk.text();
                if (chunkText != null && !chunkText.isEmpty()) {
                    onChunk.accept(chunkText);
                }
                chunk.usageMetadata().ifPresent(usage -> {
                    long elapsed = System.currentTimeMillis() - start;
                    TokenUsage tu = TokenUsage.builder()
                            .promptTokens(usage.promptTokenCount().orElse(0))
                            .completionTokens(usage.candidatesTokenCount().orElse(0))
                            .thinkingTokens(usage.thoughtsTokenCount().orElse(0))
                            .totalTokens(usage.totalTokenCount().orElse(0))
                            .elapsedMs(elapsed)
                            .build();
                    if (onUsage != null) onUsage.accept(tu);
                });
            }
            onComplete.run();
        } catch (Throwable e) {
            log.error("[streamTranslateHtml] error", e);
            onError.accept(e);
        }
    }

    public byte[] translateImageWithAccount(AiAccount account, String userPrompt,
                                            byte[] imageBytes, String mimeType, String targetLanguageName,
                                            Consumer<TokenUsage> usageCallback) {
        Client client = buildClientForAccount(account);
        String model = resolveModel(account);
        String prompt = buildImagePrompt(userPrompt, targetLanguageName);

        Content content = Content.fromParts(
                Part.fromBytes(imageBytes, mimeType),
                Part.fromText(prompt)
        );
        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseModalities(List.of("TEXT", "IMAGE"))
                .temperature(0.2f)
                .httpOptions(HttpOptions.builder().timeout(TIMEOUT_IMAGE_MS).build())
                .build();

        long start = System.currentTimeMillis();
        log.info("[translateImageWithAccount] model={}, targetLang={}, mimeType={}, imageSize={}bytes",
                 model, targetLanguageName, mimeType, imageBytes.length);
        GenerateContentResponse response = client.models.generateContent(model, content, config);
        long elapsed = System.currentTimeMillis() - start;

        logTokenUsage("translateImageWithAccount", elapsed, response);
        emitTokenUsage(response, elapsed, usageCallback);
        return extractUsableImageResult(response, "translateImageWithAccount");
    }

    // ======================== Batch API 方法（固定用主 Key） ========================

    public String getModel() {
        return MODEL;
    }

    public String buildTextTranslateJsonlEntry(String key, String text, String targetLanguageName) {
        String prompt = """
                You are a professional e-commerce product translator.
                Translate the following text to %s.
                Rules:
                - Keep brand names, model numbers, and units unchanged
                - If the text is already in the target language, keep it as is
                - Output ONLY the translated text, nothing else
                
                Text:
                %s
                """.formatted(targetLanguageName, text);

        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("key", key);

        ObjectNode request = root.putObject("request");
        ArrayNode contents = request.putArray("contents");
        ObjectNode contentNode = contents.addObject();
        ArrayNode parts = contentNode.putArray("parts");
        parts.addObject().put("text", prompt);

        ObjectNode genConfig = request.putObject("generation_config");
        genConfig.put("temperature", 0.1);
        ObjectNode thinkingConfig = genConfig.putObject("thinking_config");
        thinkingConfig.put("thinking_budget", 0);

        return root.toString();
    }

    public String buildHtmlTranslateJsonlEntry(String key, String html, String targetLanguageName) {
        String systemText = """
                You are a professional HTML content translator for e-commerce product pages.
                You MUST follow these rules strictly:
                - Translate ONLY the visible text content to %s
                - Preserve ALL HTML tags, attributes, and structure exactly as they are
                - Do NOT modify any tag names, class names, style attributes, src URLs, or href URLs
                - Do NOT modify <img> tags or their src/alt attributes in any way
                - Do NOT add or remove any HTML tags
                - Keep brand names, product model numbers unchanged
                - Output the complete translated HTML, nothing else
                """.formatted(targetLanguageName);

        String prompt = "Translate the text content in this HTML to "
                        + targetLanguageName + ":\n\n" + html;

        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("key", key);

        ObjectNode request = root.putObject("request");

        ObjectNode systemInstruction = request.putObject("system_instruction");
        ArrayNode sysParts = systemInstruction.putArray("parts");
        sysParts.addObject().put("text", systemText);

        ArrayNode contents = request.putArray("contents");
        ObjectNode contentNode = contents.addObject();
        ArrayNode parts = contentNode.putArray("parts");
        parts.addObject().put("text", prompt);

        ObjectNode genConfig = request.putObject("generation_config");
        genConfig.put("temperature", 0.1);
        ObjectNode thinkingConfig = genConfig.putObject("thinking_config");
        thinkingConfig.put("thinking_budget", 0);

        return root.toString();
    }

    public String buildImageTranslateJsonlEntry(String key, byte[] imageBytes, String mimeType, String targetLanguageName) {
        String prompt = """
                First, analyze whether the image contains any readable text.
                
                Then classify detected text into two categories:
                
                1. Translatable overlay text:
                   text that is clearly added as part of the design or layout, such as titles, \
                descriptions, feature callouts, promotional text, labels, or other explanatory \
                text placed on top of the image.
                
                2. Non-translatable embedded text:
                   text that is physically part of the photographed product itself or its packaging, \
                such as printed text on the product, bottle, box, bag, label, tag, sticker, manual \
                shown in the photo, engraved text, embossed text, or any text naturally appearing \
                inside the original photographed object.
                
                Rules:
                
                - If the image contains NO text:
                  Return exactly this text only:
                  No text detected, no translation needed.
                  Do NOT generate any image.
                
                - If the image contains ONLY non-translatable embedded text:
                  Return exactly this text only:
                  Only product/package text detected, no translation needed.
                  Do NOT generate any image.
                
                - If the image contains translatable overlay text:
                  Translate ONLY the translatable overlay text into %s.
                
                  This is a strict text-only edit on the image.
                
                  Requirements:
                  - Keep background, product, colors, and layout exactly unchanged.
                  - Do NOT translate or modify any text that is part of the actual product or \
                packaging shown in the photo.
                  - Only replace translatable overlay text; leave embedded product/package text \
                untouched.
                  - Do not redraw or recreate the image.
                  - Preserve original font style, size, alignment, and spacing as much as possible.
                  - Ensure translated text fits naturally within original text areas.
                  - Use concise, natural %s suitable for e-commerce.
                
                  Output rules (VERY IMPORTANT):
                  - Output ONLY the final translated image.
                  - Do NOT output any text, explanation, markdown, or JSON.
                  - Do NOT describe the translation.
                  - Do NOT list extracted text.
                  - The response must contain only the image.
                
                - If uncertain whether some text is overlay text or embedded product/package text, \
                leave it unchanged.
                """.formatted(targetLanguageName, targetLanguageName);

        String base64Data = Base64.getEncoder().encodeToString(imageBytes);

        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("key", key);

        ObjectNode request = root.putObject("request");
        ArrayNode contents = request.putArray("contents");
        ObjectNode contentNode = contents.addObject();
        ArrayNode parts = contentNode.putArray("parts");

        ObjectNode imagePart = parts.addObject();
        ObjectNode inlineData = imagePart.putObject("inline_data");
        inlineData.put("mime_type", mimeType);
        inlineData.put("data", base64Data);

        parts.addObject().put("text", prompt);

        ObjectNode genConfig = request.putObject("generation_config");
        ArrayNode modalities = genConfig.putArray("response_modalities");
        modalities.add("TEXT");
        modalities.add("IMAGE");
        genConfig.put("temperature", 0.2);
        ObjectNode thinkingConfig = genConfig.putObject("thinking_config");
        thinkingConfig.put("thinking_budget", 0);

        return root.toString();
    }

    public String uploadBatchFile(String jsonlContent) throws IOException {
        File tempFile = File.createTempFile("batch-translate-", ".jsonl");
        try {
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(jsonlContent);
            }
            log.info("[uploadBatchFile] 上传 JSONL 文件: size={}bytes", jsonlContent.length());
            var uploadedFile = primaryClient.files.upload(
                    tempFile,
                    UploadFileConfig.builder()
                            .displayName("product-ai-translate-" + System.currentTimeMillis())
                            .mimeType("jsonl")
                            .build());
            String fileName = uploadedFile.name().orElseThrow(() -> new RuntimeException("上传文件后未获取到文件名"));
            log.info("[uploadBatchFile] 上传成功: fileName={}", fileName);
            return fileName;
        } finally {
            Files.deleteIfExists(tempFile.toPath());
        }
    }

    public BatchJob createBatchJob(String uploadedFileName) {
        BatchJobSource source = BatchJobSource.builder()
                .fileName(uploadedFileName)
                .build();
        CreateBatchJobConfig config = CreateBatchJobConfig.builder()
                .displayName("product-ai-translate-" + System.currentTimeMillis())
                .build();
        BatchJob job = primaryClient.batches.create(MODEL, source, config);
        log.info("[createBatchJob] 批量任务已创建: name={}", job.name().orElse("N/A"));
        return job;
    }

    public BatchJob getBatchJob(String jobName) {
        return primaryClient.batches.get(jobName, null);
    }

    public void cancelBatchJob(String jobName) {
        try {
            primaryClient.batches.cancel(jobName, null);
            log.info("[cancelBatchJob] 已取消: {}", jobName);
        } catch (Exception e) {
            log.warn("[cancelBatchJob] 取消失败: {}, error={}", jobName, e.getMessage());
        }
    }

    public void deleteBatchJob(String jobName) {
        try {
            primaryClient.batches.delete(jobName, null);
            log.info("[deleteBatchJob] 已删除: {}", jobName);
        } catch (Exception e) {
            log.warn("[deleteBatchJob] 删除失败: {}, error={}", jobName, e.getMessage());
        }
    }

    public String downloadBatchResult(String fileName) throws IOException {
        log.info("[downloadBatchResult] 下载结果文件: {}", fileName);
        File tempFile = File.createTempFile("batch-result-", ".jsonl");
        try {
            primaryClient.files.download(fileName, tempFile.getAbsolutePath(), null);
            String result = Files.readString(tempFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
            log.info("[downloadBatchResult] 下载完成: size={}bytes", result.length());
            return result;
        } finally {
            Files.deleteIfExists(tempFile.toPath());
        }
    }

    public void deleteFile(String fileName) {
        try {
            primaryClient.files.delete(fileName, null);
            log.info("[deleteFile] 已删除文件: {}", fileName);
        } catch (Exception e) {
            log.warn("[deleteFile] 删除文件失败: {}, error={}", fileName, e.getMessage());
        }
    }

    // ======================== Token 辅助方法 ========================

    public static TokenUsage extractTokenUsageFromBatchResponse(com.fasterxml.jackson.databind.JsonNode responseNode) {
        com.fasterxml.jackson.databind.JsonNode meta = responseNode.path("usageMetadata");
        if (meta.isMissingNode())
            return null;
        return TokenUsage.builder()
                .promptTokens(meta.has("promptTokenCount") ? meta.get("promptTokenCount").asInt(0) : 0)
                .completionTokens(meta.has("candidatesTokenCount") ? meta.get("candidatesTokenCount").asInt(0) : 0)
                .thinkingTokens(meta.has("thoughtsTokenCount") ? meta.get("thoughtsTokenCount").asInt(0) : 0)
                .totalTokens(meta.has("totalTokenCount") ? meta.get("totalTokenCount").asInt(0) : 0)
                .build();
    }

    private TokenUsage extractTokenUsage(GenerateContentResponse response, long elapsedMs) {
        Optional<GenerateContentResponseUsageMetadata> opt = response.usageMetadata();
        if (opt.isEmpty())
            return null;
        GenerateContentResponseUsageMetadata usage = opt.get();
        return TokenUsage.builder()
                .promptTokens(usage.promptTokenCount().orElse(0))
                .completionTokens(usage.candidatesTokenCount().orElse(0))
                .thinkingTokens(usage.thoughtsTokenCount().orElse(0))
                .totalTokens(usage.totalTokenCount().orElse(0))
                .elapsedMs(elapsedMs)
                .build();
    }

    private void emitTokenUsage(GenerateContentResponse response, long elapsedMs, Consumer<TokenUsage> callback) {
        if (callback == null)
            return;
        TokenUsage usage = extractTokenUsage(response, elapsedMs);
        if (usage != null) {
            callback.accept(usage);
        }
    }

    private void logTokenUsage(String method, long elapsedMs, GenerateContentResponse response) {
        Optional<GenerateContentResponseUsageMetadata> opt = response.usageMetadata();
        if (opt.isEmpty()) {
            log.info("[{}] Gemini 响应: elapsed={}ms, usageMetadata 不可用", method, elapsedMs);
            return;
        }
        GenerateContentResponseUsageMetadata usage = opt.get();
        log.info("[{}] Gemini 响应: elapsed={}ms, promptTokens={}, candidatesTokens={}, totalTokens={}, thoughtsTokens={}, cachedTokens={}",
                 method, elapsedMs,
                 usage.promptTokenCount().orElse(null),
                 usage.candidatesTokenCount().orElse(null),
                 usage.totalTokenCount().orElse(null),
                 usage.thoughtsTokenCount().orElse(null),
                 usage.cachedContentTokenCount().orElse(null));
    }
}
