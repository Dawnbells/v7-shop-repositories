package cn.v7soft.admin.service.impl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.genai.Client;
import com.google.genai.types.BatchJob;
import com.google.genai.types.BatchJobSource;
import com.google.genai.types.CreateBatchJobConfig;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.google.genai.types.UploadFileConfig;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GeminiTranslateService {

    private static final String MODEL = "gemini-3.1-flash-image-preview";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Client client;

    public GeminiTranslateService(
            @Value("${gemini.api-key}") String apiKey,
            @Value("${gemini.proxy-host:}") String proxyHost,
            @Value("${gemini.proxy-port:0}") int proxyPort) {
        if (proxyHost != null && !proxyHost.isBlank()) {
            System.setProperty("http.proxyHost", proxyHost);
            System.setProperty("http.proxyPort", String.valueOf(proxyPort));
            System.setProperty("https.proxyHost", proxyHost);
            System.setProperty("https.proxyPort", String.valueOf(proxyPort));
            log.info("Gemini API 代理已配置: {}:{}", proxyHost, proxyPort);
        }
        this.client = Client.builder().apiKey(apiKey).build();
    }

    /**
     * 批量翻译短文本（title/summary/spec name/value 等）。
     * 将多段文本打包为一次 API 请求，返回翻译后的文本列表（与输入等长、顺序对应）。
     */
    public List<String> translateTexts(List<String> texts, String targetLanguageName) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        String numberedTexts = IntStream.range(0, texts.size())
                .mapToObj(i -> (i + 1) + ". " + texts.get(i))
                .collect(Collectors.joining("\n"));

        String prompt = """
                You are a professional e-commerce product translator.
                Translate the following numbered texts to %s.
                Rules:
                - Keep the same numbering format
                - Translate each line independently
                - Do NOT add, remove, or reorder any lines
                - Keep brand names, model numbers, and units unchanged
                - If a text is already in the target language, keep it as is
                - Output ONLY a JSON array of strings, in the same order as input
                - The array must have exactly %d elements
                
                Texts:
                %s
                """.formatted(targetLanguageName, texts.size(), numberedTexts);

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(Schema.builder()
                                        .type(Type.Known.ARRAY)
                                        .items(Schema.builder().type(Type.Known.STRING).build())
                                        .build())
                .temperature(0.1f)
                .build();

        log.info("[translateTexts] 请求 Gemini: model={}, textCount={}, targetLang={}", MODEL, texts.size(), targetLanguageName);
        log.debug("[translateTexts] prompt={}", prompt);

        long start = System.currentTimeMillis();
        GenerateContentResponse response = client.models.generateContent(MODEL, prompt, config);
        long elapsed = System.currentTimeMillis() - start;
        String json = response.text();

        logTokenUsage("translateTexts", elapsed, response);
        log.debug("[translateTexts] responseJson={}", json);

        try {
            List<String> result = OBJECT_MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
            if (result.size() != texts.size()) {
                log.warn("翻译结果数量不匹配: expected={}, actual={}", texts.size(), result.size());
                throw new RuntimeException("翻译结果数量不匹配");
            }
            return result;
        } catch (Exception e) {
            log.error("解析翻译结果失败, json={}", json, e);
            throw new RuntimeException("解析翻译结果失败", e);
        }
    }

    /**
     * 翻译 HTML 富文本。
     * 保留 HTML 标签结构不变，仅翻译可见文本内容。
     */
    public String translateHtml(String html, String targetLanguageName) {
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
                .build();

        String prompt = "Translate the text content in this HTML to "
                        + targetLanguageName + ":\n\n" + html;

        log.info("[translateHtml] 请求 Gemini: model={}, targetLang={}, htmlLength={}", MODEL, targetLanguageName, html.length());
        log.debug("[translateHtml] prompt={}", prompt);

        long start = System.currentTimeMillis();
        GenerateContentResponse response = client.models.generateContent(MODEL, prompt, config);
        long elapsed = System.currentTimeMillis() - start;
        String result = response.text();

        logTokenUsage("translateHtml", elapsed, response);
        log.debug("[translateHtml] responseText={}", result);

        return result;
    }

    /**
     * 翻译图片中的叠加设计文字。
     * 单次调用完成分析+翻译：无需翻译时返回 null，需要翻译时返回新图片字节数组。
     */
    public byte[] translateImage(byte[] imageBytes, String mimeType, String targetLanguageName) {
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
                .build();

        log.info("[translateImage] 请求 Gemini: model={}, targetLang={}, mimeType={}, imageSize={}bytes",
                MODEL, targetLanguageName, mimeType, imageBytes.length);
        log.debug("[translateImage] prompt={}", prompt);

        long start = System.currentTimeMillis();
        GenerateContentResponse response = client.models.generateContent(MODEL, content, config);
        long elapsed = System.currentTimeMillis() - start;

        logTokenUsage("translateImage", elapsed, response);

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
                int dataSize = blob.data().isPresent() ? ((byte[]) blob.data().get()).length : 0;
                log.info("[translateImage] part[{}] 类型=IMAGE, mimeType={}, dataSize={}bytes",
                        i, blob.mimeType().orElse("unknown"), dataSize);
                if (blob.data().isPresent()) {
                    imageResult = (byte[]) blob.data().get();
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

    // ======================== Batch API 方法 ========================

    public String getModel() {
        return MODEL;
    }

    /**
     * 构建文本翻译的 JSONL 行。
     */
    public String buildTextsTranslateJsonlEntry(String key, List<String> texts, String targetLanguageName) {
        String numberedTexts = IntStream.range(0, texts.size())
                .mapToObj(i -> (i + 1) + ". " + texts.get(i))
                .collect(Collectors.joining("\n"));

        String prompt = """
                You are a professional e-commerce product translator.
                Translate the following numbered texts to %s.
                Rules:
                - Keep the same numbering format
                - Translate each line independently
                - Do NOT add, remove, or reorder any lines
                - Keep brand names, model numbers, and units unchanged
                - If a text is already in the target language, keep it as is
                - Output ONLY a JSON array of strings, in the same order as input
                - The array must have exactly %d elements
                
                Texts:
                %s
                """.formatted(targetLanguageName, texts.size(), numberedTexts);

        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("key", key);

        ObjectNode request = root.putObject("request");
        ArrayNode contents = request.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", prompt);

        ObjectNode genConfig = request.putObject("generation_config");
        genConfig.put("response_mime_type", "application/json");
        genConfig.put("temperature", 0.1);
        ObjectNode thinkingConfig = genConfig.putObject("thinking_config");
        thinkingConfig.put("thinking_budget", 0);
        ObjectNode responseSchema = genConfig.putObject("response_schema");
        responseSchema.put("type", "ARRAY");
        responseSchema.putObject("items").put("type", "STRING");

        return root.toString();
    }

    /**
     * 构建 HTML 翻译的 JSONL 行。
     */
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
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", prompt);

        ObjectNode genConfig = request.putObject("generation_config");
        genConfig.put("temperature", 0.1);
        ObjectNode thinkingConfig = genConfig.putObject("thinking_config");
        thinkingConfig.put("thinking_budget", 0);

        return root.toString();
    }

    /**
     * 构建单张图片翻译的 JSONL 行。
     */
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
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");

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

    /**
     * 上传 JSONL 内容到 Gemini File API。
     * @return 上传后的文件名 (如 "files/xxx")
     */
    public String uploadBatchFile(String jsonlContent) throws IOException {
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

    /**
     * 创建 Batch Job。
     */
    public BatchJob createBatchJob(String uploadedFileName) {
        BatchJobSource source = BatchJobSource.builder()
                .fileName(uploadedFileName)
                .build();
        CreateBatchJobConfig config = CreateBatchJobConfig.builder()
                .displayName("product-ai-translate-" + System.currentTimeMillis())
                .build();
        BatchJob job = client.batches.create(MODEL, source, config);
        log.info("[createBatchJob] 批量任务已创建: name={}", job.name().orElse("N/A"));
        return job;
    }

    /**
     * 查询 Batch Job 状态。
     */
    public BatchJob getBatchJob(String jobName) {
        return client.batches.get(jobName, null);
    }

    /**
     * 取消 Batch Job。
     */
    public void cancelBatchJob(String jobName) {
        try {
            client.batches.cancel(jobName, null);
            log.info("[cancelBatchJob] 已取消: {}", jobName);
        } catch (Exception e) {
            log.warn("[cancelBatchJob] 取消失败: {}, error={}", jobName, e.getMessage());
        }
    }

    /**
     * 删除 Batch Job。
     */
    public void deleteBatchJob(String jobName) {
        try {
            client.batches.delete(jobName, null);
            log.info("[deleteBatchJob] 已删除: {}", jobName);
        } catch (Exception e) {
            log.warn("[deleteBatchJob] 删除失败: {}, error={}", jobName, e.getMessage());
        }
    }

    /**
     * 下载 Batch 结果文件内容。
     * @return 结果文件的文本内容 (JSONL 格式)
     */
    public String downloadBatchResult(String fileName) throws IOException {
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

    /**
     * 删除 Gemini File API 中的文件。
     */
    public void deleteFile(String fileName) {
        try {
            client.files.delete(fileName, null);
            log.info("[deleteFile] 已删除文件: {}", fileName);
        } catch (Exception e) {
            log.warn("[deleteFile] 删除文件失败: {}, error={}", fileName, e.getMessage());
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
