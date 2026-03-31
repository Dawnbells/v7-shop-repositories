package cn.v7soft.admin.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;

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
