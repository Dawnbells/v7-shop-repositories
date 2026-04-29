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
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.Part;
import com.google.genai.types.UploadFileConfig;

import cn.v7soft.admin.exception.DailyQuotaExhaustedException;
import cn.v7soft.admin.service.AiTranslationClient;
import cn.v7soft.dao.entities.primary.AiAccount;
import cn.v7soft.dao.enums.AiApiChannel;
import cn.v7soft.dao.enums.AiProvider;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GeminiTranslateService implements AiTranslationClient {

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

    @Override
    public boolean supports(AiProvider provider) {
        return provider == AiProvider.GEMINI;
    }

    private Client buildClient(AiAccount account, int timeoutMs) {
        HttpOptions.Builder httpOptions = HttpOptions.builder()
                .timeout(timeoutMs)
                .retryOptions(HttpRetryOptions.builder()
                        .attempts(2)
                        .httpStatusCodes(408, 500, 502, 503, 504)
                        .initialDelay(2.0)
                        .maxDelay(10.0)
                        .expBase(2.0)
                        .build());
        if (account.getApiChannel() == AiApiChannel.SUB2API && account.getBaseUrl() != null && !account.getBaseUrl().isBlank()) {
            httpOptions.baseUrl(account.getBaseUrl());
        }
        applyUserAgent(httpOptions, account);
        return Client.builder()
                .apiKey(account.getApiKey())
                .httpOptions(httpOptions.build())
                .build();
    }

    private HttpOptions buildRequestHttpOptions(AiAccount account, int timeoutMs) {
        HttpOptions.Builder httpOptions = HttpOptions.builder().timeout(timeoutMs);
        applyUserAgent(httpOptions, account);
        return httpOptions.build();
    }

    private void applyUserAgent(HttpOptions.Builder httpOptions, AiAccount account) {
        if (account != null && account.getUserAgent() != null && !account.getUserAgent().isBlank()) {
            httpOptions.headers(Map.of("User-Agent", account.getUserAgent()));
        }
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
        return translateTextRaw(null, text, targetLanguageName, usageCallback);
    }

    public String translateTextRaw(AiAccount account, String text, String targetLanguageName, Consumer<TokenUsage> usageCallback) {
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
                .httpOptions(buildRequestHttpOptions(account, TIMEOUT_TEXT_MS))
                .build();

        long start = System.currentTimeMillis();
        String model = getModel(account);
        Function<Client, GenerateContentResponse> action = client -> {
            log.info("[translateText] 请求 Gemini: model={}, targetLang={}, textLength={}, timeout={}ms",
                     model, targetLanguageName, text.length(), TIMEOUT_TEXT_MS);
            return client.models.generateContent(model, prompt, config);
        };
        GenerateContentResponse response = account == null ? callGemini(action) : action.apply(buildClient(account, TIMEOUT_TEXT_MS));
        long elapsed = System.currentTimeMillis() - start;
        String result = response.text();

        logTokenUsage("translateText", elapsed, response);
        emitTokenUsage(response, elapsed, usageCallback);
        return result;
    }

    public String translateHtmlRaw(String html, String targetLanguageName) {
        return translateHtmlRaw(html, targetLanguageName, null);
    }

    public String translateHtmlRaw(String html, String targetLanguageName, Consumer<TokenUsage> usageCallback) {
        return translateHtmlRaw(null, html, targetLanguageName, usageCallback);
    }

    public String translateHtmlRaw(AiAccount account, String html, String targetLanguageName, Consumer<TokenUsage> usageCallback) {
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
                .httpOptions(buildRequestHttpOptions(account, TIMEOUT_HTML_MS))
                .build();

        String prompt = "Translate the text content in this HTML to "
                        + targetLanguageName + ":\n\n" + html;

        long start = System.currentTimeMillis();
        String model = getModel(account);
        Function<Client, GenerateContentResponse> action = c -> {
            log.info("[translateHtml] 请求 Gemini: model={}, targetLang={}, htmlLength={}, timeout={}ms",
                     model, targetLanguageName, html.length(), TIMEOUT_HTML_MS);
            return c.models.generateContent(model, prompt, config);
        };
        GenerateContentResponse response = account == null ? callGemini(action) : action.apply(buildClient(account, TIMEOUT_HTML_MS));
        long elapsed = System.currentTimeMillis() - start;
        String result = response.text();

        logTokenUsage("translateHtml", elapsed, response);
        emitTokenUsage(response, elapsed, usageCallback);
        return result;
    }

    public byte[] translateImageRaw(byte[] imageBytes, String mimeType, String targetLanguageName) {
        return translateImageRaw(imageBytes, mimeType, targetLanguageName, null);
    }

    public byte[] translateImageRaw(byte[] imageBytes, String mimeType, String targetLanguageName,
                                    Consumer<TokenUsage> usageCallback) {
        return translateImageRaw(null, imageBytes, mimeType, targetLanguageName, usageCallback);
    }

    public byte[] translateImageRaw(AiAccount account, byte[] imageBytes, String mimeType, String targetLanguageName,
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
                .httpOptions(buildRequestHttpOptions(account, TIMEOUT_IMAGE_MS))
                .build();



        long start = System.currentTimeMillis();
        String model = getModel(account);
        Function<Client, GenerateContentResponse> action = c -> {
            log.info("[translateImage] 请求 Gemini: model={}, targetLang={}, mimeType={}, imageSize={}bytes, timeout={}ms",
                     model, targetLanguageName, mimeType, imageBytes.length, TIMEOUT_IMAGE_MS);
            return c.models.generateContent(model, content, config);
        };
        GenerateContentResponse response = account == null ? callGemini(action) : action.apply(buildClient(account, TIMEOUT_IMAGE_MS));
        long elapsed = System.currentTimeMillis() - start;

        logTokenUsage("translateImage", elapsed, response);
        emitTokenUsage(response, elapsed, usageCallback);

        return extractImageResult(response);
    }

    private byte[] extractImageResult(GenerateContentResponse response) {
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

    // ======================== Batch API 方法（固定用主 Key） ========================

    public String getModel() {
        return MODEL;
    }

    public String getModel(AiAccount account) {
        return account != null && account.getModel() != null && !account.getModel().isBlank()
                ? account.getModel()
                : MODEL;
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
        return uploadBatchFile(null, jsonlContent);
    }

    public String uploadBatchFile(AiAccount account, String jsonlContent) throws IOException {
        Client client = account == null ? primaryClient : buildClient(account, TIMEOUT_DEFAULT_MS);
        File tempFile = File.createTempFile("batch-translate-", ".jsonl");
        try {
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(jsonlContent);
            }
            log.info("[uploadBatchFile] 上传 JSONL 文件: size={}bytes", jsonlContent.length());
            var uploadedFile = client.files.upload(
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
        return createBatchJob(null, uploadedFileName);
    }

    public BatchJob createBatchJob(AiAccount account, String uploadedFileName) {
        Client client = account == null ? primaryClient : buildClient(account, TIMEOUT_DEFAULT_MS);
        BatchJobSource source = BatchJobSource.builder()
                .fileName(uploadedFileName)
                .build();
        CreateBatchJobConfig config = CreateBatchJobConfig.builder()
                .displayName("product-ai-translate-" + System.currentTimeMillis())
                .build();
        BatchJob job = client.batches.create(getModel(account), source, config);
        log.info("[createBatchJob] 批量任务已创建: name={}", job.name().orElse("N/A"));
        return job;
    }

    public BatchJob getBatchJob(String jobName) {
        return getBatchJob(null, jobName);
    }

    public BatchJob getBatchJob(AiAccount account, String jobName) {
        Client client = account == null ? primaryClient : buildClient(account, TIMEOUT_DEFAULT_MS);
        return client.batches.get(jobName, null);
    }

    public void cancelBatchJob(String jobName) {
        cancelBatchJob(null, jobName);
    }

    public void cancelBatchJob(AiAccount account, String jobName) {
        Client client = account == null ? primaryClient : buildClient(account, TIMEOUT_DEFAULT_MS);
        try {
            client.batches.cancel(jobName, null);
            log.info("[cancelBatchJob] 已取消: {}", jobName);
        } catch (Exception e) {
            log.warn("[cancelBatchJob] 取消失败: {}, error={}", jobName, e.getMessage());
        }
    }

    public void deleteBatchJob(String jobName) {
        deleteBatchJob(null, jobName);
    }

    public void deleteBatchJob(AiAccount account, String jobName) {
        Client client = account == null ? primaryClient : buildClient(account, TIMEOUT_DEFAULT_MS);
        try {
            client.batches.delete(jobName, null);
            log.info("[deleteBatchJob] 已删除: {}", jobName);
        } catch (Exception e) {
            log.warn("[deleteBatchJob] 删除失败: {}, error={}", jobName, e.getMessage());
        }
    }

    public String downloadBatchResult(String fileName) throws IOException {
        return downloadBatchResult(null, fileName);
    }

    public String downloadBatchResult(AiAccount account, String fileName) throws IOException {
        Client client = account == null ? primaryClient : buildClient(account, TIMEOUT_DEFAULT_MS);
        log.info("[downloadBatchResult] 下载结果文件: {}", fileName);
        File tempFile = File.createTempFile("batch-result-", ".jsonl");
        try {
            client.files.download(fileName, tempFile.getAbsolutePath(), null);
            String result = Files.readString(tempFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
            log.info("[downloadBatchResult] 下载完成: size={}bytes", result.length());
            return result;
        } finally {
            Files.deleteIfExists(tempFile.toPath());
        }
    }

    public void deleteFile(String fileName) {
        deleteFile(null, fileName);
    }

    public void deleteFile(AiAccount account, String fileName) {
        Client client = account == null ? primaryClient : buildClient(account, TIMEOUT_DEFAULT_MS);
        try {
            client.files.delete(fileName, null);
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
